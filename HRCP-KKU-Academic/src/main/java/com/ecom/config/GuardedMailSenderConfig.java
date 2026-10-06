package com.ecom.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Puts every outgoing email behind {@link GuardedJavaMailSender}.
 *
 * <p>The delegate is injected as the concrete {@code JavaMailSenderImpl} that
 * Spring Boot auto-configures, not as the {@code JavaMailSender} interface —
 * the wrapper is itself a {@code JavaMailSender}, so asking for the interface
 * would make the bean depend on itself.
 *
 * <p>This is an auto-configuration ordered after Boot's own mail setup, not a
 * plain {@code @Configuration}: user configuration is processed before any
 * auto-configuration, so {@code @ConditionalOnBean(JavaMailSenderImpl.class)}
 * there never saw Boot's sender and the guard was silently never installed.
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
public class GuardedMailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(GuardedMailSenderConfig.class);

    @Bean
    @Primary
    @ConditionalOnBean(JavaMailSenderImpl.class)
    public JavaMailSender guardedMailSender(JavaMailSenderImpl delegate, TestAccountRegistry testAccounts,
            @Value("${app.auth.mode:dev}") String authMode,
            @Value("${app.mail.dev-capture.enabled:true}") boolean captureEnabled,
            @Value("${app.mail.dev-capture.port:1025}") int capturePort) {
        boolean capture = captureEnabled && "dev".equalsIgnoreCase(authMode.trim());
        // บริการส่งอีเมลแต่ละตัวยังข้ามบัญชีทดสอบเองก่อนถึงตัวส่ง — ต้องปิดด้วย ไม่งั้นแจ้งเตือนไม่ถึง Mailpit
        com.ecom.util.EmailTemplateHelper.setDevCapture(capture);
        if (capture) {
            redirectToLocalCatchAll(delegate, capturePort);
        } else {
            warnIfUnconfigured(delegate);
        }
        return new GuardedJavaMailSender(delegate, testAccounts, capture);
    }

    /**
     * In dev auth mode every email, test accounts included, goes to a local
     * catch-all SMTP such as Mailpit instead of the real relay. Rewriting the
     * delegate here, rather than trusting the configured host, is what makes
     * unfiltered delivery safe: nothing can reach a real inbox from dev.
     */
    static void redirectToLocalCatchAll(JavaMailSenderImpl delegate, int port) {
        delegate.setHost("localhost");
        delegate.setPort(port);
        delegate.setUsername(null);
        delegate.setPassword(null);
        delegate.getJavaMailProperties().setProperty("mail.smtp.auth", "false");
        delegate.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "false");
        delegate.getJavaMailProperties().setProperty("mail.smtp.starttls.required", "false");
        log.warn("app.auth.mode=dev — อีเมลทุกฉบับ (รวมบัญชีทดสอบ) ถูกส่งไปที่ Mailpit localhost:{} ดูได้ที่ http://localhost:8025", port);
    }

    /**
     * Says at startup when no SMTP credentials were supplied.
     *
     * <p>{@code spring.mail.password} no longer carries a working default — a
     * credential in a committed file is a leaked credential (GAP-05) — so a
     * deployment that forgets {@code EMAIL_PASSWORD} now sends nothing. Every
     * dispatch site is {@code @Async} and catches its own failures, so the
     * symptom would otherwise be silence: no error page, no failed request, just
     * OTPs and notifications that never arrive. One line at boot is what turns
     * that into something an operator can see.
     */
    private void warnIfUnconfigured(JavaMailSenderImpl delegate) {
        String host = delegate.getHost();
        boolean isKkuRelay = host != null && host.contains("kku.ac.th");
        String authProp = delegate.getJavaMailProperties().getProperty("mail.smtp.auth", "false");
        boolean authRequired = "true".equalsIgnoreCase(authProp);

        if (isKkuRelay || !authRequired) {
            log.info("ระบบส่งอีเมลกำหนดค่าไปยัง SMTP Relay [{}:{}] (IP-Whitelisted, auth={})",
                    host, delegate.getPort(), authRequired);
            return;
        }

        boolean noPassword = delegate.getPassword() == null || delegate.getPassword().isBlank();
        boolean noUsername = delegate.getUsername() == null || delegate.getUsername().isBlank();
        if (noPassword || noUsername) {
            log.warn("""
                    ยังไม่ได้ตั้งค่าบัญชีส่งอีเมล (EMAIL_USERNAME / EMAIL_PASSWORD) —
                    ระบบทำงานได้ตามปกติทุกอย่าง ยกเว้นการส่งอีเมลออก เช่น OTP สำหรับ 2FA
                    และอีเมลแจ้งสถานะคำร้อง จะไม่ถูกส่งจนกว่าจะตั้งค่าทั้งสองตัวใน environment""");
        }
    }
}
