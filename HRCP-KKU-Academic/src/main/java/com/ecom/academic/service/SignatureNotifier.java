package com.ecom.academic.service;

import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.dto.SignatureNotice;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.service.NotificationService;
import com.ecom.util.EmailTemplateHelper;

import jakarta.mail.internet.MimeMessage;

/**
 * Tells people a document is waiting for them.
 *
 * <p>Two channels, following what the rest of the app already does: an in-app
 * notification through {@link NotificationService} (always) and an email (only
 * where the recipient has not switched them off). Failures are logged and
 * swallowed — a signature must never be lost because a mail server was down.
 *
 * <p>Everything here takes a {@link SignatureNotice} rather than an entity.
 * These methods run on a background thread with no persistence session, so an
 * entity's lazy fields would be unreadable by the time they were needed.
 */
@Service
public class SignatureNotifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureNotifier.class);

    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final NotificationService notificationService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public SignatureNotifier(NotificationService notificationService, JavaMailSender mailSender) {
        this.notificationService = notificationService;
        this.mailSender = mailSender;
    }

    /** Asks the signer whose turn it now is. */
    @Async
    public void notifySignatureRequested(SignatureNotice notice) {
        String title = "ขอให้ลงนาม: " + notice.safeDocumentLabel();
        String message = "คุณได้รับมอบหมายให้ลงนามในตำแหน่ง \"" + notice.roleLabel() + "\" "
                + "สำหรับ" + notice.module().getThaiLabel()
                + (notice.dueAt() != null ? " ภายในวันที่ " + notice.dueAt().format(DUE_FORMAT) : "");

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, "/esign/sign/" + notice.stepId(),
                    NotificationType.SIGNATURE_REQUESTED, true);
            email(recipient, title, buildRequestEmail(notice));
        }
    }

    /** Nudges a signer who has not acted yet. */
    @Async
    public void notifyReminder(SignatureNotice notice) {
        String title = "เตือน: ยังไม่ได้ลงนาม " + notice.safeDocumentLabel();
        String message = "เอกสารยังรอลายเซ็นของคุณในตำแหน่ง \"" + notice.roleLabel() + "\"";

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, "/esign/sign/" + notice.stepId(),
                    NotificationType.SIGNATURE_REMINDER, true);
            email(recipient, title, buildRequestEmail(notice));
        }
    }

    /** Tells the initiator the chain finished. */
    @Async
    public void notifyCompleted(SignatureNotice notice) {
        String title = "ลงนามครบแล้ว: " + notice.safeDocumentLabel();
        String message = "ผู้ลงนามทุกคนลงนามในเอกสารเรียบร้อยแล้ว (" + notice.progressLabel() + ")";
        String body = EmailTemplateHelper.wrapLayout("ลงนามครบทุกขั้นตอน", "เสร็จสิ้น",
                "<p>เอกสาร <strong>" + escape(notice.safeDocumentLabel()) + "</strong> "
                        + "ได้รับการลงนามครบทุกขั้นตอนแล้ว</p>"
                        + "<p>รหัสตรวจสอบเอกสาร: <strong>"
                        + escape(notice.verificationCode()) + "</strong></p>");

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, notice.module().adminLink(notice.requestId()),
                    NotificationType.SIGNATURE_COMPLETED, false);
            email(recipient, title, body);
        }
    }

    /** Tells the initiator someone refused, and why. */
    @Async
    public void notifyDeclined(SignatureNotice notice) {
        String title = "ปฏิเสธการลงนาม: " + notice.safeDocumentLabel();
        String message = notice.signerName() + " ปฏิเสธการลงนาม — เหตุผล: " + notice.declineReason();
        String body = EmailTemplateHelper.wrapLayout("ปฏิเสธการลงนาม", "ต้องดำเนินการ",
                "<p><strong>" + escape(notice.signerName()) + "</strong> ปฏิเสธการลงนามในเอกสาร "
                        + "<strong>" + escape(notice.safeDocumentLabel()) + "</strong></p>"
                        + "<p>เหตุผล: " + escape(notice.declineReason()) + "</p>"
                        + "<p>เอกสารถูกปลดล็อกให้แก้ไขได้แล้ว "
                        + "เมื่อแก้ไขเรียบร้อยจึงส่งเวียนลงนามใหม่อีกครั้ง</p>");

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, notice.module().adminLink(notice.requestId()),
                    NotificationType.SIGNATURE_DECLINED, true);
            email(recipient, title, body);
        }
    }

    /** Lets outstanding signers know they no longer need to act. */
    @Async
    public void notifyCancelled(SignatureNotice notice) {
        String title = "ยกเลิกการเวียนลงนาม: " + notice.safeDocumentLabel();
        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, "การเวียนลงนามในเอกสารนี้ถูกยกเลิกแล้ว",
                    "/esign/inbox", NotificationType.SYSTEM, false);
            email(recipient, title, EmailTemplateHelper.wrapLayout("ยกเลิกการเวียนลงนาม", "แจ้งเพื่อทราบ",
                    "<p>การเวียนลงนามในเอกสาร <strong>" + escape(notice.safeDocumentLabel())
                            + "</strong> ถูกยกเลิกโดยผู้ส่งเอกสาร</p>"));
        }
    }

    /** Tells the initiator that a signer requested a due date extension. */
    @Async
    public void notifyExtensionRequested(UserDtls initiator, SignatureRequest envelope, UserDtls signer, String reason) {
        if (initiator == null) return;
        String title = "ขอขยายเวลาลงนาม: " + (envelope.getDocumentLabel() != null ? envelope.getDocumentLabel() : "เอกสาร");
        String message = (signer != null ? signer.getName() : "ผู้ลงนาม") + " ขอขยายเวลาลงนาม"
                + (reason != null && !reason.isBlank() ? " (เหตุผล: " + reason + ")" : "");
        String link = envelope.getModule() != null ? envelope.getModule().adminLink(envelope.getRequestId()) : "/admin/academic/requests";

        notify(initiator, title, message, link, NotificationType.SIGNATURE_REMINDER, true);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private void notify(UserDtls recipient, String title, String message, String link,
            NotificationType type, boolean important) {
        if (recipient == null) {
            return;
        }
        try {
            notificationService.sendNotification(recipient, null, title, message, link, type, important);
        } catch (Exception e) {
            log.warn("Failed to create signature notification for {}: {}",
                    recipient.getEmail(), e.toString());
        }
    }

    private void email(UserDtls recipient, String subject, String htmlBody) {
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        // Honours the same per-account switch the rest of the system uses.
        if (Boolean.FALSE.equals(recipient.getEmailNotificationEnabled())) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, EmailTemplateHelper.SENDER_NAME);
            helper.setTo(recipient.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send signature email to {}: {}", recipient.getEmail(), e.toString());
        }
    }

    private String buildRequestEmail(SignatureNotice notice) {
        StringBuilder body = new StringBuilder();
        body.append("<p>เรียน ").append(escape(notice.signerName())).append("</p>");
        body.append("<p>มีเอกสารรอลายเซ็นของท่านในระบบ</p>");
        body.append("<ul>");
        body.append("<li>เอกสาร: <strong>").append(escape(notice.safeDocumentLabel())).append("</strong></li>");
        body.append("<li>ประเภทคำร้อง: ").append(escape(notice.module().getThaiLabel())).append("</li>");
        body.append("<li>ตำแหน่งที่ลงนาม: <strong>").append(escape(notice.roleLabel())).append("</strong></li>");
        if (notice.dueAt() != null) {
            body.append("<li>กำหนดลงนามภายใน: ").append(notice.dueAt().format(DUE_FORMAT)).append("</li>");
        }
        body.append("</ul>");
        body.append("<p>กรุณาเข้าสู่ระบบเพื่อตรวจสอบเอกสารและลงนาม ")
                .append("โดยไปที่เมนู <strong>\"รอลงนาม\"</strong></p>");
        // No signing link in the email on purpose: a mailbox is not an identity,
        // and signing must begin from an authenticated session in the system.
        return EmailTemplateHelper.wrapLayout("ขอเชิญลงนามในเอกสาร", "รอลงนาม", body.toString());
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
