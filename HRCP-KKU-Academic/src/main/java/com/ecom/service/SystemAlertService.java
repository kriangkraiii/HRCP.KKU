package com.ecom.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

/**
 * Tells the administrators when something the system does on its own goes wrong.
 *
 * <p>The scheduled work — pulling the faculty directory, publications, photos —
 * runs at one in the morning with nobody watching. Its only current trace is a
 * line in the application log and a status column an administrator has to think
 * to go and look at, which means a job that has been broken for a fortnight looks
 * exactly like one that ran fine.
 *
 * <p><b>Failures and recoveries are announced; routine successes are not.</b>
 * A nightly "the sync worked" message is read for about a week, then filtered
 * into a folder, and from then on the one message that matters arrives in the
 * same folder nobody opens. Success mail can be switched on with
 * {@code app.alert.on-success} for a period of watching a new deployment, but it
 * is off by default on purpose.
 *
 * <p>Repeated failures are throttled: a job broken for a week should be a daily
 * reminder, not sixty identical messages. A recovery is never throttled — the
 * all-clear is the one message worth interrupting for.
 */
@Service
public class SystemAlertService {

    private static final Logger log = LoggerFactory.getLogger(SystemAlertService.class);

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm น.");

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private final boolean emailEnabled;
    private final boolean alertOnSuccess;
    private final Duration throttle;

    /** Last time each alert key was sent, so a stuck job does not become a flood. */
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    public SystemAlertService(JavaMailSender mailSender,
            UserRepository userRepository,
            NotificationService notificationService,
            @Value("${app.alert.email.enabled:true}") boolean emailEnabled,
            @Value("${app.alert.on-success:false}") boolean alertOnSuccess,
            @Value("${app.alert.throttle-hours:12}") long throttleHours) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailEnabled = emailEnabled;
        this.alertOnSuccess = alertOnSuccess;
        this.throttle = Duration.ofHours(Math.max(throttleHours, 0));
    }

    /** Whether the caller should bother assembling a success message at all. */
    public boolean isSuccessAlertEnabled() {
        return alertOnSuccess;
    }

    /**
     * Something scheduled failed.
     *
     * @param source what broke, in words an administrator will recognise
     * @param detail what is known about why
     */
    public void failure(String source, String detail) {
        String key = "FAIL:" + source;
        if (!claim(key)) {
            log.debug("Alert for {} suppressed — one was already sent within the throttle window", source);
            return;
        }
        send(Level.FAILURE, source, detail);
    }

    /** Something that had been failing is working again. */
    public void recovery(String source, String detail) {
        // The all-clear cancels the throttle, so the next failure is reported at once.
        lastSent.remove("FAIL:" + source);
        send(Level.RECOVERY, source, detail);
    }

    /** Routine good news. Sent only where {@code app.alert.on-success} is on. */
    public void success(String source, String detail) {
        if (!alertOnSuccess) {
            return;
        }
        send(Level.SUCCESS, source, detail);
    }

    /**
     * Reserves the right to send for this key.
     *
     * @return false when one went out too recently
     */
    private boolean claim(String key) {
        Instant now = Instant.now();
        Instant previous = lastSent.get(key);
        if (previous != null && Duration.between(previous, now).compareTo(throttle) < 0) {
            return false;
        }
        lastSent.put(key, now);
        return true;
    }

    /**
     * Puts the message in front of the administrators.
     *
     * <p>Always in the application itself, where an unread badge costs nobody
     * anything; by e-mail only for those who asked for e-mail. Nothing here is
     * allowed to throw — an alert that breaks the job it is reporting on would be
     * a poor trade.
     */
    private void send(Level level, String source, String detail) {
        String title = level.prefix + " " + source;
        String stamp = LocalDateTime.now().format(STAMP);

        try {
            notificationService.notifyAdmins(null, title, detail,
                    "/admin/external-sync", NotificationType.SYSTEM, level == Level.FAILURE);
        } catch (Exception e) {
            log.warn("Could not record the in-app system alert: {}", e.toString());
        }

        if (!emailEnabled) {
            return;
        }

        try {
            for (UserDtls admin : recipients()) {
                mail(admin, level, source, detail, stamp);
            }
        } catch (Exception e) {
            log.warn("Could not send the system alert e-mail: {}", e.toString());
        }

        log.info("System alert [{}] {}: {}", level, source, detail);
    }

    /** Administrators who have not turned e-mail notifications off. */
    private List<UserDtls> recipients() {
        return userRepository.findByRole("ROLE_ADMIN").stream()
                .filter(a -> a.getEmail() != null && !a.getEmail().isBlank())
                .filter(a -> Boolean.TRUE.equals(a.getEmailNotificationEnabled()))
                .toList();
    }

    @Value("${spring.mail.username:noreply@kku.ac.th}")
    private String senderEmail;

    /**
     * One message.
     *
     * <p>Asynchronous because the caller is usually a scheduled job finishing up,
     * and an SMTP server that has gone slow should not hold that job open.
     */
    @Async
    public void mail(UserDtls admin, Level level, String source, String detail, String stamp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(admin.getEmail());
            helper.setSubject("[HRCP.KKU] " + level.prefix + " " + source);
            helper.setText(body(admin, level, source, detail, stamp), true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("System alert e-mail to an administrator failed: {}", e.toString());
        }
    }

    private String body(UserDtls admin, Level level, String source, String detail, String stamp) {
        return com.ecom.util.EmailTemplateHelper.buildSystemAlertEmail(
                admin.getName() != null ? admin.getName() : "ผู้ดูแลระบบ",
                level.heading,
                level.colour,
                level.icon,
                source,
                detail,
                stamp);
    }

    /**
     * Escapes text before it goes into the message.
     *
     * <p>The detail line quotes whatever an upstream system said when it failed,
     * which is not ours to trust as markup.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** How loud the message is. */
    public enum Level {

        FAILURE("⛔", "ทำงานไม่สำเร็จ", "#c62828", "[ไม่สำเร็จ]"),
        RECOVERY("✅", "กลับมาทำงานได้แล้ว", "#2e7d32", "[กลับมาปกติ]"),
        SUCCESS("✅", "ทำงานสำเร็จ", "#2e7d32", "[สำเร็จ]");

        private final String icon;
        private final String heading;
        private final String colour;
        private final String prefix;

        Level(String icon, String heading, String colour, String prefix) {
            this.icon = icon;
            this.heading = heading;
            this.colour = colour;
            this.prefix = prefix;
        }
    }
}
