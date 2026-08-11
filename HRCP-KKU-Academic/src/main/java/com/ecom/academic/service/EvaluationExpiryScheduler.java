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

    public EvaluationExpiryScheduler(
            AcademicRequestRepository requestRepository,
            AcademicRequestService academicService,
            JavaMailSender mailSender) {
        this.requestRepository = requestRepository;
        this.academicService = academicService;
        this.mailSender = mailSender;
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
            if (user == null || user.getEmail() == null)
                continue;
            if (user.getEmailNotificationEnabled() == null || !user.getEmailNotificationEnabled())
                continue;

            // Compute or retrieve expiry
            LocalDateTime expiry = request.getEvaluationExpiryDate();
            if (expiry == null) {
                expiry = academicService.getLatestEvaluationExpiry(user.getId());
                if (expiry == null)
                    continue;
            }

            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(now, expiry);
            if (daysLeft < 0)
                continue; // Already expired

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

            if (shouldAlert) {
                sendExpiryEmail(user, request, daysLeft, alertLabel, expiry);
            }
        }

        log.info("Evaluation expiry check completed.");
    }

    private void sendExpiryEmail(UserDtls user, AcademicRequest request,
            long daysLeft, String alertLabel, LocalDateTime expiryDate) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(user.getEmail());
            helper.setSubject("⚠️ แจ้งเตือน: ผลประเมินการสอนจะหมดอายุภายใน " + alertLabel);

            int beYear = expiryDate.getYear() + 543;
            String[] thaiMonths = { "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
                    "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม" };
            String formattedDate = expiryDate.getDayOfMonth() + " " +
                    thaiMonths[expiryDate.getMonthValue() - 1] + " " + beYear;

            String body = """
                    <div style="font-family:'Sarabun',sans-serif; max-width:600px; margin:0 auto; padding:20px;">
                        <div style="background:linear-gradient(135deg,#1a237e,#283593); color:white; padding:20px; border-radius:12px 12px 0 0;">
                            <h2 style="margin:0;">⏰ แจ้งเตือนผลประเมินการสอน</h2>
                        </div>
                        <div style="background:white; padding:20px; border:1px solid #e0e0e0; border-radius:0 0 12px 12px;">
                            <p>เรียน คุณ%s,</p>
                            <div style="background:#fff3e0; padding:15px; border-radius:8px; border-left:4px solid #ff9800; margin:15px 0;">
                                <p style="margin:0;font-weight:bold;">ผลประเมินการสอนของท่านจะหมดอายุภายใน %s</p>
                                <p style="margin:5px 0 0;">คงเหลือ: <strong>%d วัน</strong></p>
                                <p style="margin:5px 0 0;">วันหมดอายุ: <strong>%s</strong></p>
                                <p style="margin:5px 0 0;">คำร้อง: <strong>%s</strong></p>
                            </div>
                            <p>หากท่านต้องการยื่นขอตำแหน่งทางวิชาการ กรุณาดำเนินการก่อนผลประเมินหมดอายุ</p>
                            <p style="color:#999;font-size:0.85rem;">ท่านสามารถปรับตั้งค่าการแจ้งเตือนได้ที่หน้า "การตั้งค่า" ในระบบ</p>
                        </div>
                    </div>
                    """
                    .formatted(user.getName(), alertLabel, daysLeft, formattedDate, request.getRequestCode());

            helper.setText(body, true);
            mailSender.send(message);
            log.info("Sent expiry alert to {} ({}d left)", user.getEmail(), daysLeft);
        } catch (Exception e) {
            log.error("Failed to send expiry email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
