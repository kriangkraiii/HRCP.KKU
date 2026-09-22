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

    // ==================== ตำแหน่งทางวิชาการ ====================
    //
    // ช่องนี้ถูกถอดออกจากหน้าโปรไฟล์ของผู้ยื่นแล้ว แต่ฟอร์มนี้ bind เข้า UserDtls ตรง ๆ
    // โดยไม่มี DTO และไม่มี @InitBinder กั้น การถอดช่องออกจาก template จึงไม่ได้กันอะไรเลย
    // เทสต์ชุดนี้ยิงเข้า service ตรง ๆ แบบที่ไม่มีช่องอยู่แล้ว ซึ่งเป็นสิ่งที่วิธีเดิมกันไม่ได้

    @Test
    @DisplayName("ผู้ยื่นแก้ตำแหน่งทางวิชาการของตัวเองไม่ได้ แม้ยิง POST ตรง")
    void updateProfile_applicantCannotChangeOwnAcademicPosition() {
        attacker.setAcademicPosition("อาจารย์");
        attacker.setAcademicPositionEn("Lecturer");
        userRepository.save(attacker);

        UserDtls forged = new UserDtls();
        forged.setFirstName("Attacker");
        forged.setLastName("One");
        forged.setAcademicPosition("ศาสตราจารย์");
        forged.setAcademicPositionEn("Professor");

        userService.updateUserProfile(forged, emptyUpload(), attacker.getEmail());

        UserDtls after = userRepository.findById(attacker.getId()).orElseThrow();
        assertThat(after.getAcademicPosition())
                .as("ตำแหน่งวิชาการต้องมาจากทะเบียนบุคลากร ไม่ใช่จากฟอร์มของผู้ยื่น")
                .isEqualTo("อาจารย์");
        assertThat(after.getAcademicPositionEn()).isEqualTo("Lecturer");
    }

    @Test
    @DisplayName("บันทึกโปรไฟล์ปกติต้องไม่ล้างตำแหน่งวิชาการทิ้ง")
    void updateProfile_doesNotWipeAcademicPositionWhenFieldIsAbsent() {
        attacker.setAcademicPosition("รองศาสตราจารย์");
        attacker.setAcademicPositionEn("Associate Professor");
        userRepository.save(attacker);

        // หน้าโปรไฟล์ไม่ render ช่องนี้แล้ว Spring จึง bind มาเป็น null — ถ้า service
        // เซ็ตโดยไม่ดูเงื่อนไข ตำแหน่งจะหายทุกครั้งที่ผู้ใช้แค่มาแก้เบอร์โทร
        UserDtls fromForm = new UserDtls();
        fromForm.setFirstName("Attacker");
        fromForm.setLastName("One");
        fromForm.setMobileNumber("0812345678");

        userService.updateUserProfile(fromForm, emptyUpload(), attacker.getEmail());

        UserDtls after = userRepository.findById(attacker.getId()).orElseThrow();
        assertThat(after.getMobileNumber()).isEqualTo("0812345678");
        assertThat(after.getAcademicPosition()).isEqualTo("รองศาสตราจารย์");
        assertThat(after.getAcademicPositionEn()).isEqualTo("Associate Professor");
    }

    @Test
    @DisplayName("แอดมินแก้ตำแหน่งวิชาการในโปรไฟล์ตัวเองได้ ทั้งไทยและอังกฤษ")
    void updateProfile_adminMayChangeOwnAcademicPosition() {
        UserDtls admin = persistUser("admin@test.com", "Admin", "Person");
        admin.setRole("ROLE_ADMIN");
        admin.setAcademicPosition("อาจารย์");
        admin.setAcademicPositionEn("Lecturer");
        userRepository.save(admin);

        UserDtls fromForm = new UserDtls();
        fromForm.setFirstName("Admin");
        fromForm.setLastName("Person");
        fromForm.setAcademicPosition("ผู้ช่วยศาสตราจารย์");
        fromForm.setAcademicPositionEn("Assistant Professor");

        userService.updateUserProfile(fromForm, emptyUpload(), admin.getEmail());

        UserDtls after = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(after.getAcademicPosition()).isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(after.getAcademicPositionEn()).isEqualTo("Assistant Professor");
    }

    @Test
    @DisplayName("role ที่ส่งมาในฟอร์มต้องไม่ปลดล็อกช่องตำแหน่งวิชาการ")
    void updateProfile_roleInFormDoesNotUnlockAcademicPosition() {
        attacker.setAcademicPosition("อาจารย์");
        userRepository.save(attacker);

        UserDtls forged = new UserDtls();
        forged.setFirstName("Attacker");
        forged.setLastName("One");
        forged.setRole("ROLE_ADMIN");            // ฟอร์มบอกเองว่าเป็นแอดมิน
        forged.setAcademicPosition("ศาสตราจารย์");

        userService.updateUserProfile(forged, emptyUpload(), attacker.getEmail());

        UserDtls after = userRepository.findById(attacker.getId()).orElseThrow();
        assertThat(after.getAcademicPosition()).isEqualTo("อาจารย์");
        assertThat(after.getRole()).isEqualTo("ROLE_USER");
    }

    // ==================== คำนำหน้าชื่อ ====================
    //
    // คำนำหน้าฝังตำแหน่งวิชาการไว้ในตัวเอง (ผศ./รศ./ศ.) ล็อกแต่ช่องตำแหน่งแล้วเปิดช่องนี้ไว้
    // ก็แค่ย้ายที่โกหก เพราะชื่อที่ประทับบนเอกสารและใต้ลายเซ็นอ่านจากคำนำหน้า ไม่ใช่จากช่องตำแหน่ง
    // (ดู SignerNameResolver.printedName)

    @Test
    @DisplayName("ผู้ยื่นแก้คำนำหน้าชื่อของตัวเองไม่ได้ แม้ยิง POST ตรง")
    void updateProfile_applicantCannotChangeOwnTitle() {
        attacker.setTitle("อ.ดร.");
        attacker.setAcademicPosition("อาจารย์");
        userRepository.save(attacker);

        UserDtls forged = new UserDtls();
        forged.setFirstName("Attacker");
        forged.setLastName("One");
        forged.setTitle("ศ.ดร.");

        userService.updateUserProfile(forged, emptyUpload(), attacker.getEmail());

        assertThat(userRepository.findById(attacker.getId()).orElseThrow().getTitle())
                .as("ตั้งคำนำหน้าเป็น ศ.ดร. เองไม่ได้ ในเมื่อตำแหน่งวิชาการก็แก้เองไม่ได้")
                .isEqualTo("อ.ดร.");
    }

    @Test
    @DisplayName("บันทึกโปรไฟล์ปกติต้องไม่ล้างคำนำหน้าชื่อทิ้ง")
    void updateProfile_doesNotWipeTitleWhenFieldIsAbsent() {
        attacker.setTitle("ผศ.ดร.");
        userRepository.save(attacker);

        // หน้าโปรไฟล์ไม่ render ช่องนี้แล้ว Spring จึง bind มาเป็น null
        UserDtls fromForm = new UserDtls();
        fromForm.setFirstName("Attacker");
        fromForm.setLastName("One");
        fromForm.setMobileNumber("0898765432");

        userService.updateUserProfile(fromForm, emptyUpload(), attacker.getEmail());

        UserDtls after = userRepository.findById(attacker.getId()).orElseThrow();
        assertThat(after.getMobileNumber()).isEqualTo("0898765432");
        assertThat(after.getTitle()).isEqualTo("ผศ.ดร.");
    }

    @Test
    @DisplayName("แอดมินแก้คำนำหน้าชื่อในโปรไฟล์ตัวเองได้")
    void updateProfile_adminMayChangeOwnTitle() {
        UserDtls admin = persistUser("title-admin@test.com", "Admin", "Person");
        admin.setRole("ROLE_ADMIN");
        admin.setTitle("นาย");
        userRepository.save(admin);

        UserDtls fromForm = new UserDtls();
        fromForm.setFirstName("Admin");
        fromForm.setLastName("Person");
        fromForm.setTitle("รศ.ดร.");

        userService.updateUserProfile(fromForm, emptyUpload(), admin.getEmail());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getTitle())
                .isEqualTo("รศ.ดร.");
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
