package com.ecom.academic.service;

import java.util.List;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class AcademicEmailService {

    private final JavaMailSender mailSender;

    private final UserRepository userRepository;

    public AcademicEmailService(JavaMailSender mailSender, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    @Async
    public void sendStatusChangeEmail(AcademicRequest request, RequestStatus oldStatus, RequestStatus newStatus) {
        try {
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty())
                return;

            String subject = "อัปเดตสถานะคำร้องขอตำแหน่งทางวิชาการ - " + newStatus.getThaiLabel();
            String body = buildEmailBody(request, oldStatus, newStatus);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Email sending failed: " + e.getMessage());
        }
    }

    /**
     * ส่งอีเมลแจ้งเตือนแอดมินทุกคนที่เปิดการแจ้งเตือนไว้ เมื่อมีคำร้องใหม่
     */
    @Async
    public void sendNewRequestNotificationToAdmins(AcademicRequest request) {
        try {
            List<UserDtls> admins = userRepository.findByRole("ROLE_ADMIN");
            for (UserDtls admin : admins) {
                // ตรวจสอบว่าแอดมินเปิดการแจ้งเตือนหรือไม่ (default = false/ปิด)
                if (admin.getEmailNotificationEnabled() != null && admin.getEmailNotificationEnabled()) {
                    sendAdminNotification(admin, request);
                }
            }
        } catch (Exception e) {
            System.err.println("Admin notification failed: " + e.getMessage());
        }
    }

    private void sendAdminNotification(UserDtls admin, AcademicRequest request) {
        try {
            String subject = "แจ้งเตือน: มีคำร้องใหม่จาก " + request.getApplicant().getName();
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body style='font-family: Sarabun, sans-serif;'>");
            sb.append("<h2 style='color:#1a237e;'>🔔 แจ้งเตือนคำร้องใหม่</h2>");
            sb.append("<p>เรียน ").append(admin.getName()).append("</p>");
            sb.append("<p>มีคำร้องใหม่เข้ามาในระบบ:</p>");
            sb.append("<table style='border-collapse:collapse;'>");
            sb.append("<tr><td style='padding:5px 15px;font-weight:bold;'>ผู้ยื่น:</td><td>")
                    .append(request.getApplicant().getName()).append("</td></tr>");
            sb.append("<tr><td style='padding:5px 15px;font-weight:bold;'>อีเมล:</td><td>")
                    .append(request.getApplicant().getEmail()).append("</td></tr>");
            sb.append("<tr><td style='padding:5px 15px;font-weight:bold;'>คำร้องหมายเลข:</td><td>#")
                    .append(request.getId()).append("</td></tr>");
            sb.append("</table>");
            sb.append("<hr>");
            sb.append("<p>กรุณาเข้าสู่ระบบเพื่อจัดการคำร้อง</p>");
            sb.append("<p style='color:#888;font-size:0.85em;'>หากต้องการปิดการแจ้งเตือน ");
            sb.append("สามารถตั้งค่าได้ที่โปรไฟล์ของท่าน</p>");
            sb.append("</body></html>");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(admin.getEmail());
            helper.setSubject(subject);
            helper.setText(sb.toString(), true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send admin notification to " + admin.getEmail() + ": " + e.getMessage());
        }
    }

    private String buildEmailBody(AcademicRequest request, RequestStatus oldStatus, RequestStatus newStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: Sarabun, sans-serif;'>");
        sb.append("<h2>แจ้งเตือนอัปเดตสถานะคำร้อง</h2>");
        sb.append("<p>เรียน ").append(request.getApplicant().getName()).append("</p>");
        sb.append("<p>คำร้องหมายเลข: <strong>#").append(request.getId()).append("</strong></p>");
        sb.append("<p>สถานะเปลี่ยนจาก: <strong>").append(oldStatus != null ? oldStatus.getThaiLabel() : "-")
                .append("</strong></p>");
        sb.append("<p>สถานะใหม่: <strong style='color: ");

        switch (newStatus) {
            case COMPLETED_PASS -> sb.append("green");
            case COMPLETED_REVISE -> sb.append("#FFA500");
            case COMPLETED_FAIL -> sb.append("red");
            case REJECTED -> sb.append("red");
            default -> sb.append("#333");
        }

        sb.append(";'>").append(newStatus.getThaiLabel()).append("</strong></p>");

        if (newStatus == RequestStatus.MEETING_SCHEDULED && request.getMeetingDate() != null) {
            sb.append("<p>วันประชุม: <strong>").append(request.getMeetingDate()).append("</strong></p>");
            if (request.getMeetingLocation() != null) {
                sb.append("<p>สถานที่: ").append(request.getMeetingLocation()).append("</p>");
            }
        }

        sb.append("<hr>");
        sb.append("<p>กรุณาเข้าสู่ระบบเพื่อตรวจสอบรายละเอียดเพิ่มเติม</p>");
        sb.append("<p>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * ส่งอีเมลข้อเสนอแนะจากคณะอนุกรรมการถึงผู้ยื่นคำร้อง
     */
    @Async
    public void sendSuggestionEmail(AcademicRequest request, String suggestionsText) {
        try {
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty())
                return;

            String subject = "ข้อเสนอแนะจากคณะอนุกรรมการ - กรุณาแก้ไขเอกสาร";
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body style='font-family: Sarabun, sans-serif;'>");
            sb.append("<h2 style='color:#e65100;'>ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน</h2>");
            sb.append("<p>เรียน ").append(request.getApplicant().getName()).append("</p>");
            sb.append("<p>คำร้องหมายเลข: <strong>#").append(request.getId()).append("</strong></p>");
            sb.append("<hr>");
            sb.append("<h3 style='color:#1a237e;'>ข้อเสนอแนะ:</h3>");
            sb.append("<div style='background:#fff3e0;padding:15px;border-radius:8px;border-left:4px solid #e65100;'>");
            sb.append("<p style='white-space:pre-wrap;'>").append(suggestionsText != null ? suggestionsText.replace("<", "&lt;").replace(">", "&gt;") : "").append("</p>");
            sb.append("</div>");
            sb.append("<hr>");
            sb.append("<p><strong style='color:#c62828;'>กรุณาดำเนินการแก้ไขเอกสารตามข้อเสนอแนะข้างต้น</strong></p>");
            sb.append("<p>กรุณาเข้าสู่ระบบเพื่อดำเนินการแก้ไข</p>");
            sb.append("<p>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น</p>");
            sb.append("</body></html>");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(sb.toString(), true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Suggestion email sending failed: " + e.getMessage());
        }
    }
}
