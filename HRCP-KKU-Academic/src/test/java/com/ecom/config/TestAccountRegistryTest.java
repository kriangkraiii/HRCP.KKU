package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ecom.util.EmailTemplateHelper;

/**
 * Which accounts count as rehearsal accounts.
 *
 * <p>The rule used to match whole domains, so anybody at {@code example.com} or
 * {@code test.com} — and every address at {@code user.com} and
 * {@code admin.com}, not just the two seeded logins — was silently unreachable.
 * Being unreachable without any error is the worst way for this to be wrong,
 * hence the negative cases below.
 */
@DisplayName("บัญชีทดสอบที่ระบบจะไม่ส่งอีเมลจริงไปหา")
class TestAccountRegistryTest {

    private final TestAccountRegistry registry =
            new TestAccountRegistry("user@user.com, admin@admin.com");

    @Test
    @DisplayName("ตรงกับสองบัญชีที่ตั้งไว้เท่านั้น ไม่สนตัวพิมพ์เล็กใหญ่")
    void matchesTheConfiguredAddresses() {
        assertThat(registry.isTestAccount("user@user.com")).isTrue();
        assertThat(registry.isTestAccount("admin@admin.com")).isTrue();
        assertThat(registry.isTestAccount("  ADMIN@Admin.COM ")).isTrue();
    }

    @Test
    @DisplayName("ที่อยู่จริงที่โดเมนคล้ายกัน ต้องยังส่งได้ตามปกติ")
    void doesNotSwallowRealAddressesThatMerelyLookLikeTests() {
        assertThat(registry.isTestAccount("somchai@user.com")).isFalse();
        assertThat(registry.isTestAccount("hr@admin.com")).isFalse();
        assertThat(registry.isTestAccount("dean@example.com")).isFalse();
        assertThat(registry.isTestAccount("head@test.com")).isFalse();
        assertThat(registry.isTestAccount("kriangkrai.p@kkumail.com")).isFalse();
    }

    @Test
    @DisplayName("ไม่มีที่อยู่ ไม่ใช่บัญชีทดสอบ — คนละปัญหากัน")
    void blankIsNotATestAccount() {
        assertThat(registry.isTestAccount(null)).isFalse();
        assertThat(registry.isTestAccount("   ")).isFalse();
    }

    @Test
    @DisplayName("ผู้ส่งที่ไม่ได้ล็อกอิน (งาน cron) ไม่นับเป็นบัญชีทดสอบ")
    void schedulersAreNotTestActors() {
        SecurityContextHolder.clearContext();
        assertThat(registry.isTestActor()).isFalse();
    }

    @Test
    @DisplayName("ถ้าคนที่กำลังใช้งานเป็นบัญชีทดสอบ ระบบต้องรู้")
    void recognisesTheSignedInTestAccount() {
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("user@user.com", "x",
                            AuthorityUtils.createAuthorityList("ROLE_USER")));
            assertThat(registry.isTestActor()).isTrue();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("somchai@kku.ac.th", "x",
                            AuthorityUtils.createAuthorityList("ROLE_USER")));
            assertThat(registry.isTestActor()).isFalse();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("ที่อยู่ว่างยังต้องไม่ยิง SMTP แม้จะไม่ใช่บัญชีทดสอบ")
    void helperStillSkipsSendingWhenThereIsNoAddress() {
        // ตัวช่วยที่จุดส่งเมลใช้ตอบคำถามว่า "ต้องข้ามการส่งไหม" ซึ่งกว้างกว่า
        // คำถามว่า "เป็นบัญชีทดสอบไหม" — ที่อยู่ว่างส่งไม่ได้อยู่แล้ว
        assertThat(EmailTemplateHelper.isTestEmail(null)).isTrue();
        assertThat(EmailTemplateHelper.isTestEmail("  ")).isTrue();
        assertThat(EmailTemplateHelper.isTestEmail("user@user.com")).isTrue();
        assertThat(EmailTemplateHelper.isTestEmail("somchai@kku.ac.th")).isFalse();
    }
}
