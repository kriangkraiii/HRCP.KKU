package com.ecom.academic.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.dto.SignatureNotice;
import com.ecom.academic.model.SignatureAuditEvent;
import com.ecom.academic.model.SignatureAuditEventType;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureAuditEventRepository;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Runs the signing chain: who is asked, in what order, and what is recorded.
 *
 * <p>Every state change goes through here, in the same spirit as
 * {@code AcademicRequestService.updateStatus} — one choke point that always
 * writes an audit event, so no path can advance a chain quietly.
 *
 * <p>The ordering rule ("ตามลำดับขั้น") is enforced by status, not by the UI:
 * exactly one step is {@link SignatureStepStatus#ACTIVE} at a time, and
 * {@link #sign} refuses anything else. Hiding the button would not be enough —
 * a signer further down the chain could otherwise POST directly.
 */
@Service
public class SignatureWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(SignatureWorkflowService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Characters for verification codes: no 0/O/1/I, so they can be read aloud. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;

    private final SignatureRequestRepository requestRepository;
    private final SignatureStepRepository stepRepository;
    private final SignatureAuditEventRepository auditRepository;
    private final SignatureAnchorRegistry anchorRegistry;
    private final UserSignatureService userSignatureService;
    private final UserRepository userRepository;
    private final SignatureNotifier notifier;
    private final StaffMemberService staffMemberService;
    private final SignatureVerificationService verificationService;
    private final SignedDocumentArchiver archiver;
    private final DocumentWorkflowConfigService workflowConfigService;
    private final DocumentSnapshotProvider snapshotProvider;
    private final com.ecom.service.AfterCommitRunner afterCommitRunner;

    public SignatureWorkflowService(
            SignatureRequestRepository requestRepository,
            SignatureStepRepository stepRepository,
            SignatureAuditEventRepository auditRepository,
            SignatureAnchorRegistry anchorRegistry,
            UserSignatureService userSignatureService,
            UserRepository userRepository,
            SignatureNotifier notifier,
            StaffMemberService staffMemberService,
            SignatureVerificationService verificationService,
            SignedDocumentArchiver archiver,
            DocumentWorkflowConfigService workflowConfigService,
            DocumentSnapshotProvider snapshotProvider,
            com.ecom.service.AfterCommitRunner afterCommitRunner) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.auditRepository = auditRepository;
        this.anchorRegistry = anchorRegistry;
        this.userSignatureService = userSignatureService;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.staffMemberService = staffMemberService;
        this.verificationService = verificationService;
        this.archiver = archiver;
        this.workflowConfigService = workflowConfigService;
        this.snapshotProvider = snapshotProvider;
        this.afterCommitRunner = afterCommitRunner;
    }

    /**
     * Who should sign one slot, as chosen by whoever sends the document out.
     */
    public record SignerAssignment(String slotKey, Integer signerUserId) {
    }

    /** Context captured from the HTTP request, for the evidence record. */
    public record ActorContext(String ipAddress, String userAgent) {
        public static ActorContext none() {
            return new ActorContext(null, null);
        }
    }

    /** Outcome of an operation: either it worked, or here is why it did not. */
    public record Result(SignatureRequest request, String error) {
        public boolean ok() {
            return error == null;
        }

        static Result failed(String error) {
            return new Result(null, error);
        }
    }

    // =====================================================================
    // Queries
    // =====================================================================

    /**
     * The envelope currently locking a document, if there is one.
     *
     * <p>Callers use this to refuse edits: while a document is out for signature
     * — or already fully signed — changing the form would invalidate signatures
     * that were given against the old content.
     */
    public Optional<SignatureRequest> findBlockingEnvelope(SignatureModule module, Long requestId, int documentType) {
        return requestRepository.findBlockingEnvelopes(module, requestId, documentType)
                .stream().findFirst();
    }

    /**
     * Finds the active or completed envelope holding signatures for a document.
     */
    public Optional<SignatureRequest> findEnvelope(SignatureModule module, Long requestId, int documentType) {
        return findBlockingEnvelope(module, requestId, documentType);
    }

    /** Whether the document form should be read-only right now. */
    public boolean isDocumentLocked(SignatureModule module, Long requestId, int documentType) {
        return findBlockingEnvelope(module, requestId, documentType).isPresent();
    }

    /**
     * The round the clock closed on this document, if one is waiting to be
     * revived.
     *
     * <p>An expired round holds real signatures and a frozen document, but
     * {@link #findBlockingEnvelope} does not return it — expiry releases the
     * form so people are not stuck. That leaves it off every page, which is
     * where "ขอขยายเวลา" used to lead nowhere: the signer asks, and the person
     * who could grant it has nothing to click. Offered only while no newer round
     * is open, so reviving can never collide with a live one.
     */
    public Optional<SignatureRequest> findRevivableEnvelope(SignatureModule module, Long requestId, int documentType) {
        if (isDocumentLocked(module, requestId, documentType)) {
            return Optional.empty();
        }
        return requestRepository
                .findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(module, requestId, documentType)
                .stream()
                .filter(e -> e.getStatus() == SignatureRequestStatus.EXPIRED)
                .findFirst();
    }

    public List<SignatureRequest> findForRequest(SignatureModule module, Long requestId) {
        return requestRepository.findByModuleAndRequestIdOrderByDocumentTypeAsc(module, requestId);
    }

    /** Everything awaiting this person's signature. */
    public List<SignatureStep> findInbox(UserDtls signer) {
        if (signer == null) return List.of();
        return stepRepository.findInbox(signer.getId(), SignatureStepStatus.ACTIVE);
    }

    /** Open envelopes sent/initiated by this user that are currently in progress. */
    public List<SignatureRequest> findSentEnvelopes(UserDtls initiator) {
        if (initiator == null || initiator.getId() == null) return List.of();
        return requestRepository.findByInitiatedByWithSteps(initiator.getId(), SignatureRequestStatus.IN_PROGRESS);
    }

    /** All open envelopes across the system (for Admin/Staff overview). */
    public List<SignatureRequest> findAllActiveEnvelopes() {
        return requestRepository.findByStatusWithSteps(SignatureRequestStatus.IN_PROGRESS);
    }

    /** Finds the next pending signature step for a signer excluding current step. */
    public Optional<SignatureStep> findNextPendingStep(UserDtls signer, Long currentStepId) {
        if (signer == null) return Optional.empty();
        List<SignatureStep> inbox = findInbox(signer);
        return inbox.stream()
                .filter(s -> currentStepId == null || !s.getId().equals(currentStepId))
                .findFirst();
    }

    /**
     * Cancels all open signature envelopes and marks pending steps as SKIPPED for a request
     * when the request itself or its draft is cancelled/deleted.
     */
    @Transactional
    public void cancelAllForRequest(SignatureModule module, Long requestId, UserDtls actingUser, String reason) {
        List<SignatureRequest> envelopes = requestRepository.findByModuleAndRequestIdOrderByDocumentTypeAsc(module, requestId);
        for (SignatureRequest envelope : envelopes) {
            if (envelope.getStatus().isOpen()) {
                envelope.setStatus(SignatureRequestStatus.CANCELLED);
                envelope.setCancelledAt(LocalDateTime.now());
                envelope.setCancelReason(truncate(reason != null ? reason : "ยกเลิกแบบร่างคำร้อง", 500));
                if (envelope.getSteps() != null) {
                    envelope.getSteps().stream()
                            .filter(s -> s.getStatus() == SignatureStepStatus.WAITING
                                    || s.getStatus() == SignatureStepStatus.ACTIVE)
                            .forEach(s -> s.setStatus(SignatureStepStatus.SKIPPED));
                }
                requestRepository.save(envelope);
                audit(envelope, null, SignatureAuditEventType.CANCELLED, actingUser, ActorContext.none(),
                        reason != null ? reason : "ยกเลิกแบบร่างคำร้อง");
            }
        }
    }

    /**
     * Extends the due date of a signature envelope, reviving it if the clock
     * already closed it.
     *
     * <p>An {@code EXPIRED} envelope is accepted deliberately. Expiry is the one
     * terminal state nobody chose — the scheduler reached it on its own — and
     * the overdue banner offers "ขอขยายเวลา" as the way out, so refusing to
     * extend afterwards would send people to a button that leads nowhere. A
     * cancelled or fully-signed round stays closed: those ended because someone
     * decided they should.
     */
    @Transactional
    public Result extendDueDate(Long envelopeId, LocalDateTime newDueAt, UserDtls actor, ActorContext context) {
        SignatureRequest envelope = requestRepository.findByIdWithSteps(envelopeId).orElse(null);
        if (envelope == null) {
            return Result.failed("ไม่พบรายการเวียนลงนามนี้");
        }
        SignatureRequestStatus status = envelope.getStatus();
        if (!status.isOpen() && status != SignatureRequestStatus.EXPIRED) {
            return Result.failed("ไม่สามารถขยายเวลาของเอกสารที่ลงนามครบหรือยกเลิกแล้ว");
        }
        if (newDueAt != null && !newDueAt.isAfter(LocalDateTime.now())) {
            return Result.failed("กรุณาระบุกำหนดเวลาใหม่ที่ยังมาไม่ถึง");
        }

        boolean reviving = status == SignatureRequestStatus.EXPIRED;
        if (reviving && isDocumentLocked(envelope.getModule(), envelope.getRequestId(),
                envelope.getDocumentType())) {
            // Somebody already started the document over. Two open rounds on one
            // document would mean two frozen copies collecting signatures, and
            // no answer to which one the document actually is.
            return Result.failed("เอกสารฉบับนี้มีการเวียนลงนามรอบใหม่แล้ว จึงเปิดรอบเดิมขึ้นมาอีกไม่ได้");
        }

        envelope.setDueAt(newDueAt);

        if (reviving) {
            envelope.setStatus(SignatureRequestStatus.IN_PROGRESS);
            // Put back only the turns the clock took away. Every persisted step
            // has a signer — an unassigned slot never becomes a step at all —
            // so a skipped, unsigned step can only be one expiry cut short.
            envelope.getSteps().stream()
                    .filter(s -> s.getStatus() == SignatureStepStatus.SKIPPED && s.getSignedAt() == null)
                    .forEach(s -> s.setStatus(SignatureStepStatus.WAITING));
        }

        SignatureRequest saved = requestRepository.save(envelope);

        audit(saved, null, SignatureAuditEventType.DUE_EXTENDED, actor, context,
                (reviving ? "เปิดการเวียนลงนามอีกครั้ง และขยาย" : "ขยาย")
                        + "กำหนดเวลาลงนามเป็น: " + (newDueAt != null ? newDueAt.toString() : "ไม่จำกัด"));

        if (reviving) {
            // Goes through the normal activation so the staff review gate still
            // applies: reviving a round must not push a document onward that
            // nobody has read.
            activateNextStep(saved, context);
            saved = requestRepository.save(saved);
        }

        // Notify currently active signer about the extension
        SignatureRequest notified = saved;
        notified.activeStep().ifPresent(step -> {
            if (step.getSigner() != null) {
                notifier.notifySignatureRequested(noticeFor(notified, step, List.of(step.getSigner())));
            }
        });

        return new Result(saved, null);
    }

    /** Records a due date extension request from a signer to the initiator. */
    @Transactional
    public Result requestExtension(Long stepId, String reason, UserDtls signer, ActorContext context) {
        SignatureStep step = stepRepository.findByIdWithRequest(stepId).orElse(null);
        if (step == null) {
            return Result.failed("ไม่พบรายการลงนาม");
        }
        SignatureRequest envelope = step.getSignatureRequest();
        if (envelope == null || !envelope.getStatus().isOpen()) {
            return Result.failed("คำขอลงนามนี้ปิดไปแล้ว");
        }
        // The initiator of an unsubmitted request is the applicant themselves,
        // so this would post them a request addressed to themselves. The page
        // hides the button; this refuses the route.
        if (deadlineIsAdvisory(step)) {
            return Result.failed("กำหนดเวลานี้เป็นเพียงการแจ้งเตือน คุณยังลงนามได้ตามปกติ");
        }

        audit(envelope, step.getId(), SignatureAuditEventType.EXTENSION_REQUESTED, signer, context,
                "ผู้ลงนามขอขยายเวลา: " + (reason != null && !reason.isBlank() ? reason : "ไม่ระบุเหตุผล"));

        UserDtls initiator = envelope.getInitiatedBy();
        if (initiator != null) {
            notifier.notifyExtensionRequested(initiator, envelope, signer, reason);
        }

        return new Result(envelope, null);
    }

    public long countPending(UserDtls signer) {
        return stepRepository.countPendingFor(signer.getId());
    }

    public Optional<SignatureStep> findStep(Long stepId) {
        return stepRepository.findByIdWithRequest(stepId);
    }

    public List<SignatureAuditEvent> auditTrail(Long signatureRequestId) {
        return auditRepository.findBySignatureRequestIdOrderByCreatedAtAsc(signatureRequestId);
    }

    /** One envelope with its steps, for permission checks and detail views. */
    public Optional<SignatureRequest> findEnvelope(Long envelopeId) {
        return requestRepository.findByIdWithSteps(envelopeId);
    }

    public Optional<SignatureRequest> findByVerificationCode(String code) {
        return requestRepository.findByVerificationCode(code);
    }

    /** With steps loaded, for the verification page. */
    public Optional<SignatureRequest> findByVerificationCodeWithSteps(String code) {
        return requestRepository.findByVerificationCodeWithSteps(code);
    }

    /**
     * Checks whether the applicant has completed their signature step for a specific document.
     */
    public boolean isApplicantSignatureCompleted(SignatureModule module, Long requestId, int documentType) {
        List<SignatureRequest> envelopes = requestRepository.findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(module, requestId, documentType);
        if (envelopes.isEmpty()) {
            return false;
        }
        for (SignatureRequest env : envelopes) {
            if (env.getStatus() == SignatureRequestStatus.COMPLETED || env.getStatus() == SignatureRequestStatus.IN_PROGRESS) {
                List<SignatureStep> steps = stepRepository.findBySignatureRequestIdOrderByStepOrderAsc(env.getId());
                boolean hasCompletedApplicant = steps.stream()
                        .anyMatch(s -> "applicant".equals(s.getSlotKey()) && (s.getStatus() == SignatureStepStatus.SIGNED || s.getSignedAt() != null));
                if (hasCompletedApplicant) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether this document has a place for the applicant to sign.
     *
     * <p>Answered from the configured slots, not the built-in ones: if an
     * administrator has removed the applicant's slot, the form no longer offers
     * anywhere to sign, and demanding that signature before the request may be
     * submitted would leave the applicant stuck with no way out.
     */
    private boolean requiresApplicantSignature(SignatureModule module, int documentType) {
        return slotsFor(module, documentType).stream()
                .anyMatch(slot -> "applicant".equalsIgnoreCase(slot.slotKey()));
    }

    /**
     * Whether this step's deadline is a reminder rather than a gate.
     *
     * <p>True only for the applicant signing their own part of a request they
     * have not submitted yet. Nothing is waiting on them but themselves, so a
     * lapsed date has nobody to protect — and refusing the signature would only
     * block the submission the date existed to hurry along, because an unsigned
     * document keeps the request from being submitted at all. Asking them to
     * request an extension is asking them to petition themselves.
     *
     * <p>Every other step keeps the hard deadline. The ตามลำดับขั้น chain is
     * exactly where a date does protect someone: a document parked with one
     * signer is invisible to everyone behind them.
     */
    public boolean deadlineIsAdvisory(SignatureStep step) {
        if (step == null || !"applicant".equalsIgnoreCase(step.getSlotKey())) {
            return false;
        }
        SignatureRequest envelope = step.getSignatureRequest();
        if (envelope == null || snapshotProvider == null) {
            return false;
        }
        return snapshotProvider.isDraftRequest(envelope.getModule(), envelope.getRequestId());
    }

    /**
     * Checks whether applicant signatures are completed for all required documents.
     */
    public boolean areApplicantSignaturesComplete(SignatureModule module, Long requestId, List<Integer> requiredDocTypes) {
        for (int docType : requiredDocTypes) {
            boolean requiresApplicant = requiresApplicantSignature(module, docType);
            if (requiresApplicant && !isApplicantSignatureCompleted(module, requestId, docType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the list of document types that require applicant signature but are not yet signed.
     */
    public List<Integer> getUnsignedApplicantDocTypes(SignatureModule module, Long requestId, List<Integer> docTypes) {
        List<Integer> unsigned = new ArrayList<>();
        for (int docType : docTypes) {
            boolean requiresApplicant = requiresApplicantSignature(module, docType);
            if (requiresApplicant && !isApplicantSignatureCompleted(module, requestId, docType)) {
                unsigned.add(docType);
            }
        }
        return unsigned;
    }

    // =====================================================================
    // Creating an envelope
    // =====================================================================

    /**
     * Freezes a document and sends it to the first signer.
     *
     * @param frozenJson the document's form data at this moment; the chain signs
     *                   this snapshot, not whatever the form holds later
     */
    @Transactional
    public Result createEnvelope(SignatureModule module, Long requestId, int documentType,
            String documentLabel, String frozenJson, List<SignerAssignment> assignments,
            LocalDateTime dueAt, UserDtls initiator, ActorContext actor) {

        List<SignatureSlot> slots = slotsFor(module, documentType);
        if (slots.isEmpty()) {
            return Result.failed("เอกสารฉบับนี้ไม่มีจุดลงนาม จึงส่งไปลงนามไม่ได้");
        }
        if (frozenJson == null || frozenJson.isBlank()) {
            return Result.failed("ยังไม่มีข้อมูลในเอกสาร — กรุณาบันทึกเอกสารก่อนส่งไปลงนาม");
        }
        if (isDocumentLocked(module, requestId, documentType)) {
            return Result.failed("เอกสารฉบับนี้อยู่ระหว่างการเวียนลงนามหรือลงนามครบแล้ว");
        }
        if (assignments == null || assignments.isEmpty()) {
            return Result.failed("กรุณาเลือกผู้ลงนามอย่างน้อยหนึ่งคน");
        }

        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(module);
        envelope.setRequestId(requestId);
        envelope.setDocumentType(documentType);
        envelope.setDocumentLabel(documentLabel);
        envelope.setStatus(SignatureRequestStatus.IN_PROGRESS);
        envelope.setInitiatedBy(initiator);
        envelope.setFrozenJson(frozenJson);
        envelope.setFrozenHash(sha256(frozenJson));
        envelope.setVerificationCode(newVerificationCode());
        envelope.setDueAt(dueAt);

        String problem = addStepsForAssignments(envelope, slots, assignments);
        if (problem != null) {
            return Result.failed(problem);
        }

        if (envelope.getSteps().isEmpty()) {
            return Result.failed("กรุณาเลือกผู้ลงนามอย่างน้อยหนึ่งคน");
        }

        SignatureRequest saved = requestRepository.save(envelope);
        audit(saved, null, SignatureAuditEventType.CREATED, initiator, actor,
                "ส่งเอกสารไปลงนาม " + saved.getSteps().size() + " ขั้นตอน");

        activateNextStep(saved, actor);
        return new Result(requestRepository.save(saved), null);
    }

    /**
     * Attaches one step per assigned slot to an envelope, in slot order.
     *
     * <p>Shared by starting a round and by forwarding one onward, so both build
     * steps the same way. Slots left unassigned are simply not part of the round
     * yet — a document may legitimately need only some of its signatures now.
     *
     * @return an error message to show the sender, or null when all is well
     */
    private String addStepsForAssignments(SignatureRequest envelope, List<SignatureSlot> slots,
            List<SignerAssignment> assignments) {

        int order = envelope.getSteps().stream()
                .mapToInt(SignatureStep::getStepOrder)
                .max()
                .orElse(0);

        for (SignatureSlot slot : slots) {
            SignerAssignment assignment = assignments.stream()
                    .filter(a -> a.slotKey().equals(slot.slotKey()))
                    .findFirst()
                    .orElse(null);
            if (assignment == null || assignment.signerUserId() == null) {
                continue;
            }
            // Never ask the same position twice in one round.
            boolean alreadyPresent = envelope.getSteps().stream()
                    .anyMatch(existing -> slot.slotKey().equalsIgnoreCase(existing.getSlotKey())
                            && existing.getStatus() != SignatureStepStatus.SKIPPED);
            if (alreadyPresent) {
                continue;
            }

            UserDtls signer = userRepository.findById(assignment.signerUserId()).orElse(null);
            if (signer == null) {
                return "ไม่พบบัญชีผู้ลงนามสำหรับ \"" + slot.roleLabel() + "\"";
            }

            order++;
            SignatureStep step = new SignatureStep();
            step.setStepOrder(order);
            step.setSlotKey(slot.slotKey());
            step.setRoleLabel(slot.roleLabel());
            step.setAnchorPlaceholder(slot.anchorPlaceholder());
            step.setSigner(signer);
            step.setSignerNameSnapshot(signer.getName());
            step.setSignerPositionSnapshot(signer.getAcademicPosition());
            step.setStatus(SignatureStepStatus.WAITING);

            envelope.addStep(step);
        }
        return null;
    }

    /**
     * Adds the next tier of signers to a round that is already under way.
     *
     * <p>This is the administrator's half of the review gate: the applicant
     * signs and submits, staff check the document, and only then is it sent on
     * to the head of department and the dean. Without it a round could never
     * grow past the signatures chosen when it was first sent, which for a
     * document whose only initial slot is the applicant's meant it could never
     * be circulated at all.
     *
     * <p>A round already marked complete is reopened: a document whose applicant
     * signature is finished is exactly the one waiting to be forwarded.
     */
    @Transactional
    public Result forwardToNextSigners(Long envelopeId, List<SignerAssignment> assignments,
            LocalDateTime dueAt, UserDtls adminUser, ActorContext actor) {

        SignatureRequest envelope = requestRepository.findByIdWithSteps(envelopeId).orElse(null);
        if (envelope == null) {
            return Result.failed("ไม่พบคำขอลงนาม");
        }
        if (assignments == null || assignments.isEmpty()) {
            return Result.failed("กรุณาเลือกผู้ลงนามอย่างน้อยหนึ่งคน");
        }
        SignatureRequestStatus status = envelope.getStatus();
        if (!status.isOpen() && status != SignatureRequestStatus.COMPLETED) {
            return Result.failed("คำขอลงนามนี้ถูกปิดไปแล้ว (" + status.getThaiLabel() + ")");
        }

        List<SignatureSlot> slots = slotsFor(envelope.getModule(), envelope.getDocumentType());

        int before = envelope.getSteps().size();
        String problem = addStepsForAssignments(envelope, slots, assignments);
        if (problem != null) {
            return Result.failed(problem);
        }
        if (envelope.getSteps().size() == before) {
            return Result.failed("ผู้ลงนามที่เลือกถูกเพิ่มไว้ในรอบนี้อยู่แล้ว");
        }

        if (dueAt != null) {
            envelope.setDueAt(dueAt);
        }

        // Choosing who signs next is itself the act of releasing the document,
        // so staff are not made to press a second button to mean the same thing.
        if (!envelope.isCirculationStarted()) {
            envelope.setCirculationStartedAt(LocalDateTime.now());
        }

        // Reopen a finished round so the freshly added steps can run.
        if (status == SignatureRequestStatus.COMPLETED) {
            envelope.setStatus(SignatureRequestStatus.IN_PROGRESS);
            envelope.setCompletedAt(null);
        }

        audit(envelope, null, SignatureAuditEventType.FORWARDED, adminUser, actor,
                "ส่งเวียนลงนามต่ออีก " + (envelope.getSteps().size() - before) + " ขั้นตอน");

        activateNextStep(envelope, actor);
        return new Result(requestRepository.save(envelope), null);
    }

    // =====================================================================
    // Signing
    // =====================================================================

    /**
     * Records a signature and moves the chain along.
     *
     * <p>Every precondition is checked here rather than in the controller. The
     * signing page hides what a person may not do, but the page is not the
     * boundary — a direct POST is.
     */
    @Transactional
    public Result sign(Long stepId, UserDtls actingUser, Long userSignatureId,
            boolean consentAccepted, ActorContext actor) {

        SignatureStep step = stepRepository.findByIdWithRequest(stepId).orElse(null);
        if (step == null) {
            return Result.failed("ไม่พบรายการลงนาม");
        }
        SignatureRequest envelope = step.getSignatureRequest();

        // 1. This must be the person who was asked.
        if (step.getSigner() == null || !step.getSigner().getId().equals(actingUser.getId())) {
            return Result.failed("คุณไม่ใช่ผู้ที่ได้รับมอบหมายให้ลงนามในขั้นตอนนี้");
        }
        // 2. It must be their turn — this is the sequencing rule.
        if (step.getStatus() != SignatureStepStatus.ACTIVE) {
            return Result.failed("ยังไม่ถึงคิวลงนามของคุณ หรือขั้นตอนนี้ถูกดำเนินการไปแล้ว");
        }
        // 3. The envelope must still be open.
        if (!envelope.getStatus().isOpen()) {
            return Result.failed("คำขอลงนามนี้ถูกปิดไปแล้ว (" + envelope.getStatus().getThaiLabel() + ")");
        }
        if (envelope.isOverdue() && !deadlineIsAdvisory(step)) {
            return Result.failed("เลยกำหนดเวลาลงนามแล้ว กรุณาติดต่อผู้ส่งเอกสาร");
        }
        // 4. Consent is not optional — it is what makes this a signature.
        if (!consentAccepted) {
            return Result.failed("กรุณากดยอมรับข้อความยินยอมก่อนลงนาม");
        }
        // 5. The signature must be one of theirs.
        UserSignature signature = userSignatureService.findMine(userSignatureId, actingUser).orElse(null);
        if (signature == null) {
            return Result.failed("ไม่พบลายเซ็นที่เลือก — กรุณาสร้างลายเซ็นในหน้า \"ลายเซ็นของฉัน\" ก่อน");
        }
        // 6. The frozen content must be intact. Belt and braces: the form is
        //    locked while an envelope is open, so reaching this means something
        //    wrote to the row directly.
        if (!sha256(envelope.getFrozenJson()).equals(envelope.getFrozenHash())) {
            envelope.setStatus(SignatureRequestStatus.VOIDED);
            requestRepository.save(envelope);
            audit(envelope, step.getId(), SignatureAuditEventType.VOIDED, actingUser, actor,
                    "เนื้อหาเอกสารไม่ตรงกับที่บันทึกไว้ตอนส่งลงนาม");
            return Result.failed("เนื้อหาเอกสารถูกแก้ไขหลังส่งลงนาม ลายเซ็นทั้งหมดเป็นโมฆะ");
        }

        step.setStatus(SignatureStepStatus.SIGNED);
        step.setSignedAt(LocalDateTime.now());
        step.setUserSignature(signature);
        step.setImagePathSnapshot(signature.getImagePath());
        step.setConsentAccepted(true);
        step.setConsentTextVersion(SignatureStep.CONSENT_TEXT_VERSION);
        step.setAuthMethod(SignatureStep.AUTH_METHOD_SESSION);
        step.setIpAddress(actor.ipAddress());
        step.setUserAgent(truncate(actor.userAgent(), 500));
        step.setDocHashSigned(envelope.getFrozenHash());

        // Seal last: it covers every field above, so it has to be computed once
        // they are all set. Needs an id, hence the save either side.
        stepRepository.saveAndFlush(step);
        step.setEvidenceHmac(verificationService.seal(step));
        stepRepository.save(step);

        audit(envelope, step.getId(), SignatureAuditEventType.SIGNED, actingUser, actor,
                "ลงนามในตำแหน่ง \"" + step.getRoleLabel() + "\"");

        activateNextStep(envelope, actor);
        return new Result(requestRepository.save(envelope), null);
    }

    /** Notes that the signer opened the document, before they decide. */
    @Transactional
    public void recordView(SignatureStep step, UserDtls viewer, ActorContext actor) {
        if (step.getViewedAt() != null) {
            return; // first look is the one worth recording
        }
        step.setViewedAt(LocalDateTime.now());
        stepRepository.save(step);
        audit(step.getSignatureRequest(), step.getId(), SignatureAuditEventType.VIEWED, viewer, actor,
                "เปิดดูเอกสารก่อนลงนาม");
    }

    /**
     * Refuses to sign, ending the round.
     *
     * <p>A decline stops the whole chain rather than skipping a step: the people
     * after this person were going to sign on the assumption that everyone
     * before them had agreed.
     */
    @Transactional
    public Result decline(Long stepId, UserDtls actingUser, String reason, ActorContext actor) {
        SignatureStep step = stepRepository.findByIdWithRequest(stepId).orElse(null);
        if (step == null) {
            return Result.failed("ไม่พบรายการลงนาม");
        }
        SignatureRequest envelope = step.getSignatureRequest();

        if (step.getSigner() == null || !step.getSigner().getId().equals(actingUser.getId())) {
            return Result.failed("คุณไม่ใช่ผู้ที่ได้รับมอบหมายให้ลงนามในขั้นตอนนี้");
        }
        if (step.getStatus() != SignatureStepStatus.ACTIVE) {
            return Result.failed("ขั้นตอนนี้ถูกดำเนินการไปแล้ว");
        }
        if (reason == null || reason.isBlank()) {
            return Result.failed("กรุณาระบุเหตุผลที่ปฏิเสธการลงนาม");
        }

        step.setStatus(SignatureStepStatus.DECLINED);
        step.setDeclinedAt(LocalDateTime.now());
        step.setDeclineReason(truncate(reason.trim(), 500));
        step.setIpAddress(actor.ipAddress());
        step.setUserAgent(truncate(actor.userAgent(), 500));
        stepRepository.save(step);

        envelope.setStatus(SignatureRequestStatus.DECLINED);
        envelope.getSteps().stream()
                .filter(s -> s.getStatus() == SignatureStepStatus.WAITING)
                .forEach(s -> s.setStatus(SignatureStepStatus.SKIPPED));

        audit(envelope, step.getId(), SignatureAuditEventType.DECLINED, actingUser, actor,
                "ปฏิเสธการลงนาม: " + step.getDeclineReason());

        notifier.notifyDeclined(noticeFor(envelope, step, List.of(envelope.getInitiatedBy())));
        return new Result(requestRepository.save(envelope), null);
    }

    /**
     * Closes a round whose deadline has passed.
     *
     * <p>Separate from {@link #cancel} because nobody decided this — it is the
     * clock, and the audit trail should say so.
     */
    @Transactional
    public Result expire(Long envelopeId) {
        SignatureRequest envelope = requestRepository.findByIdWithSteps(envelopeId).orElse(null);
        if (envelope == null) {
            return Result.failed("ไม่พบคำขอลงนาม");
        }
        if (!envelope.getStatus().isOpen()) {
            return Result.failed("คำขอลงนามนี้ปิดไปแล้ว");
        }

        envelope.setStatus(SignatureRequestStatus.EXPIRED);
        envelope.getSteps().stream()
                .filter(s -> s.getStatus() == SignatureStepStatus.WAITING
                        || s.getStatus() == SignatureStepStatus.ACTIVE)
                .forEach(s -> s.setStatus(SignatureStepStatus.SKIPPED));

        audit(envelope, null, SignatureAuditEventType.EXPIRED, null, ActorContext.none(),
                "เลยกำหนดลงนาม ระบบปิดคำขอโดยอัตโนมัติ");

        notifier.notifyCancelled(noticeFor(envelope, null,
                envelope.getSteps().stream()
                        .filter(s -> s.getSigner() != null && s.getSignedAt() == null)
                        .map(SignatureStep::getSigner)
                        .toList()));

        return new Result(requestRepository.save(envelope), null);
    }

    /** A reminder's payload, resolved while the entities are still attached. */
    public SignatureNotice reminderNoticeFor(SignatureRequest envelope, SignatureStep step) {
        return noticeFor(envelope, step, List.of(step.getSigner()));
    }

    /** Withdraws a circulating document, releasing the lock on its form. */
    @Transactional
    public Result cancel(Long envelopeId, UserDtls actingUser, String reason, ActorContext actor) {
        SignatureRequest envelope = requestRepository.findById(envelopeId).orElse(null);
        if (envelope == null) {
            return Result.failed("ไม่พบคำขอลงนาม");
        }
        if (!envelope.getStatus().isOpen()) {
            return Result.failed("คำขอลงนามนี้ปิดไปแล้ว");
        }

        envelope.setStatus(SignatureRequestStatus.CANCELLED);
        envelope.setCancelledAt(LocalDateTime.now());
        envelope.setCancelReason(truncate(reason, 500));
        envelope.getSteps().stream()
                .filter(s -> s.getStatus() == SignatureStepStatus.WAITING
                        || s.getStatus() == SignatureStepStatus.ACTIVE)
                .forEach(s -> s.setStatus(SignatureStepStatus.SKIPPED));

        audit(envelope, null, SignatureAuditEventType.CANCELLED, actingUser, actor,
                reason == null || reason.isBlank() ? "ยกเลิกการเวียนลงนาม" : reason);

        // Recipients resolved here, inside the transaction: the notifier runs on
        // another thread and could not walk these associations itself.
        List<UserDtls> outstanding = envelope.getSteps().stream()
                .filter(s2 -> s2.getSigner() != null && s2.getSignedAt() == null)
                .map(SignatureStep::getSigner)
                .toList();
        notifier.notifyCancelled(noticeFor(envelope, null, outstanding));
        return new Result(requestRepository.save(envelope), null);
    }

    /**
     * Admin requests document correction and re-signing by the applicant.
     * Cancels any active/completed blocking envelope, releases the edit lock on the document,
     * and sends real-time Email + In-App notification to the applicant.
     */
    @Transactional
    public Result requestDocumentResign(SignatureModule module, Long requestId, int documentType,
            String reason, UserDtls adminUser, ActorContext actor) {
        List<SignatureRequest> blocking = requestRepository.findBlockingEnvelopes(module, requestId, documentType);
        String docLabel = snapshotProvider.labelFor(module, documentType);

        for (SignatureRequest envelope : blocking) {
            envelope.setStatus(SignatureRequestStatus.CANCELLED);
            envelope.setCancelledAt(LocalDateTime.now());
            envelope.setCancelReason(truncate(reason != null && !reason.isBlank()
                    ? "แอดมินส่งกลับให้แก้ไขและลงนามใหม่: " + reason
                    : "แอดมินส่งกลับให้แก้ไขและลงนามใหม่", 500));
            envelope.getSteps().forEach(s -> {
                if (s.getStatus() == SignatureStepStatus.WAITING || s.getStatus() == SignatureStepStatus.ACTIVE) {
                    s.setStatus(SignatureStepStatus.SKIPPED);
                }
            });
            requestRepository.save(envelope);
            audit(envelope, null, SignatureAuditEventType.DECLINED, adminUser, actor,
                    "แอดมินส่งกลับให้แก้ไขและลงนามใหม่: " + (reason != null && !reason.isBlank() ? reason : "-"));
        }

        UserDtls applicant = snapshotProvider.applicantOf(module, requestId);
        if (applicant != null) {
            notifier.notifyResignRequested(applicant, module, requestId, documentType, docLabel, reason);
        }

        return new Result(blocking.isEmpty() ? null : blocking.get(0), null);
    }

    // =====================================================================
    // Chain advancement
    // =====================================================================

    /**
     * Promotes the next waiting step, or completes the envelope.
     *
     * <p>Called after every signature. Keeping it in one place is what stops the
     * chain from ever having two active steps or none.
     */
    private void activateNextStep(SignatureRequest envelope, ActorContext actor) {
        if (envelope.allSigned()) {
            envelope.setStatus(SignatureRequestStatus.COMPLETED);
            envelope.setCompletedAt(LocalDateTime.now());
            audit(envelope, null, SignatureAuditEventType.COMPLETED, null, actor,
                    "ลงนามครบทุกขั้นตอน");
            notifier.notifyCompleted(noticeFor(envelope, null, List.of(envelope.getInitiatedBy())));
            // Archived off-thread: PDF conversion is slow and must not extend
            // the signing transaction. Run after commit to prevent optimistic locking race.
            if (afterCommitRunner != null) {
                afterCommitRunner.run(() -> archiver.archive(envelope.getId()));
            } else {
                archiver.archive(envelope.getId());
            }
            return;
        }

        Optional<SignatureStep> next = envelope.nextWaitingStep();
        if (next.isEmpty()) {
            return; // an active step is already outstanding
        }
        if (envelope.activeStep().isPresent()) {
            return;
        }

        SignatureStep step = next.get();

        // The review gate. The applicant signs their own part whenever they like,
        // but everyone after them waits until staff have read the document and
        // released it — a mistake must not reach the dean because the system
        // forwarded it on its own.
        if (!"applicant".equalsIgnoreCase(step.getSlotKey()) && !envelope.isCirculationStarted()) {
            log.info("Holding signature step {} ({}) for envelope {}: staff have not released it for circulation yet",
                    step.getId(), step.getRoleLabel(), envelope.getId());
            return;
        }

        step.setStatus(SignatureStepStatus.ACTIVE);
        step.setNotifiedAt(LocalDateTime.now());
        stepRepository.save(step);

        audit(envelope, step.getId(), SignatureAuditEventType.NOTIFIED, null, actor,
                "แจ้งเตือน " + step.getSignerNameSnapshot() + " ให้ลงนาม");
        notifier.notifySignatureRequested(noticeFor(envelope, step, List.of(step.getSigner())));
    }

    /**
     * Staff have checked this document; let it go to the signers after the
     * applicant.
     *
     * <p>Deliberately per document rather than per request: each one is read on
     * its own, and some are ready to circulate while others still need fixing.
     *
     * @return the released envelope, or an error to show the person who asked
     */
    @Transactional
    public Result startCirculation(Long envelopeId, UserDtls staff, ActorContext actor) {
        SignatureRequest envelope = requestRepository.findByIdWithSteps(envelopeId).orElse(null);
        if (envelope == null) {
            return Result.failed("ไม่พบคำขอลงนาม");
        }
        if (!envelope.getStatus().isOpen()) {
            return Result.failed("คำขอลงนามนี้ถูกปิดไปแล้ว (" + envelope.getStatus().getThaiLabel() + ")");
        }
        if (envelope.isCirculationStarted()) {
            return Result.failed("เอกสารฉบับนี้ถูกส่งเวียนลงนามไปแล้ว");
        }
        boolean anyoneToAsk = envelope.getSteps().stream()
                .anyMatch(step -> !"applicant".equalsIgnoreCase(step.getSlotKey())
                        && step.getStatus() == SignatureStepStatus.WAITING);
        if (!anyoneToAsk) {
            return Result.failed("ยังไม่มีผู้ลงนามลำดับถัดไปให้ส่งต่อ กรุณาเลือกผู้ลงนามก่อน");
        }

        envelope.setCirculationStartedAt(LocalDateTime.now());
        audit(envelope, null, SignatureAuditEventType.FORWARDED, staff, actor,
                "เจ้าหน้าที่ตรวจสอบเอกสารแล้ว และเริ่มส่งเวียนลงนาม");

        activateNextStep(envelope, actor);
        return new Result(requestRepository.save(envelope), null);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Flattens an envelope into plain values for the notifier.
     *
     * <p>Called on the transactional thread so every lazy association is still
     * reachable. Nulls are filtered from the recipient list because the
     * initiator or a signer account can legitimately be absent.
     */
    private UserDtls unproxy(UserDtls u) {
        if (u == null || u.getId() == null) return u;
        return userRepository.findById(u.getId()).orElse(u);
    }

    private SignatureNotice noticeFor(SignatureRequest envelope, SignatureStep step,
            List<UserDtls> recipients) {
        List<UserDtls> unproxiedRecipients = recipients.stream()
                .filter(java.util.Objects::nonNull)
                .map(this::unproxy)
                .toList();

        return new SignatureNotice(
                envelope.getId(),
                envelope.getModule(),
                envelope.getRequestId(),
                envelope.getDocumentLabel(),
                envelope.getVerificationCode(),
                envelope.getProgressLabel(),
                envelope.getDueAt(),
                step == null ? null : step.getId(),
                step == null ? null : step.getRoleLabel(),
                step == null ? null : step.getSignerNameSnapshot(),
                step == null ? null : step.getDeclineReason(),
                unproxiedRecipients);
    }

    /**
     * Appends to the trail. The catch covers mapping and in-memory faults, so a
     * malformed event cannot undo a signature — but it is not a safety net for
     * database rejections: PostgreSQL aborts the whole transaction on error, and
     * every later statement in the caller then fails with "current transaction is
     * aborted" no matter what is caught here. The trail's schema has to accept
     * whatever {@link SignatureAuditEventType} can produce.
     */
    private void audit(SignatureRequest envelope, Long stepId, SignatureAuditEventType type,
            UserDtls actorUser, ActorContext actor, String detail) {
        try {
            SignatureAuditEvent event = new SignatureAuditEvent();
            event.setSignatureRequest(envelope);
            event.setStepId(stepId);
            event.setEventType(type);
            event.setActor(actorUser);
            event.setIpAddress(actor == null ? null : actor.ipAddress());
            event.setUserAgent(actor == null ? null : truncate(actor.userAgent(), 500));
            event.setDetail(truncate(detail, 1000));
            auditRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to write signature audit event {}: {}", type, e.toString());
        }
    }

    /** SHA-256, hex encoded — the integrity check over frozen content. */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    private String newVerificationCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!requestRepository.existsByVerificationCode(candidate)) {
                return candidate;
            }
        }
        // 32^10 of space: repeated collisions mean something is wrong, and a
        // unique constraint violation is a better outcome than a silent clash.
        throw new IllegalStateException("Could not generate a unique verification code");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * The signature positions this document has, in signing order.
     *
     * <p>Reads what an administrator configured under
     * {@code /admin/academic/settings/signers}, falling back to the built-in
     * layout only when nothing has been configured. Everything that needs slots
     * goes through here: asking the registry directly would quietly ignore that
     * configuration and circulate the document to the wrong people, in the
     * wrong order, with nothing to show that anything was amiss.
     */
    private List<SignatureSlot> slotsFor(SignatureModule module, int documentType) {
        return workflowConfigService != null
                ? workflowConfigService.effectiveSlotsFor(module, documentType)
                : anchorRegistry.slotsFor(module, documentType);
    }

    /**
     * Builds the "ส่งไปลงนาม" panel for a document form.
     *
     * <p>Offers people without a linked account too, marked unselectable. Hiding
     * them would leave an administrator hunting for a colleague who is simply
     * missing from the list with no clue why — the panel says
     * "ยังไม่ได้ผูกบัญชี" instead.
     */
    public com.ecom.academic.dto.SignaturePanelView buildPanel(SignatureModule module, Long requestId,
            int documentType, UserDtls viewer) {

        List<SignatureSlot> slots = slotsFor(module, documentType);
        if (slots.isEmpty()) {
            return com.ecom.academic.dto.SignaturePanelView.unsignable();
        }

        Map<String, Integer> defaultSignerUserIds = workflowConfigService != null
                ? workflowConfigService.defaultSignerUserIds(module, documentType)
                : Map.of();

        // 1. Resolve applicant user for this specific request
        UserDtls applicantUser = snapshotProvider != null ? snapshotProvider.applicantOf(module, requestId) : null;
        if (applicantUser == null && viewer != null && snapshotProvider != null
                && snapshotProvider.isApplicantOf(module, requestId, viewer.getId())) {
            applicantUser = viewer;
        }

        // 2. Role-matched suggestions per slot (inactive accounts excluded)
        java.util.Map<String, List<com.ecom.academic.dto.SignerOptionDTO>> recommended =
                new java.util.LinkedHashMap<>();
        for (SignatureSlot slot : slots) {
            if (slot.defaultStaffRole() == null || "applicant".equalsIgnoreCase(slot.slotKey())) {
                if (applicantUser != null && Boolean.TRUE.equals(applicantUser.getIsEnable())
                        && (applicantUser.getAccountNonLocked() == null || Boolean.TRUE.equals(applicantUser.getAccountNonLocked()))) {
                    recommended.put(slot.slotKey(), List.of(com.ecom.academic.dto.SignerOptionDTO.fromUser(applicantUser)));
                } else {
                    recommended.put(slot.slotKey(), List.of());
                }
                continue;
            }
            recommended.put(slot.slotKey(),
                    staffMemberService.findByRoleWithAccountStatus(slot.defaultStaffRole()).stream()
                            .filter(com.ecom.academic.model.StaffMember::isSignable)
                            .map(com.ecom.academic.dto.SignerOptionDTO::from)
                            .toList());
        }

        // 3. Everyone with an ACTIVE account (Staff members + Admins/Staff/Users)
        java.util.Map<Integer, com.ecom.academic.dto.SignerOptionDTO> othersMap = new java.util.LinkedHashMap<>();
        staffMemberService.findAllWithAccounts().stream()
                .filter(com.ecom.academic.model.StaffMember::isSignable)
                .forEach(s -> {
                    com.ecom.academic.dto.SignerOptionDTO dto = com.ecom.academic.dto.SignerOptionDTO.from(s);
                    if (dto.userId() != null) {
                        othersMap.put(dto.userId(), dto);
                    }
                });

        userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsEnable()))
                .forEach(u -> {
                    if (!othersMap.containsKey(u.getId())) {
                        othersMap.put(u.getId(), com.ecom.academic.dto.SignerOptionDTO.fromUser(u));
                    }
                });

        List<com.ecom.academic.dto.SignerOptionDTO> others = new java.util.ArrayList<>(othersMap.values());
        others.sort(java.util.Comparator.comparing(com.ecom.academic.dto.SignerOptionDTO::displayName,
                java.util.Comparator.nullsLast(String::compareToIgnoreCase)));

        com.ecom.academic.dto.SignerOptionDTO applicantOption = applicantUser != null
                ? com.ecom.academic.dto.SignerOptionDTO.fromUser(applicantUser)
                : null;

        boolean isAdminViewer = viewer != null
                && ("ROLE_ADMIN".equals(viewer.getRole()) || "ROLE_STAFF".equals(viewer.getRole()));

        SignatureRequest activeEnvelope = findBlockingEnvelope(module, requestId, documentType).orElse(null);

        boolean deadlineAdvisory = activeEnvelope != null
                && activeEnvelope.activeStep().map(this::deadlineIsAdvisory).orElse(false);

        // Only worth looking for a closed round when no live one is in the way.
        SignatureRequest revivableEnvelope = activeEnvelope == null
                ? findRevivableEnvelope(module, requestId, documentType).orElse(null)
                : null;

        return new com.ecom.academic.dto.SignaturePanelView(
                slots, recommended, others, defaultSignerUserIds,
                applicantOption,
                com.ecom.academic.dto.SignerOptionDTO.fromUser(viewer),
                activeEnvelope,
                true,
                isAdminViewer,
                deadlineAdvisory,
                revivableEnvelope);
    }

    /** Signed steps carrying the images to stamp, in order. */
    public List<SignatureStep> signedSteps(Long envelopeId) {
        return new ArrayList<>(stepRepository.findSignedSteps(envelopeId));
    }
}
