package com.ecom.academic.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;

import jakarta.mail.internet.MimeMessage;

@Service
public class AcademicEmailService {

    @Autowired
    private JavaMailSender mailSender;

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
}
