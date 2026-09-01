package com.ecom.util;

import java.util.Properties;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends one of every e-mail this system produces, to a real inbox, through real
 * Gmail. For looking at templates by eye — nothing here asserts anything.
 *
 * <p><b>Credentials come from the environment, never from this file.</b> A Gmail
 * app password used to sit in the source, which meant it was in the repository,
 * in every clone of it, and in the history of both (GAP-05). Anyone who wants to
 * run this now supplies their own:
 *
 * <pre>
 * EMAIL_USERNAME=you@example.com \
 * EMAIL_PASSWORD='xxxx xxxx xxxx xxxx' \
 * EMAIL_TARGET=you@example.com \
 *   ./mvnw test -DskipTests=false -Dtest=SendAllTestEmailsTest
 * </pre>
 *
 * (and takes the {@code @Disabled} off for that one run — {@code MailSafetyNetTest}
 * will fail until it is put back, which is the point of it.)
 */
@Disabled("Manual live SMTP test - enable when explicitly sending test emails")
class SendAllTestEmailsTest {

    /**
     * Reads one required setting, or stops with an explanation.
     *
     * <p>Refusing to run beats defaulting to somebody's address: the previous
     * defaults mailed a named person's inbox from their own account, so a
     * misfired run went out under their name.
     */
    private static String required(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("""
                    ต้องตั้งค่า %s ก่อนรันเทสนี้ — เทสนี้ล็อกอินและส่งอีเมลออกไปจริง
                    ตัวอย่าง: EMAIL_USERNAME=you@example.com EMAIL_PASSWORD='app password' \
                              EMAIL_TARGET=you@example.com ./mvnw test -Dtest=SendAllTestEmailsTest"""
                    .formatted(variable));
        }
        return value;
    }

    private final String targetEmail = required("EMAIL_TARGET");

    private JavaMailSenderImpl createMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(required("EMAIL_USERNAME"));
        sender.setPassword(required("EMAIL_PASSWORD"));

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        return sender;
    }

    private void send(JavaMailSenderImpl mailSender, String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailSender.getUsername(), EmailTemplateHelper.SENDER_NAME);
        helper.setTo(targetEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
        System.out.println("✅ Sent: " + subject);
        Thread.sleep(800); // polite delay between SMTP dispatches
    }

    @Test
    @DisplayName("ส่งอีเมลทดสอบทุกรูปแบบไปยังที่อยู่ที่ตั้งไว้ใน EMAIL_TARGET (ไม่มีไฟล์แนบ)")
    void sendAllEmailTemplates() throws Exception {
        JavaMailSenderImpl mailSender = createMailSender();
        System.out.println("🚀 กำลังส่งอีเมลทดสอบทุกรูปแบบ (Zero Attachment / Instant CDN) ไปยัง: " + targetEmail);

        // 1. Password Reset
        String resetUrl = "https://localhost:8081/reset-password?token=sample_test_token_123456";
        send(mailSender, "[ทดสอบ 1/9] รีเซ็ตรหัสผ่าน - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)",
                EmailTemplateHelper.buildPasswordResetEmail(resetUrl));

        // 2. First-time Login OTP
        send(mailSender, "[ทดสอบ 2/9] รหัส OTP สำหรับเข้าสู่ระบบครั้งแรก - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)",
                EmailTemplateHelper.buildOtpEmail("อ.ดร.เกรียงไกร พรหมมาตย์", "849201", "เข้าสู่ระบบครั้งแรก", 5));

        // 3. 2FA Login OTP
        send(mailSender, "[ทดสอบ 3/9] รหัส OTP สำหรับเข้าสู่ระบบ (2FA) - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น",
                EmailTemplateHelper.buildOtpEmail("อ.ดร.เกรียงไกร พรหมมาตย์", "620584", "เข้าสู่ระบบ 2-Factor Authentication", 5));

        // 4. Teaching Evaluation Status Update (Pass)
        send(mailSender, "[ทดสอบ 4/9] อัปเดตสถานะคำร้องขอประเมินผลการสอน (#105) - แจ้งผล - ผ่าน",
                EmailTemplateHelper.buildStatusChangeEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "คำร้องขอประเมินผลการสอน",
                        "105",
                        "อยู่ระหว่างการพิจารณาของคณะกรรมการ",
                        "แจ้งผล - ผ่านการประเมิน",
                        "#16a34a",
                        "<div style='background:#f0fdf4;border:1px solid #bbf7d0;border-radius:6px;padding:12px 16px;margin-bottom:16px;color:#166534;font-size:13px;'><strong>🎉 ยินดีด้วย!</strong> ผลการประเมินการสอนของท่านผ่านเกณฑ์มาตรฐานเรียบร้อยแล้ว ท่านสามารถนำผลการประเมินไปใช้ยื่นคำขอตำแหน่งทางวิชาการได้ภายใน 1 ปี</div>"));

        // 5. Subcommittee Suggestions / Revision Request
        send(mailSender, "[ทดสอบ 5/9] ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน (#106) - กรุณาแก้ไขเอกสาร",
                EmailTemplateHelper.buildSuggestionEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "106",
                        "1. กรุณาปรับปรุงแผนการสอนในบทที่ 4 ให้มีความชัดเจนด้านเกณฑ์การวัดผล (Rubric Score)\n2. เพิ่มเอกสารอ้างอิงและคู่มือปฏิบัติการในภาคผนวก ค ให้ครบถ้วน\n3. แนบไฟล์วิดีโอบันทึกการสอนเพิ่มเติม"));

        // 6. Position Request Status Update (College Approved)
        send(mailSender, "[ทดสอบ 6/9] อัปเดตสถานะคำร้องขอตำแหน่งทางวิชาการ (KKU-POS-2026-0042) - อนุมัติระดับวิทยาลัย",
                EmailTemplateHelper.buildStatusChangeEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "คำร้องขอกำหนดตำแหน่งทางวิชาการ (ผู้ช่วยศาสตราจารย์)",
                        "KKU-POS-2026-0042",
                        "คณะกรรมการกลั่นกรองเห็นชอบ",
                        "คณะกรรมการประจำวิทยาลัยอนุมัติ",
                        "#16a34a",
                        "<div style='background:#f0fdf4;border:1px solid #bbf7d0;border-radius:6px;padding:12px 16px;margin-bottom:16px;color:#166534;font-size:13px;'>คำร้องของท่านผ่านการพิจารณาเห็นชอบจากคณะกรรมการประจำวิทยาลัยการคอมพิวเตอร์แล้ว และจะจัดส่งเอกสารไปยังกองทรัพยากรบุคคล มหาวิทยาลัยขอนแก่น ต่อไป</div>"));

        // 7. Admin Alert: New Position Request
        send(mailSender, "[ทดสอบ 7/9] แจ้งเตือนคำร้องขอตำแหน่งทางวิชาการใหม่ (KKU-POS-2026-0043) - อ.ดร.สมชาย ใจดี",
                EmailTemplateHelper.buildAdminNewRequestEmail(
                        "ผู้ดูแลระบบ (Admin)",
                        "อ.ดร.สมชาย ใจดี",
                        "somchai.j@kku.ac.th",
                        "คำร้องขอกำหนดตำแหน่งทางวิชาการ (รองศาสตราจารย์)",
                        "KKU-POS-2026-0043",
                        "รองศาสตราจารย์ (สาขาวิชาวิทยาการคอมพิวเตอร์)"));

        // 8. Evaluation Expiry Countdown Reminder (30 days left)
        send(mailSender, "[ทดสอบ 8/9] แจ้งเตือน: ผลประเมินการสอน (KKU-ACAD-2025-0012) จะหมดอายุภายใน 1 เดือน - HRCP.KKU",
                EmailTemplateHelper.buildEvaluationExpiryEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "KKU-ACAD-2025-0012",
                        "1 เดือน",
                        30,
                        "18 กันยายน 2569"));

        // 9. System Alert Email (Admin Alert)
        send(mailSender, "[ทดสอบ 9/9] [HRCP.KKU] สำเร็จ การดึงข้อมูลบุคลากรจากเว็บไซต์คณะ",
                EmailTemplateHelper.buildSystemAlertEmail(
                        "ผู้ดูแลระบบ (Admin)",
                        "การซิงค์ข้อมูลบุคลากรสำเร็จ",
                        "#16a34a",
                        "✅",
                        "CpDirectorySyncService",
                        "อ่านข้อมูลจากเว็บคณะสำเร็จ 76 คน — อัปเดตข้อมูลครบถ้วน\nใช้เวลาในการประมวลผล: 1.42 วินาที\nไม่มีข้อผิดพลาด",
                        "18 ส.ค. 2026 21:00:00"));

        System.out.println("🎉 ส่งอีเมลทดสอบครบทั้ง 9 รูปแบบเรียบร้อยแล้ว (ไม่มีไฟล์แนบ โหลดทันที)!");
    }
}
