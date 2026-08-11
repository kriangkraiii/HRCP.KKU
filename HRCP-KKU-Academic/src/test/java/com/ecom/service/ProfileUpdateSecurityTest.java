package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Security regression tests for profile update.
 *
 * Covers the bugs found in the 2026-08-11 audit:
 *   C-01 — any user could overwrite another user's profile by changing the form `id`
 *   C-02 — the uploaded filename was used verbatim as a path, allowing traversal
 *          and arbitrary extensions, with REPLACE_EXISTING overwriting files
 *   M-04 — Optional.get() on a missing id threw NoSuchElementException (HTTP 500)
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:profilesecuritydb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class ProfileUpdateSecurityTest {

    @TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload.profile-image-dir", () -> tempUploadDir.toString());
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private UserDtls attacker;
    private UserDtls victim;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        attacker = persistUser("attacker@test.com", "Attacker", "One");
        victim = persistUser("victim@test.com", "Victim", "Two");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private UserDtls persistUser(String email, String firstName, String lastName) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setPassword("irrelevant");
        u.setRole("ROLE_USER");
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        u.setFailedAttempt(0);
        u.setProfileImage("default.png");
        return userRepository.save(u);
    }

    private MockMultipartFile emptyUpload() {
        return new MockMultipartFile("img", "", "image/jpeg", new byte[0]);
    }

    // ==================== C-01 ====================

    @Test
    @DisplayName("C-01: form id ของผู้ใช้คนอื่นต้องถูกเพิกเฉย ไม่ใช่นำไปใช้ค้นหา")
    void updateProfile_ignoresFormIdAndUsesAuthenticatedUser() {
        UserDtls forged = new UserDtls();
        forged.setId(victim.getId());          // attacker points at the victim's row
        forged.setFirstName("HACKED");
        forged.setLastName("HACKED");
        forged.setMobileNumber("0000000000");

        userService.updateUserProfile(forged, emptyUpload(), attacker.getEmail());

        UserDtls victimAfter = userRepository.findById(victim.getId()).orElseThrow();
        assertThat(victimAfter.getFirstName())
                .as("victim's profile must be untouched")
                .isEqualTo("Victim");
        assertThat(victimAfter.getMobileNumber()).isNotEqualTo("0000000000");
    }

    @Test
    @DisplayName("C-01: การแก้ไขต้องมีผลกับบัญชีที่ล็อกอินอยู่เท่านั้น")
    void updateProfile_appliesChangesToAuthenticatedUser() {
        UserDtls form = new UserDtls();
        form.setId(victim.getId());            // wrong id on purpose
        form.setFirstName("Renamed");
        form.setLastName("Properly");

        userService.updateUserProfile(form, emptyUpload(), attacker.getEmail());

        UserDtls attackerAfter = userRepository.findById(attacker.getId()).orElseThrow();
        assertThat(attackerAfter.getFirstName()).isEqualTo("Renamed");
        assertThat(attackerAfter.getLastName()).isEqualTo("Properly");
    }

    // ==================== C-02 ====================

    @Test
    @DisplayName("C-02: ชื่อไฟล์แบบ path traversal ต้องไม่เขียนออกนอกโฟลเดอร์ปลายทาง")
    void upload_withTraversalFilename_doesNotEscapeUploadDir() throws Exception {
        Path outside = tempUploadDir.getParent().resolve("pwned.png");
        Files.deleteIfExists(outside);

        MockMultipartFile evil = new MockMultipartFile(
                "img", "../pwned.png", "image/png", pngBytes());

        userService.updateUserProfile(new UserDtls(), evil, attacker.getEmail());

        assertThat(Files.exists(outside))
                .as("no file may be written outside the upload directory")
                .isFalse();
    }

    @Test
    @DisplayName("C-02: ไฟล์ที่ไม่ใช่รูปภาพต้องถูกปฏิเสธ")
    void upload_withNonImageExtension_isRejected() {
        MockMultipartFile html = new MockMultipartFile(
                "img", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());

        userService.updateUserProfile(new UserDtls(), html, attacker.getEmail());

        UserDtls after = userRepository.findById(attacker.getId()).orElseThrow();
        assertThat(after.getProfileImage())
                .as("an .html upload must never become the profile image")
                .doesNotEndWith(".html");
    }

    @Test
    @DisplayName("C-02: ผู้ใช้สองคนอัปโหลดชื่อไฟล์เดียวกันต้องไม่ทับกัน")
    void upload_sameFilenameByTwoUsers_doesNotOverwrite() {
        MockMultipartFile a = new MockMultipartFile("img", "avatar.png", "image/png", pngBytes());
        MockMultipartFile b = new MockMultipartFile("img", "avatar.png", "image/png", pngBytes());

        userService.updateUserProfile(new UserDtls(), a, attacker.getEmail());
        userService.updateUserProfile(new UserDtls(), b, victim.getEmail());

        String attackerImage = userRepository.findById(attacker.getId()).orElseThrow().getProfileImage();
        String victimImage = userRepository.findById(victim.getId()).orElseThrow().getProfileImage();

        assertThat(attackerImage).isNotEqualTo(victimImage);
    }

    @Test
    @DisplayName("C-02: ชื่อไฟล์ที่บันทึกลง DB ต้องเป็นชื่อที่ระบบสร้าง ไม่ใช่ชื่อจาก client")
    void upload_storesGeneratedFilenameNotClientFilename() {
        MockMultipartFile img = new MockMultipartFile("img", "avatar.png", "image/png", pngBytes());

        userService.updateUserProfile(new UserDtls(), img, attacker.getEmail());

        String stored = userRepository.findById(attacker.getId()).orElseThrow().getProfileImage();
        assertThat(stored).isNotEqualTo("avatar.png");
        assertThat(stored).endsWith(".png");
    }

    // ==================== C-02 via /admin/update-profile-image ====================

    @Test
    @DisplayName("C-02: updateProfileImageOnly ต้องไม่เขียนไฟล์ออกนอกโฟลเดอร์เช่นกัน")
    void updateProfileImageOnly_withTraversalFilename_doesNotEscapeUploadDir() throws Exception {
        Path outside = tempUploadDir.getParent().resolve("pwned-only.png");
        Files.deleteIfExists(outside);

        // Note: this filename passes an extension whitelist — the traversal is
        // in the directory part, which an extension check never inspects.
        MockMultipartFile evil = new MockMultipartFile(
                "img", "../pwned-only.png", "image/png", pngBytes());

        userService.updateProfileImageOnly(attacker.getId(), evil);

        assertThat(Files.exists(outside)).isFalse();
    }

    @Test
    @DisplayName("C-02: updateProfileImageOnly ต้องปฏิเสธไฟล์ที่ไม่ใช่รูปภาพ")
    void updateProfileImageOnly_withNonImage_reportsFailure() {
        MockMultipartFile html = new MockMultipartFile(
                "img", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());

        var result = userService.updateProfileImageOnly(attacker.getId(), html);

        assertThat(result.get("success")).isEqualTo("false");
    }

    // ==================== M-04 ====================

    @Test
    @DisplayName("M-04: อีเมลที่ไม่มีในระบบต้องคืน null ไม่ใช่โยน exception")
    void updateProfile_withUnknownUser_returnsNullInsteadOfThrowing() {
        assertThatCode(() -> {
            UserDtls result = userService.updateUserProfile(
                    new UserDtls(), emptyUpload(), "does-not-exist@test.com");
            assertThat(result).isNull();
        }).doesNotThrowAnyException();
    }

    /** ProfileImageStorage decodes uploads, so tests need real encoded bytes. */
    private static byte[] pngBytes() {
        try {
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not encode a test png", e);
        }
    }
}
