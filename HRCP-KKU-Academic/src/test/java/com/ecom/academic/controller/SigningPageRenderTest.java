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

        mockMvc.perform(get("/user/academic/request/" + request.getId() + "/document/0")
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

        mockMvc.perform(get("/user/academic/request/" + request.getId() + "/document/0")
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

        // เอกสารที่ 1 มีสองช่อง: ผู้ยื่น และนักทรัพยากรบุคคล
        // ผู้ยื่นส่งไปโดยเลือกเฉพาะช่องของตัวเอง ช่อง hr จึงยังว่าง
        var created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "แบบตรวจสอบเอกสาร", "{\"applicant_name\":\"ทดสอบ\"}",
                java.util.List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        org.assertj.core.api.Assertions.assertThat(created.ok()).isTrue();

        mockMvc.perform(get("/admin/academic/request/" + request.getId() + "/document/1")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                // ก่อนแก้: แผงถูกล็อก ไม่มีทางเติมผู้ลงนามที่เหลือได้เลย
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/forward")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("ยืนยันความถูกต้องและส่งเวียนลงนามต่อ")));
    }
}
