package com.ecom.academic.service;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.config.KkuEmailDomains;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.sso.ExternalLinkService;
import com.ecom.util.EmailTemplateHelper;

/**
 * ผู้ลงนามจากนอกวิทยาลัย — สร้างบัญชี {@link UserDtls#ROLE_EXTERNAL} จากอีเมล แล้วส่งอีเมลเชิญ
 *
 * <p>ผู้ลงนามภายนอกมีสองกลุ่ม ({@link KkuEmailDomains}) และลงนามผ่านหน้าลงนามเดิมทั้งคู่:
 * <ul>
 * <li>บุคลากร มข. ต่างคณะ (อีเมลโดเมน มข.) — เข้าด้วย KKU SSO ({@code SsoAccessPolicy} รับบัญชีนี้)
 * และต้องลงนามด้วย .p12 ของ มข.
 * <li>คนนอก มข. — เข้าด้วยลิงก์ในอีเมล ({@link ExternalLinkService}) มี .p12 ของหน่วยงานตัวเองก็ใช้ได้
 * ไม่มีก็ยืนยันตัวตนทางอีเมลแล้วระบบประทับรับรองแทน ({@code SystemSealService})
 * </ul>
 * ดู docs/PLAN-external-signer.md และ docs/PLAN-external-magic-link.md
 *
 * <p>อีเมลที่มีบัญชีอยู่แล้วไม่สร้างซ้ำ — คืนบัญชีเดิม (คนใน มข. ที่พิมพ์อีเมลเข้ามาก็คือคนในระบบ)
 */
@Service
public class ExternalSignerService {

    private static final Logger log = LoggerFactory.getLogger(ExternalSignerService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** ข้อมูลผู้ลงนามภายนอกที่ผู้เชิญกรอก */
    public record Invite(String title, String firstName, String lastName, String email, String affiliation) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String publicBaseUrl;
    private final KkuEmailDomains kkuDomains;
    private final ExternalLinkService externalLinks;

    public ExternalSignerService(UserRepository users, PasswordEncoder passwordEncoder, JavaMailSender mailSender,
            @Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}") String senderEmail,
            @Value("${app.public-base-url:https://hrd.computing.kku.ac.th}") String publicBaseUrl,
            KkuEmailDomains kkuDomains, ExternalLinkService externalLinks) {
        this.kkuDomains = kkuDomains;
        this.externalLinks = externalLinks;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /**
     * @return บัญชีของผู้ลงนาม — บัญชีเดิมถ้าอีเมลนี้มีอยู่แล้ว หรือบัญชีภายนอกที่สร้างใหม่
     * @throws IllegalArgumentException เมื่อข้อมูลไม่ครบหรืออีเมลไม่ถูกต้อง (ข้อความภาษาไทยสำหรับผู้ใช้)
     */
    @Transactional
    public UserDtls invite(Invite in, UserDtls invitedBy) {
        return invite(in, invitedBy, null);
    }

    /**
     * @param briefing เรื่องที่ผู้ลงนามถูกเชิญมา (คำร้องของใคร ฐานะอะไร ต้องทำอะไร) หรือ null เมื่อไม่ทราบ
     */
    @Transactional
    public UserDtls invite(Invite in, UserDtls invitedBy, SignerBriefing.Briefing briefing) {
        String email = in.email() == null ? "" : in.email().strip().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("อีเมลไม่ถูกต้อง");
        }
        UserDtls existing = users.findByEmailIgnoreCase(email);
        if (existing != null) {
            return existing;
        }
        String first = strip(in.firstName());
        String last = strip(in.lastName());
        if (first.isEmpty() || last.isEmpty()) {
            throw new IllegalArgumentException("กรุณากรอกชื่อและนามสกุลของผู้ลงนาม");
        }
        // คำนำหน้าพิมพ์ทั้งในเอกสารและในหนังสือที่ส่งถึงผู้ลงนาม — "เรียน วิภา ภายนอก" ไม่เป็นทางการ
        if (strip(in.title()).isEmpty()) {
            throw new IllegalArgumentException("กรุณากรอกคำนำหน้าหรือตำแหน่งทางวิชาการของผู้ลงนาม เช่น รศ.ดร., นาย, นาง");
        }

        UserDtls user = new UserDtls();
        user.setEmail(email);
        user.setRole(UserDtls.ROLE_EXTERNAL);
        user.setTitle(strip(in.title()));
        user.setFirstName(first);
        user.setLastName(last);
        user.setName(first + " " + last);
        user.setAffiliation(strip(in.affiliation()));
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);
        // เข้าระบบด้วย KKU SSO เท่านั้น — รหัสผ่านสุ่มที่ไม่มีใครรู้ เพื่อให้คอลัมน์ไม่ว่าง
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        UserDtls saved = users.save(user);
        log.info("External signer {} invited by {}", email, invitedBy != null ? invitedBy.getEmail() : "-");
        sendInvitation(saved, invitedBy, briefing);
        return saved;
    }

    private void sendInvitation(UserDtls user, UserDtls invitedBy, SignerBriefing.Briefing briefing) {
        if (EmailTemplateHelper.isTestEmail(user.getEmail())) {
            log.info("Test account — external signer invitation not mailed to {}", user.getEmail());
            return;
        }
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
            helper.setTo(user.getEmail());
            helper.setSubject(briefing != null
                    ? "ขอเรียนเชิญเป็น" + briefing.role() + " — " + briefing.matter()
                    : "แจ้งการเป็นผู้ลงนามเอกสารอิเล็กทรอนิกส์ - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");
            helper.setText(EmailTemplateHelper.wrapLayout(
                    briefing != null ? "ขอเรียนเชิญเป็นผู้ลงนามเอกสาร" : "แจ้งการเป็นผู้ลงนามเอกสารอิเล็กทรอนิกส์",
                    "ผู้ลงนามภายนอก", invitationBody(user, invitedBy, briefing)), true);
            EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            // บัญชีถูกสร้างแล้ว อีเมลแจ้งให้ลงนามจะตามมาเองตอนถึงคิว — อีเมลเชิญหายไม่ควรล้มการเลือกผู้ลงนาม
            log.error("External signer invitation to {} failed: {}", user.getEmail(), e.toString());
        }
    }

    /**
     * หนังสือเชิญ — ผู้รับไม่รู้จักระบบนี้มาก่อน จึงต้องบอกครบในฉบับเดียว: เรื่องอะไร ของใคร ใครเสนอชื่อ
     * ท่านอยู่ในฐานะอะไร ต้องทำอะไรเมื่อใด เข้าระบบและลงนามอย่างไร และถามใครได้
     */
    String invitationBody(UserDtls user, UserDtls invitedBy, SignerBriefing.Briefing briefing) {
        String inviter = invitedBy != null ? EmailTemplateHelper.formalName(invitedBy) : "เจ้าหน้าที่ของวิทยาลัยฯ";
        EmailTemplateHelper.Letter letter;
        if (briefing != null) {
            letter = EmailTemplateHelper.Letter.of("ขอเรียนเชิญเป็น" + briefing.role(), EmailTemplateHelper.formalName(user))
                    .para("ด้วยวิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น อยู่ระหว่างดำเนินการ" + briefing.matter()
                            + " ในการนี้ " + inviter + " ได้เสนอชื่อท่านเป็น" + briefing.role()
                            + " วิทยาลัยฯ จึงขอเรียนเชิญท่าน และขอแจ้งรายละเอียด ดังนี้");
            java.util.Map<String, String> rows = new java.util.LinkedHashMap<>();
            rows.put("เรื่อง", briefing.matter());
            rows.put("ผู้ยื่นคำร้อง", briefing.applicant());
            rows.put("ท่านได้รับการเสนอชื่อเป็น", briefing.role());
            rows.put("ผู้เสนอชื่อ", inviter);
            letter.details(rows)
                    .steps("สิ่งที่วิทยาลัยฯ ขอความอนุเคราะห์จากท่าน", briefing.duties());
        } else {
            letter = EmailTemplateHelper.Letter.of("แจ้งการเป็นผู้ลงนามเอกสารอิเล็กทรอนิกส์", EmailTemplateHelper.formalName(user))
                    .para("ด้วย " + inviter + " วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น ได้ระบุให้ท่านเป็นผู้ลงนาม"
                            + "ในเอกสารอิเล็กทรอนิกส์ของระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์"
                            + " เมื่อเอกสารพร้อมให้ลงนาม ระบบจะส่งอีเมลแจ้งท่านอีกครั้ง พร้อมรายละเอียดของเอกสาร");
        }
        return letter
                .steps("การเข้าสู่ระบบและการลงนาม", signInSteps(user))
                .note("info", null, "ขณะนี้ท่านยังไม่ต้องดำเนินการใด ๆ ในระบบ"
                        + (invitedBy != null && invitedBy.getEmail() != null
                                ? " หากมีข้อสงสัย กรุณาติดต่อ " + inviter + " อีเมล " + invitedBy.getEmail()
                                : ""))
                .close(briefing != null ? "จึงเรียนมาเพื่อโปรดพิจารณา และขอขอบคุณมา ณ โอกาสนี้"
                        : "จึงเรียนมาเพื่อโปรดทราบ");
    }

    /** วิธีเข้าระบบและลงนาม — คนนอก มข. ใช้ลิงก์ในอีเมล บุคลากร มข. ต่างคณะใช้ KKU SSO และ .p12 ของ มข. */
    private java.util.List<String> signInSteps(UserDtls user) {
        if (kkuDomains.isOutsideKku(user) && externalLinks.isEnabled()) {
            return java.util.List.of(
                    "วิทยาลัยฯ ได้จัดทำบัญชีผู้ใช้ให้ท่านแล้ว ท่านไม่ต้องตั้งรหัสผ่าน",
                    "เมื่อถึงลำดับการลงนามของท่าน ระบบจะส่งอีเมลพร้อมลิงก์เข้าสู่ระบบเพื่อเปิดเอกสาร ลิงก์ใช้ได้ครั้งเดียว"
                            + "ภายใน " + externalLinks.ttlDays() + " วัน หากหมดอายุ ขอลิงก์ใหม่ได้ที่ "
                            + publicBaseUrl + ExternalLinkService.PATH,
                    "ท่านไม่จำเป็นต้องมีใบรับรองอิเล็กทรอนิกส์ (Digital ID) ระบบจะส่งรหัสยืนยันตัวตนไปยังอีเมลนี้"
                            + "เพื่อใช้ประกอบการลงนาม หากท่านมี Digital ID (.p12) ของหน่วยงาน สามารถติดตั้งเพื่อใช้ลงนามแทนได้");
        }
        if (kkuDomains.isOutsideKku(user)) {
            return java.util.List.of(
                    "วิทยาลัยฯ ได้จัดทำบัญชีผู้ใช้ให้ท่านแล้ว โดยใช้อีเมล " + user.getEmail(),
                    "เมื่อถึงลำดับการลงนามของท่าน ระบบจะส่งอีเมลแจ้งพร้อมลิงก์สำหรับเปิดเอกสาร",
                    "หากท่านมีใบรับรองอิเล็กทรอนิกส์ (Digital ID) ของหน่วยงาน สามารถติดตั้งได้ที่เมนู “ลายเซ็นของฉัน”"
                            + " หากไม่มี ระบบจะส่งรหัสยืนยันตัวตนไปยังอีเมลนี้เพื่อใช้ประกอบการลงนาม");
        }
        return java.util.List.of(
                "วิทยาลัยฯ ได้จัดทำบัญชีผู้ใช้ให้ท่านแล้ว ท่านเข้าสู่ระบบได้ที่ " + publicBaseUrl
                        + " ด้วย KKU SSO โดยใช้อีเมล " + user.getEmail(),
                "เมื่อถึงลำดับการลงนามของท่าน ระบบจะส่งอีเมลแจ้งพร้อมลิงก์สำหรับเปิดเอกสาร",
                "ท่านต้องลงนามด้วยใบรับรองอิเล็กทรอนิกส์ (Digital ID) ของมหาวิทยาลัยขอนแก่น หากยังไม่มี"
                        + " กรุณาดาวน์โหลดไฟล์ .p12 จาก i.kku.ac.th แล้วติดตั้งที่เมนู “ลายเซ็นของฉัน” ก่อนถึงลำดับลงนาม");
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }
}
