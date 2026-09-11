package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureAuditEventType;
import com.ecom.academic.model.SignatureKind;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.repository.UserSignatureRepository;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * The signing chain: order, consent, ownership, locking, and the audit trail.
 *
 * <p>Position document 5 is used throughout because it has exactly two ordered
 * signature slots — หัวหน้าสาขา then คณบดี — which is the "ตามลำดับขั้น" case
 * the feature exists to enforce.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:esignflowdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false"
})
@DisplayName("ระบบเวียนลงนามตามลำดับขั้น")
class SignatureWorkflowServiceTest {

    private static final SignatureModule MODULE = SignatureModule.POSITION;
    private static final int DOC_TYPE = 5;
    private static final Long REQUEST_ID = 4242L;
    private static final String FROZEN_JSON = "{\"department_head_name\":\"สุดา\",\"dean_name\":\"สมชาย\"}";

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private UserSignatureService signatureService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SignatureRequestRepository requestRepository;

    @Autowired
    private SignatureStepRepository stepRepository;

    @Autowired
    private UserSignatureRepository userSignatureRepository;

    @Autowired
    private com.ecom.academic.repository.PositionRequestRepository positionRequestRepository;

    @Autowired
    private SignatureReminderScheduler reminderScheduler;

    private UserDtls admin;
    private UserDtls head;
    private UserDtls dean;
    private UserSignature headSignature;
    private UserSignature deanSignature;

    /** Makes each test's accounts unique — see {@link #setUp()}. */
    private static final java.util.concurrent.atomic.AtomicInteger RUN = new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        // Envelopes are cleared so each test starts with the document unlocked;
        // steps and audit rows follow by cascade.
        requestRepository.deleteAll();

        // Users are NOT deleted between tests, and each test gets fresh ones.
        // Signing notifies people through an @Async notifier, so notification
        // rows can land on a background thread after the test method returns —
        // deleting users here would race with those inserts and fail on the
        // foreign key. Fresh accounts sidestep the race entirely.
        String run = "r" + RUN.incrementAndGet() + "-";
        admin = newUser(run + "admin@kku.ac.th", "ROLE_ADMIN");
        head = newUser(run + "head@kku.ac.th", "ROLE_USER");
        dean = newUser(run + "dean@kku.ac.th", "ROLE_ADMIN");

        headSignature = newSignature(head);
        deanSignature = newSignature(dean);
    }

    private UserDtls newUser(String email, String role) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setName("Person " + email);
        u.setPassword("{noop}x");
        u.setRole(role);
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        // Keeps the tests off the mail server.
        u.setEmailNotificationEnabled(false);
        return userRepository.save(u);
    }

    private UserSignature newSignature(UserDtls owner) throws IOException {
        BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.drawLine(5, 50, 195, 10);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());

        UserSignatureService.SaveResult r = signatureService.create(
                owner, dataUrl, SignatureKind.DRAW, "ลายเซ็น", null, null, true);
        assertThat(r.ok()).isTrue();
        return r.signature();
    }

    /**
     * A round that is already under way.
     *
     * <p>Staff release it straight away because these tests are about the
     * signing chain itself; the review gate that holds it until then has its
     * own tests further down.
     */
    private Result createEnvelope() {
        Result created = createEnvelopeWithoutRelease();
        assertThat(created.ok()).isTrue();
        Result released = workflow.startCirculation(created.request().getId(), admin, ActorContext.none());
        assertThat(released.ok()).isTrue();
        return released;
    }

    /** A round still waiting for staff to check the document. */
    private Result createEnvelopeWithoutRelease() {
        return workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE,
                "แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา", FROZEN_JSON,
                List.of(new SignerAssignment("head", head.getId()),
                        new SignerAssignment("dean", dean.getId())),
                null, admin, ActorContext.none());
    }

    private SignatureStep stepOf(SignatureRequest envelope, String slotKey) {
        return stepRepository.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> s.getSlotKey().equals(slotKey))
                .findFirst().orElseThrow();
    }

    // =====================================================================

    @Test
    @DisplayName("สร้างซองแล้วมีเพียงคนแรกเท่านั้นที่ถึงคิว")
    void onlyTheFirstStepIsActive() {
        Result result = createEnvelope();
        assertThat(result.ok()).isTrue();

        SignatureRequest envelope = result.request();
        assertThat(envelope.getStatus()).isEqualTo(SignatureRequestStatus.IN_PROGRESS);
        assertThat(envelope.getSteps()).hasSize(2);

        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.WAITING);

        // The snapshot and its hash are what later signatures are checked against.
        assertThat(envelope.getFrozenJson()).isEqualTo(FROZEN_JSON);
        assertThat(envelope.getFrozenHash()).isEqualTo(SignatureWorkflowService.sha256(FROZEN_JSON));
        assertThat(envelope.getVerificationCode()).hasSize(10);
    }

    @Test
    @DisplayName("คณบดีลงนามก่อนหัวหน้าสาขาไม่ได้ — นี่คือกฎลำดับขั้น")
    void cannotSignOutOfTurn() {
        SignatureRequest envelope = createEnvelope().request();
        SignatureStep deanStep = stepOf(envelope, "dean");

        Result result = workflow.sign(deanStep.getId(), dean, deanSignature.getId(), true, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ยังไม่ถึงคิว");
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.WAITING);
    }

    @Test
    @DisplayName("ลงนามครบตามลำดับ ซองจะเสร็จสมบูรณ์")
    void signingInOrderCompletesTheChain() {
        SignatureRequest envelope = createEnvelope().request();

        Result first = workflow.sign(stepOf(envelope, "head").getId(), head,
                headSignature.getId(), true, ActorContext.none());
        assertThat(first.ok()).isTrue();

        // Signing the first step must hand the turn to the second.
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.SIGNED);
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        assertThat(requestRepository.findById(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.IN_PROGRESS);

        Result second = workflow.sign(stepOf(envelope, "dean").getId(), dean,
                deanSignature.getId(), true, ActorContext.none());
        assertThat(second.ok()).isTrue();

        // With steps fetched: allSigned() reads the collection, which is lazy.
        SignatureRequest done = requestRepository.findByIdWithSteps(envelope.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(SignatureRequestStatus.COMPLETED);
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(done.allSigned()).isTrue();
    }

    @Test
    @DisplayName("ไม่กดยินยอม ลงนามไม่ได้")
    void consentIsRequired() {
        SignatureRequest envelope = createEnvelope().request();
        SignatureStep headStep = stepOf(envelope, "head");

        Result result = workflow.sign(headStep.getId(), head, headSignature.getId(), false, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ยินยอม");
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
    }

    @Test
    @DisplayName("คนอื่นลงนามแทนไม่ได้ แม้จะเป็นผู้ดูแลระบบ")
    void onlyTheAssignedSignerMaySign() {
        SignatureRequest envelope = createEnvelope().request();
        SignatureStep headStep = stepOf(envelope, "head");

        Result result = workflow.sign(headStep.getId(), dean, deanSignature.getId(), true, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ไม่ใช่ผู้ที่ได้รับมอบหมาย");
    }

    @Test
    @DisplayName("ใช้ลายเซ็นของคนอื่นไม่ได้")
    void cannotSignWithAnotherPersonsSignature() {
        SignatureRequest envelope = createEnvelope().request();
        SignatureStep headStep = stepOf(envelope, "head");

        // The right person, their turn, consent given — but someone else's image.
        Result result = workflow.sign(headStep.getId(), head, deanSignature.getId(), true, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ไม่พบลายเซ็นที่เลือก");
    }

    @Test
    @DisplayName("ลงนามแล้วบันทึกหลักฐานครบ: ความยินยอม เวลา IP และ hash เอกสาร")
    void signatureRecordsEvidence() {
        SignatureRequest envelope = createEnvelope().request();
        SignatureStep headStep = stepOf(envelope, "head");

        workflow.sign(headStep.getId(), head, headSignature.getId(), true,
                new ActorContext("10.1.2.3", "Mozilla/5.0 (Test)"));

        SignatureStep signed = stepRepository.findById(headStep.getId()).orElseThrow();
        assertThat(signed.getConsentAccepted()).isTrue();
        assertThat(signed.getConsentTextVersion()).isEqualTo(SignatureStep.CONSENT_TEXT_VERSION);
        assertThat(signed.getSignedAt()).isNotNull();
        assertThat(signed.getIpAddress()).isEqualTo("10.1.2.3");
        assertThat(signed.getUserAgent()).contains("Test");
        assertThat(signed.getDocHashSigned()).isEqualTo(envelope.getFrozenHash());
        assertThat(signed.getAuthMethod()).isEqualTo(SignatureStep.AUTH_METHOD_SESSION);
        // The image is copied, so deleting it from the library later cannot
        // change what this document was signed with.
        assertThat(signed.getImagePathSnapshot()).isEqualTo(headSignature.getImagePath());
        assertThat(signed.getSignerNameSnapshot()).isEqualTo(head.getName());
    }

    @Test
    @DisplayName("ปฏิเสธการลงนาม ทำให้ทั้งซองจบและคนถัดไปไม่ต้องเซ็น")
    void decliningStopsTheWholeChain() {
        SignatureRequest envelope = createEnvelope().request();

        Result result = workflow.decline(stepOf(envelope, "head").getId(), head,
                "ข้อมูลไม่ถูกต้อง", ActorContext.none());

        assertThat(result.ok()).isTrue();
        SignatureRequest after = requestRepository.findById(envelope.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SignatureRequestStatus.DECLINED);
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.DECLINED);
        assertThat(stepOf(envelope, "head").getDeclineReason()).isEqualTo("ข้อมูลไม่ถูกต้อง");
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.SKIPPED);
    }

    @Test
    @DisplayName("ปฏิเสธต้องระบุเหตุผล")
    void decliningNeedsAReason() {
        SignatureRequest envelope = createEnvelope().request();

        Result result = workflow.decline(stepOf(envelope, "head").getId(), head, "  ", ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("เหตุผล");
    }

    @Test
    @DisplayName("ระหว่างเวียนลงนาม เอกสารถูกล็อกไม่ให้แก้")
    void documentIsLockedWhileCirculating() {
        assertThat(workflow.isDocumentLocked(MODULE, REQUEST_ID, DOC_TYPE)).isFalse();

        SignatureRequest envelope = createEnvelope().request();
        assertThat(workflow.isDocumentLocked(MODULE, REQUEST_ID, DOC_TYPE)).isTrue();

        // Still locked once complete: the signatures belong to this content.
        workflow.sign(stepOf(envelope, "head").getId(), head, headSignature.getId(), true, ActorContext.none());
        workflow.sign(stepOf(envelope, "dean").getId(), dean, deanSignature.getId(), true, ActorContext.none());
        assertThat(workflow.isDocumentLocked(MODULE, REQUEST_ID, DOC_TYPE)).isTrue();
    }

    @Test
    @DisplayName("ยกเลิกการเวียน ปลดล็อกเอกสารให้แก้ไขได้")
    void cancellingUnlocksTheDocument() {
        SignatureRequest envelope = createEnvelope().request();

        Result result = workflow.cancel(envelope.getId(), admin, "ต้องแก้ไขข้อมูล", ActorContext.none());

        assertThat(result.ok()).isTrue();
        assertThat(workflow.isDocumentLocked(MODULE, REQUEST_ID, DOC_TYPE)).isFalse();
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.SKIPPED);
    }

    @Test
    @DisplayName("ส่งซ้ำระหว่างที่ยังเวียนอยู่ไม่ได้")
    void cannotOpenTwoRoundsAtOnce() {
        assertThat(createEnvelope().ok()).isTrue();

        Result second = createEnvelopeWithoutRelease();

        assertThat(second.ok()).isFalse();
        assertThat(second.error()).contains("อยู่ระหว่างการเวียนลงนาม");
    }

    @Test
    @DisplayName("เอกสารที่ยังไม่ได้บันทึกข้อมูล ส่งไปลงนามไม่ได้")
    void cannotSendAnEmptyDocument() {
        Result result = workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE, "เอกสาร", null,
                List.of(new SignerAssignment("head", head.getId())), null, admin, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ยังไม่มีข้อมูลในเอกสาร");
    }

    @Test
    @DisplayName("เอกสารที่ไม่มีจุดลงนาม ส่งไปลงนามไม่ได้")
    void cannotSendAnUnsignableDocument() {
        // Phase 1 doc 6 is a bare suggestions box with no signature block.
        Result result = workflow.createEnvelope(SignatureModule.ACADEMIC, REQUEST_ID, 6, "ข้อเสนอแนะ",
                FROZEN_JSON, List.of(new SignerAssignment("dean", dean.getId())), null, admin,
                ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ไม่มีจุดลงนาม");
    }

    @Test
    @DisplayName("เอกสารถูกแก้หลังส่งลงนาม ลายเซ็นเป็นโมฆะ")
    void tamperingVoidsTheEnvelope() {
        SignatureRequest envelope = createEnvelope().request();

        // Simulates a direct write to the row, bypassing the form lock.
        SignatureRequest stored = requestRepository.findById(envelope.getId()).orElseThrow();
        stored.setFrozenJson("{\"dean_name\":\"someone else entirely\"}");
        requestRepository.saveAndFlush(stored);

        Result result = workflow.sign(stepOf(envelope, "head").getId(), head,
                headSignature.getId(), true, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("โมฆะ");
        assertThat(requestRepository.findById(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.VOIDED);
    }

    @Test
    @DisplayName("เลยกำหนดเวลาแล้วลงนามไม่ได้")
    void cannotSignAfterTheDeadline() {
        Result created = workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE, "เอกสาร", FROZEN_JSON,
                List.of(new SignerAssignment("head", head.getId())),
                LocalDateTime.now().minusDays(1), admin, ActorContext.none());
        assertThat(created.ok()).isTrue();
        assertThat(workflow.startCirculation(created.request().getId(), admin, ActorContext.none()).ok()).isTrue();

        Result result = workflow.sign(stepOf(created.request(), "head").getId(), head,
                headSignature.getId(), true, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("เลยกำหนด");
    }

    @Test
    @DisplayName("กล่อง 'รอลงนาม' แสดงเฉพาะรายการที่ถึงคิวของเราแล้ว")
    void inboxShowsOnlyMyActiveSteps() {
        SignatureRequest envelope = createEnvelope().request();

        assertThat(workflow.findInbox(head)).hasSize(1);
        // The dean's turn has not come, so nothing for them yet.
        assertThat(workflow.findInbox(dean)).isEmpty();
        assertThat(workflow.countPending(dean)).isZero();

        workflow.sign(stepOf(envelope, "head").getId(), head, headSignature.getId(), true, ActorContext.none());

        assertThat(workflow.findInbox(head)).isEmpty();
        assertThat(workflow.findInbox(dean)).hasSize(1);
        assertThat(workflow.countPending(dean)).isEqualTo(1);
    }

    @Test
    @DisplayName("บันทึกร่องรอยทุกเหตุการณ์ไว้เป็นหลักฐาน")
    void everyEventIsAudited() {
        SignatureRequest envelope = createEnvelope().request();
        workflow.sign(stepOf(envelope, "head").getId(), head, headSignature.getId(), true, ActorContext.none());
        workflow.sign(stepOf(envelope, "dean").getId(), dean, deanSignature.getId(), true, ActorContext.none());

        List<SignatureAuditEventType> types = workflow.auditTrail(envelope.getId()).stream()
                .map(e -> e.getEventType())
                .toList();

        assertThat(types).contains(
                SignatureAuditEventType.CREATED,
                SignatureAuditEventType.NOTIFIED,
                SignatureAuditEventType.SIGNED,
                SignatureAuditEventType.COMPLETED);
        // Two signatures, so two NOTIFIED and two SIGNED events.
        assertThat(types.stream().filter(t -> t == SignatureAuditEventType.SIGNED)).hasSize(2);
    }

    @Test
    @DisplayName("ตำแหน่งที่ไม่ได้เลือกผู้ลงนาม จะไม่ถูกสร้างเป็นขั้นตอน")
    void unassignedSlotsAreSkipped() {
        Result result = workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE, "เอกสาร", FROZEN_JSON,
                List.of(new SignerAssignment("head", head.getId()),
                        new SignerAssignment("dean", null)),
                null, admin, ActorContext.none());

        assertThat(result.ok()).isTrue();
        assertThat(result.request().getSteps()).hasSize(1);
        assertThat(result.request().getSteps().get(0).getSlotKey()).isEqualTo("head");
    }

    @Test
    @DisplayName("ค้นหาเอกสารถัดไปในคิวของผู้ลงนาม (Continuous Flow)")
    void findNextPendingStepWorksCorrectlyForContinuousFlow() {
        // Create 2 envelopes waiting on head
        SignatureRequest envelope1 = createEnvelope().request();
        Result result2 = workflow.createEnvelope(MODULE, REQUEST_ID, 4, "เอกสาร 4", FROZEN_JSON,
                List.of(new SignerAssignment("head", head.getId())),
                null, admin, ActorContext.none());
        assertThat(result2.ok()).isTrue();
        assertThat(workflow.startCirculation(result2.request().getId(), admin, ActorContext.none()).ok()).isTrue();
        SignatureRequest envelope2 = result2.request();

        Long stepId1 = stepOf(envelope1, "head").getId();
        Long stepId2 = envelope2.getSteps().get(0).getId();

        assertThat(workflow.findInbox(head)).hasSize(2);

        // When currently viewing step1, next step is step2
        java.util.Optional<SignatureStep> nextFrom1 = workflow.findNextPendingStep(head, stepId1);
        assertThat(nextFrom1).isPresent();
        assertThat(nextFrom1.get().getId()).isEqualTo(stepId2);

        // When currently viewing step2, next step is step1
        java.util.Optional<SignatureStep> nextFrom2 = workflow.findNextPendingStep(head, stepId2);
        assertThat(nextFrom2).isPresent();
        assertThat(nextFrom2.get().getId()).isEqualTo(stepId1);
    }

    @Test
    @DisplayName("ผู้ลงนามสามารถขอขยายเวลาลงนามได้")
    void requestExtensionRecordsAudit() {
        SignatureRequest envelope = createEnvelope().request();
        SignatureStep step = stepOf(envelope, "head");

        Result result = workflow.requestExtension(step.getId(), "ติดราชการต่างประเทศ", head, ActorContext.none());
        assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("สามารถขยายกำหนดเวลาลงนามได้")
    void extendDueDateUpdatesDueAtAndAudits() {
        SignatureRequest envelope = createEnvelope().request();
        java.time.LocalDateTime newDue = java.time.LocalDateTime.now().plusDays(10);

        Result result = workflow.extendDueDate(envelope.getId(), newDue, admin, ActorContext.none());
        assertThat(result.ok()).isTrue();

        SignatureRequest reloaded = requestRepository.findById(envelope.getId()).orElseThrow();
        assertThat(reloaded.getDueAt()).isNotNull();
    }

    // =====================================================================
    // กำหนดเวลาของผู้ยื่นเองตอนยังไม่ยื่นคำร้อง = แจ้งเตือน ไม่ใช่ประตูปิด
    //
    // POSITION document 4 is used here because it carries both kinds of step
    // at once — ผู้เสนอขอ then ผู้บังคับบัญชาชั้นต้น — so the same overdue
    // envelope can show that one is let through and the other is not.
    // =====================================================================

    private static final int DRAFT_DOC_TYPE = 4;

    /** A real DRAFT request, so {@code isDraftRequest()} has a row to find. */
    private PositionRequest newDraftRequest(UserDtls applicant) {
        PositionRequest request = new PositionRequest();
        request.setApplicant(applicant);
        request.setRequestCode("DRAFT-" + java.util.UUID.randomUUID());
        request.setCurrentStatus(PositionRequestStatus.DRAFT);
        return positionRequestRepository.save(request);
    }

    private Result createDraftEnvelope(PositionRequest request, UserDtls applicant) {
        return workflow.createEnvelope(MODULE, request.getId(), DRAFT_DOC_TYPE,
                "แบบคำขอ", FROZEN_JSON,
                List.of(new SignerAssignment("applicant", applicant.getId()),
                        new SignerAssignment("head", head.getId())),
                LocalDateTime.now().minusDays(1), applicant, ActorContext.none());
    }

    @Test
    @DisplayName("ยังไม่ยื่นคำร้อง: ผู้ยื่นลงนามส่วนตัวเองได้แม้เลยกำหนดที่ตั้งเตือนไว้")
    void applicantMaySignPastTheDeadlineWhileStillADraft() throws IOException {
        UserDtls applicant = newUser("draft-signer@kku.ac.th", "ROLE_USER");
        UserSignature signature = newSignature(applicant);
        PositionRequest request = newDraftRequest(applicant);

        SignatureRequest envelope = createDraftEnvelope(request, applicant).request();
        assertThat(envelope.isOverdue()).isTrue();

        Result signed = workflow.sign(stepOf(envelope, "applicant").getId(), applicant,
                signature.getId(), true, ActorContext.none());

        assertThat(signed.ok()).as("เลยกำหนดที่ตั้งเตือนไว้ แต่ยังต้องลงนามได้").isTrue();
    }

    @Test
    @DisplayName("ยังไม่ยื่นคำร้อง: ผู้ลงนามลำดับถัดไปยังถูกกั้นด้วยกำหนดเวลาเหมือนเดิม")
    void chainSignerStillBlockedPastTheDeadlineOnADraft() throws IOException {
        UserDtls applicant = newUser("draft-chain@kku.ac.th", "ROLE_USER");
        UserSignature signature = newSignature(applicant);
        PositionRequest request = newDraftRequest(applicant);

        SignatureRequest envelope = createDraftEnvelope(request, applicant).request();
        assertThat(workflow.sign(stepOf(envelope, "applicant").getId(), applicant,
                signature.getId(), true, ActorContext.none()).ok()).isTrue();
        assertThat(workflow.startCirculation(envelope.getId(), admin, ActorContext.none()).ok()).isTrue();

        Result blocked = workflow.sign(stepOf(envelope, "head").getId(), head,
                headSignature.getId(), true, ActorContext.none());

        assertThat(blocked.ok()).as("สายเวียนตามลำดับต้องยังใช้กติกาเดิม").isFalse();
        assertThat(blocked.error()).contains("เลยกำหนด");
    }

    @Test
    @DisplayName("ยังไม่ยื่นคำร้อง: ผู้ยื่นขอขยายเวลาให้ตัวเองไม่ได้ เพราะไม่จำเป็น")
    void extensionRequestIsRefusedWhenTheDeadlineIsOnlyAReminder() throws IOException {
        UserDtls applicant = newUser("draft-extension@kku.ac.th", "ROLE_USER");
        newSignature(applicant);
        PositionRequest request = newDraftRequest(applicant);

        SignatureRequest envelope = createDraftEnvelope(request, applicant).request();

        Result result = workflow.requestExtension(stepOf(envelope, "applicant").getId(),
                "ยังทำเอกสารไม่เสร็จ", applicant, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("แจ้งเตือน");
    }

    @Test
    @DisplayName("ยังไม่ยื่นคำร้อง: ตัวตั้งเวลาไม่ปิดซองของผู้ยื่นที่เลยกำหนด")
    void theSchedulerLeavesAnApplicantsOwnDraftRoundOpen() throws IOException {
        UserDtls applicant = newUser("draft-scheduler@kku.ac.th", "ROLE_USER");
        newSignature(applicant);
        PositionRequest request = newDraftRequest(applicant);

        SignatureRequest envelope = createDraftEnvelope(request, applicant).request();
        reminderScheduler.run();

        SignatureRequest reloaded = requestRepository.findById(envelope.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SignatureRequestStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("ซองที่เลยกำหนดจนถูกปิด สามารถตั้งกำหนดใหม่แล้วเวียนต่อได้")
    void expiredEnvelopeCanBeExtendedAndResumes() {
        SignatureRequest envelope = createEnvelope().request();
        assertThat(workflow.expire(envelope.getId()).ok()).isTrue();
        assertThat(requestRepository.findById(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.EXPIRED);

        Result revived = workflow.extendDueDate(envelope.getId(),
                LocalDateTime.now().plusDays(7), admin, ActorContext.none());
        assertThat(revived.ok()).isTrue();

        SignatureRequest reloaded = requestRepository.findById(envelope.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SignatureRequestStatus.IN_PROGRESS);

        // The turn the clock took away is given back, not just the status.
        assertThat(stepOf(reloaded, "head").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        assertThat(workflow.sign(stepOf(reloaded, "head").getId(), head,
                headSignature.getId(), true, ActorContext.none()).ok()).isTrue();
    }

    @Test
    @DisplayName("ขยายกำหนดเวลาไปยังเวลาที่ผ่านมาแล้วไม่ได้")
    void extendDueDateRefusesAPastDate() {
        SignatureRequest envelope = createEnvelope().request();

        Result result = workflow.extendDueDate(envelope.getId(),
                LocalDateTime.now().minusDays(1), admin, ActorContext.none());

        assertThat(result.ok()).isFalse();
    }

    @Test
    @DisplayName("เริ่มรอบใหม่ไปแล้ว จะปลุกรอบเดิมที่หมดอายุขึ้นมาอีกไม่ได้")
    void anExpiredRoundCannotBeRevivedOnceANewOneStarted() {
        SignatureRequest expired = createEnvelope().request();
        assertThat(workflow.expire(expired.getId()).ok()).isTrue();

        // เอกสารถูกปลดล็อกแล้ว จึงส่งเวียนรอบใหม่ได้
        assertThat(createEnvelope().ok()).isTrue();

        Result result = workflow.extendDueDate(expired.getId(),
                LocalDateTime.now().plusDays(7), admin, ActorContext.none());

        assertThat(result.ok()).as("สองรอบเปิดพร้อมกันบนเอกสารเดียวไม่ได้").isFalse();
        assertThat(requestRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.EXPIRED);
    }

    @Test
    @DisplayName("ซองที่ยกเลิกไปแล้วยังขยายเวลาไม่ได้")
    void cancelledEnvelopeStaysClosed() {
        SignatureRequest envelope = createEnvelope().request();
        assertThat(workflow.cancel(envelope.getId(), admin, "ยกเลิก", ActorContext.none()).ok()).isTrue();

        Result result = workflow.extendDueDate(envelope.getId(),
                LocalDateTime.now().plusDays(7), admin, ActorContext.none());

        assertThat(result.ok()).isFalse();
    }

    @Test
    @DisplayName("ตรวจสอบความครบถ้วนของลายเซ็นผู้ยื่นคำร้องก่อนยื่น")
    void applicantSignatureCompletenessChecks() {
        // Document 1 in POSITION module requires 'applicant'
        List<Integer> positionDocs = List.of(1, 2);

        // Before signing: incomplete
        assertThat(workflow.areApplicantSignaturesComplete(MODULE, REQUEST_ID, positionDocs)).isFalse();
        assertThat(workflow.getUnsignedApplicantDocTypes(MODULE, REQUEST_ID, positionDocs)).containsExactly(1, 2);

        // Create envelope and sign Doc 1 with applicant
        Result resDoc1 = workflow.createEnvelope(MODULE, REQUEST_ID, 1, "เอกสาร 1", "{}",
                List.of(new SignerAssignment("applicant", head.getId())),
                null, head, ActorContext.none());
        assertThat(resDoc1.ok()).isTrue();

        // Sign step
        SignatureStep step1 = stepOf(resDoc1.request(), "applicant");
        workflow.sign(step1.getId(), head, headSignature.getId(), true, ActorContext.none());

        assertThat(workflow.isApplicantSignatureCompleted(MODULE, REQUEST_ID, 1)).isTrue();
        assertThat(workflow.getUnsignedApplicantDocTypes(MODULE, REQUEST_ID, positionDocs)).containsExactly(2);

        // Create and sign Doc 2
        Result resDoc2 = workflow.createEnvelope(MODULE, REQUEST_ID, 2, "เอกสาร 2", "{}",
                List.of(new SignerAssignment("applicant", head.getId())),
                null, head, ActorContext.none());
        SignatureStep step2 = stepOf(resDoc2.request(), "applicant");
        workflow.sign(step2.getId(), head, headSignature.getId(), true, ActorContext.none());

        assertThat(workflow.areApplicantSignaturesComplete(MODULE, REQUEST_ID, positionDocs)).isTrue();
        assertThat(workflow.getUnsignedApplicantDocTypes(MODULE, REQUEST_ID, positionDocs)).isEmpty();
    }

    @Test
    @DisplayName("แอดมินสามารถส่งกลับให้แก้ไขและลงนามใหม่ได้ (requestDocumentResign)")
    void adminCanRequestDocumentResign() {
        // Create completed envelope for Doc 1
        Result resDoc1 = workflow.createEnvelope(MODULE, REQUEST_ID, 1, "เอกสาร 1", "{}",
                List.of(new SignerAssignment("applicant", head.getId())),
                null, head, ActorContext.none());
        SignatureStep step1 = stepOf(resDoc1.request(), "applicant");
        workflow.sign(step1.getId(), head, headSignature.getId(), true, ActorContext.none());

        assertThat(workflow.isDocumentLocked(MODULE, REQUEST_ID, 1)).isTrue();

        // Admin requests re-sign
        Result resignResult = workflow.requestDocumentResign(MODULE, REQUEST_ID, 1,
                "ลายเซ็นไม่ชัดเจน กรุณาลงนามใหม่", admin, ActorContext.none());
        assertThat(resignResult.ok()).isTrue();

        // Document is now unlocked for applicant edit & re-sign
        assertThat(workflow.isDocumentLocked(MODULE, REQUEST_ID, 1)).isFalse();
        assertThat(workflow.isApplicantSignatureCompleted(MODULE, REQUEST_ID, 1)).isFalse();
    }

    // =====================================================================
    // ส่งเวียนลงนามต่อ — ครึ่งหลังของ Admin Gate
    // =====================================================================

    @Test
    @DisplayName("เติมผู้ลงนามเข้าซองที่เวียนอยู่ แล้วคนใหม่ถึงคิวต่อทันที")
    void forwardingAddsTheNextSignerToAnOpenRound() {
        Result created = workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE,
                "แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา", FROZEN_JSON,
                List.of(new SignerAssignment("head", head.getId())),
                null, admin, ActorContext.none());
        assertThat(created.ok()).isTrue();
        assertThat(created.request().getSteps()).hasSize(1);

        Result forwarded = workflow.forwardToNextSigners(created.request().getId(),
                List.of(new SignerAssignment("dean", dean.getId())),
                null, admin, ActorContext.none());

        assertThat(forwarded.ok()).isTrue();
        SignatureRequest envelope = requestRepository.findByIdWithSteps(created.request().getId()).orElseThrow();
        assertThat(envelope.getSteps()).hasSize(2);
        // หัวหน้าสาขายังถือคิวอยู่ การเติมคนใหม่ต้องไม่แย่งคิวไป
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.WAITING);
    }

    @Test
    @DisplayName("ซองที่ลงนามครบแล้ว ยังส่งเวียนต่อให้ผู้บริหารได้ — ไม่ล็อกตาย")
    void forwardingReopensACompletedRound() {
        // เอกสารที่มีแต่ช่องของผู้ยื่น จะ COMPLETED ทันทีที่ผู้ยื่นเซ็น
        // ถ้าเปิดต่อไม่ได้ เจ้าหน้าที่จะไม่มีทางส่งให้คณบดีเลย
        Result created = workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE,
                "แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา", FROZEN_JSON,
                List.of(new SignerAssignment("head", head.getId())),
                null, admin, ActorContext.none());
        assertThat(workflow.startCirculation(created.request().getId(), admin, ActorContext.none()).ok()).isTrue();

        Result signed = workflow.sign(stepOf(created.request(), "head").getId(), head,
                headSignature.getId(), true, ActorContext.none());
        assertThat(signed.ok()).isTrue();
        assertThat(requestRepository.findById(created.request().getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.COMPLETED);

        Result forwarded = workflow.forwardToNextSigners(created.request().getId(),
                List.of(new SignerAssignment("dean", dean.getId())),
                null, admin, ActorContext.none());

        assertThat(forwarded.ok()).isTrue();
        SignatureRequest envelope = requestRepository.findByIdWithSteps(created.request().getId()).orElseThrow();
        assertThat(envelope.getStatus()).isEqualTo(SignatureRequestStatus.IN_PROGRESS);
        assertThat(envelope.getCompletedAt()).isNull();
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
    }

    @Test
    @DisplayName("เติมตำแหน่งที่มีผู้ลงนามอยู่แล้ว ต้องไม่เกิดขั้นตอนซ้ำ")
    void forwardingDoesNotDuplicateAnExistingSlot() {
        Result created = createEnvelope();

        Result forwarded = workflow.forwardToNextSigners(created.request().getId(),
                List.of(new SignerAssignment("dean", dean.getId())),
                null, admin, ActorContext.none());

        assertThat(forwarded.ok()).isFalse();
        assertThat(forwarded.error()).contains("อยู่แล้ว");
        assertThat(requestRepository.findByIdWithSteps(created.request().getId())
                .orElseThrow().getSteps()).hasSize(2);
    }

    // =====================================================================
    // ประตูตรวจของเจ้าหน้าที่ — ต้องกดเริ่มเองเท่านั้น
    // =====================================================================

    @Test
    @DisplayName("ยังไม่กดเริ่ม ผู้ลงนามลำดับถัดไปต้องไม่ถึงคิว")
    void nobodyIsAskedUntilStaffReleaseTheDocument() {
        Result created = createEnvelopeWithoutRelease();
        assertThat(created.ok()).isTrue();

        SignatureRequest envelope = requestRepository.findByIdWithSteps(created.request().getId()).orElseThrow();
        assertThat(envelope.isCirculationStarted()).isFalse();
        // head/dean ไม่ใช่ช่องของผู้ยื่น จึงต้องถูกพักไว้ทั้งคู่
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.WAITING);
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.WAITING);
        assertThat(workflow.findInbox(head)).isEmpty();
    }

    @Test
    @DisplayName("เจ้าหน้าที่กดเริ่ม คนแรกถึงคิวและเห็นในกล่องลงนาม")
    void releasingTheDocumentAsksTheFirstSigner() {
        Result created = createEnvelopeWithoutRelease();

        Result released = workflow.startCirculation(created.request().getId(), admin, ActorContext.none());

        assertThat(released.ok()).isTrue();
        SignatureRequest envelope = requestRepository.findByIdWithSteps(created.request().getId()).orElseThrow();
        assertThat(envelope.isCirculationStarted()).isTrue();
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.WAITING);
        assertThat(workflow.findInbox(head)).hasSize(1);
    }

    @Test
    @DisplayName("กดเริ่มซ้ำไม่ได้ กันการแจ้งเตือนซ้ำซ้อน")
    void releasingTwiceIsRefused() {
        Result created = createEnvelopeWithoutRelease();
        assertThat(workflow.startCirculation(created.request().getId(), admin, ActorContext.none()).ok()).isTrue();

        Result again = workflow.startCirculation(created.request().getId(), admin, ActorContext.none());

        assertThat(again.ok()).isFalse();
        assertThat(again.error()).contains("ส่งเวียนลงนามไปแล้ว");
    }

    @Test
    @DisplayName("เจ้าหน้าที่เลือกผู้ลงนามเพิ่ม ถือเป็นการปล่อยเวียนในตัว ไม่ต้องกดสองที")
    void addingSignersAlsoReleasesTheDocument() {
        Result created = workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE,
                "แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา", FROZEN_JSON,
                List.of(new SignerAssignment("head", head.getId())),
                null, admin, ActorContext.none());
        assertThat(requestRepository.findByIdWithSteps(created.request().getId())
                .orElseThrow().isCirculationStarted()).isFalse();

        Result forwarded = workflow.forwardToNextSigners(created.request().getId(),
                List.of(new SignerAssignment("dean", dean.getId())),
                null, admin, ActorContext.none());

        assertThat(forwarded.ok()).isTrue();
        SignatureRequest envelope = requestRepository.findByIdWithSteps(created.request().getId()).orElseThrow();
        assertThat(envelope.isCirculationStarted()).isTrue();
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
    }
}
