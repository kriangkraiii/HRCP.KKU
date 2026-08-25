package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;

/**
 * Chases signatures that have gone quiet, and closes rounds that ran out of time.
 *
 * <p>Follows the pattern of {@code EvaluationExpiryScheduler}: one daily pass,
 * cron configurable, everything guarded so one bad row cannot stop the rest.
 *
 * <p>A document waiting on one person is invisible to everyone else — the
 * initiator has no way of telling "not looked at yet" from "forgotten". These
 * reminders are what stop a request stalling for a month because someone missed
 * an email.
 */
@Service
public class SignatureReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SignatureReminderScheduler.class);

    private final SignatureRequestRepository requestRepository;
    private final SignatureStepRepository stepRepository;
    private final SignatureNotifier notifier;
    private final SignatureWorkflowService workflow;

    /**
     * Days of silence before nudging, and again between nudges.
     *
     * <p>Three days by default: long enough not to badger someone who is simply
     * busy, short enough that a forgotten document surfaces within the week.
     */
    @Value("${app.esign.reminder-after-days:3}")
    private int reminderAfterDays;

    public SignatureReminderScheduler(SignatureRequestRepository requestRepository,
            SignatureStepRepository stepRepository,
            SignatureNotifier notifier,
            SignatureWorkflowService workflow) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.notifier = notifier;
        this.workflow = workflow;
    }

    /**
     * Daily pass: nudge the quiet, expire the overdue.
     *
     * <p>08:30 by default — after the expiry mailer at 08:00, so the two do not
     * compete for the mail server or arrive as one indistinguishable burst.
     */
    @Scheduled(cron = "${app.esign.reminder-cron:0 30 8 * * *}")
    @Transactional
    public void run() {
        int reminded = sendReminders();
        Overdue overdue = handleOverdue();
        if (reminded > 0 || overdue.expired() > 0 || overdue.nudged() > 0) {
            log.info("Signature reminders: {} nudged, {} past their own reminder, {} expired",
                    reminded, overdue.nudged(), overdue.expired());
        }
    }

    /** Nudges whoever is holding up a document and has not acted recently. */
    private int sendReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, reminderAfterDays));
        int sent = 0;

        for (SignatureRequest envelope : requestRepository
                .findByStatusOrderByCreatedAtAsc(SignatureRequestStatus.IN_PROGRESS)) {
            try {
                for (SignatureStep step : stepRepository
                        .findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId())) {

                    if (step.getStatus() != SignatureStepStatus.ACTIVE || step.getSigner() == null) {
                        continue;
                    }

                    // Measure from the last time we contacted them, not from when
                    // the document was created — otherwise a long chain would
                    // reminder-spam whoever happens to be holding it.
                    LocalDateTime lastContact = step.getRemindedAt() != null
                            ? step.getRemindedAt()
                            : step.getNotifiedAt();
                    if (lastContact == null || lastContact.isAfter(cutoff)) {
                        continue;
                    }

                    step.setRemindedAt(LocalDateTime.now());
                    stepRepository.save(step);
                    notifier.notifyReminder(workflow.reminderNoticeFor(envelope, step));
                    sent++;
                }
            } catch (Exception e) {
                log.warn("Could not process reminders for envelope {}: {}", envelope.getId(), e.toString());
            }
        }
        return sent;
    }

    /**
     * Deals with rounds whose deadline has passed — most are closed, some are
     * only reminded.
     *
     * <p>Closing means {@code EXPIRED} rather than left open: an envelope past
     * its date already refuses signatures, so leaving it "in progress" would
     * show a document as pending that nobody can actually complete.
     *
     * <p>Rounds waiting on an applicant to sign their own unsubmitted request
     * are the exception — their date is a reminder they set for themselves, and
     * signatures are still accepted past it. Closing those would strand the
     * request: the document would count as unsigned, and submitting it needs
     * that signature.
     */
    private Overdue handleOverdue() {
        List<SignatureRequest> overdue = requestRepository
                .findByStatusAndDueAtBefore(SignatureRequestStatus.IN_PROGRESS, LocalDateTime.now());
        int expired = 0;
        int nudged = 0;

        for (SignatureRequest envelope : overdue) {
            try {
                SignatureStep advisoryStep = advisoryStepOf(envelope);
                if (advisoryStep != null) {
                    nudged += nudgePastReminder(envelope, advisoryStep);
                    continue;
                }
                workflow.expire(envelope.getId());
                expired++;
            } catch (Exception e) {
                log.warn("Could not expire envelope {}: {}", envelope.getId(), e.toString());
            }
        }
        return new Overdue(expired, nudged);
    }

    /** What one pass over the overdue rounds did. */
    private record Overdue(int expired, int nudged) {
    }

    /** The step holding this round, if its deadline is only a reminder. */
    private SignatureStep advisoryStepOf(SignatureRequest envelope) {
        return stepRepository.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> s.getStatus() == SignatureStepStatus.ACTIVE && s.getSigner() != null)
                .filter(workflow::deadlineIsAdvisory)
                .findFirst()
                .orElse(null);
    }

    /**
     * Tells someone the date they set for themselves has arrived.
     *
     * <p>Stamping {@code remindedAt} is what keeps this to one message: the
     * three-day rule in {@link #sendReminders()} reads the same field, so the
     * reminder does not repeat every morning for as long as the document sits
     * there. Reading it here too is what stops the same pass sending both a
     * nudge and this notice within minutes of each other.
     *
     * @return 1 when a message was sent, 0 when they were contacted recently
     */
    private int nudgePastReminder(SignatureRequest envelope, SignatureStep step) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, reminderAfterDays));
        LocalDateTime lastContact = step.getRemindedAt() != null ? step.getRemindedAt() : step.getNotifiedAt();
        if (lastContact != null && lastContact.isAfter(cutoff)) {
            return 0;
        }

        step.setRemindedAt(LocalDateTime.now());
        stepRepository.save(step);
        notifier.notifyDeadlineReached(workflow.reminderNoticeFor(envelope, step));
        return 1;
    }
}
