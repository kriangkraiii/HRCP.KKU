package com.ecom.academic.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
            SignedDocumentArchiver archiver) {
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
    }

    /**
     * Who should sign one slot, as chosen by whoever sends the document out.
     *
     * @param delegateReason set when this person signs on behalf of the role
     *                       holder ("ปฏิบัติราชการแทน"), which Thai official
     *                       documents require whenever the office holder is
     *                       unavailable; null when they hold the role themselves
     */
    public record SignerAssignment(String slotKey, Integer signerUserId, String delegateReason) {

        /** An ordinary assignment, with nobody being deputised for. */
        public SignerAssignment(String slotKey, Integer signerUserId) {
            this(slotKey, signerUserId, null);
        }
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

    /** Whether the document form should be read-only right now. */
    public boolean isDocumentLocked(SignatureModule module, Long requestId, int documentType) {
        return findBlockingEnvelope(module, requestId, documentType).isPresent();
    }

    public List<SignatureRequest> findForRequest(SignatureModule module, Long requestId) {
        return requestRepository.findByModuleAndRequestIdOrderByDocumentTypeAsc(module, requestId);
    }

    /** Everything awaiting this person's signature. */
    public List<SignatureStep> findInbox(UserDtls signer) {
        return stepRepository.findInbox(signer.getId(), SignatureStepStatus.ACTIVE);
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

        List<SignatureSlot> slots = anchorRegistry.slotsFor(module, documentType);
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

        int order = 0;
        for (SignatureSlot slot : slots) {
            SignerAssignment assignment = assignments.stream()
                    .filter(a -> a.slotKey().equals(slot.slotKey()))
                    .findFirst()
                    .orElse(null);
            // Slots left unassigned are simply not part of this round — a
            // document may legitimately need only some of its signatures now.
            if (assignment == null || assignment.signerUserId() == null) {
                continue;
            }

            UserDtls signer = userRepository.findById(assignment.signerUserId()).orElse(null);
            if (signer == null) {
                return Result.failed("ไม่พบบัญชีผู้ลงนามสำหรับ \"" + slot.roleLabel() + "\"");
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

            // Acting for someone else: recorded so the document prints "(แทน)"
            // and the audit trail says why this person signed in that place.
            if (assignment.delegateReason() != null && !assignment.delegateReason().isBlank()) {
                step.setDelegateReason(truncate(assignment.delegateReason().trim(), 500));
            }

            envelope.addStep(step);
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
        if (envelope.isOverdue()) {
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
            // the signing transaction.
            archiver.archive(envelope.getId());
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
        step.setStatus(SignatureStepStatus.ACTIVE);
        step.setNotifiedAt(LocalDateTime.now());
        stepRepository.save(step);

        audit(envelope, step.getId(), SignatureAuditEventType.NOTIFIED, null, actor,
                "แจ้งเตือน " + step.getSignerNameSnapshot() + " ให้ลงนาม");
        notifier.notifySignatureRequested(noticeFor(envelope, step, List.of(step.getSigner())));
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
    private SignatureNotice noticeFor(SignatureRequest envelope, SignatureStep step,
            List<UserDtls> recipients) {
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
                recipients.stream().filter(java.util.Objects::nonNull).toList());
    }

    /** Appends to the trail. Never throws: losing an event must not undo a signature. */
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

    /** Slots for a document paired with any assignment already made. */
    public List<SignatureSlot> slotsFor(SignatureModule module, int documentType) {
        return anchorRegistry.slotsFor(module, documentType);
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

        List<SignatureSlot> slots = anchorRegistry.slotsFor(module, documentType);
        if (slots.isEmpty()) {
            return com.ecom.academic.dto.SignaturePanelView.unsignable();
        }

        // Role-matched suggestions per slot. Shown first, but never the only
        // choice — the role tags are hand-maintained and frequently incomplete.
        java.util.Map<String, List<com.ecom.academic.dto.SignerOptionDTO>> recommended =
                new java.util.LinkedHashMap<>();
        for (SignatureSlot slot : slots) {
            if (slot.defaultStaffRole() == null) {
                recommended.put(slot.slotKey(), List.of());
                continue;
            }
            recommended.put(slot.slotKey(),
                    staffMemberService.findByRoleWithAccountStatus(slot.defaultStaffRole()).stream()
                            .map(com.ecom.academic.dto.SignerOptionDTO::from)
                            .toList());
        }

        // Everyone with a linked account, so any real person can be asked —
        // including administrators, who are often absent from the staff
        // directory yet still sign documents.
        List<com.ecom.academic.dto.SignerOptionDTO> others =
                staffMemberService.findAllWithAccounts().stream()
                        .filter(com.ecom.academic.model.StaffMember::isSignable)
                        .map(com.ecom.academic.dto.SignerOptionDTO::from)
                        .toList();

        return new com.ecom.academic.dto.SignaturePanelView(
                slots, recommended, others,
                com.ecom.academic.dto.SignerOptionDTO.fromUser(viewer),
                findBlockingEnvelope(module, requestId, documentType).orElse(null),
                true);
    }

    /** Signed steps carrying the images to stamp, in order. */
    public List<SignatureStep> signedSteps(Long envelopeId) {
        return new ArrayList<>(stepRepository.findSignedSteps(envelopeId));
    }
}
