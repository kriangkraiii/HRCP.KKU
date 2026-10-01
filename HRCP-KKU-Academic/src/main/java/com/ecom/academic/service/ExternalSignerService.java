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

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.util.EmailTemplateHelper;

/**
 * ผู้ลงนามจากนอกวิทยาลัย — สร้างบัญชี {@link UserDtls#ROLE_EXTERNAL} จากอีเมล แล้วส่งอีเมลเชิญ
 *
 * <p>ผู้ลงนามภายนอกเข้าระบบด้วย KKU SSO เหมือนทุกคน ({@code SsoAccessPolicy} รับบัญชีนี้) และลงนามผ่าน
 * หน้าลงนามเดิมทั้งหมด: มี Digital ID (.p12) ของหน่วยงานตัวเองก็ใช้ของตัวเอง ไม่มีก็ยืนยันตัวตนทาง
 * อีเมลแล้วระบบประทับรับรองแทน ({@code SystemSealService}) ดู docs/PLAN-external-signer.md
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

    public ExternalSignerService(UserRepository users, PasswordEncoder passwordEncoder, JavaMailSender mailSender,
            @Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}") String senderEmail,
            @Value("${app.public-base-url:https://hrd.computing.kku.ac.th}") String publicBaseUrl) {
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
        sendInvitation(saved, invitedBy);
        return saved;
    }

    private void sendInvitation(UserDtls user, UserDtls invitedBy) {
        if (EmailTemplateHelper.isTestEmail(user.getEmail())) {
            log.info("Test account — external signer invitation not mailed to {}", user.getEmail());
            return;
        }
        try {
            String inviter = invitedBy != null && invitedBy.getName() != null ? invitedBy.getName() : "เจ้าหน้าที่";
            String body = "<p>เรียน " + escape(user.getName()) + "</p>"
                    + "<p>" + escape(inviter) + " ได้ระบุท่านเป็นผู้ลงนามในเอกสารของระบบพัฒนาบุคลากร "
                    + "วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น เมื่อเอกสารพร้อมให้ลงนาม ระบบจะส่งอีเมลแจ้งท่านอีกครั้ง</p>"
                    + "<p>การเข้าสู่ระบบ: ใช้ <strong>KKU SSO</strong> ด้วยอีเมล <strong>" + escape(user.getEmail())
                    + "</strong> ที่ <a href='" + publicBaseUrl + "/signin'>" + publicBaseUrl + "</a></p>"
                    + "<p>การลงนาม: หากท่านมี Digital ID (.p12) ของหน่วยงาน สามารถติดตั้งได้ที่เมนู \"ลายเซ็นของฉัน\" "
                    + "หากไม่มี ระบบจะส่งรหัสยืนยันตัวตนทางอีเมลนี้ให้ตอนลงนาม</p>";
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
            helper.setTo(user.getEmail());
            helper.setSubject("คำเชิญลงนามเอกสารอิเล็กทรอนิกส์ - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");
            helper.setText(EmailTemplateHelper.wrapLayout("คำเชิญลงนามเอกสารอิเล็กทรอนิกส์", "ผู้ลงนามภายนอก", body), true);
            EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            // บัญชีถูกสร้างแล้ว อีเมลแจ้งให้ลงนามจะตามมาเองตอนถึงคิว — อีเมลเชิญหายไม่ควรล้มการเลือกผู้ลงนาม
            log.error("External signer invitation to {} failed: {}", user.getEmail(), e.toString());
        }
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
