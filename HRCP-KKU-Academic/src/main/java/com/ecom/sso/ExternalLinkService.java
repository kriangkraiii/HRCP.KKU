package com.ecom.sso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.ExternalSignInLink;
import com.ecom.academic.model.ExternalSignInLink.Purpose;
import com.ecom.academic.repository.ExternalSignInLinkRepository;
import com.ecom.config.KkuEmailDomains;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.SignInService;
import com.ecom.util.EmailTemplateHelper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * ลิงก์เข้าสู่ระบบทางอีเมลของผู้ลงนามภายนอก มข. (docs/PLAN-external-magic-link.md)
 *
 * <p>คนนอก มข. ไม่มีบัญชี KKU SSO และหน้าเข้าสู่ระบบบน production มีแค่ปุ่ม SSO ปุ่มในอีเมลขอให้ลงนามจึงเป็น
 * ลิงก์ที่เปิด session ให้เอง ลิงก์ใช้ได้ครั้งเดียว มีอายุ และเก็บแค่ SHA-256 ของ token ถ้าฐานข้อมูลรั่ว
 * ก็ไม่มีลิงก์ที่ใช้ได้หลุดออกไป
 *
 * <p>ออกและใช้ได้เฉพาะบัญชีที่ {@link KkuEmailDomains#isOutsideKku} — คน มข. ทุกคน รวมถึงคณะอื่นที่เป็น
 * {@code ROLE_EXTERNAL} ต้องเข้าทาง KKU SSO ลิงก์นี้จึงไม่ใช่ทางเลี่ยง SSO แม้ผู้เรียกจะส่งบัญชีผิดมา
 */
@Service
public class ExternalLinkService {

    private static final Logger log = LoggerFactory.getLogger(ExternalLinkService.class);

    public static final String PATH = "/auth/link";

    /**
     * ผลของการขอลิงก์ใหม่ — หน้าเว็บบอกผู้ขอตรง ๆ ว่าส่งหรือไม่ส่ง เพราะอะไร (ผู้ใช้ขอ 14 ต.ค. 2569)
     *
     * <p>แลกกับการที่หน้านี้บอกได้ว่าอีเมลไหนเป็นผู้ลงนามภายนอกในระบบ — จำกัดด้วย RateLimitFilter
     * ซึ่งนับ /auth/link เป็นการกรอกข้อมูลเข้าระบบ (30 ครั้งต่อนาทีต่อ IP)
     */
    public enum ResendOutcome {
        SENT,
        /** ส่งให้อีเมลนี้ไปไม่ถึงหนึ่งนาที — ไม่ส่งซ้ำ */
        RECENTLY_SENT,
        /** อีเมลโดเมน มข. — เข้าด้วย KKU SSO */
        KKU_ACCOUNT,
        /** มีบัญชี แต่ไม่ใช่ผู้ลงนามภายนอก — เข้าทางหน้าเข้าสู่ระบบปกติ */
        NOT_EXTERNAL,
        DISABLED,
        NOT_FOUND
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /** ผู้ใช้ที่ลิงก์พาเข้าระบบ และขั้นลงนามที่ต้องพาไป (null = กล่องงานลงนาม) */
    public record Redemption(UserDtls user, Long stepId) {
    }

    private record Usable(ExternalSignInLink link, UserDtls user) {
    }

    private final ExternalSignInLinkRepository links;
    private final UserRepository users;
    private final KkuEmailDomains kkuDomains;
    private final SignInService signInService;
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final Duration ttl;
    private final Duration sessionTimeout;
    private final String publicBaseUrl;
    private final String senderEmail;
    private final Cache<String, Boolean> recentResends;
    private Clock clock = Clock.systemDefaultZone();

    public ExternalLinkService(ExternalSignInLinkRepository links, UserRepository users, KkuEmailDomains kkuDomains,
            SignInService signInService, JavaMailSender mailSender,
            @Value("${app.auth.email-link.enabled:true}") boolean enabled,
            @Value("${app.auth.email-link.ttl-days:7}") int ttlDays,
            @Value("${app.auth.email-link.session-minutes:30}") int sessionMinutes,
            @Value("${app.auth.email-link.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${app.public-base-url:https://hrd.computing.kku.ac.th}") String publicBaseUrl,
            @Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}") String senderEmail) {
        this.links = links;
        this.users = users;
        this.kkuDomains = kkuDomains;
        this.signInService = signInService;
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.ttl = Duration.ofDays(ttlDays);
        this.sessionTimeout = Duration.ofMinutes(sessionMinutes);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.senderEmail = senderEmail;
        this.recentResends = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, resendCooldownSeconds)))
                .build();
    }

    /** ปิดด้วย {@code app.auth.email-link.enabled=false} แล้วระบบกลับไปส่งลิงก์ /esign/sign/{id} แบบเดิม */
    public boolean isEnabled() {
        return enabled;
    }

    /** อายุลิงก์เป็นวัน — สำหรับข้อความในอีเมล */
    public long ttlDays() {
        return ttl.toDays();
    }

    public Duration sessionTimeout() {
        return sessionTimeout;
    }

    /** ผู้รับคนนี้ควรได้ลิงก์นี้แทนลิงก์ปกติหรือไม่ */
    public boolean appliesTo(UserDtls user) {
        return enabled && kkuDomains.isOutsideKku(user) && signInService.isLocallyUsable(user);
    }

    /**
     * ออกลิงก์ใหม่ — ลิงก์เดิมที่ยังไม่ใช้ยังใช้ได้จนหมดอายุ เพราะผู้รับอาจกดจากอีเมลฉบับไหนก็ได้
     *
     * @return URL เต็มสำหรับใส่ในอีเมล
     * @throws IllegalArgumentException เมื่อบัญชีนี้ต้องเข้าทาง KKU SSO
     */
    @Transactional
    public String issue(UserDtls user, Long stepId, Purpose purpose) {
        if (!appliesTo(user)) {
            throw new IllegalArgumentException("ลิงก์เข้าสู่ระบบทางอีเมลใช้ได้เฉพาะผู้ลงนามภายนอก มข.: "
                    + (user == null ? "-" : user.getEmail()));
        }
        String token = newToken();
        LocalDateTime now = LocalDateTime.now(clock);
        links.save(new ExternalSignInLink(user.getId(), hash(token), stepId, purpose, now, now.plus(ttl)));
        String url = publicBaseUrl + PATH + "/" + token;
        if (EmailTemplateHelper.isTestEmail(user.getEmail())) {
            log.debug("Sign-in link for test account {}: {}", user.getEmail(), url);
        }
        return url;
    }

    /** ลิงก์นี้ยังใช้ได้ไหม — สำหรับหน้ายืนยัน ไม่ใช้ token */
    @Transactional(readOnly = true)
    public boolean isLive(String token) {
        return usable(token).isPresent();
    }

    /**
     * ใช้ลิงก์ — ตรวจครบทุกข้อในข้อ 4.4 ของแผน แล้วทำเครื่องหมายว่าใช้แล้วแบบ atomic
     */
    @Transactional
    public Optional<Redemption> redeem(String token, String ip) {
        Optional<Usable> found = usable(token);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ExternalSignInLink link = found.get().link();
        if (links.markUsed(link.getId(), LocalDateTime.now(clock), truncate(ip, 45)) != 1) {
            log.info("Sign-in link {} was used by a concurrent request", link.getId());
            return Optional.empty();
        }
        log.info("External signer {} signed in with link {} from {}", found.get().user().getEmail(), link.getId(), ip);
        return Optional.of(new Redemption(found.get().user(), link.getStepId()));
    }

    /**
     * ขอลิงก์ใหม่ที่ /auth/link — ส่งเฉพาะบัญชีผู้ลงนามภายนอก มข. ที่ยังใช้งานได้ และไม่ได้ขอไปเมื่อครู่
     */
    @Transactional
    public ResendOutcome resend(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return ResendOutcome.NOT_FOUND;
        }
        if (kkuDomains.isKku(normalized)) {
            return ResendOutcome.KKU_ACCOUNT;
        }
        UserDtls user = users.findByEmailIgnoreCase(normalized);
        if (user == null) {
            log.info("Sign-in link resend for {} not sent — no account", normalized);
            return ResendOutcome.NOT_FOUND;
        }
        if (!user.isExternal()) {
            return ResendOutcome.NOT_EXTERNAL;
        }
        if (!signInService.isLocallyUsable(user)) {
            log.info("Sign-in link resend for {} not sent — account disabled", normalized);
            return ResendOutcome.DISABLED;
        }
        if (recentResends.asMap().putIfAbsent(normalized, Boolean.TRUE) != null) {
            return ResendOutcome.RECENTLY_SENT;
        }
        sendLink(user, issue(user, null, Purpose.RESEND));
        return ResendOutcome.SENT;
    }

    /** เก็บแถวที่หมดอายุไว้ 90 วันเผื่อสอบย้อน แล้วลบ */
    @Scheduled(cron = "${app.auth.email-link.purge-cron:0 40 3 * * *}")
    @Transactional
    public void purgeExpired() {
        int removed = links.deleteExpiredBefore(LocalDateTime.now(clock).minusDays(90));
        if (removed > 0) {
            log.info("Removed {} sign-in link(s) that expired more than 90 days ago", removed);
        }
    }

    /** สำหรับเทส — เลื่อนเวลา */
    void useClock(Clock clock) {
        this.clock = clock;
    }

    private Optional<Usable> usable(String token) {
        if (!enabled || token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        Optional<ExternalSignInLink> link = links.findByTokenHash(hash(token));
        if (link.isEmpty()) {
            return Optional.empty();
        }
        if (!link.get().isUsableAt(LocalDateTime.now(clock))) {
            log.info("Sign-in link {} refused — {}", link.get().getId(),
                    link.get().getUsedAt() != null ? "already used" : "expired");
            return Optional.empty();
        }
        UserDtls user = users.findById(link.get().getUserId()).orElse(null);
        // บัญชีอาจถูกปิด เปลี่ยนบทบาท หรือเปลี่ยนอีเมลเป็นของ มข. หลังออกลิงก์ — ตรวจซ้ำตอนใช้ทุกครั้ง
        if (!appliesTo(user)) {
            log.warn("Sign-in link {} refused — account {} no longer qualifies", link.get().getId(),
                    user == null ? link.get().getUserId() : user.getEmail());
            return Optional.empty();
        }
        return Optional.of(new Usable(link.get(), user));
    }

    private void sendLink(UserDtls user, String url) {
        if (EmailTemplateHelper.isTestEmail(user.getEmail())) {
            return;
        }
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
            helper.setTo(user.getEmail());
            helper.setSubject("ลิงก์เข้าสู่ระบบเพื่อลงนามเอกสาร - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");
            String body = EmailTemplateHelper.Letter.of("ลิงก์เข้าสู่ระบบเพื่อลงนามเอกสาร", EmailTemplateHelper.formalName(user))
                    .para("ตามที่ท่านขอลิงก์เข้าสู่ระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น"
                            + " ท่านเข้าสู่ระบบเพื่อดูและลงนามเอกสารที่รอท่านอยู่ได้ที่ลิงก์ด้านล่าง")
                    .link("เข้าสู่ระบบ", url)
                    .note("warn", null, "ลิงก์นี้ใช้ได้ครั้งเดียว ภายใน " + ttlDays() + " วัน โปรดอย่าส่งต่ออีเมลฉบับนี้"
                            + "\nหากท่านไม่ได้ขอลิงก์นี้ ไม่ต้องดำเนินการใด ๆ")
                    .close("จึงเรียนมาเพื่อโปรดทราบ");
            helper.setText(EmailTemplateHelper.wrapLayout("ลิงก์เข้าสู่ระบบ", "ผู้ลงนามภายนอก", body), true);
            EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Sign-in link email to {} failed: {}", user.getEmail(), e.toString());
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
