package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    private final com.ecom.repository.UserRepository userRepository;

    @Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}")
    private String senderEmail;

    @Value("${app.public-base-url:https://hrd.computing.kku.ac.th}")
    private String publicBaseUrl = "https://hrd.computing.kku.ac.th";

    public SignatureNotifier(NotificationService notificationService, JavaMailSender mailSender,
            com.ecom.repository.UserRepository userRepository) {
        this.notificationService = notificationService;
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    /** Asks the signer whose turn it now is. */
    @Async
    public void notifySignatureRequested(SignatureNotice notice) {
        String title = "ขอให้ลงนาม: " + notice.safeDocumentLabel();
        String message = "ท่านได้รับมอบหมายให้ลงนามในตำแหน่ง \"" + notice.roleLabel() + "\" "
                + "สำหรับ" + notice.module().getThaiLabel()
                + (notice.dueAt() != null ? " ภายในวันที่ " + notice.dueAt().format(DUE_FORMAT) : "");

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, "/esign/sign/" + notice.stepId(),
                    NotificationType.SIGNATURE_REQUESTED, true);
            email(recipient, "ขอความอนุเคราะห์ลงนาม: " + notice.safeDocumentLabel() + aboutWhom(notice),
                    buildRequestEmail(notice, recipient, false));
        }
    }

    /** Nudges a signer who has not acted yet. */
    @Async
    public void notifyReminder(SignatureNotice notice) {
        String title = "เตือน: ยังไม่ได้ลงนาม " + notice.safeDocumentLabel();
        String message = "เอกสารยังรอลายเซ็นของท่านในตำแหน่ง \"" + notice.roleLabel() + "\"";

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, "/esign/sign/" + notice.stepId(),
                    NotificationType.SIGNATURE_REMINDER, true);
            email(recipient, "ขอเตือนการลงนาม: " + notice.safeDocumentLabel() + aboutWhom(notice),
                    buildRequestEmail(notice, recipient, true));
        }
    }

    /**
     * Tells someone the date they set for themselves has arrived.
     *
     * <p>Worded apart from {@link #notifyReminder} on purpose. This one goes to
     * an applicant whose deadline is only a reminder, and the message has to say
     * so — "เลยกำหนดลงนาม" with no qualifier reads as a door that has closed, and
     * would send them looking for someone to ask for an extension they do not
     * need.
     */
    @Async
    public void notifyDeadlineReached(SignatureNotice notice) {
        String title = "ถึงกำหนดที่ตั้งเตือนไว้: " + notice.safeDocumentLabel();
        String message = "เลยกำหนดที่ท่านตั้งเตือนไว้สำหรับ \"" + notice.safeDocumentLabel() + "\" แล้ว "
                + "ท่านยังลงนามได้ตามปกติ และยื่นคำร้องได้เมื่อลงนามครบ";

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, "/esign/sign/" + notice.stepId(),
                    NotificationType.SIGNATURE_REMINDER, true);
            String body = EmailTemplateHelper.Letter.of("ถึงกำหนดที่ท่านตั้งเตือนไว้สำหรับการลงนาม", formal(recipient))
                    .para("เอกสาร “" + notice.safeDocumentLabel() + "” ยังรอการลงนามของท่าน และถึงวันที่ท่านตั้งเตือนไว้แล้ว")
                    .note("info", null, "กำหนดนี้เป็นเพียงการแจ้งเตือน เอกสารยังไม่ถูกปิด ท่านลงนามได้ตามปกติ"
                            + " และยื่นคำร้องได้ทันทีที่ลงนามครบทุกฉบับ")
                    .button("เข้าสู่ระบบเพื่อลงนาม", link("/esign/sign/" + notice.stepId()))
                    .close("จึงเรียนมาเพื่อโปรดทราบ");
            email(recipient, "ถึงกำหนดที่ตั้งเตือนไว้: " + notice.safeDocumentLabel(),
                    EmailTemplateHelper.wrapLayout("ถึงกำหนดที่ตั้งเตือนไว้", "แจ้งเตือน", body));
        }
    }

    /** Tells the initiator the chain finished. */
    @Async
    public void notifyCompleted(SignatureNotice notice) {
        String title = "ลงนามครบแล้ว: " + notice.safeDocumentLabel();
        String message = "ผู้ลงนามทุกคนลงนามในเอกสารเรียบร้อยแล้ว (" + notice.progressLabel() + ")";

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, requestLinkFor(recipient, notice.module(), notice.requestId()),
                    NotificationType.SIGNATURE_COMPLETED, false);
            String body = EmailTemplateHelper.Letter.of("เอกสาร “" + notice.safeDocumentLabel() + "” ลงนามครบแล้ว",
                            formal(recipient))
                    .para("เอกสาร “" + notice.safeDocumentLabel() + "”" + ofCase(notice)
                            + " ได้รับการลงนามครบทุกตำแหน่งแล้ว")
                    .details(caseRows(notice, "ความคืบหน้า", notice.progressLabel()))
                    .para("ท่านสามารถดาวน์โหลดเอกสารที่ลงนามแล้ว และดำเนินการในขั้นตอนต่อไปได้ในระบบ")
                    .button("เปิดคำร้องในระบบ", link(requestLinkFor(recipient, notice.module(), notice.requestId())))
                    .close("จึงเรียนมาเพื่อโปรดทราบ");
            email(recipient, "ลงนามครบแล้ว: " + notice.safeDocumentLabel() + aboutWhom(notice),
                    EmailTemplateHelper.wrapLayout("เอกสารลงนามครบแล้ว", "เสร็จสิ้น", body));
        }
    }

    /** Tells the initiator someone refused, and why. */
    @Async
    public void notifyDeclined(SignatureNotice notice) {
        String title = "ปฏิเสธการลงนาม: " + notice.safeDocumentLabel();
        String message = notice.signerName() + " ปฏิเสธการลงนาม — เหตุผล: " + notice.declineReason();

        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, message, requestLinkFor(recipient, notice.module(), notice.requestId()),
                    NotificationType.SIGNATURE_DECLINED, true);
            String body = EmailTemplateHelper.Letter.of("ผู้ลงนามไม่ลงนามในเอกสาร “" + notice.safeDocumentLabel() + "”",
                            formal(recipient))
                    .para(nameOr(notice.signerName(), "ผู้ลงนาม") + " ในฐานะ" + nameOr(notice.roleLabel(), "ผู้ลงนาม")
                            + " ไม่ลงนามในเอกสาร “" + notice.safeDocumentLabel() + "”" + ofCase(notice)
                            + " การเวียนลงนามจึงหยุดลง")
                    .details(caseRows(notice, null, null))
                    .note("danger", "เหตุผล", nameOr(notice.declineReason(), "-"))
                    .para("ระบบได้ปลดล็อกเอกสารให้แก้ไขได้แล้ว ขอให้ตรวจสอบและแก้ไขตามเหตุผลข้างต้น"
                            + " แล้วส่งเวียนลงนามใหม่อีกครั้ง")
                    .button("เปิดคำร้องในระบบ", link(requestLinkFor(recipient, notice.module(), notice.requestId())))
                    .close("จึงเรียนมาเพื่อโปรดดำเนินการ");
            email(recipient, "ผู้ลงนามไม่ลงนาม: " + notice.safeDocumentLabel() + aboutWhom(notice),
                    EmailTemplateHelper.wrapLayout("ผู้ลงนามไม่ลงนามในเอกสาร", "ต้องดำเนินการ", body));
        }
    }

    /** Lets outstanding signers know they no longer need to act. */
    @Async
    public void notifyCancelled(SignatureNotice notice) {
        String title = "ยกเลิกการเวียนลงนาม: " + notice.safeDocumentLabel();
        for (UserDtls recipient : notice.recipients()) {
            notify(recipient, title, "การเวียนลงนามในเอกสารนี้ถูกยกเลิกแล้ว",
                    "/esign/inbox", NotificationType.SYSTEM, false);
            String body = EmailTemplateHelper.Letter.of("ยกเลิกการขอให้ลงนามในเอกสาร “" + notice.safeDocumentLabel() + "”",
                            formal(recipient))
                    .para("ตามที่วิทยาลัยการคอมพิวเตอร์ได้ขอความอนุเคราะห์ท่านลงนามในเอกสาร “"
                            + notice.safeDocumentLabel() + "”" + ofCase(notice)
                            + " บัดนี้ผู้ส่งเอกสารได้ยกเลิกการเวียนลงนามแล้ว ท่านจึงไม่ต้องลงนามในเอกสารดังกล่าว"
                            + " หากมีการส่งเอกสารฉบับใหม่ ระบบจะแจ้งท่านอีกครั้ง")
                    .close("จึงเรียนมาเพื่อโปรดทราบ และขออภัยในความไม่สะดวก");
            email(recipient, "ยกเลิกการขอให้ลงนาม: " + notice.safeDocumentLabel() + aboutWhom(notice),
                    EmailTemplateHelper.wrapLayout("ยกเลิกการเวียนลงนาม", "แจ้งเพื่อทราบ", body));
        }
    }

    /**
     * Tells the people waiting on a document that a signer recorded an adverse finding.
     *
     * <p>The signature still stands and the round carries on — the supervisor's
     * job on this form is to record what they found, not to stop the process.
     * But a "ไม่ครบถ้วน" buried inside a rendered document is a finding nobody
     * would see until someone opened the file, so it is announced.
     */
    @Async
    public void notifySignerChoiceAlert(List<UserDtls> recipients, SignatureModule module, Long requestId,
            String documentLabel, String signerName, String question, String answer) {
        String safeDoc = documentLabel != null && !documentLabel.isBlank() ? documentLabel : "เอกสาร";
        String who = signerName != null && !signerName.isBlank() ? signerName : "ผู้ลงนาม";
        String title = "ผลการตรวจสอบ: " + answer + " — " + safeDoc;
        String message = who + " ลงนามแล้วและบันทึก" + question + "เป็น \"" + answer + "\"";

        for (UserDtls recipient : recipients) {
            notify(recipient, title, message, requestLinkFor(recipient, module, requestId),
                    NotificationType.SIGNATURE_DECLINED, true);
            String body = EmailTemplateHelper.Letter.of("ผลการตรวจสอบในเอกสาร “" + safeDoc + "”", formal(recipient))
                    .para(who + " ได้ลงนามในเอกสาร “" + safeDoc + "” ของ" + module.getThaiLabel()
                            + " รหัส " + requestId + " แล้ว และได้บันทึกผลการตรวจสอบที่ควรทราบ ดังนี้")
                    .note("warn", question, answer)
                    .para("การเวียนลงนามยังดำเนินต่อไปตามปกติ ขอให้ตรวจสอบว่าต้องดำเนินการใดเพิ่มเติมหรือไม่")
                    .button("เปิดคำร้องในระบบ", link(requestLinkFor(recipient, module, requestId)))
                    .close("จึงเรียนมาเพื่อโปรดพิจารณา");
            email(recipient, "ผลการตรวจสอบ “" + answer + "”: " + safeDoc,
                    EmailTemplateHelper.wrapLayout("ผลการตรวจสอบจากผู้ลงนาม", "ควรตรวจสอบ", body));
        }
    }

    /** Tells the initiator that a signer requested a due date extension. */
    @Async
    public void notifyExtensionRequested(UserDtls initiator, SignatureRequest envelope, UserDtls signer, String reason) {
        if (initiator == null) return;
        String title = "ขอขยายเวลาลงนาม: " + (envelope.getDocumentLabel() != null ? envelope.getDocumentLabel() : "เอกสาร");
        String message = (signer != null ? signer.getName() : "ผู้ลงนาม") + " ขอขยายเวลาลงนาม"
                + (reason != null && !reason.isBlank() ? " (เหตุผล: " + reason + ")" : "");
        String link = envelope.getModule() != null
                ? requestLinkFor(initiator, envelope.getModule(), envelope.getRequestId())
                : "/esign/inbox";

        notify(initiator, title, message, link, NotificationType.SIGNATURE_REMINDER, true);
    }

    /**
     * Tells the applicant that admin requested document correction and re-signing.
     *
     * <p>รับ id ไม่ใช่ตัว {@link UserDtls}: เมธอดนี้รันบนเธรดอื่น ถ้ารับ entity มาจากคำขอของเจ้าหน้าที่
     * (มักเป็น proxy ที่ยังไม่โหลด) การแตะมันที่นี่จะไปโหลดผ่าน session ของเธรดนั้น ขณะที่เธรดนั้นกำลัง
     * บันทึกการส่งกลับอยู่ — Hibernate session ใช้ข้ามเธรดไม่ได้ การส่งกลับจึงพังเป็นระยะด้วย
     * "Illegal pop() with non-matching JdbcValuesSourceProcessingState"
     */
    @Async
    public void notifyResignRequested(Integer applicantId, SignatureModule module, Long requestId, int documentType,
            String documentLabel, String reason) {
        if (applicantId == null) return;
        UserDtls applicant = userRepository.findById(applicantId).orElse(null);
        if (applicant == null) return;
        String safeDoc = documentLabel != null && !documentLabel.isBlank() ? documentLabel : ("เอกสารที่ " + documentType);
        String title = "ขอให้แก้ไขและลงนามใหม่: " + safeDoc;
        String message = "เจ้าหน้าที่แจ้งให้ท่านแก้ไขข้อมูลและลงนามใหม่ในเอกสาร " + safeDoc
                + (reason != null && !reason.isBlank() ? " (เหตุผล: " + reason + ")" : "");
        String userLink = module == SignatureModule.POSITION
                ? "/user/position/request/" + requestId + "/document/" + documentType
                : "/user/academic/request/" + requestId + "/document/" + documentType;

        notify(applicant, title, message, userLink, NotificationType.SIGNATURE_DECLINED, true);

        EmailTemplateHelper.Letter letter = EmailTemplateHelper.Letter.of(
                        "ขอให้แก้ไขเอกสาร “" + safeDoc + "” และลงนามใหม่", EmailTemplateHelper.formalName(applicant))
                .para("เจ้าหน้าที่ได้ตรวจสอบเอกสาร “" + safeDoc + "” ของ" + module.getThaiLabel() + " รหัส " + requestId
                        + " ของท่านแล้ว และขอให้ท่านแก้ไขข้อมูลพร้อมลงนามใหม่");
        if (reason != null && !reason.isBlank()) {
            letter.note("warn", "สิ่งที่ต้องแก้ไข", reason.trim());
        }
        String body = letter
                .steps("สิ่งที่ท่านต้องดำเนินการ", List.of(
                        "เข้าสู่ระบบ แล้วเปิดเอกสาร “" + safeDoc + "” (ระบบปลดล็อกให้แก้ไขได้แล้ว)",
                        "แก้ไขข้อมูลตามรายละเอียดข้างต้น",
                        "ลงนามอิเล็กทรอนิกส์ในเอกสารฉบับแก้ไข เจ้าหน้าที่จะตรวจสอบและดำเนินการต่อ"))
                .button("เปิดเอกสารเพื่อแก้ไข", link(userLink))
                .close("จึงเรียนมาเพื่อโปรดดำเนินการ");
        email(applicant, "ขอให้แก้ไขเอกสารและลงนามใหม่: " + safeDoc,
                EmailTemplateHelper.wrapLayout("ขอให้แก้ไขเอกสารและลงนามใหม่", "ต้องดำเนินการ", body));
    }

    /**
     * บอกเจ้าหน้าที่ว่าผู้ยื่นแก้เอกสารที่ส่งกลับเสร็จแล้ว (ลงนามใหม่ หรือกดยื่นการแก้ไข)
     *
     * <p>ซองที่ผู้ยื่นลงนามแล้วรอให้เจ้าหน้าที่ตรวจก่อนส่งเวียน ถ้าไม่แจ้ง ซองจะรออยู่เงียบ ๆ
     * จนกว่าจะมีคนบังเอิญเปิดดู
     */
    @Async
    public void notifyRevisionSubmitted(SignatureModule module, Long requestId, String documentLabel,
            String applicantName) {
        String safeDoc = documentLabel != null && !documentLabel.isBlank() ? documentLabel : "เอกสาร";
        String who = applicantName != null && !applicantName.isBlank() ? applicantName : "ผู้ยื่นคำร้อง";
        String title = "ผู้ยื่นส่งเอกสารที่แก้ไขแล้ว: " + safeDoc;
        String message = who + " แก้ไขเอกสาร " + safeDoc + " ตามที่ส่งกลับเรียบร้อยแล้ว กรุณาตรวจสอบและดำเนินการต่อ";
        List<UserDtls> admins;
        try {
            admins = userRepository.findByRole("ROLE_ADMIN");
        } catch (Exception e) {
            log.warn("Could not load admins to announce revision on {} request {}: {}", module, requestId, e.toString());
            return;
        }
        for (UserDtls admin : admins) {
            notify(admin, title, message, module.adminLink(requestId), NotificationType.REVISION_SUBMITTED, true);
            String body = EmailTemplateHelper.Letter.of("ผู้ยื่นส่งเอกสาร “" + safeDoc + "” ฉบับแก้ไขแล้ว", formal(admin))
                    .para(who + " ได้แก้ไขเอกสาร “" + safeDoc + "” ของ" + module.getThaiLabel() + " รหัส " + requestId
                            + " ตามที่ส่งกลับ และลงนามในฉบับแก้ไขเรียบร้อยแล้ว")
                    .para("ขอให้ตรวจสอบความถูกต้อง และส่งเวียนลงนามต่อตามขั้นตอน")
                    .button("เปิดคำร้องในระบบ", link(module.adminLink(requestId)))
                    .close("จึงเรียนมาเพื่อโปรดดำเนินการ");
            email(admin, "ผู้ยื่นส่งเอกสารฉบับแก้ไขแล้ว: " + safeDoc + " (" + who + ")",
                    EmailTemplateHelper.wrapLayout("ผู้ยื่นส่งเอกสารฉบับแก้ไขแล้ว", "ต้องดำเนินการ", body));
        }
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
        if (EmailTemplateHelper.isTestEmail(recipient.getEmail())) {
            log.debug("Skipping real SMTP email for test/mock account: {}", recipient.getEmail());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
            helper.setTo(recipient.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send signature email to {}: {}", recipient.getEmail(), e.toString());
        }
    }

    /**
     * ขอให้ลงนาม — บอกว่าเป็นเอกสารของคำร้องใคร ลงนามในฐานะอะไร ภายในเมื่อใด และลงนามอย่างไร
     * ผู้ลงนามจากนอก มข. ได้คำอธิบายเพิ่ม ว่าเรื่องนี้คืออะไร และสิ่งที่ต้องทำทีละขั้น
     */
    private String buildRequestEmail(SignatureNotice notice, UserDtls recipient, boolean reminder) {
        String doc = notice.safeDocumentLabel();
        boolean outsider = recipient != null && recipient.isExternal();
        SignerBriefing.Briefing brief = notice.briefing();

        EmailTemplateHelper.Letter letter = EmailTemplateHelper.Letter.of(
                (reminder ? "ขอเตือนการลงนามในเอกสาร “" : "ขอความอนุเคราะห์ลงนามในเอกสาร “") + doc + "”",
                formal(recipient));
        if (outsider && brief != null) {
            letter.para("ด้วยวิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น อยู่ระหว่างดำเนินการ" + brief.matter()
                    + " ซึ่งท่านได้รับการเสนอชื่อเป็น" + brief.role());
        }
        letter.para((reminder ? "เอกสาร “" + doc + "”" + ofCase(notice) + " ยังรอการลงนามของท่าน"
                : "บัดนี้ถึงลำดับที่ท่านต้องลงนามในเอกสาร “" + doc + "”" + ofCase(notice))
                + " ในฐานะ" + nameOr(notice.roleLabel(), "ผู้ลงนาม")
                + " วิทยาลัยฯ จึงขอความอนุเคราะห์ท่านตรวจสอบเอกสารและลงนามผ่านระบบอิเล็กทรอนิกส์");

        java.util.Map<String, String> rows = caseRows(notice, "ลงนามในฐานะ", notice.roleLabel());
        if (notice.dueAt() != null) {
            long daysLeft = java.time.Duration.between(LocalDateTime.now(), notice.dueAt()).toDays();
            rows.put("กำหนดลงนามภายใน", notice.dueAt().format(DUE_FORMAT)
                    + (daysLeft >= 0 ? " (เหลือประมาณ " + (daysLeft == 0 ? "ไม่ถึง 1 วัน" : daysLeft + " วัน") + ")"
                            : " (เลยกำหนดแล้ว)"));
        }
        rows.put("รหัสตรวจสอบเอกสาร", notice.verificationCode());
        letter.details(rows);

        if (outsider) {
            List<String> steps = new java.util.ArrayList<>();
            if (brief != null) {
                steps.addAll(brief.duties());
            }
            steps.add("กดปุ่ม “เข้าสู่ระบบเพื่อลงนาม” ด้านล่าง แล้วเข้าสู่ระบบด้วย KKU SSO โดยใช้อีเมล "
                    + recipient.getEmail());
            steps.add("ตรวจสอบเอกสาร แล้วลงนาม หากท่านมีใบรับรองอิเล็กทรอนิกส์ (Digital ID) ของหน่วยงาน สามารถใช้ลงนามได้"
                    + " หากไม่มี ระบบจะส่งรหัสยืนยันตัวตนไปยังอีเมลนี้เพื่อใช้ประกอบการลงนาม");
            letter.steps("ขั้นตอนการลงนาม", steps);
        } else {
            letter.para("ท่านสามารถตรวจสอบเอกสารและลงนามได้โดยกดปุ่มด้านล่าง แล้วเข้าสู่ระบบด้วย KKU SSO");
        }
        String body = letter
                .button("เข้าสู่ระบบเพื่อลงนาม", link("/esign/sign/" + notice.stepId()))
                .close("จึงเรียนมาเพื่อโปรดพิจารณาลงนาม และขอขอบคุณมา ณ โอกาสนี้");
        return EmailTemplateHelper.wrapLayout(
                reminder ? "ขอเตือนการลงนามเอกสารอิเล็กทรอนิกส์" : "ขอความอนุเคราะห์ลงนามเอกสารอิเล็กทรอนิกส์",
                outsider ? "ผู้ลงนามภายนอก" : "รอลงนาม", body);
    }

    /** ชื่อขึ้นต้นจดหมายของผู้รับ */
    private static String formal(UserDtls recipient) {
        return EmailTemplateHelper.formalName(recipient);
    }

    /** ต่อท้ายหัวเรื่องอีเมล: " — คำร้องของ ..." ให้รู้ตั้งแต่กล่องจดหมายว่าเป็นเรื่องของใคร */
    private static String aboutWhom(SignatureNotice notice) {
        return notice.caseSummary() == null ? "" : " — " + notice.caseSummary().matter();
    }

    /** ต่อท้ายชื่อเอกสารในประโยค: " ของคำร้อง..." */
    private static String ofCase(SignatureNotice notice) {
        return notice.caseSummary() == null ? "" : " ของ" + notice.caseSummary().matter();
    }

    /** แถวรายละเอียดของคำร้อง ตามด้วยแถวเพิ่มเติม (ถ้ามี) */
    private static java.util.Map<String, String> caseRows(SignatureNotice notice, String extraLabel, String extraValue) {
        java.util.Map<String, String> rows = new java.util.LinkedHashMap<>();
        rows.put("เอกสาร", notice.safeDocumentLabel());
        rows.put("ประเภทคำร้อง", notice.module() == null ? null : notice.module().getThaiLabel());
        if (notice.caseSummary() != null) {
            rows.put("ผู้ยื่นคำร้อง", notice.caseSummary().applicant());
            rows.put("รหัสคำร้อง", notice.caseSummary().requestCode());
        }
        if (extraLabel != null) {
            rows.put(extraLabel, extraValue);
        }
        return rows;
    }

    private static String nameOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * หน้าคำร้องที่ผู้รับคนนี้เปิดได้จริง
     *
     * <p>ผู้รับแจ้งเตือนเรื่องซองไม่ได้มีแต่เจ้าหน้าที่ — ผู้ยื่นส่งเอกสารของตัวเองไปลงนามได้ จึงเป็น
     * ผู้ส่งซอง และผลการตรวจสอบของผู้ลงนามก็แจ้งผู้ยื่นด้วย ลิงก์ {@code /admin/**} ที่ส่งให้ทุกคน
     * เท่าเดิมพาผู้ยื่นไปเจอหน้า 403 เจ้าหน้าที่ ROLE_STAFF ก็เปิด {@code /admin/**} ไม่ได้
     * จึงไปกล่องลงนามแทน
     */
    static String requestLinkFor(UserDtls recipient, SignatureModule module, Long requestId) {
        String role = recipient == null ? null : recipient.getRole();
        if ("ROLE_ADMIN".equals(role)) {
            return module.adminLink(requestId);
        }
        if ("ROLE_USER".equals(role)) {
            return module.userLink(requestId);
        }
        return "/esign/inbox";
    }

    /** ลิงก์เต็มสำหรับใส่ในอีเมล — ลิงก์แบบ "/esign/..." เปิดจากโปรแกรมอีเมลไม่ได้ */
    private String link(String path) {
        if (path == null) {
            return null;
        }
        return path.startsWith("http") ? path : publicBaseUrl.replaceAll("/+$", "") + path;
    }

}
