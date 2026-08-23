package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ecom.academic.model.SignatureKind;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.UserSignatureRepository;
import com.ecom.academic.service.UserSignatureService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * End-to-end tests for the personal signature library.
 *
 * <p>Goes through the real filter chain and renders the real Thymeleaf template,
 * because two of the things worth proving here only exist at that level: that
 * the page is reachable by both roles, and that one person cannot fetch
 * another's signature image.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:esigntestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false"
})
@DisplayName("ลายเซ็นของฉัน (/esign)")
class UserSignatureControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSignatureRepository signatureRepository;

    @Autowired
    private UserSignatureService signatureService;

    private MockMvc mvc;
    private UserDtls owner;
    private UserDtls stranger;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        signatureRepository.deleteAll();
        userRepository.deleteAll();

        owner = newUser("owner@kku.ac.th", "ROLE_USER");
        stranger = newUser("stranger@kku.ac.th", "ROLE_ADMIN");
    }

    private UserDtls newUser(String email, String role) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setName("Test Person");
        u.setPassword("{noop}irrelevant");
        u.setRole(role);
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        return userRepository.save(u);
    }

    private static String signaturePngDataUrl() throws IOException {
        BufferedImage image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.drawLine(10, 60, 230, 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private UserSignature saveSignatureFor(UserDtls person) throws IOException {
        UserSignatureService.SaveResult result = signatureService.create(
                person, signaturePngDataUrl(), SignatureKind.DRAW, "ลายเซ็นของฉัน", null, null, false);
        assertThat(result.ok()).isTrue();
        return result.signature();
    }

    @Test
    @DisplayName("หน้าลายเซ็นเปิดได้และ template เรนเดอร์ผ่าน")
    void pageRendersForAUser() throws Exception {
        mvc.perform(get("/esign/my-signatures").with(user(owner.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("academic/esign/my_signatures"))
                .andExpect(model().attributeExists("signatures", "maxSignatures"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ลายเซ็นของฉัน")));
    }

    @Test
    @DisplayName("ผู้ดูแลระบบก็เปิดหน้าเดียวกันได้ (คณบดีมักเป็น ROLE_ADMIN)")
    void pageRendersForAnAdminToo() throws Exception {
        // The whole reason these routes live under /esign rather than /user.
        mvc.perform(get("/esign/my-signatures").with(user(stranger.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("academic/esign/my_signatures"));
    }

    @Test
    @DisplayName("บันทึกลายเซ็นใหม่แล้วขึ้นในรายการ และเป็นลายเซ็นหลักอัตโนมัติ")
    void savingASignatureStoresIt() throws Exception {
        mvc.perform(post("/esign/my-signatures")
                        .with(user(owner.getEmail()).roles("USER")).with(csrf())
                        .param("imageData", signaturePngDataUrl())
                        .param("kind", "DRAW")
                        .param("name", "ลายเซ็นของฉัน"))
                .andExpect(status().is3xxRedirection());

        var saved = signatureService.findMine(owner);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getName()).isEqualTo("ลายเซ็นของฉัน");
        assertThat(saved.get(0).getKind()).isEqualTo(SignatureKind.DRAW);
        // First one becomes the default so signing has something pre-selected.
        assertThat(saved.get(0).getIsDefault()).isTrue();
        assertThat(saved.get(0).getWidthPx()).isPositive();
    }

    @Test
    @DisplayName("เจ้าของเปิดรูปลายเซ็นตัวเองได้")
    void ownerCanFetchTheirOwnImage() throws Exception {
        UserSignature mine = saveSignatureFor(owner);

        mvc.perform(get("/esign/signature/" + mine.getId() + "/image")
                        .with(user(owner.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("คนอื่นเปิดรูปลายเซ็นของเราไม่ได้ แม้จะเป็นผู้ดูแลระบบ")
    void othersCannotFetchSomeoneElsesImage() throws Exception {
        UserSignature mine = saveSignatureFor(owner);

        // 404 rather than 403: a signature image is personal data, and refusing
        // by "forbidden" would confirm the id exists. Admin gets no exception —
        // being an administrator is not ownership.
        mvc.perform(get("/esign/signature/" + mine.getId() + "/image")
                        .with(user(stranger.getEmail()).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ผู้ที่ยังไม่ล็อกอินถูกส่งไปหน้าเข้าสู่ระบบ")
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/esign/my-signatures"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("ลบแล้วหายจากรายการ แต่แถวยังอยู่เป็นหลักฐาน")
    void deleteIsSoftAndHidesFromTheList() throws Exception {
        UserSignature mine = saveSignatureFor(owner);

        mvc.perform(post("/esign/my-signatures/" + mine.getId() + "/delete")
                        .with(user(owner.getEmail()).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(signatureService.findMine(owner)).isEmpty();
        // Kept, because signed documents reference it.
        assertThat(signatureRepository.findById(mine.getId())).isPresent();
    }

    @Test
    @DisplayName("ลบลายเซ็นของคนอื่นไม่ได้")
    void cannotDeleteSomeoneElsesSignature() throws Exception {
        UserSignature mine = saveSignatureFor(owner);

        mvc.perform(post("/esign/my-signatures/" + mine.getId() + "/delete")
                        .with(user(stranger.getEmail()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(signatureService.findMine(owner)).hasSize(1);
    }

    @Test
    @DisplayName("ตั้งลายเซ็นหลักได้ทีละหนึ่งอันเท่านั้น")
    void onlyOneDefaultAtATime() throws Exception {
        UserSignature first = saveSignatureFor(owner);
        UserSignature second = saveSignatureFor(owner);

        mvc.perform(post("/esign/my-signatures/" + second.getId() + "/default")
                        .with(user(owner.getEmail()).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(signatureService.findMine(first.getId(), owner).orElseThrow().getIsDefault()).isFalse();
        assertThat(signatureService.findMine(second.getId(), owner).orElseThrow().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("เก็บได้ไม่เกินจำนวนสูงสุด")
    void enforcesThePerUserCap() throws IOException {
        for (int i = 0; i < UserSignatureService.MAX_PER_USER; i++) {
            saveSignatureFor(owner);
        }

        UserSignatureService.SaveResult overflow = signatureService.create(
                owner, signaturePngDataUrl(), SignatureKind.DRAW, "เกินโควตา", null, null, false);

        assertThat(overflow.ok()).isFalse();
        assertThat(overflow.error()).contains(String.valueOf(UserSignatureService.MAX_PER_USER));
        assertThat(signatureService.findMine(owner)).hasSize(UserSignatureService.MAX_PER_USER);
    }

    @Test
    @DisplayName("ข้อมูลรูปที่ไม่ใช่ PNG ถูกปฏิเสธพร้อมข้อความบอกเหตุผล")
    void rejectsBadImagePayload() {
        UserSignatureService.SaveResult result = signatureService.create(
                owner, "data:text/html;base64,PHNjcmlwdD4=", SignatureKind.DRAW, "ไม่ดี", null, null, false);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isNotBlank();
        assertThat(signatureService.findMine(owner)).isEmpty();
    }
}
