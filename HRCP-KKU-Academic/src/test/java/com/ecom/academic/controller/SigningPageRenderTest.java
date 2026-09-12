package com.ecom.academic.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.model.SignatureKind;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Renders the signing pages for real, through Thymeleaf.
 *
 * <p>Every template here was edited without any test touching it: the shared
 * layout gained the flash-message block, the inbox gained a held-round badge,
 * and the signature panel gained the forward form. A Thymeleaf mistake compiles
 * perfectly and only fails when somebody opens the page — which is exactly how
 * the original fault went unnoticed — so these tests open the pages.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:signrenderdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false"
})
@DisplayName("หน้าจอลงนามต้อง render ได้จริง")
class SigningPageRenderTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicRequestService requestService;

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private com.ecom.academic.service.UserSignatureService signatureService;

    @Autowired
    private com.ecom.academic.repository.UserDigitalCertificateRepository certificateRepository;

    private MockMvc mockMvc;
    private UserDtls applicant;
    private UserDtls admin;

    private static final java.util.concurrent.atomic.AtomicInteger RUN =
            new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        String run = "render" + RUN.incrementAndGet() + "-";
        applicant = newUser(run + "applicant@kku.ac.th", "ROLE_USER");
        admin = newUser(run + "admin@kku.ac.th", "ROLE_ADMIN");
    }

    private UserDtls newUser(String email, String role) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setName("ผู้ใช้ " + email);
        u.setPassword("{noop}x");
        u.setRole(role);
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        u.setEmailNotificationEnabled(false);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("กล่องลงนามเปิดได้ทั้งผู้ใช้ทั่วไปและแอดมิน")
    void inboxRenders() throws Exception {
        mockMvc.perform(get("/esign/inbox").with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/esign/inbox").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("หน้าฟอร์มเอกสารพร้อมแผงลงนาม render ได้ และมีปุ่มส่งไปลงนาม")
    void applicantDocumentFormRendersTheSignaturePanel() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);

        mockMvc.perform(get("/user/academic/request/" + request.getId() + "/document/1")
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("/esign/envelope/create")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("ส่งและลงนามในส่วนของผู้ยื่นคำร้อง")));
    }

    @Test
    @DisplayName("ข้อความผลลัพธ์จาก layout กลางแสดงจริง ไม่ใช่หายเงียบ")
    void flashMessagesAreRenderedByTheSharedLayout() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);

        mockMvc.perform(get("/user/academic/request/" + request.getId() + "/document/1")
                        .with(user(applicant.getEmail()).roles("USER"))
                        .flashAttr("errorMsg", "ยังไม่มีข้อมูลในเอกสาร"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("ยังไม่มีข้อมูลในเอกสาร")));
    }

    @Test
    @DisplayName("เอกสารที่ส่งไปแล้วแต่ยังขาดผู้ลงนาม แอดมินต้องเห็นปุ่มส่งเวียนต่อ")
    void adminSeesTheForwardFormWhenSlotsAreStillUnfilled() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);

        // เอกสารที่ 2 มีสองช่อง: ผู้ยื่น และนักทรัพยากรบุคคล
        // ผู้ยื่นส่งไปโดยเลือกเฉพาะช่องของตัวเอง ช่อง hr จึงยังว่าง
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2,
                "แบบตรวจสอบเอกสาร", "{\"applicant_name\":\"ทดสอบ\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        org.assertj.core.api.Assertions.assertThat(created.ok()).isTrue();

        mockMvc.perform(get("/admin/academic/request/" + request.getId() + "/document/2")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                // ก่อนแก้: แผงถูกล็อก ไม่มีทางเติมผู้ลงนามที่เหลือได้เลย
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/forward")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("ยืนยันความถูกต้องและส่งเวียนลงนามต่อ")));
    }
    @Test
    @DisplayName("หน้าลงนาม render ได้ และ URL ตัวอย่างเอกสารต้องเรียกได้จริง")
    void signPageBuildsAWorkingPreviewUrl() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ ขอรับการประเมินผลการสอน", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        org.assertj.core.api.Assertions.assertThat(created.ok()).isTrue();
        Long stepId = created.request().getSteps().get(0).getId();

        // เดิม @{...} มีวงเล็บต่อท้าย ซึ่ง Thymeleaf อ่านเป็นรายการพารามิเตอร์
        // ผลคือหน้าพังทั้งหน้า (ถ้าไม่มีลายเซ็น) หรือได้ URL เพี้ยนจนกรอบเอกสารขึ้น 404
        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String marker = "data-pdf-url=\"";
        int at = html.indexOf(marker);
        org.assertj.core.api.Assertions.assertThat(at).as("ต้องมี data-pdf-url").isNotNegative();
        String previewUrl = html.substring(at + marker.length(), html.indexOf('"', at + marker.length()));

        org.assertj.core.api.Assertions.assertThat(previewUrl)
                .isEqualTo("/esign/sign/" + stepId + "/preview");

        // และ URL นั้นต้องเรียกได้จริง ไม่ใช่ 404
        mockMvc.perform(get(previewUrl).with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("เลยกำหนดที่ตั้งเตือนไว้เอง: หน้าลงนามต้องเป็นป้ายเตือน ไม่ใช่ปุ่มขอขยายเวลา")
    void anAdvisoryDeadlineRendersAReminderNotAGate() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                java.time.LocalDateTime.now().minusDays(1), applicant, ActorContext.none());
        org.assertj.core.api.Assertions.assertThat(created.ok()).isTrue();
        Long stepId = created.request().getSteps().get(0).getId();

        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("เลยกำหนดที่คุณตั้งเตือนไว้แล้ว")
                .doesNotContain("เอกสารนี้เลยกำหนดลงนามแล้ว")
                .doesNotContain("requestExtensionModal")
                // ปุ่มลงนามต้องยังกดได้จริง ไม่ใช่ถูกปิดเงียบ ๆ
                .doesNotContain("ขั้นตอนนี้ไม่สามารถลงนามได้แล้ว");
    }

    @Test
    @DisplayName("ผู้ยื่นต้องเห็นช่องตั้งเตือนเวลาลงนามของตัวเองในแผงส่งลงนาม")
    void theApplicantCanSetTheirOwnReminderDate() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);

        mockMvc.perform(get("/user/academic/request/" + request.getId() + "/document/1")
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("ตั้งเตือนให้ลงนามภายใน (ไม่บังคับ)")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("เลยกำหนดแล้วยังลงนามได้ตามปกติ")));
    }

    @Test
    @DisplayName("แผงลงนามต้องบอกว่ากำหนดที่เลยไปเป็นเพียงการแจ้งเตือน ไม่ใช่ป้ายแดง")
    void thePanelMarksAnAdvisoryDeadlineAsAReminder() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                java.time.LocalDateTime.now().minusDays(1), applicant, ActorContext.none());
        org.assertj.core.api.Assertions.assertThat(created.ok()).isTrue();

        mockMvc.perform(get("/user/academic/request/" + request.getId() + "/document/1")
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("เลยกำหนดที่ตั้งเตือนไว้ (ยังลงนามได้)")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("ตั้งเตือนให้ลงนามภายใน:")));
    }

    @Test
    @DisplayName("รอบที่ถูกปิดเพราะเลยกำหนด แอดมินต้องมีปุ่มตั้งกำหนดใหม่เพื่อเวียนต่อ")
    void anExpiredRoundOffersAWayBack() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2,
                "แบบตรวจสอบเอกสาร", "{\"applicant_name\":\"ทดสอบ\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId()),
                        new SignerAssignment("hr", admin.getId())),
                null, admin, ActorContext.none());
        org.assertj.core.api.Assertions.assertThat(created.ok()).isTrue();
        org.assertj.core.api.Assertions.assertThat(
                workflow.expire(created.request().getId()).ok()).isTrue();

        // ก่อนแก้: ซองที่ EXPIRED ไม่ปรากฏบนหน้าไหนเลย ปุ่ม "ขอขยายเวลา"
        // ที่ผู้ลงนามกดจึงไม่มีใครกดรับได้
        mockMvc.perform(get("/admin/academic/request/" + request.getId() + "/document/2")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("ตั้งกำหนดใหม่และเวียนต่อ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/esign/envelope/" + created.request().getId() + "/extend-due")));
    }

    /** A saved signature for this person, so the preview has one to stamp. */
    private Long newSignature(UserDtls owner) throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(200, 60, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.drawLine(5, 50, 195, 10);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        String dataUrl = "data:image/png;base64,"
                + java.util.Base64.getEncoder().encodeToString(out.toByteArray());

        var saved = signatureService.create(owner, dataUrl, SignatureKind.DRAW, "ลายเซ็น", null, null, true);
        org.assertj.core.api.Assertions.assertThat(saved.ok()).isTrue();
        return saved.signature().getId();
    }

    @Test
    @DisplayName("ผู้ลงนามที่มีลายเซ็นอยู่แล้ว — URL ต้องพ่วง userSignatureId ที่ถูกต้อง")
    void previewUrlCarriesTheChosenSignature() throws Exception {
        Long signatureId = newSignature(applicant);

        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ ขอรับการประเมินผลการสอน", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        Long stepId = created.request().getSteps().get(0).getId();

        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String marker = "data-pdf-url=\"";
        int at = html.indexOf(marker);
        String previewUrl = html.substring(at + marker.length(), html.indexOf('"', at + marker.length()));

        // นี่คือเคสที่พังจริง: ของเดิมได้ URL เพี้ยนจนกรอบเอกสารขึ้นหน้า 404
        org.assertj.core.api.Assertions.assertThat(previewUrl)
                .isEqualTo("/esign/sign/" + stepId + "/preview?userSignatureId=" + signatureId);

        mockMvc.perform(get("/esign/sign/" + stepId + "/preview")
                        .param("userSignatureId", String.valueOf(signatureId))
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ผู้ลงนามเลือกลายเซ็น — การ์ดลายเซ็นต้องมีตัวบอกสถานะชัดเจน (ค่าเริ่มต้น, กำลังเลือกใช้อันนี้, active notice)")
    void signatureSelectionCardsRenderClearlyWithDefaultAndActiveIndicators() throws Exception {
        com.ecom.academic.model.UserDigitalCertificate cert = new com.ecom.academic.model.UserDigitalCertificate();
        cert.setUser(applicant);
        cert.setCertificatePath("dummy_active.p12");
        cert.setOriginalFilename("kku_active.p12");
        cert.setSubjectDn("CN=นายสมชาย ทดสอบ, O=KKU, C=TH");
        cert.setIssuerDn("CN=ODT KKU CA, O=KKU, C=TH");
        cert.setValidFrom(java.time.LocalDateTime.now().minusDays(10));
        cert.setValidTo(java.time.LocalDateTime.now().plusYears(1));
        cert.setActive(true);
        certificateRepository.save(cert);

        Long sig1 = newSignature(applicant); // default = true

        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(200, 60, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        String dataUrl = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        signatureService.create(applicant, dataUrl, SignatureKind.DRAW, "ลายเซ็น 2 (แบบทางการ)", null, null, false);

        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ ขอรับการประเมินผลการสอน", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        Long stepId = created.request().getSteps().get(0).getId();

        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("sig-choice-card")
                .contains("ค่าเริ่มต้น")
                .contains("กำลังเลือกใช้อันนี้")
                .contains("activeSigNotice")
                .contains("selectedSigLabel")
                .contains("ลายเซ็น 2 (แบบทางการ)");
    }

    @Test
    @DisplayName("ผู้ลงนามที่ยังไม่มี Digital ID (.p12) — หน้าลงนามต้องแสดงกล่องแจ้งเตือนบังคับติดตั้งและมีคู่มือ Modal")
    void signingPageShowsP12RequirementAlertAndModalGuideWhenNoCert() throws Exception {
        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        Long stepId = created.request().getSteps().get(0).getId();

        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("จำเป็นต้องติดตั้ง Digital ID (.p12)")
                .contains("p12GuideModal")
                .contains("คู่มือการขอรับไฟล์ Digital ID (.p12) และรหัสผ่าน");
    }

    @Test
    @DisplayName("ผู้ลงนามที่ยังไม่มี Digital ID (.p12) — กดยืนยันลงนาม POST ต้องถูกปฏิเสธและแจ้งเตือนให้ติดตั้งก่อน")
    void signEndpointBlocksUserWithoutActiveP12Cert() throws Exception {
        Long signatureId = newSignature(applicant);

        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        Long stepId = created.request().getSteps().get(0).getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/esign/sign/" + stepId)
                        .param("userSignatureId", String.valueOf(signatureId))
                        .param("consent", "true")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/esign/sign/" + stepId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("errorMsg"));
    }

    @Test
    @DisplayName("ผู้ลงนามที่มีใบรับรอง .p12 หมดอายุแล้ว — หน้าลงนามต้องแจ้งเตือนว่าหมดอายุ และซ่อนฟอร์มลงนาม")
    void signingPageShowsExpiredAlertWhenCertIsExpired() throws Exception {
        com.ecom.academic.model.UserDigitalCertificate expiredCert = new com.ecom.academic.model.UserDigitalCertificate();
        expiredCert.setUser(applicant);
        expiredCert.setCertificatePath("dummy_expired.p12");
        expiredCert.setOriginalFilename("kku_expired.p12");
        expiredCert.setSubjectDn("CN=นายสมชาย ทดสอบ, O=KKU, C=TH");
        expiredCert.setIssuerDn("CN=ODT KKU CA, O=KKU, C=TH");
        expiredCert.setValidFrom(java.time.LocalDateTime.now().minusYears(2));
        expiredCert.setValidTo(java.time.LocalDateTime.now().minusDays(1)); // หมดอายุแล้ว
        expiredCert.setActive(true);
        certificateRepository.save(expiredCert);

        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        Long stepId = created.request().getSteps().get(0).getId();

        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("ใบรับรอง Digital ID (.p12) หมดอายุแล้ว")
                .contains("จะไม่สามารถใช้ลงนามในเอกสารได้")
                .contains("ไปอัปเดตไฟล์ .p12 ใหม่")
                .doesNotContain("ยืนยันการลงนาม");
    }

    @Test
    @DisplayName("ผู้ลงนามที่ใบรับรองหมดอายุ — กดยืนยันลงนาม POST ต้องถูกปฏิเสธและแจ้งเตือนว่าใบรับรองหมดอายุ")
    void signEndpointBlocksUserWithExpiredP12Cert() throws Exception {
        Long signatureId = newSignature(applicant);

        com.ecom.academic.model.UserDigitalCertificate expiredCert = new com.ecom.academic.model.UserDigitalCertificate();
        expiredCert.setUser(applicant);
        expiredCert.setCertificatePath("dummy_expired.p12");
        expiredCert.setOriginalFilename("kku_expired.p12");
        expiredCert.setSubjectDn("CN=นายสมชาย ทดสอบ, O=KKU, C=TH");
        expiredCert.setIssuerDn("CN=ODT KKU CA, O=KKU, C=TH");
        expiredCert.setValidFrom(java.time.LocalDateTime.now().minusYears(2));
        expiredCert.setValidTo(java.time.LocalDateTime.now().minusDays(1)); // หมดอายุแล้ว
        expiredCert.setActive(true);
        certificateRepository.save(expiredCert);

        AcademicRequest request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ", "{\"applicant_name\":\"สมชาย\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        Long stepId = created.request().getSteps().get(0).getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/esign/sign/" + stepId)
                        .param("userSignatureId", String.valueOf(signatureId))
                        .param("consent", "true")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/esign/sign/" + stepId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("errorMsg",
                        org.hamcrest.Matchers.containsString("หมดอายุแล้ว")));
    }

    @Test
    @DisplayName("ระบบสร้างตราประทับ Digital Signature Stamp สำหรับพิมพ์ชื่อ (TYPE) ได้ถูกต้อง")
    void generateDigitalStampProducesValidPng() throws Exception {
        // Test Thai digits conversion
        String thaiConverted = com.ecom.academic.service.UserSignatureService.toThaiDigits("2026.08.24 15:47:09 +07'00'");
        org.assertj.core.api.Assertions.assertThat(thaiConverted).isEqualTo("๒๐๒๖.๐๘.๒๔ ๑๕:๔๗:๐๙ +๐๗'๐๐'");

        // Test generation with email
        byte[] pngWithEmail = signatureService.generateDigitalStampPng("สุธน เจริญศิริ", "sutoch@kku.ac.th", java.time.LocalDateTime.now());
        org.assertj.core.api.Assertions.assertThat(pngWithEmail).isNotNull();
        org.assertj.core.api.Assertions.assertThat(pngWithEmail.length).isGreaterThan(1000);

        // Test generation with overload
        byte[] png = signatureService.generateDigitalStampPng("สมโภช พิมพ์พงษ์ต้อน", java.time.LocalDateTime.now());
        org.assertj.core.api.Assertions.assertThat(png).isNotNull();
        org.assertj.core.api.Assertions.assertThat(png.length).isGreaterThan(1000);

        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        org.assertj.core.api.Assertions.assertThat(img).isNotNull();
        org.assertj.core.api.Assertions.assertThat(img.getWidth()).isEqualTo(540);
        org.assertj.core.api.Assertions.assertThat(img.getHeight()).isEqualTo(185);

        // Test generation from raw ink image (for DRAW and UPLOAD)
        java.awt.image.BufferedImage inkImg = new java.awt.image.BufferedImage(200, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = inkImg.createGraphics();
        g2.setColor(java.awt.Color.BLACK);
        g2.drawLine(10, 10, 190, 70);
        g2.dispose();
        java.io.ByteArrayOutputStream inkBaos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(inkImg, "PNG", inkBaos);

        byte[] pngFromInk = signatureService.generateDigitalStampFromImage(
                inkBaos.toByteArray(), "สมโภช พิมพ์พงษ์ต้อน", "somphot@kku.ac.th", java.time.LocalDateTime.now());
        org.assertj.core.api.Assertions.assertThat(pngFromInk).isNotNull();
        org.assertj.core.api.Assertions.assertThat(pngFromInk.length).isGreaterThan(1000);

        java.awt.image.BufferedImage compositeImg = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngFromInk));
        org.assertj.core.api.Assertions.assertThat(compositeImg.getWidth()).isEqualTo(540);
        org.assertj.core.api.Assertions.assertThat(compositeImg.getHeight()).isEqualTo(185);

        // กรณีที่ 2: มีคำนำหน้าทางวิชาการ (ผศ.ดร. หรือ ผศ.)
        String formattedCase2 = com.ecom.academic.service.UserSignatureService.formatSignerNameWithPosition(
                "เกรียงไกร เกียรติบูรณกุล", "ผู้ช่วยศาสตราจารย์", "ผศ.ดร.");
        org.assertj.core.api.Assertions.assertThat(formattedCase2).isEqualTo("ผศ.ดร.เกรียงไกร เกียรติบูรณกุล");

        String formattedCase2Abbr = com.ecom.academic.service.UserSignatureService.formatSignerNameWithPosition(
                "กอเอ๋ยกอไก่ อ้ายยังจำได้มั้ย", null, "ผศ.");
        org.assertj.core.api.Assertions.assertThat(formattedCase2Abbr).isEqualTo("ผศ.กอเอ๋ยกอไก่ อ้ายยังจำได้มั้ย");

        // กรณีที่ 3: ไม่มีคำนำหน้าทางวิชาการ (ไม่ใส่คำนำหน้าทั่วไป และไม่ใส่คำว่า ผู้ช่วยศาสตราจารย์ นำหน้า)
        String formattedCase3 = com.ecom.academic.service.UserSignatureService.formatSignerNameWithPosition(
                "กอเอ๋ยกอไก่ อ้ายยังจำได้มั้ย", "ผู้ช่วยศาสตราจารย์");
        org.assertj.core.api.Assertions.assertThat(formattedCase3).isEqualTo("กอเอ๋ยกอไก่ อ้ายยังจำได้มั้ย");

        String formattedCase3Civil = com.ecom.academic.service.UserSignatureService.formatSignerNameWithPosition(
                "นายสมชาย ใจดี", "ผู้ช่วยศาสตราจารย์", "นาย");
        org.assertj.core.api.Assertions.assertThat(formattedCase3Civil).isEqualTo("สมชาย ใจดี");

        byte[] pngWithPosition = signatureService.generateDigitalStampPng(
                "เกรียงไกร เกียรติบูรณกุล", "ผู้ช่วยศาสตราจารย์", "kriangkrai@kku.ac.th", java.time.LocalDateTime.now());
        org.assertj.core.api.Assertions.assertThat(pngWithPosition).isNotNull();
        org.assertj.core.api.Assertions.assertThat(pngWithPosition.length).isGreaterThan(1000);

        java.awt.image.BufferedImage imgWithPos = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngWithPosition));
        org.assertj.core.api.Assertions.assertThat(imgWithPos.getWidth()).isEqualTo(540);
        org.assertj.core.api.Assertions.assertThat(imgWithPos.getHeight()).isEqualTo(185);

        // Test generation from image with position
        byte[] pngFromInkWithPos = signatureService.generateDigitalStampFromImage(
                inkBaos.toByteArray(), "เกรียงไกร เกียรติบูรณกุล", "ผู้ช่วยศาสตราจารย์", "kriangkrai@kku.ac.th", java.time.LocalDateTime.now());
        org.assertj.core.api.Assertions.assertThat(pngFromInkWithPos).isNotNull();
        java.awt.image.BufferedImage compositeImgWithPos = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngFromInkWithPos));
        org.assertj.core.api.Assertions.assertThat(compositeImgWithPos.getWidth()).isEqualTo(540);
        org.assertj.core.api.Assertions.assertThat(compositeImgWithPos.getHeight()).isEqualTo(185);
    }

    @Test
    @DisplayName("หน้ารวมลายเซ็น (ยังไม่มี .p12): ต้องแสดงขั้นตอนที่ 1 ติดตั้งใบรับรองก่อน และล็อกส่วนสร้างลายเซ็น")
    void mySignaturesRendersPrerequisiteStepWhenNoCert() throws Exception {
        String html = mockMvc.perform(get("/esign/my-signatures")
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("ขั้นตอนที่ 1")
                .contains("ติดตั้งใบรับรองดิจิทัล Digital ID (.p12) ของท่านก่อน")
                .contains("ขั้นตอนที่ 2: สร้างและบันทึกลายเซ็น")
                .contains("รอการติดตั้ง Digital ID (.p12)")
                .doesNotContain("id=\"signatureEditor\"")
                .doesNotContain("id=\"btnOpenCertManage\"");
    }

    @Test
    @DisplayName("หน้ารวมลายเซ็น (มี .p12 แล้ว): ต้องปลดล็อกส่วนสร้างลายเซ็น และมีปุ่มจัดการใบรับรอง .p12 พร้อม Modal")
    void mySignaturesRendersEditorAndManageButtonWhenActiveCertExists() throws Exception {
        com.ecom.academic.model.UserDigitalCertificate cert = new com.ecom.academic.model.UserDigitalCertificate();
        cert.setUser(applicant);
        cert.setCertificatePath("active_cert.p12");
        cert.setOriginalFilename("kku_cert.p12");
        cert.setSubjectDn("CN=เกรียงไกร ประเสริฐ, O=KKU, C=TH");
        cert.setIssuerDn("CN=ODT KKU CA, O=KKU, C=TH");
        cert.setValidFrom(java.time.LocalDateTime.now().minusDays(5));
        cert.setValidTo(java.time.LocalDateTime.now().plusYears(1));
        cert.setActive(true);
        certificateRepository.save(cert);

        String html = mockMvc.perform(get("/esign/my-signatures")
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("Digital ID:")
                .contains("เกรียงไกร ประเสริฐ")
                .contains("จัดการใบรับรอง .p12")
                .contains("id=\"signatureEditor\"")
                .contains("id=\"p12ManageModal\"")
                .doesNotContain("p12-step-badge")
                .doesNotContain("รอการติดตั้ง Digital ID (.p12)");
    }
}
