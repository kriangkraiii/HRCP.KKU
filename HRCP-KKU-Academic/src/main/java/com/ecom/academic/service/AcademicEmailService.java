package com.ecom.academic.service;

import java.util.List;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class AcademicEmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final com.ecom.service.NotificationService notificationService;
    private final AcademicRequestRepository requestRepository;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@kku.ac.th}")
    private String senderEmail;

    public AcademicEmailService(JavaMailSender mailSender, UserRepository userRepository,
            com.ecom.service.NotificationService notificationService,
            AcademicRequestRepository requestRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.requestRepository = requestRepository;
    }

    /**
     * Re-reads the request on this thread.
     *
     * <p>These methods run on a background thread. The entity the caller was
     * holding belongs to the caller's Hibernate session, which is very likely
     * still open — touching its lazy associations from here would put two
     * threads on one session. So the caller passes an id and nothing else, and
     * we load our own detached copy with the applicant already attached.
     */
    private AcademicRequest reload(Long requestId) {
        if (requestId == null) {
            return null;
        }
        return requestRepository.findByIdWithApplicant(requestId).orElse(null);
    }

    @Async
    public void sendStatusChangeEmail(Long requestId, RequestStatus oldStatus, RequestStatus newStatus) {
        sendStatusChangeEmail(requestId, oldStatus, newStatus, null);
    }

    /**
     * @param note หมายเหตุที่แอดมินใส่ตอนเปลี่ยนสถานะ — ใช้เป็นเหตุผลเมื่อคำร้องถูกส่งคืนให้แก้ไข (Flow ข้อ 2)
     */
    @Async
    public void sendStatusChangeEmail(Long requestId, RequestStatus oldStatus, RequestStatus newStatus, String note) {
        try {
            AcademicRequest request = reload(requestId);
            if (request == null) {
                return;
            }
            // 1. Create in-app notification for applicant
            if (request != null && request.getApplicant() != null) {
                boolean isImportant = isReturn(oldStatus, newStatus)
                        || newStatus == RequestStatus.COMPLETED_REVISE
                        || newStatus == RequestStatus.COMPLETED_PASS
                        || newStatus == RequestStatus.COMPLETED_FAIL;
                String notifTitle = isReturn(oldStatus, newStatus)
                        ? "คำร้องขอประเมินผลการสอนถูกส่งคืนให้แก้ไข"
                        : "อัปเดตสถานะการประเมิน: " + newStatus.getThaiLabel();
                String notifMsg = isReturn(oldStatus, newStatus)
                        ? "คำร้องขอประเมินผลการสอน (#" + request.getId() + ") ของท่านถูกส่งคืนให้แก้ไข เหตุผล: "
                                + (note != null ? note : "-")
                        : "คำร้องขอประเมินผลการสอน (#" + request.getId() + ") ของท่าน ได้รับการปรับสถานะเป็น " + newStatus.getThaiLabel();
                // คำร้องที่ถูกส่งคืนเป็นแบบร่างแล้ว หน้ารายละเอียดจะพาไปที่หน้าแก้แบบร่างอยู่ดี
                String notifLink = isReturn(oldStatus, newStatus)
                        ? "/user/academic/new-request"
                        : "/user/academic/request/" + request.getId();
                notificationService.sendNotification(request.getApplicant(), null, notifTitle, notifMsg, notifLink, com.ecom.model.NotificationType.ACADEMIC_STATUS_UPDATE, isImportant);
            }

            // 2. Send email notification
            if (request.getApplicant() == null || Boolean.FALSE.equals(request.getApplicant().getIsEnable())) {
                return;
            }
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty() || com.ecom.util.EmailTemplateHelper.isTestEmail(applicantEmail))
                return;

            String subject = isReturn(oldStatus, newStatus)
                    ? "คำร้องขอประเมินผลการสอน (#" + request.getId() + ") ถูกส่งคืนให้แก้ไข"
                    : "อัปเดตสถานะคำร้องขอประเมินผลการสอน (#" + request.getId() + ") - " + newStatus.getThaiLabel();
            String body = buildEmailBody(request, oldStatus, newStatus, note);

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
    public void sendNewRequestNotificationToAdmins(Long requestId) {
        try {
            AcademicRequest request = reload(requestId);
            if (request == null) {
                return;
            }
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

    /** Flow ข้อ 2 — คณบดีไม่เห็นชอบ คำร้องถูกส่งคืนเป็นแบบร่าง */
    private static boolean isReturn(RequestStatus oldStatus, RequestStatus newStatus) {
        return oldStatus == RequestStatus.RECEIVED && newStatus == RequestStatus.DRAFT;
    }

    private String buildEmailBody(AcademicRequest request, RequestStatus oldStatus, RequestStatus newStatus,
            String note) {
        String statusColor = switch (newStatus) {
            case COMPLETED_PASS, COMPLETED -> "#16a34a";
            case COMPLETED_REVISE, DRAFT -> "#d97706";
            case COMPLETED_FAIL -> "#dc2626";
            case RECEIVED -> "#2563eb";
            default -> newStatus.getColor() != null ? newStatus.getColor() : "#1e3a8a";
        };

        String extraDetails = "";
        if (newStatus == RequestStatus.MEETING_SCHEDULED && request.getMeetingDate() != null) {
            extraDetails = "<div style='background:#f1f5f9;border:1px solid #cbd5e1;padding:12px 16px;border-radius:6px;margin-bottom:16px;font-size:13px;'>"
                    + "<div style='font-weight:600;color:#1e293b;margin-bottom:4px;'>รายละเอียดการนัดหมาย:</div>"
                    + "<div>วันประชุม: <strong>" + request.getMeetingDate() + "</strong></div>"
                    + (request.getMeetingLocation() != null ? "<div>สถานที่: " + request.getMeetingLocation() + "</div>" : "")
                    + "</div>";
        }
        if (isReturn(oldStatus, newStatus)) {
            extraDetails = "<div style='background:#fffbeb;border:1px solid #fcd34d;padding:12px 16px;border-radius:6px;margin-bottom:16px;font-size:13px;'>"
                    + "<div style='font-weight:600;color:#92400e;margin-bottom:4px;'>เหตุผลที่ส่งคืน:</div>"
                    + "<div>" + org.springframework.web.util.HtmlUtils.htmlEscape(note != null ? note : "-") + "</div>"
                    + "<div style='margin-top:8px;'>กรุณาแก้ไขเอกสาร ลงนามอิเล็กทรอนิกส์ใหม่ แล้วยื่นคำร้องอีกครั้ง</div>"
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
    public void sendSuggestionEmail(Long requestId, String suggestionsText) {
        try {
            AcademicRequest request = reload(requestId);
            if (request == null || request.getApplicant() == null) {
                return;
            }
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
