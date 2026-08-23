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

    private Result createEnvelope() {
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

        Result second = createEnvelope();

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
        // Phase 1 doc 5 is a bare suggestions box with no signature block.
        Result result = workflow.createEnvelope(SignatureModule.ACADEMIC, REQUEST_ID, 5, "ข้อเสนอแนะ",
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
}
