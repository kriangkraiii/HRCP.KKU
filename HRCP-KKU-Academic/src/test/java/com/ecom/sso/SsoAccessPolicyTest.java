package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * KKU SSO authenticates the entire university — every student, every
 * department, every contractor account. Authentication is therefore not
 * authorisation for this system, and these tests pin the boundary: a valid KKU
 * login on its own must never be enough to get in.
 */
class SsoAccessPolicyTest {

    private final FsFacultyRepository facultyRepo = mock(FsFacultyRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private SsoAccessPolicy policy(String allowedEmails, boolean allowExistingLocal) {
        return new SsoAccessPolicy(facultyRepo, userRepository, allowedEmails, allowExistingLocal);
    }

    private FsFaculty faculty(String email, String activeFlag) {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(1001L);
        f.setEmail(email);
        f.setIsActive(activeFlag);
        f.setSyncedAt(LocalDateTime.now());
        return f;
    }

    @Test
    @DisplayName("อาจารย์ที่อยู่ในระบบเข้าได้")
    void facultyInDirectoryIsAllowed() {
        when(facultyRepo.findByEmailNormalized("somchai@kku.ac.th"))
                .thenReturn(Optional.of(faculty("somchai@kku.ac.th", "A")));

        SsoAccessPolicy.Decision d = policy("", false).evaluate("somchai@kku.ac.th");

        assertThat(d.allowed()).isTrue();
        assertThat(d.faculty()).isNotNull();
    }

    @Test
    @DisplayName("คนที่ไม่อยู่ในรายชื่อ ต้องเข้าไม่ได้ แม้ล็อกอิน KKU SSO สำเร็จ")
    void validKkuAccountOutsideTheDirectoryIsRefused() {
        // A real student with a working KKU account.
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(null);

        SsoAccessPolicy.Decision d = policy("", false).evaluate("student@kkumail.com");

        assertThat(d.allowed())
                .as("ยืนยันตัวตนผ่าน ไม่ได้แปลว่ามีสิทธิ์เข้าระบบนี้")
                .isFalse();
        assertThat(d.reason()).contains("ยังไม่ได้รับสิทธิ์");
    }

    @Test
    @DisplayName("อาจารย์ที่ถูกระงับในระบบต้นทาง ต้องเข้าไม่ได้")
    void inactiveFacultyIsRefused() {
        when(facultyRepo.findByEmailNormalized("retired@kku.ac.th"))
                .thenReturn(Optional.of(faculty("retired@kku.ac.th", "0")));

        SsoAccessPolicy.Decision d = policy("", false).evaluate("retired@kku.ac.th");

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("ระงับ");
    }

    @Test
    @DisplayName("อีเมลที่ใส่ไว้ใน allowed-emails เข้าได้ (สำหรับแอดมินที่ไม่ใช่อาจารย์)")
    void explicitlyAllowlistedAddressIsAllowed() {
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());

        SsoAccessPolicy.Decision d = policy("admin@kku.ac.th, support@kku.ac.th", false)
                .evaluate("admin@kku.ac.th");

        assertThat(d.allowed()).isTrue();
        assertThat(d.faculty()).isNull();
    }

    @Test
    @DisplayName("allowed-emails ต้องไม่สนตัวพิมพ์เล็กใหญ่และช่องว่าง")
    void allowlistMatchingIgnoresCaseAndWhitespace() {
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());
        SsoAccessPolicy p = policy("  Admin@KKU.ac.th ", false);

        assertThat(p.evaluate("admin@kku.ac.th").allowed()).isTrue();
        assertThat(p.evaluate("  ADMIN@kku.ac.th  ").allowed()).isTrue();
    }

    @Test
    @DisplayName("บัญชีเดิมในระบบต้องเข้าไม่ได้ ถ้าไม่ได้เปิดสวิตช์ไว้")
    void existingLocalAccountIsNotAWayAroundTheAllowlist() {
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());

        UserDtls stale = new UserDtls();
        stale.setEmail("old@kku.ac.th");
        stale.setIsEnable(true);
        when(userRepository.findByEmail("old@kku.ac.th")).thenReturn(stale);

        assertThat(policy("", false).evaluate("old@kku.ac.th").allowed())
                .as("แถวเก่าที่ค้างในฐานข้อมูลต้องไม่กลายเป็นช่องทางเลี่ยงรายชื่อ")
                .isFalse();
        // ...unless the deployment explicitly opts in.
        assertThat(policy("", true).evaluate("old@kku.ac.th").allowed()).isTrue();
    }

    @Test
    @DisplayName("บัญชีเดิมที่ถูกปิดใช้งาน ต้องเข้าไม่ได้แม้เปิดสวิตช์")
    void disabledLocalAccountStaysOutEvenWhenOptedIn() {
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());

        UserDtls disabled = new UserDtls();
        disabled.setEmail("banned@kku.ac.th");
        disabled.setIsEnable(false);
        when(userRepository.findByEmail("banned@kku.ac.th")).thenReturn(disabled);

        assertThat(policy("", true).evaluate("banned@kku.ac.th").allowed()).isFalse();
    }

    @Test
    @DisplayName("บัญชีที่แอดมินปิดใช้งาน ต้องเข้าไม่ได้ แม้ยังอยู่ในรายชื่ออาจารย์")
    void deactivatedLocalAccountIsRefusedEvenWhenStillInTheDirectory() {
        // Deactivation is a local decision the upstream directory knows nothing
        // about. If only the directory were consulted, switching an account off
        // would have no effect at all on the SSO door.
        when(facultyRepo.findByEmailNormalized("suspended@kku.ac.th"))
                .thenReturn(Optional.of(faculty("suspended@kku.ac.th", "A")));

        UserDtls deactivated = new UserDtls();
        deactivated.setEmail("suspended@kku.ac.th");
        deactivated.setIsEnable(false);
        when(userRepository.findByEmail("suspended@kku.ac.th")).thenReturn(deactivated);

        SsoAccessPolicy.Decision d = policy("", false).evaluate("suspended@kku.ac.th");

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("ปิดการใช้งาน");
    }

    @Test
    @DisplayName("บัญชีที่ปิดใช้งาน ต้องเข้าไม่ได้ แม้อยู่ใน allowed-emails")
    void deactivatedLocalAccountIsRefusedEvenWhenAllowlisted() {
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());

        UserDtls deactivated = new UserDtls();
        deactivated.setEmail("admin@kku.ac.th");
        deactivated.setIsEnable(false);
        when(userRepository.findByEmail("admin@kku.ac.th")).thenReturn(deactivated);

        assertThat(policy("admin@kku.ac.th", false).evaluate("admin@kku.ac.th").allowed()).isFalse();
    }

    @Test
    @DisplayName("SSO ไม่ส่งอีเมลกลับมา ต้องปฏิเสธ")
    void missingEmailIsRefused() {
        SsoAccessPolicy p = policy("", false);

        assertThat(p.evaluate(null).allowed()).isFalse();
        assertThat(p.evaluate("   ").allowed()).isFalse();
    }

    @Test
    @DisplayName("allowed-emails ว่าง ต้องไม่กลายเป็นอนุญาตทุกคน")
    void emptyAllowlistDoesNotBecomeWildcard() {
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());

        assertThat(policy("", false).evaluate("anyone@kku.ac.th").allowed()).isFalse();
        assertThat(policy("  ,  , ", false).evaluate("anyone@kku.ac.th").allowed()).isFalse();
    }
}
