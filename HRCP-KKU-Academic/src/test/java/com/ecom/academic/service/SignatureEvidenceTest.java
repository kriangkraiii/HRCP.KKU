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
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.SignatureKind;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * The evidence layer: HMAC seals, verification reports, delegation and expiry.
 *
 * <p>Runs with a configured signing key so the sealing paths are actually
 * exercised — with the key absent (the default) seals are skipped by design,
 * and these assertions would silently pass on nothing.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:esignevidencedb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false",
        "app.esign.hmac-secret=test-signing-key-not-for-production"
})
@DisplayName("หลักฐานการลงนาม (ผนึก HMAC / ตรวจสอบ / ลงนามแทน / หมดเวลา)")
class SignatureEvidenceTest {

    private static final SignatureModule MODULE = SignatureModule.POSITION;
    private static final int DOC_TYPE = 5;
    private static final Long REQUEST_ID = 9001L;
    private static final String FROZEN_JSON = "{\"department_head_name\":\"สุดา\",\"dean_name\":\"สมชาย\"}";

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureVerificationService verification;

    @Autowired
    private UserSignatureService signatureService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SignatureRequestRepository requestRepository;

    @Autowired
    private SignatureStepRepository stepRepository;

    private UserDtls admin;
    private UserDtls head;
    private UserDtls dean;
    private UserSignature headSignature;
    private UserSignature deanSignature;

    @BeforeEach
    void setUp() throws IOException {
        requestRepository.deleteAll();

        // Fresh accounts each run — the async notifier writes rows that race
        // any attempt to delete users between tests.
        String run = "e" + RUN.incrementAndGet() + "-";
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

    private SignatureRequest createEnvelope(List<SignerAssignment> assignments, LocalDateTime dueAt) {
        Result r = workflow.createEnvelope(MODULE, REQUEST_ID, DOC_TYPE, "แบบประเมินคุณสมบัติ",
                FROZEN_JSON, assignments, dueAt, admin, ActorContext.none());
        assertThat(r.ok()).as(r.error()).isTrue();
        return r.request();
    }

    private SignatureStep stepOf(SignatureRequest envelope, String slotKey) {
        return stepRepository.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> s.getSlotKey().equals(slotKey))
                .findFirst().orElseThrow();
    }

    // =====================================================================

    @Test
    @DisplayName("มีการตั้งกุญแจแล้ว ระบบต้องผนึกหลักฐานทุกลายเซ็น")
    void signaturesAreSealed() {
        assertThat(verification.isConfigured()).isTrue();

        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("head", head.getId())), null);
        workflow.sign(stepOf(envelope, "head").getId(), head, headSignature.getId(), true, ActorContext.none());

        SignatureStep signed = stepRepository.findById(stepOf(envelope, "head").getId()).orElseThrow();
        assertThat(signed.getEvidenceHmac()).isNotBlank();
        assertThat(verification.sealIntact(signed)).isTrue();
    }

    @Test
    @DisplayName("แก้ไขหลักฐานในฐานข้อมูล ต้องตรวจจับได้จากผนึกที่ไม่ตรง")
    void tamperingWithEvidenceBreaksTheSeal() {
        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("head", head.getId())), null);
        workflow.sign(stepOf(envelope, "head").getId(), head, headSignature.getId(), true, ActorContext.none());

        // Someone edits the record directly — the exact case the seal exists for.
        SignatureStep signed = stepRepository.findById(stepOf(envelope, "head").getId()).orElseThrow();
        signed.setSignedAt(signed.getSignedAt().minusDays(30));
        stepRepository.saveAndFlush(signed);

        assertThat(verification.sealIntact(signed)).isFalse();

        SignatureVerificationService.VerificationReport report =
                verification.verify(requestRepository.findByIdWithSteps(envelope.getId()).orElseThrow());
        assertThat(report.allSealsIntact()).isFalse();
        assertThat(report.fullyValid()).isFalse();
        assertThat(report.headlineThai()).contains("ผิดปกติ");
    }

    @Test
    @DisplayName("ลงนามครบและไม่ถูกแก้ไข ผลตรวจสอบต้องผ่านทั้งหมด")
    void completedAndUntouchedVerifiesClean() {
        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("head", head.getId()),
                        new SignerAssignment("dean", dean.getId())),
                null);

        workflow.sign(stepOf(envelope, "head").getId(), head, headSignature.getId(), true, ActorContext.none());
        workflow.sign(stepOf(envelope, "dean").getId(), dean, deanSignature.getId(), true, ActorContext.none());

        SignatureVerificationService.VerificationReport report =
                verification.verify(requestRepository.findByIdWithSteps(envelope.getId()).orElseThrow());

        assertThat(report.contentIntact()).isTrue();
        assertThat(report.allSealsIntact()).isTrue();
        assertThat(report.sealingEnabled()).isTrue();
        assertThat(report.fullyValid()).isTrue();
        assertThat(report.signers()).hasSize(2);
        assertThat(report.signers()).allMatch(SignatureVerificationService.SignerLine::sealIntact);
    }

    @Test
    @DisplayName("เนื้อหาถูกแก้ ผลตรวจสอบต้องบอกว่าไม่ตรงกับฉบับที่ลงนาม")
    void editedContentIsReported() {
        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("head", head.getId())), null);
        workflow.sign(stepOf(envelope, "head").getId(), head, headSignature.getId(), true, ActorContext.none());

        SignatureRequest stored = requestRepository.findByIdWithSteps(envelope.getId()).orElseThrow();
        stored.setFrozenJson("{\"dean_name\":\"changed\"}");
        requestRepository.saveAndFlush(stored);

        SignatureVerificationService.VerificationReport report = verification.verify(stored);
        assertThat(report.contentIntact()).isFalse();
        assertThat(report.fullyValid()).isFalse();
    }

    @Test
    @DisplayName("ลงนามแทนต้องบันทึกเหตุผลและพิมพ์ (แทน) นำหน้าชื่อ")
    void delegatedSigningIsRecorded() {
        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("dean", dean.getId(),
                        "คณบดีลาราชการ รองคณบดีปฏิบัติราชการแทน")),
                null);

        SignatureStep step = stepOf(envelope, "dean");
        assertThat(step.isDelegated()).isTrue();
        assertThat(step.getDelegateReason()).contains("ปฏิบัติราชการแทน");
        assertThat(step.getPrintedSignerName()).startsWith("(แทน)");
    }

    @Test
    @DisplayName("ไม่ได้ลงนามแทน ต้องไม่มีคำว่า (แทน)")
    void ordinarySigningIsNotMarkedDelegated() {
        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("dean", dean.getId())), null);

        SignatureStep step = stepOf(envelope, "dean");
        assertThat(step.isDelegated()).isFalse();
        assertThat(step.getPrintedSignerName()).doesNotContain("(แทน)");
    }

    @Test
    @DisplayName("เลยกำหนดแล้ว ระบบปิดคำขอและข้ามขั้นตอนที่เหลือ")
    void expiryClosesTheRound() {
        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("head", head.getId()),
                        new SignerAssignment("dean", dean.getId())),
                LocalDateTime.now().minusDays(1));

        Result result = workflow.expire(envelope.getId());

        assertThat(result.ok()).isTrue();
        assertThat(requestRepository.findById(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.EXPIRED);
        assertThat(stepOf(envelope, "head").getStatus()).isEqualTo(SignatureStepStatus.SKIPPED);
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.SKIPPED);
        // An expired round no longer holds the document.
        assertThat(workflow.isDocumentLocked(MODULE, REQUEST_ID, DOC_TYPE)).isFalse();
    }

    @Test
    @DisplayName("ปิดคำขอที่ปิดไปแล้วซ้ำไม่ได้")
    void cannotExpireTwice() {
        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("head", head.getId())),
                LocalDateTime.now().minusDays(1));

        assertThat(workflow.expire(envelope.getId()).ok()).isTrue();
        assertThat(workflow.expire(envelope.getId()).ok()).isFalse();
    }

    @Test
    @DisplayName("แอดมินเลือกตัวเองเป็นผู้ลงนามและเซ็นได้")
    void anAdminCanBeAssignedAndSign() throws IOException {
        // The dean here is ROLE_ADMIN, which is the common real arrangement.
        UserSignature adminSignature = newSignature(admin);

        SignatureRequest envelope = createEnvelope(
                List.of(new SignerAssignment("dean", admin.getId())), null);

        assertThat(workflow.findInbox(admin)).hasSize(1);

        Result result = workflow.sign(stepOf(envelope, "dean").getId(), admin,
                adminSignature.getId(), true, ActorContext.none());

        assertThat(result.ok()).as(result.error()).isTrue();
        assertThat(stepOf(envelope, "dean").getStatus()).isEqualTo(SignatureStepStatus.SIGNED);
        assertThat(requestRepository.findById(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("แผงเลือกผู้ลงนามต้องเสนอ 'ฉัน' ให้แอดมินเลือกเซ็นเองได้")
    void panelOffersTheCurrentUserAsASigner() {
        com.ecom.academic.dto.SignaturePanelView panel =
                workflow.buildPanel(MODULE, REQUEST_ID, DOC_TYPE, admin);

        assertThat(panel.signable()).isTrue();
        assertThat(panel.currentUserOption()).isNotNull();
        assertThat(panel.currentUserOption().userId()).isEqualTo(admin.getId());
        assertThat(panel.currentUserOption().signable()).isTrue();
        assertThat(panel.canSend()).isTrue();
    }

    @Test
    @DisplayName("รหัสตรวจสอบต้องไม่ซ้ำกันระหว่างคำขอ")
    void verificationCodesAreUnique() {
        SignatureRequest first = createEnvelope(
                List.of(new SignerAssignment("head", head.getId())), null);
        workflow.cancel(first.getId(), admin, "ทดสอบ", ActorContext.none());

        SignatureRequest second = createEnvelope(
                List.of(new SignerAssignment("head", head.getId())), null);

        assertThat(second.getVerificationCode()).isNotEqualTo(first.getVerificationCode());
    }
}
