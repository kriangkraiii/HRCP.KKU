package com.ecom.academic.service;

import java.util.List;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class PositionEmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final com.ecom.service.NotificationService notificationService;
    private final PositionRequestRepository requestRepository;

    @org.springframework.beans.factory.annotation.Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}")
    private String senderEmail;

    public PositionEmailService(JavaMailSender mailSender, UserRepository userRepository,
            com.ecom.service.NotificationService notificationService,
            PositionRequestRepository requestRepository) {
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
    private PositionRequest reload(Long requestId) {
        if (requestId == null) {
            return null;
        }
        return requestRepository.findByIdWithApplicant(requestId).orElse(null);
    }

    @Async
    public void sendStatusChangeEmail(Long requestId,
            PositionRequestStatus oldStatus, PositionRequestStatus newStatus) {
        sendStatusChangeEmail(requestId, oldStatus, newStatus, null);
    }

    /**
     * @param note หมายเหตุที่แอดมินใส่ตอนเปลี่ยนสถานะ — เป็นเหตุผลเมื่อส่งกลับให้แก้ไข ผู้ยื่นต้องเห็น
     *             ไม่งั้นรู้แค่ว่าถูกส่งกลับแต่ไม่รู้ว่าต้องแก้อะไร (เหมือนเฟส 1)
     */
    @Async
    public void sendStatusChangeEmail(Long requestId,
            PositionRequestStatus oldStatus, PositionRequestStatus newStatus, String note) {
        try {
            PositionRequest request = reload(requestId);
            if (request == null) {
                return;
            }
            // 1. Create in-app notification for applicant
            if (request != null && request.getApplicant() != null) {
                boolean isImportant = newStatus == PositionRequestStatus.REVISION_REQUESTED
                        || newStatus == PositionRequestStatus.SCREENING_APPROVED
                        || newStatus == PositionRequestStatus.COLLEGE_APPROVED
                        || newStatus == PositionRequestStatus.SENT_TO_HR
                        || newStatus == PositionRequestStatus.COUNCIL_APPROVED
                        || newStatus == PositionRequestStatus.COUNCIL_REJECTED;
                String notifTitle = "อัปเดตสถานะขอตำแหน่ง: " + newStatus.getThaiLabel();
                String notifMsg = "คำร้องขอตำแหน่งทางวิชาการ (" + request.getRequestCode() + ") ของท่าน ได้รับการปรับสถานะเป็น " + newStatus.getThaiLabel()
                        + (hasNote(note) ? " " + noteLabel(newStatus) + " " + note.trim() : "");
                String notifLink = "/user/position/request/" + request.getId();
                notificationService.sendNotification(request.getApplicant(), null, notifTitle, notifMsg, notifLink, com.ecom.model.NotificationType.POSITION_STATUS_UPDATE, isImportant);
            }

            // 2. Send email notification
            if (request.getApplicant() == null || Boolean.FALSE.equals(request.getApplicant().getIsEnable())) {
                return;
            }
            String applicantEmail = request.getApplicant().getEmail();
            if (applicantEmail == null || applicantEmail.isEmpty() || com.ecom.util.EmailTemplateHelper.isTestEmail(applicantEmail))
                return;

            String subject = "แจ้งความคืบหน้าคำขอกำหนดตำแหน่งทางวิชาการ (รหัส " + request.getRequestCode() + "): "
                    + newStatus.getThaiLabel();
            String body = buildStatusEmailBody(request, oldStatus, newStatus, note);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail), com.ecom.util.EmailTemplateHelper.SENDER_NAME);
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
    public void sendNewRequestNotificationToAdmins(Long requestId) {
        try {
            PositionRequest request = reload(requestId);
            if (request == null) {
                return;
            }
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
                if (Boolean.FALSE.equals(admin.getIsEnable())) {
                    continue;
                }
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
            if (admin == null || com.ecom.util.EmailTemplateHelper.isTestEmail(admin.getEmail())) {
                return;
            }
            String applicantName = com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant());
            String subject = "คำขอกำหนดตำแหน่งทางวิชาการใหม่ (รหัส " + request.getRequestCode() + ") จาก " + applicantName;
            String body = com.ecom.util.EmailTemplateHelper.buildAdminNewRequestEmail(
                    com.ecom.util.EmailTemplateHelper.formalName(admin),
                    applicantName,
                    request.getApplicant().getEmail(),
                    "คำร้องขอตำแหน่งทางวิชาการ",
                    request.getRequestCode(),
                    request.getTargetPosition());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(com.ecom.util.EmailTemplateHelper.resolveSenderEmail(senderEmail), com.ecom.util.EmailTemplateHelper.SENDER_NAME);
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

    private static boolean hasNote(String note) {
        return note != null && !note.isBlank();
    }

    private static String noteLabel(PositionRequestStatus newStatus) {
        return newStatus == PositionRequestStatus.REVISION_REQUESTED
                ? "เหตุผลที่ส่งกลับให้แก้ไข:"
                : "หมายเหตุจากเจ้าหน้าที่:";
    }

    private String buildStatusEmailBody(PositionRequest request,
            PositionRequestStatus oldStatus, PositionRequestStatus newStatus, String note) {
        String statusColor = switch (newStatus) {
            case SCREENING_APPROVED, COLLEGE_APPROVED, COUNCIL_APPROVED -> "#16a34a";
            case COUNCIL_REJECTED -> "#dc2626";
            case REVISION_REQUESTED -> "#d97706";
            case SENT_TO_HR -> "#0d9488";
            case DRAFT -> "#64748b";
            default -> newStatus.getColor() != null ? newStatus.getColor() : "#1e3a8a";
        };

        String extraDetails = "";
        if (request.getTargetPosition() != null && !request.getTargetPosition().isBlank()) {
            extraDetails = com.ecom.util.EmailTemplateHelper.noteHtml("info", null,
                    "ตำแหน่งทางวิชาการที่ขอ: " + request.getTargetPosition().strip());
        }
        if (hasNote(note)) {
            extraDetails += com.ecom.util.EmailTemplateHelper.noteHtml("warn",
                    noteLabel(newStatus).replaceAll(":$", ""), note.trim());
        }

        return com.ecom.util.EmailTemplateHelper.buildStatusChangeEmail(
                com.ecom.util.EmailTemplateHelper.formalName(request.getApplicant()),
                "คำขอกำหนดตำแหน่งทางวิชาการ",
                request.getRequestCode(),
                oldStatus != null ? oldStatus.getThaiLabel() : "-",
                newStatus.getThaiLabel(),
                statusColor,
                meaningOf(newStatus),
                extraDetails);
    }

    /** สถานะนี้หมายถึงอะไรสำหรับผู้ขอ และขั้นต่อไปคืออะไร */
    static String meaningOf(PositionRequestStatus newStatus) {
        return switch (newStatus) {
            case DRAFT -> "คำขอของท่านอยู่ในสถานะแบบร่าง ท่านสามารถแก้ไขเอกสารและยื่นคำขอได้เมื่อพร้อม";
            case DOCUMENT_RECEIVED -> "วิทยาลัยฯ ได้รับคำขอกำหนดตำแหน่งทางวิชาการของท่านแล้ว"
                    + " เจ้าหน้าที่จะตรวจสอบความถูกต้องและครบถ้วนของเอกสารต่อไป";
            case DOCUMENT_VERIFICATION -> "เจ้าหน้าที่อยู่ระหว่างตรวจสอบความถูกต้องและครบถ้วนของเอกสาร"
                    + " หากต้องแก้ไขเอกสาร วิทยาลัยฯ จะแจ้งให้ท่านทราบ";
            case SCREENING_COMMITTEE -> "คำขอของท่านได้รับการเสนอเข้าวาระการประชุมคณะกรรมการกลั่นกรองฯ แล้ว"
                    + " ผลการพิจารณาจะแจ้งให้ท่านทราบภายหลังการประชุม";
            case REVISION_REQUESTED -> "ขอให้ท่านแก้ไขเอกสารตามรายละเอียดข้างล่างนี้ แก้ไขในระบบ และลงนามใหม่ในเอกสารที่แก้ไข"
                    + " แล้วส่งกลับมาให้วิทยาลัยฯ ดำเนินการต่อ";
            case SCREENING_APPROVED -> "ที่ประชุมคณะกรรมการกลั่นกรองฯ ได้รับรองมติแล้ว"
                    + " ขั้นต่อไปวิทยาลัยฯ จะเสนอวาระต่อคณะกรรมการประจำวิทยาลัยฯ";
            case COLLEGE_COMMITTEE -> "คำขอของท่านได้รับการเสนอเข้าวาระการประชุมคณะกรรมการประจำวิทยาลัยฯ แล้ว"
                    + " ผลการพิจารณาจะแจ้งให้ท่านทราบภายหลังการประชุม";
            case COLLEGE_APPROVED -> "คณะกรรมการประจำวิทยาลัยฯ ได้รับรองมติแล้ว"
                    + " ขั้นต่อไปวิทยาลัยฯ จะส่งเรื่องไปยังกองทรัพยากรบุคคล มหาวิทยาลัยขอนแก่น";
            case SENT_TO_HR -> "วิทยาลัยฯ ได้ส่งคำขอของท่านไปยังกองทรัพยากรบุคคล มหาวิทยาลัยขอนแก่น แล้ว"
                    + " ขั้นตอนต่อจากนี้ดำเนินการตามกระบวนการของมหาวิทยาลัย";
            case COUNCIL_APPROVED -> "สภามหาวิทยาลัยขอนแก่นมีมติกำหนดตำแหน่งทางวิชาการให้ท่านแล้ว";
            case COUNCIL_REJECTED -> "สภามหาวิทยาลัยขอนแก่นมีมติไม่กำหนดตำแหน่งทางวิชาการ"
                    + " หากท่านประสงค์ขอทบทวนผลการพิจารณา ขอได้ไม่เกิน 2 ครั้ง โดยแสดงเหตุผลทางวิชาการ"
                    + " ยื่นที่วิทยาลัยฯ ภายใน 90 วันนับตั้งแต่วันที่รับทราบมติ (ข้อบังคับ มข. พ.ศ. 2569 ข้อ 35)";
            case APPEAL_SUBMITTED -> "วิทยาลัยฯ ได้ยื่นขอทบทวนผลการพิจารณาของท่านแล้ว"
                    + " ผลการทบทวนจะแจ้งให้ท่านทราบเมื่อสภามหาวิทยาลัยมีมติ";
        };
    }
}
