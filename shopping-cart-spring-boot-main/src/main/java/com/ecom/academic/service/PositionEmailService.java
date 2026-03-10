package com.ecom.academic.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class PositionEmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @Async
    public void sendStatusChangeEmail(PositionRequest request,
            PositionRequestStatus oldStatus, PositionRequestStatus newStatus) {
        try {
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty())
                return;

            String subject = "อัปเดตสถานะคำร้องขอตำแหน่งทางวิชาการ - " + newStatus.getThaiLabel();
            String body = buildStatusEmailBody(request, oldStatus, newStatus);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Position email sending failed: " + e.getMessage());
        }
    }

    @Async
    public void sendNewRequestNotificationToAdmins(PositionRequest request) {
        try {
            List<UserDtls> admins = userRepository.findByRole("ROLE_ADMIN");
            for (UserDtls admin : admins) {
                if (admin.getEmailNotificationEnabled() != null && admin.getEmailNotificationEnabled()) {
                    sendAdminNotification(admin, request);
                }
            }
        } catch (Exception e) {
            System.err.println("Position admin notification failed: " + e.getMessage());
        }
    }

    private void sendAdminNotification(UserDtls admin, PositionRequest request) {
        try {
            String subject = "แจ้งเตือน: คำร้องขอตำแหน่งใหม่จาก " + request.getApplicant().getName();
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body style='font-family: Sarabun, sans-serif;'>");
            sb.append("<h2 style='color:#1a237e;'>🔔 คำร้องขอตำแหน่งทางวิชาการใหม่</h2>");
            sb.append("<p>เรียน ").append(admin.getName()).append("</p>");
            sb.append("<p>มีคำร้องขอตำแหน่งทางวิชาการใหม่เข้ามาในระบบ:</p>");
            sb.append("<table style='border-collapse:collapse;'>");
            sb.append("<tr><td style='padding:5px 15px;font-weight:bold;'>ผู้ยื่น:</td><td>")
                    .append(request.getApplicant().getName()).append("</td></tr>");
            sb.append("<tr><td style='padding:5px 15px;font-weight:bold;'>รหัสคำร้อง:</td><td>")
                    .append(request.getRequestCode()).append("</td></tr>");
            if (request.getTargetPosition() != null) {
                sb.append("<tr><td style='padding:5px 15px;font-weight:bold;'>ตำแหน่งที่ขอ:</td><td>")
                        .append(request.getTargetPosition()).append("</td></tr>");
            }
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
            System.err.println("Failed to send position admin notification to " + admin.getEmail()
                    + ": " + e.getMessage());
        }
    }

    private String buildStatusEmailBody(PositionRequest request,
            PositionRequestStatus oldStatus, PositionRequestStatus newStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: Sarabun, sans-serif;'>");
        sb.append("<h2>แจ้งเตือนอัปเดตสถานะคำร้องขอตำแหน่งทางวิชาการ</h2>");
        sb.append("<p>เรียน ").append(request.getApplicant().getName()).append("</p>");
        sb.append("<p>คำร้อง: <strong>").append(request.getRequestCode()).append("</strong></p>");
        sb.append("<p>สถานะเปลี่ยนจาก: <strong>")
                .append(oldStatus != null ? oldStatus.getThaiLabel() : "-").append("</strong></p>");
        sb.append("<p>สถานะใหม่: <strong style='color: ");

        switch (newStatus) {
            case APPROVED -> sb.append("green");
            case REJECTED -> sb.append("red");
            case COMPLETED -> sb.append("#1b5e20");
            default -> sb.append("#333");
        }

        sb.append(";'>").append(newStatus.getThaiLabel()).append("</strong></p>");

        if (request.getTargetPosition() != null) {
            sb.append("<p>ตำแหน่งที่ขอ: <strong>").append(request.getTargetPosition()).append("</strong></p>");
        }

        sb.append("<hr>");
        sb.append("<p>กรุณาเข้าสู่ระบบเพื่อตรวจสอบรายละเอียดเพิ่มเติม</p>");
        sb.append("<p>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น</p>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
