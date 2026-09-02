package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.model.UserDtls;

import jakarta.mail.internet.MimeMessage;

/**
 * Scheduled service: checks daily for upcoming evaluation expiry
 * and sends email notifications based on user preferences.
 */
@Service
public class EvaluationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvaluationExpiryScheduler.class);

    private final AcademicRequestRepository requestRepository;
    private final AcademicRequestService academicService;
    private final JavaMailSender mailSender;
    private final com.ecom.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@kku.ac.th}")
    private String senderEmail;

    public EvaluationExpiryScheduler(
            AcademicRequestRepository requestRepository,
            AcademicRequestService academicService,
            JavaMailSender mailSender,
            com.ecom.service.NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.academicService = academicService;
        this.mailSender = mailSender;
        this.notificationService = notificationService;
    }

    /** Run daily at 8:00 AM */
    @Scheduled(cron = "0 0 8 * * *")
    public void checkExpiryAndNotify() {
        log.info("Starting evaluation expiry check...");

        List<AcademicRequest> allRequests = requestRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (AcademicRequest request : allRequests) {
            if (request.getCurrentStatus() != RequestStatus.COMPLETED_PASS
                    && request.getCurrentStatus() != RequestStatus.COMPLETED) {
                continue;
            }

            UserDtls user = request.getApplicant();
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            if (Boolean.FALSE.equals(user.getIsEnable())) {
                continue; // Skip inactive/disabled accounts
            }
            if (user.getEmailNotificationEnabled() == null || !user.getEmailNotificationEnabled()) {
                continue;
            }

            LocalDateTime expiry = request.getEvaluationExpiryDate();
            if (expiry == null) {
                expiry = academicService.getLatestEvaluationExpiry(user.getId());
                if (expiry == null) {
                    continue;
                }
            }

            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(now, expiry);
            if (daysLeft < 0) {
                continue; // Already expired
            }

            // Check which alerts are enabled and match the interval
            boolean shouldAlert = false;
            String alertLabel = "";

            if (daysLeft <= 7 && Boolean.TRUE.equals(user.getExpiryAlert1w())) {
                shouldAlert = true;
                alertLabel = "1 สัปดาห์";
            } else if (daysLeft <= 30 && daysLeft > 7 && Boolean.TRUE.equals(user.getExpiryAlert1m())) {
                shouldAlert = true;
                alertLabel = "1 เดือน";
            } else if (daysLeft <= 90 && daysLeft > 30 && Boolean.TRUE.equals(user.getExpiryAlert3m())) {
                shouldAlert = true;
                alertLabel = "3 เดือน";
            } else if (daysLeft <= 180 && daysLeft > 90 && Boolean.TRUE.equals(user.getExpiryAlert6m())) {
                shouldAlert = true;
                alertLabel = "6 เดือน";
            }

            // Check user-configured custom expiry alerts (up to 5)
            if (!shouldAlert && user.getCustomExpiryAlerts() != null && !user.getCustomExpiryAlerts().isBlank()) {
                List<com.ecom.model.CustomExpiryAlert> customAlerts = parseCustomAlerts(user.getCustomExpiryAlerts());
                for (com.ecom.model.CustomExpiryAlert ca : customAlerts) {
                    if (ca.isEnabled() && daysLeft == ca.getDays()) {
                        shouldAlert = true;
                        alertLabel = ca.getLabel();
                        break;
                    }
                }
            }

            if (shouldAlert) {
                sendExpiryEmail(user, request, daysLeft, alertLabel, expiry);
            }
        }

        log.info("Evaluation expiry check completed.");
    }

    private List<com.ecom.model.CustomExpiryAlert> parseCustomAlerts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<com.ecom.model.CustomExpiryAlert>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse customExpiryAlerts for user: {}", e.getMessage());
            return List.of();
        }
    }

    private void sendExpiryEmail(UserDtls user, AcademicRequest request,
            long daysLeft, String alertLabel, LocalDateTime expiryDate) {
        try {
            int beYear = expiryDate.getYear() + 543;
            String[] thaiMonths = { "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
                    "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม" };
            String formattedDate = expiryDate.getDayOfMonth() + " " +
                    thaiMonths[expiryDate.getMonthValue() - 1] + " " + beYear;

            String requestCode = request.getRequestCode() != null ? request.getRequestCode() : String.valueOf(request.getId());

            String subject = "แจ้งเตือน: ผลประเมินการสอน (" + requestCode + ") จะหมดอายุภายใน " + alertLabel + " - HRCP.KKU";
            String body = com.ecom.util.EmailTemplateHelper.buildEvaluationExpiryEmail(
                    user.getName(),
                    requestCode,
                    alertLabel,
                    daysLeft,
                    formattedDate);

            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;

            // Send real email only if not a test/mock account
            if (!com.ecom.util.EmailTemplateHelper.isTestEmail(user.getEmail())) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
                helper.setTo(user.getEmail());
                helper.setSubject(subject);
                helper.setText(body, true);
                com.ecom.util.EmailTemplateHelper.attachLogos(helper);
                mailSender.send(message);
                log.info("Sent expiry alert to {} ({}d left)", user.getEmail(), daysLeft);
            } else {
                log.debug("Skipped real SMTP expiry email for test account {}", user.getEmail());
            }

            // Create In-App Notification (marked as important)
            String notifTitle = "ผลประเมินการสอนจะหมดอายุภายใน " + alertLabel;
            String notifMsg = "ผลการประเมินการสอน (" + requestCode + ") ของท่านจะหมดอายุในอีก " + daysLeft + " วัน (วันที่ " + formattedDate + ") กรุณาดำเนินการยื่นขอตำแหน่งก่อนหมดอายุ";
            String notifLink = "/user/position/dashboard";
            notificationService.sendNotification(user, null, notifTitle, notifMsg, notifLink, com.ecom.model.NotificationType.EXPIRY_WARNING, true);
        } catch (Exception e) {
            log.error("Failed to send expiry email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
