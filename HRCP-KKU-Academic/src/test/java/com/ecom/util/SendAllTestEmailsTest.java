package com.ecom.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.internet.MimeMessage;

@SpringBootTest
@Disabled("Manual live SMTP test - enable when explicitly sending test emails")
class SendAllTestEmailsTest {

    private static final String TARGET_EMAIL = "kriangkrai.p@kkumail.com";

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:kriangkrai.p@kkumail.com}")
    private String senderEmail;

    private void send(String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(senderEmail, EmailTemplateHelper.SENDER_NAME);
        helper.setTo(TARGET_EMAIL);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        EmailTemplateHelper.attachLogos(helper);
        mailSender.send(message);
        System.out.println("✅ Sent: " + subject);
        Thread.sleep(800); // polite delay between SMTP dispatches
    }

    @Test
    @DisplayName("ส่งอีเมลทดสอบทุกรูปแบบไปยัง kriangkrai.p@kkumail.com")
    void sendAllEmailTemplates() throws Exception {
        System.out.println("🚀 กำลังส่งอีเมลทดสอบทุกรูปแบบไปยัง: " + TARGET_EMAIL);

        // 1. Password Reset
        String resetUrl = "https://localhost:8081/reset-password?token=sample_test_token_123456";
        send("[ทดสอบ 1/9] รีเซ็ตรหัสผ่าน - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)",
                EmailTemplateHelper.buildPasswordResetEmail(resetUrl));

        // 2. First-time Login OTP
        send("[ทดสอบ 2/9] รหัส OTP สำหรับเข้าสู่ระบบครั้งแรก - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)",
                EmailTemplateHelper.buildOtpEmail("อ.ดร.เกรียงไกร พรหมมาตย์", "849201", "เข้าสู่ระบบครั้งแรก", 5));

        // 3. 2FA Login OTP
        send("[ทดสอบ 3/9] รหัส OTP สำหรับเข้าสู่ระบบ (2FA) - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น",
                EmailTemplateHelper.buildOtpEmail("อ.ดร.เกรียงไกร พรหมมาตย์", "59283417", "เข้าสู่ระบบ 2-Factor Authentication", 5));

        // 4. Teaching Evaluation Status Update (Passed)
        String extraMeeting = "<div style='background:#f1f5f9;border:1px solid #cbd5e1;padding:12px 16px;border-radius:6px;margin-bottom:16px;font-size:13px;'>"
                + "<div style='font-weight:600;color:#1e293b;margin-bottom:4px;'>📅 รายละเอียดการประชุมประเมิน:</div>"
                + "<div>วันประชุม: <strong>25 สิงหาคม 2569 เวลา 09:30 น.</strong></div>"
                + "<div>สถานที่: <strong>ห้องประชุมวิทยวิภาส 1 ชั้น 2 วิทยาลัยการคอมพิวเตอร์</strong></div>"
                + "</div>";
        send("[ทดสอบ 4/9] อัปเดตสถานะคำร้องขอประเมินผลการสอน (#105) - แจ้งผล - ผ่าน",
                EmailTemplateHelper.buildStatusChangeEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "คำร้องขอประเมินผลการสอน",
                        "105",
                        "นัดหมายวันประชุม",
                        "แจ้งผล - ผ่าน",
                        "#16a34a",
                        extraMeeting));

        // 5. Subcommittee Suggestion / Revision Request
        String suggestions = "1. กรุณาปรับปรุงแผนการสอน (Course Syllabus) ในสัปดาห์ที่ 7 ให้ระบุวิธีการประเมินแบบ Formative Assessment ให้ชัดเจนยิ่งขึ้น\n"
                + "2. เพิ่มเติมเอกสารประกอบการสอนบทที่ 4 ในส่วนของตัวอย่างกรณีศึกษาทางวิทยาการข้อมูล\n"
                + "3. ตรวจสอบลายมือชื่อของผู้ร่วมสอนในแบบฟอร์ม ก.พ.อ. 02 ให้ครบถ้วนทุกจุด";
        send("[ทดสอบ 5/9] ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน (#106) - กรุณาแก้ไขเอกสาร",
                EmailTemplateHelper.buildSuggestionEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "106",
                        suggestions));

        // 6. Academic Position Status Update (College Approved)
        String positionDetails = "<div style='background:#f1f5f9;border:1px solid #cbd5e1;padding:12px 16px;border-radius:6px;margin-bottom:16px;font-size:13px;'>"
                + "ตำแหน่งทางวิชาการที่ยื่นขอ: <strong style='color:#1e3a8a;'>ผู้ช่วยศาสตราจารย์ (สาขาวิชาวิทยาการคอมพิวเตอร์)</strong>"
                + "</div>";
        send("[ทดสอบ 6/9] อัปเดตสถานะคำร้องขอตำแหน่งทางวิชาการ (KKU-POS-2026-0042) - อนุมัติระดับวิทยาลัย",
                EmailTemplateHelper.buildStatusChangeEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "คำร้องขอตำแหน่งทางวิชาการ",
                        "KKU-POS-2026-0042",
                        "อยู่ระหว่างการพิจารณา",
                        "อนุมัติระดับวิทยาลัย (ส่งต่อ ก.บ.ม.)",
                        "#16a34a",
                        positionDetails));

        // 7. Admin Alert: New Position Request Submitted
        send("[ทดสอบ 7/9] แจ้งเตือนคำร้องขอตำแหน่งทางวิชาการใหม่ (KKU-POS-2026-0043) - อ.ดร.สมชาย ใจดี",
                EmailTemplateHelper.buildAdminNewRequestEmail(
                        "ผู้ดูแลระบบ (Admin)",
                        "อ.ดร.สมชาย ใจดี",
                        "somchai@kku.ac.th",
                        "คำร้องขอตำแหน่งทางวิชาการ",
                        "KKU-POS-2026-0043",
                        "รองศาสตราจารย์ (สาขาวิชาเทคโนโลยีสารสนเทศ)"));

        // 8. Teaching Evaluation Expiry Warning
        send("[ทดสอบ 8/9] แจ้งเตือน: ผลประเมินการสอน (KKU-ACAD-2025-0012) จะหมดอายุภายใน 1 เดือน - HRCP.KKU",
                EmailTemplateHelper.buildEvaluationExpiryEmail(
                        "อ.ดร.เกรียงไกร พรหมมาตย์",
                        "KKU-ACAD-2025-0012",
                        "1 เดือน",
                        30,
                        "18 กันยายน 2569"));

        // 9. System Alert / Sync Report for Admins
        String systemDetail = "ดึงข้อมูลอาจารย์และบุคลากรจากเว็บไซต์วิทยาลัย (computing.kku.ac.th/people) สำเร็จ:\n"
                + "- อ่านข้อมูลทั้งหมด: 76 รายการ\n"
                + "- อัปเดตรูปภาพใหม่: 0 รายการ (Reuse จากดิสก์แคช 100%)\n"
                + "- เติมข้อมูลที่ว่าง: 44 รายการ\n"
                + "- ใช้เวลาในการประมวลผล: 25 ms";
        send("[ทดสอบ 9/9] [HRCP.KKU] สำเร็จ การดึงข้อมูลบุคลากรจากเว็บไซต์คณะ",
                EmailTemplateHelper.buildSystemAlertEmail(
                        "ผู้ดูแลระบบ (Admin)",
                        "การดึงข้อมูลบุคลากรจากเว็บไซต์คณะสำเร็จ",
                        "#16a34a",
                        "✅",
                        "College Directory Sync Job",
                        systemDetail,
                        "18/08/2569 20:55 น."));

        System.out.println("🎉 ส่งอีเมลทดสอบครบทั้ง 9 รูปแบบเรียบร้อยแล้ว!");
    }
}
