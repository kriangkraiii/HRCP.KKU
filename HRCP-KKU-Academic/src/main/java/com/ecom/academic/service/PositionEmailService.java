package com.ecom.academic.service;

import java.util.List;

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

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final com.ecom.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@kku.ac.th}")
    private String senderEmail;

    public PositionEmailService(JavaMailSender mailSender, UserRepository userRepository, com.ecom.service.NotificationService notificationService) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Async
    public void sendStatusChangeEmail(PositionRequest request,
            PositionRequestStatus oldStatus, PositionRequestStatus newStatus) {
        try {
            // 1. Create in-app notification for applicant
            if (request != null && request.getApplicant() != null) {
                boolean isImportant = newStatus == PositionRequestStatus.REVISION_REQUESTED
                        || newStatus == PositionRequestStatus.REJECTED
                        || newStatus == PositionRequestStatus.SCREENING_APPROVED
                        || newStatus == PositionRequestStatus.COLLEGE_APPROVED
                        || newStatus == PositionRequestStatus.SENT_TO_HR;
                String notifTitle = "อัปเดตสถานะขอตำแหน่ง: " + newStatus.getThaiLabel();
                String notifMsg = "คำร้องขอตำแหน่งทางวิชาการ (" + request.getRequestCode() + ") ของท่าน ได้รับการปรับสถานะเป็น " + newStatus.getThaiLabel();
                String notifLink = "/user/position/request/" + request.getId();
                notificationService.sendNotification(request.getApplicant(), null, notifTitle, notifMsg, notifLink, com.ecom.model.NotificationType.POSITION_STATUS_UPDATE, isImportant);
            }

            // 2. Send email notification
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty())
                return;

            String subject = "อัปเดตสถานะคำร้องขอตำแหน่งทางวิชาการ (" + request.getRequestCode() + ") - " + newStatus.getThaiLabel();
            String body = buildStatusEmailBody(request, oldStatus, newStatus);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Position email sending failed: " + e.getMessage());
        }
    }

    @Async
    public void sendNewRequestNotificationToAdmins(PositionRequest request) {
        try {
            // 1. Create in-app notification for all admins
            if (request != null && request.getApplicant() != null) {
                String notifTitle = "คำร้องขอตำแหน่งทางวิชาการใหม่";
                String notifMsg = "มีคำร้องขอตำแหน่งใหม่ (" + request.getRequestCode() + ") ยื่นโดย " + request.getApplicant().getName();
                String notifLink = "/admin/position/request/" + request.getId();
                notificationService.notifyAdmins(request.getApplicant(), notifTitle, notifMsg, notifLink, com.ecom.model.NotificationType.POSITION_NEW_REQUEST, false);
            }

            // 2. Send email to admins who opted-in
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
            String subject = "แจ้งเตือนคำร้องขอตำแหน่งทางวิชาการใหม่ (" + request.getRequestCode() + ") - " + request.getApplicant().getName();
            String body = com.ecom.util.EmailTemplateHelper.buildAdminNewRequestEmail(
                    admin.getName(),
                    request.getApplicant().getName(),
                    request.getApplicant().getEmail(),
                    "คำร้องขอตำแหน่งทางวิชาการ",
                    request.getRequestCode(),
                    request.getTargetPosition());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(admin.getEmail());
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send position admin notification to " + admin.getEmail()
                    + ": " + e.getMessage());
        }
    }

    private String buildStatusEmailBody(PositionRequest request,
            PositionRequestStatus oldStatus, PositionRequestStatus newStatus) {
        String statusColor = switch (newStatus) {
            case SCREENING_APPROVED, COLLEGE_APPROVED -> "#16a34a";
            case REVISION_REQUESTED -> "#d97706";
            case SENT_TO_HR -> "#0d9488";
            case REJECTED -> "#dc2626";
            case DRAFT -> "#64748b";
            default -> newStatus.getColor() != null ? newStatus.getColor() : "#1e3a8a";
        };

        String extraDetails = "";
        if (request.getTargetPosition() != null && !request.getTargetPosition().isBlank()) {
            extraDetails = "<div style='background:#f1f5f9;border:1px solid #cbd5e1;padding:12px 16px;border-radius:6px;margin-bottom:16px;font-size:13px;'>"
                    + "ตำแหน่งทางวิชาการที่ยื่นขอ: <strong style='color:#1e293b;'>" + request.getTargetPosition() + "</strong>"
                    + "</div>";
        }

        return com.ecom.util.EmailTemplateHelper.buildStatusChangeEmail(
                request.getApplicant().getName(),
                "คำร้องขอตำแหน่งทางวิชาการ",
                request.getRequestCode(),
                oldStatus != null ? oldStatus.getThaiLabel() : "-",
                newStatus.getThaiLabel(),
                statusColor,
                extraDetails);
    }
}
