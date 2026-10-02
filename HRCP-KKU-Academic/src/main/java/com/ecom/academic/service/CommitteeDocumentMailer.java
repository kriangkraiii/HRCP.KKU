package com.ecom.academic.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicDocumentEditLog;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.service.NotificationService;
import com.ecom.util.EmailTemplateHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.internet.MimeMessage;

/**
 * ส่งหนังสือเชิญเป็นกรรมการ (เอกสารที่ 5) ถึงกรรมการแต่ละท่านทางอีเมล เมื่อหนังสือลงนามครบ
 *
 * <p>แต่ละท่านได้อีเมลของตัวเอง แนบหนังสือฉบับที่ส่งถึงท่าน (PDF) และมีปุ่มพาไปหน้าเปิดดูเอกสาร
 * ของผู้ยื่นในระบบ ไฟล์ของผู้ยื่นไม่ได้แนบมาด้วย เพราะรวมกันได้ถึง 75 MB เกินกว่าที่เซิร์ฟเวอร์อีเมล
 * รับได้ ลิงก์ที่ผู้ยื่นแนบไว้ใส่ไว้ในเนื้ออีเมลให้เปิดได้ทันที
 *
 * <p>ส่งเมื่อกรรมการทุกท่านผูกกับบัญชีที่มีอีเมลได้และสร้างหนังสือได้ครบเท่านั้น ถ้าส่งไปบางท่าน
 * แล้วหยุด การกดส่งซ้ำจะทำให้ท่านที่ได้แล้วได้ซ้ำอีก
 */
@Service
public class CommitteeDocumentMailer {

    private static final Logger log = LoggerFactory.getLogger(CommitteeDocumentMailer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** ผลการส่ง — ข้อความภาษาไทยสำหรับเจ้าหน้าที่ */
    public record Outcome(boolean sent, String message) {
    }

    private record Letter(int number, String name, UserDtls account, byte[] file) {
    }

    private final SignatureRequestRepository envelopes;
    private final AcademicRequestRepository requestRepository;
    private final AcademicRequestService requests;
    private final NamedAccountResolver accounts;
    private final SignedDocumentRenderer renderer;
    private final DocumentSnapshotProvider snapshots;
    private final JavaMailSender mailSender;
    private final NotificationService notifications;
    private final String senderEmail;
    private final String publicBaseUrl;

    public CommitteeDocumentMailer(SignatureRequestRepository envelopes,
            AcademicRequestRepository requestRepository,
            AcademicRequestService requests,
            NamedAccountResolver accounts,
            SignedDocumentRenderer renderer,
            DocumentSnapshotProvider snapshots,
            JavaMailSender mailSender,
            NotificationService notifications,
            @Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}") String senderEmail,
            @Value("${app.public-base-url:https://hrd.computing.kku.ac.th}") String publicBaseUrl) {
        this.envelopes = envelopes;
        this.requestRepository = requestRepository;
        this.requests = requests;
        this.accounts = accounts;
        this.renderer = renderer;
        this.snapshots = snapshots;
        this.mailSender = mailSender;
        this.notifications = notifications;
        this.senderEmail = senderEmail;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /** หน้าเปิดดูเอกสารของผู้ยื่นสำหรับกรรมการ */
    public static String filesPath(Long requestId) {
        return "/esign/committee/academic/" + requestId;
    }

    /**
     * ส่งทันทีที่ซองเอกสารที่ 5 ปิด — ส่งไม่ได้ก็แจ้งเจ้าหน้าที่ให้แก้แล้วกดส่งซ้ำจากหน้าคำร้อง
     */
    @Async
    @Transactional
    public void sendWhenSigned(Long envelopeId) {
        Outcome outcome = send(envelopeId, null);
        if (outcome.sent()) {
            return;
        }
        SignatureRequest envelope = envelopes.findById(envelopeId).orElse(null);
        if (envelope != null) {
            notifications.notifyAdmins(null, "ส่งหนังสือเชิญถึงกรรมการทางอีเมลไม่สำเร็จ", outcome.message(),
                    "/admin/academic/request/" + envelope.getRequestId(), NotificationType.SYSTEM, true);
        }
    }

    /**
     * @param sentBy เจ้าหน้าที่ที่กดส่ง หรือ null เมื่อระบบส่งเองหลังลงนามครบ (บันทึกในนามผู้ส่งเวียน)
     */
    @Transactional
    public Outcome send(Long envelopeId, UserDtls sentBy) {
        SignatureRequest envelope = envelopes.findByIdWithSteps(envelopeId).orElse(null);
        if (envelope == null || envelope.getModule() != SignatureModule.ACADEMIC
                || envelope.getDocumentType() != AcademicRequestService.COMMITTEE_COPIES_DOC_TYPE) {
            return new Outcome(false, "ไม่พบหนังสือเชิญเป็นกรรมการ (เอกสารที่ 5)");
        }
        if (envelope.getStatus() != SignatureRequestStatus.COMPLETED) {
            return new Outcome(false, "เอกสารที่ 5 ยังลงนามไม่ครบ");
        }
        AcademicRequest request = requestRepository.findByIdWithApplicant(envelope.getRequestId()).orElse(null);
        if (request == null) {
            return new Outcome(false, "ไม่พบคำร้อง");
        }
        String code = request.getRequestCode() != null ? request.getRequestCode() : String.valueOf(request.getId());

        List<String> problems = new ArrayList<>();
        List<Letter> letters = letters(envelope, request, problems);
        if (!problems.isEmpty()) {
            String message = "ยังไม่ได้ส่งหนังสือเชิญคำร้อง #" + code + " ถึงกรรมการ — " + String.join("; ", problems);
            log.warn(message);
            return new Outcome(false, message);
        }

        List<String> files = new ArrayList<>();
        List<Map.Entry<String, String>> links = new ArrayList<>();
        for (DocumentSnapshotProvider.SignerAttachment a : snapshots.attachmentsOf(SignatureModule.ACADEMIC, request.getId())) {
            if (a.link()) {
                links.add(Map.entry(a.filename(), a.storedPath()));
            } else {
                files.add(a.filename());
            }
        }

        String applicantName = request.getApplicant() != null ? request.getApplicant().getName() : "-";
        String link = filesPath(request.getId());
        UserDtls actor = sentBy != null ? sentBy : envelope.getInitiatedBy();
        List<String> delivered = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Letter letter : letters) {
            String email = letter.account().getEmail();
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
                helper.setTo(email);
                helper.setSubject("ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิประเมินผลการสอน — คำร้อง #" + code + " (" + applicantName + ")");
                helper.setText(EmailTemplateHelper.buildCommitteeInvitationEmail(letter.name(), code, applicantName,
                        publicBaseUrl + link, files, links), true);
                EmailTemplateHelper.attachLogos(helper);
                helper.addAttachment(letterFilename(code, letter), new ByteArrayResource(letter.file()));
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("ส่งหนังสือเชิญคำร้อง #{} ถึง {} ไม่สำเร็จ: {}", code, email, e.toString());
                failed.add(letter.name() + " <" + email + ">");
                continue;
            }
            delivered.add(letter.name() + " <" + email + ">");
            // กรรมการที่เข้าระบบแล้วไม่ได้กลับมาที่ลิงก์ในอีเมล — กระดิ่งแจ้งเตือนพาไปหน้าเดียวกัน
            notifications.sendNotification(letter.account(), actor, "หนังสือเชิญเป็นกรรมการประเมินผลการสอน",
                    "คำร้อง #" + code + " ของ " + applicantName + " — เปิดดูเอกสารประกอบการประเมินผลการสอนของผู้ยื่น",
                    link, NotificationType.COMMITTEE_INVITATION, true);
        }

        if (!delivered.isEmpty()) {
            requests.logDocumentChange(request, AcademicRequestService.COMMITTEE_COPIES_DOC_TYPE,
                    "ส่งหนังสือเชิญถึงกรรมการทางอีเมล: " + String.join(", ", delivered), actor,
                    AcademicDocumentEditLog.EditAction.COMMITTEE_EMAILED);
        }
        if (!failed.isEmpty()) {
            return new Outcome(false, "ส่งหนังสือเชิญคำร้อง #" + code + " ถึง " + String.join(", ", failed)
                    + " ไม่สำเร็จ" + (delivered.isEmpty() ? "" : " (ส่งถึง " + String.join(", ", delivered) + " แล้ว)"));
        }
        return new Outcome(true, "ส่งหนังสือเชิญถึงกรรมการทางอีเมลแล้ว: " + String.join(", ", delivered));
    }

    /**
     * หนังสือแต่ละฉบับกับบัญชีของกรรมการที่ฉบับนั้นส่งถึง — ชื่อจากข้อมูลที่ลงนามไป ไม่ใช่จากฟอร์มที่อาจถูกแก้ทีหลัง
     * ใช้บัญชีที่เลือกไว้ในเอกสารที่ 3 เมื่อชื่อยังตรงกับคำสั่งแต่งตั้ง ไม่อย่างนั้นจับคู่จากชื่อในหนังสือ
     */
    private List<Letter> letters(SignatureRequest envelope, AcademicRequest request, List<String> problems) {
        Map<String, Object> signed;
        try {
            signed = JSON.readValue(envelope.getFrozenJson(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            problems.add("อ่านข้อมูลหนังสือที่ลงนามแล้วไม่ได้");
            return List.of();
        }
        Map<String, String> appointed = requests.appointedCommittee(request);
        List<String> names = new ArrayList<>();
        List<UserDtls> recipients = new ArrayList<>();
        for (int i = 1; i <= AcademicRequestService.COMMITTEE_COPIES; i++) {
            String name = NamedAccountResolver.normalize(signed.get("committee_name_" + i));
            if (name.isEmpty()) {
                problems.add("ฉบับที่ " + i + ": ไม่มีชื่อกรรมการ");
                continue;
            }
            String seat = NamedAccountResolver.COMMITTEE_FIELDS.get(i - 1);
            NamedAccountResolver.Resolution r = name.equals(NamedAccountResolver.normalize(appointed.get(seat)))
                    ? accounts.resolve(appointed, seat)
                    : accounts.resolve(name, null);
            if (r.account() == null) {
                problems.add("ฉบับที่ " + i + " “" + name + "”: " + NamedAccountResolver.describe(r.problem()));
            } else if (r.account().getEmail() == null || r.account().getEmail().isBlank()) {
                problems.add("ฉบับที่ " + i + " “" + name + "”: บัญชีไม่มีอีเมล");
            }
            names.add(name);
            recipients.add(r.account());
        }
        if (!problems.isEmpty()) {
            return List.of();
        }

        List<byte[]> files = renderLetters(envelope, "pdf");
        if (files == null) {
            // แปลง PDF ไม่ได้ (LibreOffice ล่ม) ไม่ใช่เหตุให้กรรมการไม่ได้หนังสือ — ส่งเป็น DOCX แทน
            files = renderLetters(envelope, "docx");
        }
        if (files == null) {
            problems.add("สร้างไฟล์หนังสือไม่สำเร็จ");
            return List.of();
        }
        List<Letter> letters = new ArrayList<>();
        for (int i = 0; i < recipients.size(); i++) {
            letters.add(new Letter(i + 1, names.get(i), recipients.get(i), files.get(i)));
        }
        return letters;
    }

    private List<byte[]> renderLetters(SignatureRequest envelope, String format) {
        try {
            List<byte[]> files = renderer.renderLettersForDownload(envelope, format,
                    AcademicRequestService.COMMITTEE_COPIES);
            return files == null || files.stream().anyMatch(f -> f == null || f.length == 0) ? null : files;
        } catch (Exception e) {
            log.warn("สร้างหนังสือของซอง {} เป็น {} ไม่สำเร็จ: {}", envelope.getId(), format, e.toString());
            return null;
        }
    }

    /** ตัวสร้างหนังสือคืน DOCX เมื่อแปลง PDF ไม่ได้ — ตั้งนามสกุลตามเนื้อไฟล์จริง */
    private static String letterFilename(String code, Letter letter) {
        boolean docx = letter.file().length > 1 && letter.file()[0] == 'P' && letter.file()[1] == 'K';
        return code + "_หนังสือเชิญกรรมการ_ฉบับที่_" + letter.number() + (docx ? ".docx" : ".pdf");
    }
}
