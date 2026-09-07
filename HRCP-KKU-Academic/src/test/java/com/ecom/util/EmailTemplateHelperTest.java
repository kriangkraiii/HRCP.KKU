package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailTemplateHelperTest {

    @Test
    @DisplayName("Email template ต้องมีชื่อทางการและโลโก้ CDN ครบถ้วน")
    void containsOfficialUniversityAndCollegeIdentity() {
        String html = EmailTemplateHelper.wrapLayout("ทดสอบหัวเรื่อง", "ป้ายกำกับ", "<p>เนื้อหาทดสอบ</p>");

        assertThat(html)
                .contains(EmailTemplateHelper.LOGO_KKU_URL)
                .contains(EmailTemplateHelper.LOGO_CP_URL)
                .contains("วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น")
                .contains("College of Computing, Khon Kaen University")
                .contains("ระบบบริหารจัดการตำแหน่งทางวิชาการ (HRCP.KKU)")
                .contains("043-009700")
                .contains("computing.kku.ac.th")
                .contains("ทดสอบหัวเรื่อง")
                .contains("เนื้อหาทดสอบ");
    }

    @Test
    @DisplayName("สร้างอีเมล OTP ต้องมีรหัส OTP โลโก้ และระบุชื่อทางการ")
    void buildsOtpEmailCorrectly() {
        String html = EmailTemplateHelper.buildOtpEmail("ดร.สมชาย ใจดี", "12345678", "เข้าสู่ระบบ", 5);

        assertThat(html)
                .contains(EmailTemplateHelper.LOGO_KKU_URL)
                .contains(EmailTemplateHelper.LOGO_CP_URL)
                .contains("12345678")
                .contains("ดร.สมชาย ใจดี")
                .contains("เข้าสู่ระบบ")
                .contains("5 นาที")
                .contains("วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");
    }

    @Test
    @DisplayName("สร้างอีเมลรีเซ็ตรหัสผ่านต้องมีลิงก์และรูปแบบทางการ")
    void buildsPasswordResetEmailCorrectly() {
        String resetUrl = "https://localhost:8081/reset-password?token=abc123xyz";
        String html = EmailTemplateHelper.buildPasswordResetEmail(resetUrl);

        assertThat(html)
                .contains(EmailTemplateHelper.LOGO_KKU_URL)
                .contains(EmailTemplateHelper.LOGO_CP_URL)
                .contains(resetUrl)
                .contains("ตั้งรหัสผ่านใหม่")
                .contains("วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");
    }

    @Test
    @DisplayName("สร้างอีเมลอัปเดตสถานะคำร้องต้องมีรหัสคำร้องและสถานะใหม่")
    void buildsStatusChangeEmailCorrectly() {
        String html = EmailTemplateHelper.buildStatusChangeEmail(
                "ผศ.ดร.สมศรี มีสุข",
                "คำร้องขอตำแหน่งทางวิชาการ",
                "POS-2026-001",
                "แบบร่าง",
                "รับคำร้องแล้ว",
                "#16a34a",
                "<div>รายละเอียดเพิ่มเติม</div>");

        assertThat(html)
                .contains(EmailTemplateHelper.LOGO_KKU_URL)
                .contains(EmailTemplateHelper.LOGO_CP_URL)
                .contains("ผศ.ดร.สมศรี มีสุข")
                .contains("POS-2026-001")
                .contains("รับคำร้องแล้ว")
                .contains("วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");
    }

    @Test
    @DisplayName("สร้างอีเมลรายงานปัญหา/ข้อเสนอแนะต้องมีรายละเอียดครบถ้วน")
    void buildsFeedbackEmailCorrectly() {
        String html = EmailTemplateHelper.buildFeedbackEmail(
                "อาจารย์ใจดี", "teacher@kku.ac.th", "ปัญหาระบบ",
                "กดปุ่มไม่ทำงาน", "รายละเอียดทดสอบ", 2);

        assertThat(html)
                .contains(EmailTemplateHelper.LOGO_KKU_URL)
                .contains(EmailTemplateHelper.LOGO_CP_URL)
                .contains("อาจารย์ใจดี")
                .contains("teacher@kku.ac.th")
                .contains("ปัญหาระบบ")
                .contains("กดปุ่มไม่ทำงาน")
                .contains("รายละเอียดทดสอบ")
                .contains("2 ไฟล์");
    }

    @Test
    @DisplayName("attachLogos ต้องแนบรูปภาพ inline logo โดยไม่เกิดข้อผิดพลาด")
    void attachLogosWithoutException() throws Exception {
        org.springframework.mail.javamail.JavaMailSenderImpl sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        jakarta.mail.internet.MimeMessage message = sender.createMimeMessage();
        org.springframework.mail.javamail.MimeMessageHelper helper =
                new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

        helper.setText("<html><body>Hello</body></html>", true);
        EmailTemplateHelper.attachLogos(helper);

        // Verify that inline parts were added inside the multipart/related container
        jakarta.mail.Multipart rootMp = (jakarta.mail.Multipart) message.getContent();
        jakarta.mail.Multipart relatedMp = (jakarta.mail.Multipart) rootMp.getBodyPart(0).getContent();
        assertThat(relatedMp.getCount()).isEqualTo(3); // HTML body + 2 inline logos
    }
}
