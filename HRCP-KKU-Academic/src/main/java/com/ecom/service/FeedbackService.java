package com.ecom.service;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.UserDtls;
import com.ecom.util.EmailTemplateHelper;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends feedback / issue-report emails on behalf of the logged-in user.
 *
 * <p>Stateless — nothing is persisted to the database. The email is the
 * only record, and the sender receives a CC as confirmation.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final JavaMailSender mailSender;

    @Value("${app.feedback.recipients:}")
    private String recipientsCsv;

    @Value("${app.feedback.max-images:3}")
    private int maxImages;

    @Value("${app.feedback.max-image-bytes:5242880}")
    private long maxImageBytes;

    @Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}")
    private String senderEmail;

    public FeedbackService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Returns the configured recipient list for the dropdown. */
    public List<String> getRecipientList() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(recipientsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public int getMaxImages() {
        return maxImages;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    /** Returns true if at least one recipient email is configured. */
    public boolean hasRecipients() {
        return !getRecipientList().isEmpty();
    }

    /**
     * ภาพที่แนบ อ่านเข้าหน่วยความจำแล้ว
     *
     * <p>ส่งอีเมลทำงานบนอีก thread หลังคำขอจบไปแล้ว ตอนนั้น Tomcat ลบไฟล์ชั่วคราวของ {@link MultipartFile}
     * ทิ้งไปแล้ว — เดิมส่ง MultipartFile ข้ามไป อ่านไม่ได้ อีเมลทั้งฉบับจึงไม่ออก (ทั้งที่หน้าเว็บบอกว่าส่งแล้ว)
     */
    public record Image(String filename, byte[] bytes, String contentType) {

        public static Image of(MultipartFile file) throws java.io.IOException {
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.png";
            return new Image(name, file.getBytes(), file.getContentType());
        }
    }

    /**
     * Sends the feedback email asynchronously to all configured recipients.
     *
     * @param sender    the logged-in user filing the report
     * @param category  ปัญหาระบบ / ข้อเสนอแนะ / อื่นๆ
     * @param subject   free-text subject line
     * @param detail    full description
     * @param images    attached screenshots (may be empty)
     */
    @Async
    public void sendFeedback(UserDtls sender, String category, String subject,
                             String detail, List<Image> images) {
        try {
            List<String> recipients = getRecipientList().stream()
                    .filter(r -> !EmailTemplateHelper.isTestEmail(r))
                    .toList();

            if (recipients.isEmpty()) {
                log.warn("Feedback email from {} not sent: no valid recipients configured or all are test accounts",
                        sender.getEmail());
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
            helper.setTo(recipients.toArray(new String[0]));

            // CC back to the sender so they have a copy
            if (sender.getEmail() != null && !sender.getEmail().isBlank()) {
                helper.setCc(sender.getEmail());
            }

            String fullSubject = "[CP HRD] แจ้ง" + category + ": " + subject;
            helper.setSubject(fullSubject);

            String senderName = sender.getName() != null ? sender.getName() : "ผู้ใช้งาน";
            String senderEmailStr = sender.getEmail() != null ? sender.getEmail() : "-";
            int imageCount = images != null ? images.size() : 0;

            String body = EmailTemplateHelper.buildFeedbackEmail(
                    senderName, senderEmailStr, category, subject, detail, imageCount);
            helper.setText(body, true);
            EmailTemplateHelper.attachLogos(helper);

            // Attach images
            if (images != null) {
                for (Image image : images) {
                    helper.addAttachment(image.filename(), new ByteArrayResource(image.bytes()),
                            image.contentType());
                }
            }

            mailSender.send(message);

            log.info("Feedback email sent from {} to {} recipients {} (category={})",
                    sender.getEmail(), recipients.size(), recipients, category);

        } catch (Exception e) {
            log.error("Failed to send feedback email from {}: {}",
                    sender.getEmail(), e.getMessage(), e);
        }
    }
}
