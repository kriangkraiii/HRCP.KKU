package com.ecom.academic.service;

import java.util.List;
import java.util.Map;

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

    @org.springframework.beans.factory.annotation.Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}")
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

    @org.springframework.beans.factory.annotation.Value("${app.public-base-url:https://hrd.computing.kku.ac.th}")
    private String publicBaseUrl;

    @Async
    public void sendStatusChangeEmail(Long requestId, RequestStatus oldStatus, RequestStatus newStatus) {
        sendStatusChangeEmail(requestId, oldStatus, newStatus, null);
    }

    /**
     * หนังสือแจ้งผล (เอกสารที่ 9) ออกเลขที่และวันที่แล้ว — แจ้งผู้ยื่นในระบบและทางอีเมล พร้อมลิงก์เปิดดูหนังสือ
     *
     * @param letter ข้อมูลเอกสารที่ 9 ที่บันทึกแล้ว (เลขที่ วันที่ ผลประเมิน วันหมดอายุ)
     */
    @Async
    public void sendResultLetterEmail(Long requestId, Map<String, String> letter) {
        try {
            AcademicRequest request = reload(requestId);
            if (request == null || request.getApplicant() == null) {
                return;
            }
            String code = request.getRequestCode() != null ? request.getRequestCode() : String.valueOf(request.getId());
            String link = "/user/academic/request/" + request.getId();
            notificationService.sendNotification(request.getApplicant(), null,
                    "หนังสือแจ้งผลการประเมินผลการสอนออกแล้ว",
                    "หนังสือแจ้งผลการประเมินผลการสอนของคำร้อง #" + code + " ออกแล้ว กดเพื่อเปิดดูหนังสือ",
                    link, com.ecom.model.NotificationType.ACADEMIC_STATUS_UPDATE, true);

            if (Boolean.FALSE.equals(request.getApplicant().getIsEnable())) {
                return;
            }
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty()
                    || com.ecom.util.EmailTemplateHelper.isTestEmail(applicantEmail)) {
                return;
            }
            String body = com.ecom.util.EmailTemplateHelper.buildResultLetterEmail(
                    request.getApplicant().getName(), code,
                    letter.get("memo_no"), letter.get("date"), letter.get("result_level"),
                    letter.get("expiration_date"), publicBaseUrl + link);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail), com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(applicantEmail);
            helper.setSubject("หนังสือแจ้งผลการประเมินผลการสอน (#" + code + ") ออกแล้ว");
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Result letter email sending failed: " + e.getMessage());
        }
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
                        || newStatus.carriesAFailedResult();
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
                    ? "คำร้องขอรับการประเมินผลการสอน (รหัส " + request.getId() + ") ถูกส่งคืนเพื่อแก้ไข"
                    : "แจ้งความคืบหน้าคำร้องขอรับการประเมินผลการสอน (รหัส " + request.getId() + "): "
                            + newStatus.getThaiLabel();
            String body = buildEmailBody(request, oldStatus, newStatus, note);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail), com.ecom.util.EmailTemplateHelper.SENDER_NAME);
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

    /**
     * ข้อ 13 → 14 — ผู้ยื่นส่งเอกสารที่แก้ไขแล้ว เจ้าหน้าที่ต้องตรวจแล้วส่งต่อคณะอนุกรรมการ
     * และนัดประชุมรอบใหม่ — แจ้งในระบบทุกคน และอีเมลถึงผู้ที่เปิดรับอีเมล
     */
    @Async
    public void sendRevisionSubmittedToAdmins(Long requestId) {
        try {
            AcademicRequest request = reload(requestId);
            if (request == null || request.getApplicant() == null) {
                return;
            }
            String applicantName = com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant());
            String adminLink = "/admin/academic/request/" + request.getId();
            notificationService.notifyAdmins(request.getApplicant(),
                    "ผู้ยื่นส่งเอกสารประเมินการสอนฉบับแก้ไขแล้ว",
                    applicantName + " ส่งเอกสารฉบับแก้ไขของคำร้อง #" + request.getId()
                            + " แล้ว กรุณาตรวจและส่งต่อคณะอนุกรรมการ พร้อมนัดประชุมรอบใหม่",
                    adminLink, com.ecom.model.NotificationType.REVISION_SUBMITTED, true);

            for (UserDtls admin : userRepository.findByRole("ROLE_ADMIN")) {
                if (Boolean.FALSE.equals(admin.getIsEnable())
                        || !Boolean.TRUE.equals(admin.getEmailNotificationEnabled())
                        || com.ecom.util.EmailTemplateHelper.isTestEmail(admin.getEmail())) {
                    continue;
                }
                String body = com.ecom.util.EmailTemplateHelper.Letter
                        .of("ผู้ยื่นส่งเอกสารประเมินการสอนฉบับแก้ไขแล้ว",
                                com.ecom.util.EmailTemplateHelper.formalName(admin))
                        .para(applicantName + " ได้ส่งเอกสารประเมินการสอนที่แก้ไขตามข้อเสนอแนะของคณะอนุกรรมการ"
                                + " สำหรับคำร้องรหัส " + request.getId() + " เรียบร้อยแล้ว")
                        .steps("สิ่งที่ต้องดำเนินการ", List.of(
                                "ตรวจเอกสารฉบับแก้ไขในหน้ารายละเอียดคำร้อง",
                                "ส่งเอกสารฉบับแก้ไขให้คณะอนุกรรมการประเมินการสอนพิจารณา",
                                "นัดหมายวันประชุมคณะอนุกรรมการรอบใหม่ในระบบ"))
                        .button("เปิดคำร้องในระบบ", publicBaseUrl.replaceAll("/+$", "") + adminLink)
                        .close("จึงเรียนมาเพื่อโปรดดำเนินการ");

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail),
                        com.ecom.util.EmailTemplateHelper.SENDER_NAME);
                helper.setTo(admin.getEmail());
                helper.setSubject("ผู้ยื่นส่งเอกสารฉบับแก้ไขแล้ว: คำร้องขอรับการประเมินผลการสอน (รหัส "
                        + request.getId() + ") จาก " + applicantName);
                helper.setText(com.ecom.util.EmailTemplateHelper.wrapLayout(
                        "ผู้ยื่นส่งเอกสารฉบับแก้ไขแล้ว", "ต้องดำเนินการ", body), true);
                com.ecom.util.EmailTemplateHelper.attachLogos(helper);
                mailSender.send(message);
            }
        } catch (Exception e) {
            System.err.println("Revision-submitted admin notification failed: " + e.getMessage());
        }
    }

    /**
     * ผู้ยื่นขอทบทวนผลการประเมินที่ไม่ผ่าน (1669/2569 ข้อ 10.3) — เจ้าหน้าที่ต้องเสนอหัวหน้าส่วนงานพิจารณา
     * แจ้งในระบบทุกคน และอีเมลถึงผู้ที่เปิดรับอีเมล
     */
    @Async
    public void sendAppealSubmittedToAdmins(Long requestId) {
        try {
            AcademicRequest request = reload(requestId);
            if (request == null || request.getApplicant() == null) {
                return;
            }
            String applicantName = com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant());
            String adminLink = "/admin/academic/request/" + request.getId();
            notificationService.notifyAdmins(request.getApplicant(),
                    "ผู้ยื่นขอทบทวนผลการประเมินผลการสอน",
                    applicantName + " ขอทบทวนผลการประเมินของคำร้อง #" + request.getId()
                            + " กรุณาเสนอหัวหน้าส่วนงานพิจารณา",
                    adminLink, com.ecom.model.NotificationType.ACADEMIC_STATUS_UPDATE, true);

            for (UserDtls admin : userRepository.findByRole("ROLE_ADMIN")) {
                if (Boolean.FALSE.equals(admin.getIsEnable())
                        || !Boolean.TRUE.equals(admin.getEmailNotificationEnabled())
                        || com.ecom.util.EmailTemplateHelper.isTestEmail(admin.getEmail())) {
                    continue;
                }
                String body = com.ecom.util.EmailTemplateHelper.Letter
                        .of("ผู้ยื่นขอทบทวนผลการประเมินผลการสอน",
                                com.ecom.util.EmailTemplateHelper.formalName(admin))
                        .para(applicantName + " ขอทบทวนผลการประเมินผลการสอนที่ไม่ผ่าน"
                                + " สำหรับคำร้องรหัส " + request.getId()
                                + " ตามประกาศมหาวิทยาลัยขอนแก่น ฉบับที่ 1669/2569 ข้อ 10.3")
                        .steps("สิ่งที่ต้องดำเนินการ", List.of(
                                "อ่านเหตุผลที่ขอทบทวนในประวัติสถานะของคำร้อง",
                                "เสนอหัวหน้าส่วนงานพิจารณา",
                                "รับทบทวน: นัดประชุมคณะอนุกรรมการรอบใหม่ / ยืนผลเดิม: เปลี่ยนสถานะเป็น \"แจ้งผล - ไม่ผ่าน\" พร้อมเหตุผล"))
                        .button("เปิดคำร้องในระบบ", publicBaseUrl.replaceAll("/+$", "") + adminLink)
                        .close("จึงเรียนมาเพื่อโปรดดำเนินการ");

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail),
                        com.ecom.util.EmailTemplateHelper.SENDER_NAME);
                helper.setTo(admin.getEmail());
                helper.setSubject("ขอทบทวนผลการประเมิน: คำร้องขอรับการประเมินผลการสอน (รหัส "
                        + request.getId() + ") จาก " + applicantName);
                helper.setText(com.ecom.util.EmailTemplateHelper.wrapLayout(
                        "ผู้ยื่นขอทบทวนผลการประเมิน", "ต้องดำเนินการ", body), true);
                com.ecom.util.EmailTemplateHelper.attachLogos(helper);
                mailSender.send(message);
            }
        } catch (Exception e) {
            System.err.println("Appeal-submitted admin notification failed: " + e.getMessage());
        }
    }

    private void sendAdminNotification(UserDtls admin, AcademicRequest request) {
        try {
            if (admin == null || com.ecom.util.EmailTemplateHelper.isTestEmail(admin.getEmail())) {
                return;
            }
            String applicantName = com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant());
            String subject = "คำร้องขอรับการประเมินผลการสอนใหม่ (รหัส " + request.getId() + ") จาก " + applicantName;
            String body = com.ecom.util.EmailTemplateHelper.buildAdminNewRequestEmail(
                    com.ecom.util.EmailTemplateHelper.formalName(admin),
                    applicantName,
                    request.getApplicant().getEmail(),
                    "คำร้องขอรับการประเมินผลการสอน",
                    String.valueOf(request.getId()),
                    null);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail), com.ecom.util.EmailTemplateHelper.SENDER_NAME);
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
            case SUBCOMMITTEE_FAIL, COLLEGE_ENDORSED_FAIL, COMPLETED_FAIL -> "#dc2626";
            case APPEAL_SUBMITTED -> "#6a1b9a";
            case RECEIVED -> "#2563eb";
            default -> newStatus.getColor() != null ? newStatus.getColor() : "#1e3a8a";
        };

        String extraDetails = "";
        // วันประชุมและสถานที่เป็นความลับทางราชการ ผู้ยื่นไม่ได้เข้าประชุมด้วย
        // อีเมลจึงบอกแค่ว่าคำร้องเดินมาถึงขั้นนี้แล้ว ไม่แนบรายละเอียดการนัดหมาย
        if (isReturn(oldStatus, newStatus)) {
            extraDetails = com.ecom.util.EmailTemplateHelper.noteHtml("warn", "เหตุผลที่ส่งคืน",
                    note != null && !note.isBlank() ? note.trim() : "-");
        }

        return com.ecom.util.EmailTemplateHelper.buildStatusChangeEmail(
                com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant()),
                "คำร้องขอรับการประเมินผลการสอน",
                String.valueOf(request.getId()),
                oldStatus != null ? oldStatus.getThaiLabel() : "-",
                newStatus.getThaiLabel(),
                statusColor,
                meaningOf(oldStatus, newStatus),
                extraDetails);
    }

    /** สถานะนี้หมายถึงอะไรสำหรับผู้ยื่น และขั้นต่อไปคืออะไร */
    static String meaningOf(RequestStatus oldStatus, RequestStatus newStatus) {
        if (isReturn(oldStatus, newStatus)) {
            return "คำร้องของท่านถูกส่งคืนเป็นแบบร่างด้วยเหตุผลข้างล่างนี้ ขอให้ท่านแก้ไขเอกสาร ลงนามอิเล็กทรอนิกส์ใหม่"
                    + " แล้วยื่นคำร้องอีกครั้ง";
        }
        return switch (newStatus) {
            case DRAFT -> "คำร้องของท่านอยู่ในสถานะแบบร่าง ท่านสามารถแก้ไขเอกสารและยื่นคำร้องได้เมื่อพร้อม";
            case RECEIVED -> "วิทยาลัยฯ ได้รับคำร้องของท่านแล้ว เจ้าหน้าที่จะตรวจสอบเอกสาร"
                    + " และเสนอแต่งตั้งคณะอนุกรรมการประเมินผลการสอนต่อไป";
            case SUB_COMMITTEE_APPOINTED -> "คณบดีได้ลงนามคำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอนแล้ว"
                    + " ขั้นต่อไปวิทยาลัยฯ จะนัดหมายคณะอนุกรรมการเพื่อประเมินผลการสอนของท่าน";
            case MEETING_SCHEDULED -> "วิทยาลัยฯ ได้นัดหมายคณะอนุกรรมการเพื่อประเมินผลการสอนของท่านแล้ว"
                    + " ผลการประเมินจะแจ้งให้ท่านทราบภายหลังการประเมิน";
            case COMPLETED_PASS -> "คณะอนุกรรมการได้ประเมินแล้ว ผลการสอนของท่านผ่านเกณฑ์"
                    + " ขั้นต่อไปวิทยาลัยฯ จะเสนอคณะกรรมการประจำวิทยาลัยฯ เพื่อรับรองผลการประเมิน";
            case COMPLETED_REVISE -> "คณะอนุกรรมการได้ประเมินแล้ว และขอให้ท่านปรับปรุงเอกสารตามข้อเสนอแนะ"
                    + " กรุณาตรวจสอบข้อเสนอแนะในระบบ แก้ไข แล้วส่งเอกสารฉบับแก้ไข";
            case REVISION_SUBMITTED -> "วิทยาลัยฯ ได้รับเอกสารฉบับแก้ไขของท่านแล้ว และจะเสนอคณะอนุกรรมการพิจารณาอีกครั้ง";
            case COLLEGE_ENDORSED -> "คณะกรรมการประจำวิทยาลัยฯ ได้รับรองผลการประเมินผลการสอนของท่านแล้ว"
                    + " วิทยาลัยฯ จะจัดทำบันทึกข้อความแจ้งผลการประเมินอย่างเป็นทางการต่อไป";
            case COMPLETED -> "การประเมินผลการสอนของท่านเสร็จสิ้นแล้ว ท่านสามารถดาวน์โหลดบันทึกข้อความแจ้งผลการประเมินได้ในระบบ"
                    + " และใช้ผลการประเมินนี้ประกอบการขอกำหนดตำแหน่งทางวิชาการได้จนกว่าผลการประเมินจะหมดอายุ";
            case SUBCOMMITTEE_FAIL -> "คณะอนุกรรมการได้ประเมินแล้ว ผลการสอนของท่านยังไม่ผ่านเกณฑ์ของตำแหน่งที่ขอ"
                    + " ขั้นต่อไปวิทยาลัยฯ จะเสนอคณะกรรมการประจำวิทยาลัยฯ เพื่อรับรองผลการประเมิน"
                    + " แล้วแจ้งผลอย่างเป็นทางการ";
            case COLLEGE_ENDORSED_FAIL -> "คณะกรรมการประจำวิทยาลัยฯ ได้รับรองผลการประเมินผลการสอนของท่านแล้ว"
                    + " วิทยาลัยฯ จะจัดทำบันทึกข้อความแจ้งผลการประเมินอย่างเป็นทางการต่อไป";
            case COMPLETED_FAIL -> oldStatus == RequestStatus.APPEAL_SUBMITTED
                    ? "หัวหน้าส่วนงานได้พิจารณาคำขอทบทวนแล้ว และยืนผลการประเมินเดิม"
                            + " เหตุผลดูได้ที่ประวัติสถานะในหน้าคำร้อง"
                    : "วิทยาลัยฯ แจ้งผลการประเมินอย่างเป็นทางการแล้ว ผลการสอนของท่านยังไม่ผ่านเกณฑ์ของตำแหน่งที่ขอ"
                            + " หากท่านเห็นว่าไม่ได้รับความเป็นธรรม ขอทบทวนต่อหัวหน้าส่วนงานได้ที่หน้าคำร้องในระบบ"
                            + " ภายใน 30 วันทำการนับจากวันที่ทราบผล (ประกาศ มข. ฉบับที่ 1669/2569 ข้อ 10.3)";
            case APPEAL_SUBMITTED -> "วิทยาลัยฯ ได้รับคำขอทบทวนผลการประเมินของท่านแล้ว"
                    + " หัวหน้าส่วนงานจะพิจารณาและแจ้งผลให้ท่านทราบ";
        };
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

            String subject = "ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน และการแก้ไขเอกสาร (รหัส " + request.getId() + ")";
            String body = com.ecom.util.EmailTemplateHelper.buildSuggestionEmail(
                    com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant()),
                    String.valueOf(request.getId()),
                    suggestionsText);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail), com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Suggestion email sending failed: " + e.getMessage());
        }
    }

    /**
     * ข้อเสนอแนะแจ้งเพื่อทราบ (เอกสารที่ 6 ดำเนินการต่อโดยไม่ส่งกลับแก้ไข) — แจ้งในระบบและทางอีเมล
     * สถานะคำร้องไม่เปลี่ยน
     */
    @Async
    public void sendSuggestionNoticeEmail(Long requestId, String suggestionsText) {
        try {
            AcademicRequest request = reload(requestId);
            if (request == null || request.getApplicant() == null) {
                return;
            }
            notificationService.sendNotification(request.getApplicant(), null,
                    "ข้อเสนอแนะจากคณะอนุกรรมการ",
                    "คำร้องขอประเมินผลการสอน (#" + request.getId() + ") — " + suggestionsText
                            + " (แจ้งเพื่อทราบ ไม่ต้องแก้ไขเอกสาร)",
                    "/user/academic/request/" + request.getId(),
                    com.ecom.model.NotificationType.ACADEMIC_STATUS_UPDATE, false);

            if (Boolean.FALSE.equals(request.getApplicant().getIsEnable())) {
                return;
            }
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty() || com.ecom.util.EmailTemplateHelper.isTestEmail(applicantEmail))
                return;

            String subject = "ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน (รหัส " + request.getId() + ") แจ้งเพื่อทราบ";
            String body = com.ecom.util.EmailTemplateHelper.buildSuggestionNoticeEmail(
                    com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant()),
                    String.valueOf(request.getId()),
                    suggestionsText);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail), com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(applicantEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Suggestion notice email sending failed: " + e.getMessage());
        }
    }
}
