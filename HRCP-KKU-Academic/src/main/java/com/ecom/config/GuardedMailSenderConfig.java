package com.ecom.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
public class GuardedMailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(GuardedMailSenderConfig.class);

    @Bean
    @Primary
    @ConditionalOnBean(JavaMailSenderImpl.class)
    public JavaMailSender guardedMailSender(JavaMailSenderImpl delegate, TestAccountRegistry testAccounts) {
        warnIfUnconfigured(delegate);
        return new GuardedJavaMailSender(delegate, testAccounts);
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
