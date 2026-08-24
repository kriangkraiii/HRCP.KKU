package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.dto.SignatureNotice;
import com.ecom.academic.model.SignatureModule;
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

    /** Tells the applicant that admin requested document correction and re-signing. */
    @Async
    public void notifyResignRequested(UserDtls applicant, SignatureModule module, Long requestId, int documentType,
            String documentLabel, String reason) {
        if (applicant == null) return;
        String safeDoc = documentLabel != null && !documentLabel.isBlank() ? documentLabel : ("เอกสารที่ " + documentType);
        String title = "ขอให้แก้ไขและลงนามใหม่: " + safeDoc;
        String message = "แอดมินแจ้งให้ท่านแก้ไขข้อมูลและลงนามใหม่ในเอกสาร " + safeDoc
                + (reason != null && !reason.isBlank() ? " (เหตุผล: " + reason + ")" : "");
        String userLink = module == SignatureModule.POSITION
                ? "/user/position/request/" + requestId + "/document/" + documentType
                : "/user/academic/request/" + requestId + "/document/" + documentType;

        notify(applicant, title, message, userLink, NotificationType.SIGNATURE_DECLINED, true);

        String body = EmailTemplateHelper.wrapLayout("แจ้งให้แก้ไขข้อมูลและลงนามใหม่", "ต้องดำเนินการ",
                "<p>แอดมินได้ตรวจสอบเอกสาร <strong>" + escape(safeDoc) + "</strong> และขอให้ท่านแก้ไขข้อมูลพร้อมลงนามใหม่อีกครั้ง</p>"
                        + (reason != null && !reason.isBlank() ? "<p><strong>เหตุผลที่ส่งกลับ:</strong> " + escape(reason) + "</p>" : "")
                        + "<p>ระบบได้ปลดล็อกเอกสารให้ท่านสามารถเข้าสู่ระบบเพื่อแก้ไขและลงนามใหม่ได้ทันที</p>");
        email(applicant, title, body);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private void notify(UserDtls recipient, String title, String message, String link,
            NotificationType type, boolean important) {
        if (recipient == null || Boolean.FALSE.equals(recipient.getIsEnable())) {
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
        if (Boolean.FALSE.equals(recipient.getIsEnable())) {
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
        body.append("<p style='font-size: 15px; line-height: 1.6;'>เรียน <strong>").append(escape(notice.signerName())).append("</strong></p>");
        body.append("<p style='font-size: 14px; line-height: 1.6;'>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น ขอความอนุเคราะห์ท่านในการตรวจสอบและลงนามในเอกสารอิเล็กทรอนิกส์ตามรายละเอียดดังต่อไปนี้:</p>");
        
        body.append("<div style='background-color: #f8fafc; border: 1px solid #e2e8f0; border-left: 4px solid #0d47a1; padding: 15px 18px; border-radius: 8px; margin: 16px 0;'>");
        body.append("<table style='width: 100%; border-collapse: collapse; font-size: 14px;'>");
        body.append("<tr><td style='padding: 4px 0; color: #64748b; width: 140px;'>เอกสาร:</td><td style='padding: 4px 0; font-weight: 600; color: #0f172a;'>").append(escape(notice.safeDocumentLabel())).append("</td></tr>");
        body.append("<tr><td style='padding: 4px 0; color: #64748b;'>ประเภทคำร้อง:</td><td style='padding: 4px 0; color: #0f172a;'>").append(escape(notice.module().getThaiLabel())).append("</td></tr>");
        body.append("<tr><td style='padding: 4px 0; color: #64748b;'>ตำแหน่งที่ลงนาม:</td><td style='padding: 4px 0; font-weight: 600; color: #0d47a1;'>").append(escape(notice.roleLabel())).append("</td></tr>");
        body.append("<tr><td style='padding: 4px 0; color: #64748b;'>รหัสตรวจสอบ:</td><td style='padding: 4px 0; font-family: monospace; color: #475569;'>").append(escape(notice.verificationCode())).append("</td></tr>");
        body.append("</table>");
        body.append("</div>");

        if (notice.dueAt() != null) {
            long daysLeft = java.time.Duration.between(LocalDateTime.now(), notice.dueAt()).toDays();
            String timeText = daysLeft >= 0 ? " (เหลือเวลาประมาณ " + (daysLeft == 0 ? "วันนี้" : daysLeft + " วัน") + ")" : " (เลยกำหนดเวลา)";
            body.append("<div style='background-color: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; padding: 12px 16px; margin: 16px 0; color: #92400e; font-size: 14px;'>");
            body.append("📅 <strong>กำหนดลงนามภายใน:</strong> ").append(notice.dueAt().format(DUE_FORMAT)).append(timeText);
            body.append("</div>");
        }

        String signLink = "/esign/sign/" + notice.stepId();
        body.append("<p style='font-size: 14px; color: #475569; margin-top: 20px;'>ท่านสามารถตรวจสอบเอกสารฉบับจริงและลงนามผ่านระบบด้วยบัญชี <strong>KKU SSO</strong> โดยกดปุ่มด้านล่างนี้:</p>");
        body.append("<div style='text-align: center; margin: 24px 0;'>");
        body.append("<a href='").append(signLink).append("' style='display: inline-block; background-color: #0d47a1; color: #ffffff; text-decoration: none; padding: 12px 28px; border-radius: 6px; font-weight: 600; font-size: 15px; box-shadow: 0 2px 4px rgba(13, 71, 161, 0.2);'>เข้าสู่ระบบ KKU SSO เพื่อลงนาม</a>");
        body.append("</div>");
        body.append("<p style='font-size: 12px; color: #94a3b8; text-align: center;'>หากปุ่มไม่ทำงาน สามารถเข้าสู่ระบบและไปที่เมนู <strong>\"รอลงนาม\"</strong> ได้โดยตรง</p>");

        return EmailTemplateHelper.wrapLayout("ขอความอนุเคราะห์ลงนามเอกสารอิเล็กทรอนิกส์", "รอลงนาม", body.toString());
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
