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
    private final com.ecom.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@kku.ac.th}")
    private String senderEmail;

    public AcademicEmailService(JavaMailSender mailSender, UserRepository userRepository, com.ecom.service.NotificationService notificationService) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Async
    public void sendStatusChangeEmail(AcademicRequest request, RequestStatus oldStatus, RequestStatus newStatus) {
        try {
            // 1. Create in-app notification for applicant
            if (request != null && request.getApplicant() != null) {
                boolean isImportant = newStatus == RequestStatus.COMPLETED_REVISE
                        || newStatus == RequestStatus.COMPLETED_PASS
                        || newStatus == RequestStatus.COMPLETED_FAIL
                        || newStatus == RequestStatus.REJECTED;
                String notifTitle = "อัปเดตสถานะการประเมิน: " + newStatus.getThaiLabel();
                String notifMsg = "คำร้องขอประเมินผลการสอน (#" + request.getId() + ") ของท่าน ได้รับการปรับสถานะเป็น " + newStatus.getThaiLabel();
                String notifLink = "/user/academic/request/" + request.getId();
                notificationService.sendNotification(request.getApplicant(), null, notifTitle, notifMsg, notifLink, com.ecom.model.NotificationType.ACADEMIC_STATUS_UPDATE, isImportant);
            }

            // 2. Send email notification
            if (request.getApplicant() == null || Boolean.FALSE.equals(request.getApplicant().getIsEnable())) {
                return;
            }
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty() || com.ecom.util.EmailTemplateHelper.isTestEmail(applicantEmail))
                return;

            String subject = "อัปเดตสถานะคำร้องขอประเมินผลการสอน (#" + request.getId() + ") - " + newStatus.getThaiLabel();
            String body = buildEmailBody(request, oldStatus, newStatus);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Email sending failed: " + e.getMessage());
        }
    }

    /**
     * ส่งการแจ้งเตือนและอีเมลแจ้งแอดมินทุกคนเมื่อมีคำร้องใหม่
     */
    @Async
    public void sendNewRequestNotificationToAdmins(AcademicRequest request) {
        try {
            // 1. Create in-app notification for all admins
            if (request != null && request.getApplicant() != null) {
                String notifTitle = "คำร้องขอรับการประเมินใหม่";
                String notifMsg = "มีคำร้องขอประเมินผลการสอนใหม่ (#" + request.getId() + ") ยื่นโดย " + request.getApplicant().getName();
                String notifLink = "/admin/academic/request/" + request.getId();
                notificationService.notifyAdmins(request.getApplicant(), notifTitle, notifMsg, notifLink, com.ecom.model.NotificationType.ACADEMIC_NEW_REQUEST, false);
            }

            // 2. Send email to admins who opted-in
            List<UserDtls> admins = userRepository.findByRole("ROLE_ADMIN");
            for (UserDtls admin : admins) {
                if (Boolean.FALSE.equals(admin.getIsEnable())) {
                    continue;
                }
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
            if (admin == null || com.ecom.util.EmailTemplateHelper.isTestEmail(admin.getEmail())) {
                return;
            }
            String subject = "แจ้งเตือนคำร้องขอรับการประเมินใหม่ (#" + request.getId() + ") - " + request.getApplicant().getName();
            String body = com.ecom.util.EmailTemplateHelper.buildAdminNewRequestEmail(
                    admin.getName(),
                    request.getApplicant().getName(),
                    request.getApplicant().getEmail(),
                    "คำร้องขอรับการประเมินผลการสอน",
                    String.valueOf(request.getId()),
                    null);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(admin.getEmail());
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send admin notification to " + admin.getEmail() + ": " + e.getMessage());
        }
    }

    private String buildEmailBody(AcademicRequest request, RequestStatus oldStatus, RequestStatus newStatus) {
        String statusColor = switch (newStatus) {
            case COMPLETED_PASS, COMPLETED -> "#16a34a";
            case COMPLETED_REVISE -> "#d97706";
            case COMPLETED_FAIL, REJECTED -> "#dc2626";
            case RECEIVED -> "#2563eb";
            default -> newStatus.getColor() != null ? newStatus.getColor() : "#1e3a8a";
        };

        String extraDetails = "";
        if (newStatus == RequestStatus.MEETING_SCHEDULED && request.getMeetingDate() != null) {
            extraDetails = "<div style='background:#f1f5f9;border:1px solid #cbd5e1;padding:12px 16px;border-radius:6px;margin-bottom:16px;font-size:13px;'>"
                    + "<div style='font-weight:600;color:#1e293b;margin-bottom:4px;'>📅 รายละเอียดการนัดหมาย:</div>"
                    + "<div>วันประชุม: <strong>" + request.getMeetingDate() + "</strong></div>"
                    + (request.getMeetingLocation() != null ? "<div>สถานที่: " + request.getMeetingLocation() + "</div>" : "")
                    + "</div>";
        }

        return com.ecom.util.EmailTemplateHelper.buildStatusChangeEmail(
                request.getApplicant().getName(),
                "คำร้องขอประเมินผลการสอน",
                String.valueOf(request.getId()),
                oldStatus != null ? oldStatus.getThaiLabel() : "-",
                newStatus.getThaiLabel(),
                statusColor,
                extraDetails);
    }

    /**
     * ส่งอีเมลข้อเสนอแนะจากคณะอนุกรรมการถึงผู้ยื่นคำร้อง
     */
    @Async
    public void sendSuggestionEmail(AcademicRequest request, String suggestionsText) {
        try {
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty() || com.ecom.util.EmailTemplateHelper.isTestEmail(applicantEmail))
                return;

            String subject = "ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน (#" + request.getId() + ") - กรุณาแก้ไขเอกสาร";
            String body = com.ecom.util.EmailTemplateHelper.buildSuggestionEmail(
                    request.getApplicant().getName(),
                    String.valueOf(request.getId()),
                    suggestionsText);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Suggestion email sending failed: " + e.getMessage());
        }
    }
}
