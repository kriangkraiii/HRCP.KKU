package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ecom.config.KkuEmailDomains;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.sso.ExternalLinkService;

/**
 * หนังสือเชิญบอกวิธีเข้าระบบและลงนามตามกลุ่มของผู้ลงนาม (docs/PLAN-external-magic-link.md ข้อ 2.1, 4.6)
 *
 * <p>ทดสอบตัวข้อความตรง ๆ เพราะเทสที่ส่งอีเมลจริงห้ามใช้โดเมน kku.ac.th
 */
@DisplayName("หนังสือเชิญผู้ลงนามภายนอก: คนนอก มข. ใช้ลิงก์ในอีเมล บุคลากร มข. ต่างคณะใช้ KKU SSO และ .p12 ของ มข.")
class ExternalSignerInvitationTextTest {

    private final ExternalLinkService links = mock(ExternalLinkService.class);
    private final ExternalSignerService service = new ExternalSignerService(mock(UserRepository.class),
            mock(PasswordEncoder.class), mock(JavaMailSender.class), "noreply@example.invalid",
            "https://hrd.example.invalid", new KkuEmailDomains("kku.ac.th,kkumail.com"), links);

    private static UserDtls external(String email) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setRole(UserDtls.ROLE_EXTERNAL);
        u.setTitle("รศ.ดร.");
        u.setFirstName("วิภา");
        u.setLastName("ภายนอก");
        return u;
    }

    @Test
    @DisplayName("คนนอก มข.: ไม่ต้องตั้งรหัสผ่าน รอลิงก์ในอีเมล ไม่ต้องมี Digital ID — ไม่พูดถึง KKU SSO")
    void anOutsiderIsToldAboutTheEmailLink() {
        when(links.isEnabled()).thenReturn(true);
        when(links.ttlDays()).thenReturn(7L);

        String body = service.invitationBody(external("wipa@cmu.ac.th"), null, null);

        assertThat(body).contains("ไม่ต้องตั้งรหัสผ่าน").contains("ภายใน 7 วัน")
                .contains("https://hrd.example.invalid/auth/link")
                .contains("ไม่จำเป็นต้องมีใบรับรองอิเล็กทรอนิกส์")
                .doesNotContain("KKU SSO").doesNotContain("i.kku.ac.th");
    }

    @Test
    @DisplayName("บุคลากร มข. ต่างคณะ: เข้าด้วย KKU SSO และต้องติดตั้ง .p12 ของ มข. — ไม่มีทางยืนยันทางอีเมล")
    void aKkuColleagueIsToldToUseSsoAndAKkuDigitalId() {
        when(links.isEnabled()).thenReturn(true);

        String body = service.invitationBody(external("somsri@kku.ac.th"), null, null);

        assertThat(body).contains("KKU SSO").contains("i.kku.ac.th")
                .doesNotContain("/auth/link")
                .doesNotContain("ระบบจะส่งรหัสยืนยันตัวตนไปยังอีเมลนี้");
    }

    @Test
    @DisplayName("ปิดลิงก์ทางอีเมลอยู่: คนนอก มข. ไม่ได้รับคำสั่งให้รอลิงก์ที่จะไม่มาถึง")
    void noEmailLinkPromiseWhenTheFeatureIsOff() {
        when(links.isEnabled()).thenReturn(false);

        String body = service.invitationBody(external("wipa@cmu.ac.th"), null, null);

        assertThat(body).doesNotContain("/auth/link").doesNotContain("ไม่ต้องตั้งรหัสผ่าน");
    }
}
