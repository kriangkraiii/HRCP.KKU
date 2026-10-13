package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailTemplateHelperTest {

    @Test
    @DisplayName("Email template ต้องมีชื่อทางการและอัตลักษณ์ CP HRD สไตล์ Sign-in ครบถ้วน")
    void containsOfficialUniversityAndCollegeIdentity() {
        String html = EmailTemplateHelper.wrapLayout("ทดสอบหัวเรื่อง", "ป้ายกำกับ", "<p>เนื้อหาทดสอบ</p>");

        assertThat(html)
                .contains("CP")
                .contains("HRD")
                .contains("COLLEGE OF COMPUTING HUMAN RESOURCE DEVELOPMENT SYSTEM")
                .contains("วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น")
                .contains("College of Computing, Khon Kaen University")
                .contains("043-009700")
                .contains("computing.kku.ac.th")
                .contains("ทดสอบหัวเรื่อง")
                .contains("เนื้อหาทดสอบ");
    }

    @Test
    @DisplayName("สร้างอีเมล OTP ต้องมีรหัส OTP และระบุชื่อทางการ")
    void buildsOtpEmailCorrectly() {
        String html = EmailTemplateHelper.buildOtpEmail("ดร.สมชาย ใจดี", "12345678", "เข้าสู่ระบบ", 5);

        assertThat(html)
                .contains("CP")
                .contains("HRD")
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
                .contains("CP")
                .contains("HRD")
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
                .contains("CP")
                .contains("HRD")
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
                .contains("CP")
                .contains("HRD")
                .contains("อาจารย์ใจดี")
                .contains("teacher@kku.ac.th")
                .contains("ปัญหาระบบ")
                .contains("กดปุ่มไม่ทำงาน")
                .contains("รายละเอียดทดสอบ")
                .contains("2 ไฟล์");
    }

    @Test
    @DisplayName("attachLogos ต้องเป็น safe no-op เพื่อไม่ให้เกิดป้าย [inline] ใน Gmail")
    void attachLogosWithoutException() throws Exception {
        org.springframework.mail.javamail.JavaMailSenderImpl sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        jakarta.mail.internet.MimeMessage message = sender.createMimeMessage();
        org.springframework.mail.javamail.MimeMessageHelper helper =
                new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

        helper.setText("<html><body>Hello</body></html>", true);
        EmailTemplateHelper.attachLogos(helper);

        // Verify zero-attachment: No inline parts added, eliminating [inline] in Gmail
        jakarta.mail.Multipart rootMp = (jakarta.mail.Multipart) message.getContent();
        jakarta.mail.Multipart relatedMp = (jakarta.mail.Multipart) rootMp.getBodyPart(0).getContent();
        assertThat(relatedMp.getCount()).isEqualTo(1); // Only HTML body, 0 inline image attachments
    }

    /**
     * Every email the system sends, built the way the services build them. A feature checked here
     * holds for all of them, because they share one layout and one letter builder.
     */
    private static java.util.List<String> everyEmail() {
        String url = "https://hrd.computing.kku.ac.th/admin/academic/request/6";
        String letter = EmailTemplateHelper.Letter.of("เรื่องทดสอบ", "ท่าน")
                .details(java.util.Map.of("รหัสคำร้อง", "KKU-1"))
                .note("warn", "หมายเหตุ", "บรรทัดหนึ่ง\nบรรทัดสอง")
                .link("เปิดคำร้องในระบบ", url)
                .close("จึงเรียนมาเพื่อโปรดทราบ");
        return java.util.List.of(
                EmailTemplateHelper.wrapLayout("หัวเรื่อง", "ป้าย", letter),
                EmailTemplateHelper.buildOtpEmail("คุณทดสอบ", "123456", "ลงนาม", 5),
                EmailTemplateHelper.buildPasswordResetEmail(url),
                EmailTemplateHelper.buildResultLetterEmail("คุณทดสอบ", "KKU-1", "อว 660/1", "1 ต.ค. 2569",
                        "ดีมาก", "1 ต.ค. 2571", url),
                EmailTemplateHelper.buildCommitteeInvitationEmail("ศ.ดร.ทดสอบ", "KKU-1", "คุณทดสอบ", url,
                        java.util.List.of("a.pdf"), java.util.List.of(java.util.Map.entry("ลิงก์", "https://drive.google.com/x"))),
                EmailTemplateHelper.buildSystemAlertEmail("ผู้ดูแลระบบ", "ทดสอบ", "#dc2626", "!", "search",
                        "บรรทัดหนึ่ง\nบรรทัดสอง", "00:00"));
    }

    @Test
    @DisplayName("อีเมลไม่มีปุ่ม — ลิงก์เป็นตัวอักษรที่เห็น URL เต็ม")
    void emailsCarryPlainLinksNotButtons() {
        for (String html : everyEmail()) {
            assertThat(html)
                    .doesNotContain("หากปุ่มไม่ทำงาน")
                    .doesNotContain("padding:12px 28px")
                    .doesNotContain("display:inline-block");
        }
        assertThat(everyEmail().get(0))
                .contains(">https://hrd.computing.kku.ac.th/admin/academic/request/6</a>");
    }

    @Test
    @DisplayName("ไม่ใช้ CSS ที่ caniemail.com ระบุว่า Outlook/Gmail ไม่รองรับ")
    void emailsAvoidCssTheMajorClientsDrop() {
        for (String html : everyEmail()) {
            assertThat(html)
                    .as("Outlook for Windows อ่านแค่ background-color — gradient ทำให้หัวขาว ตัวอักษรขาวมองไม่เห็น")
                    .doesNotContain("linear-gradient")
                    .doesNotContain("rgba(")
                    .doesNotContain("box-shadow")
                    .doesNotContain("overflow:")
                    .doesNotContain("white-space:")
                    .doesNotContain("word-break")
                    .doesNotContain("target=");
            assertThat(html)
                    .as("Outlook ไม่รู้จัก max-width — ความกว้าง 600px มาจากตารางเฉพาะ Outlook")
                    .contains("<!--[if mso]><table role='presentation' width='600'");
        }
    }

    @Test
    @DisplayName("ทุกอีเมลบอกว่าต้องเข้าไปดำเนินการที่ hrd.computing.kku.ac.th")
    void everyEmailNamesTheSystemToActIn() {
        for (String html : everyEmail()) {
            assertThat(html)
                    .contains("เข้าสู่ระบบเพื่อดำเนินการได้ที่ระบบพัฒนาบุคลากร (CP HRD)")
                    .contains("<a href='https://hrd.computing.kku.ac.th/'");
        }
    }
}
