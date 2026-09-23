package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.repository.AcademicAttachmentRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * ผู้ลงนามต้องเห็นว่ากำลังพิจารณาคำร้องของใคร และเปิดไฟล์แนบประกอบได้จากหน้าลงนาม
 *
 * <p>ไฟล์แนบเดิมเปิดได้เฉพาะใต้ {@code /admin/**} ผู้ลงนามที่เป็น ROLE_USER จึงตัดสิน
 * "เห็นควร / ไม่เห็นควร" โดยไม่เห็นหลักฐานประกอบเลย endpoint ใหม่ใต้ {@code /esign/**}
 * ต้องไม่กลายเป็นช่องให้คนนอกหรือเลข id ที่เดาเอาไปเปิดไฟล์ของคำร้องอื่น
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
@DisplayName("หน้าลงนามแสดงข้อมูลคำร้องและไฟล์แนบ")
class SigningRequestContextTest {

    private static final AtomicInteger RUN = new AtomicInteger();

    @TempDir
    Path tempDir;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicRequestService requestService;

    @Autowired
    private AcademicAttachmentRepository attachmentRepository;

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private com.ecom.academic.service.UserSignatureService signatureService;

    @Autowired
    private com.ecom.academic.repository.UserDigitalCertificateRepository certificateRepository;

    private MockMvc mockMvc;
    private UserDtls applicant;
    private UserDtls outsider;
    private AcademicRequest request;
    private Long stepId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        String run = "ctx" + RUN.incrementAndGet() + "-";
        applicant = newUser(run + "applicant@kku.ac.th", "ROLE_USER");
        outsider = newUser(run + "outsider@kku.ac.th", "ROLE_USER");

        request = requestService.createDraftRequest(applicant);
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "บันทึกข้อความ", "{\"applicant_name\":\"สมชาย\"}",
                List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        assertThat(created.ok()).isTrue();
        stepId = created.request().getSteps().get(0).getId();
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

    private AcademicAttachment attach(AcademicRequest owner, String filename, String fileType,
            boolean deleted) throws Exception {
        Path file = tempDir.resolve(RUN.get() + "-" + filename);
        Files.write(file, "%PDF-1.4 test".getBytes());
        AcademicAttachment a = new AcademicAttachment();
        a.setRequest(owner);
        a.setOriginalFilename(filename);
        a.setStoredFilePath(file.toString());
        a.setFileType(fileType);
        a.setFileSize(Files.size(file));
        a.setIsDeleted(deleted);
        return attachmentRepository.save(a);
    }

    private AcademicAttachment attachLink(AcademicRequest owner, String url) {
        AcademicAttachment a = new AcademicAttachment();
        a.setRequest(owner);
        a.setOriginalFilename("ผลงานออนไลน์");
        a.setStoredFilePath(url);
        a.setFileType("LINK");
        a.setFileSize(0L);
        a.setIsDeleted(false);
        return attachmentRepository.save(a);
    }

    private String attachmentUrl(Long attachmentId) {
        return "/esign/sign/" + stepId + "/attachment/" + attachmentId;
    }

    @Test
    @DisplayName("หน้าลงนามแสดงเลขที่คำร้อง ผู้ยื่น และรายการไฟล์แนบที่ยังไม่ถูกลบ")
    void signPageShowsRequestSummaryAndAttachments() throws Exception {
        AcademicAttachment kept = attach(request, "มคอ3-ภาคต้น.pdf", "PDF", false);
        attach(request, "ไฟล์ที่ลบแล้ว.pdf", "PDF", true);
        AcademicRequest reloaded = requestService.findById(request.getId()).orElseThrow();

        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("ข้อมูลคำร้อง")
                .contains(reloaded.getRequestCode())
                .contains(applicant.getName())
                .contains("ไฟล์แนบประกอบการพิจารณา")
                .contains("มคอ3-ภาคต้น.pdf")
                .contains(attachmentUrl(kept.getId()))
                .doesNotContain("ไฟล์ที่ลบแล้ว.pdf");
    }

    @Test
    @DisplayName("คำร้องที่ไม่มีไฟล์แนบ แสดงว่าไม่มี แทนการ์ดว่างเปล่า")
    void signPageSaysWhenThereAreNoAttachments() throws Exception {
        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("ไม่มีไฟล์แนบ");
    }

    @Test
    @DisplayName("คำอธิบายเสริมอยู่ในปุ่ม (i) ส่วนข้อความยินยอมยังอยู่บนหน้าเต็มฉบับ")
    void explanationsMoveIntoInfoTipsButConsentStaysInline() throws Exception {
        com.ecom.academic.model.UserDigitalCertificate cert = new com.ecom.academic.model.UserDigitalCertificate();
        cert.setUser(applicant);
        cert.setCertificatePath("dummy.p12");
        cert.setOriginalFilename("kku.p12");
        cert.setSubjectDn("CN=ทดสอบ, O=KKU, C=TH");
        cert.setIssuerDn("CN=ODT KKU CA, O=KKU, C=TH");
        cert.setValidFrom(java.time.LocalDateTime.now().minusDays(1));
        cert.setValidTo(java.time.LocalDateTime.now().plusYears(1));
        cert.setActive(true);
        certificateRepository.save(cert);
        newSignature(applicant);

        String html = mockMvc.perform(get("/esign/sign/" + stepId)
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                // ปุ่ม (i) ต้องทำงานได้จริง: มี popover พร้อมเนื้อหา และโหลดสคริปต์ที่เปิดมัน
                .contains("/js/info_tip.js")
                .contains("data-bs-toggle=\"popover\"")
                .contains("data-bs-content=\"เอกสารนี้เป็นฉบับที่ล็อกไว้ตอนส่งเวียนลงนาม")
                // ข้อความยินยอมเป็นสาระสำคัญทางกฎหมาย ต้องไม่ถูกย้ายไปซ่อนในปุ่ม
                .contains(com.ecom.academic.model.SignatureStep.CONSENT_TEXT)
                .contains("ข้าพเจ้าได้อ่านและยินยอมตามข้อความข้างต้น")
                .doesNotContain("Freeze Snapshot")
                .doesNotContain("One-Click Sign");
    }

    private void newSignature(UserDtls owner) throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(200, 60, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        String dataUrl = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        assertThat(signatureService.create(owner, dataUrl, com.ecom.academic.model.SignatureKind.DRAW,
                "ลายเซ็น", null, null, true).ok()).isTrue();
    }

    @Test
    @DisplayName("ผู้ลงนามเปิดไฟล์แนบของคำร้องที่ตัวเองพิจารณาได้")
    void theSignerCanOpenAnAttachment() throws Exception {
        AcademicAttachment a = attach(request, "evidence.pdf", "PDF", false);

        mockMvc.perform(get(attachmentUrl(a.getId()))
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")));
    }

    @Test
    @DisplayName("คนนอกที่ไม่ใช่ผู้ลงนามของขั้นนี้ เปิดไฟล์แนบไม่ได้")
    void anOutsiderCannotOpenAnAttachment() throws Exception {
        AcademicAttachment a = attach(request, "evidence.pdf", "PDF", false);

        mockMvc.perform(get(attachmentUrl(a.getId()))
                        .with(user(outsider.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ใช้ step ของตัวเองเปิดไฟล์แนบของคำร้องอื่นไม่ได้")
    void anAttachmentOfAnotherRequestIsNotReachable() throws Exception {
        AcademicRequest other = requestService.createDraftRequest(outsider);
        AcademicAttachment foreign = attach(other, "someone-else.pdf", "PDF", false);

        mockMvc.perform(get(attachmentUrl(foreign.getId()))
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ไฟล์แนบที่ถูกลบแล้วเปิดไม่ได้")
    void aDeletedAttachmentIsNotReachable() throws Exception {
        AcademicAttachment deleted = attach(request, "old.pdf", "PDF", true);

        mockMvc.perform(get(attachmentUrl(deleted.getId()))
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ไฟล์แนบแบบลิงก์พาไปที่ลิงก์นั้น")
    void aLinkAttachmentRedirects() throws Exception {
        AcademicAttachment link = attachLink(request, "https://example.org/portfolio");

        mockMvc.perform(get(attachmentUrl(link.getId()))
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.org/portfolio"));
    }
}
