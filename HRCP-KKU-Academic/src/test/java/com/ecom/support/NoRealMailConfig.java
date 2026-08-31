package com.ecom.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Replaces the SMTP transport with {@link RecordingMailSender} for every test.
 *
 * <p>The bean supplied here is a {@link JavaMailSenderImpl}, which is exactly
 * what {@code GuardedMailSenderConfig} asks for as its delegate. So the
 * production guard is still constructed, still wraps this, and still applies its
 * suppression rules — the only thing that changes is that the last step records
 * the message rather than transmitting it.
 *
 * <p>Replacing the {@code JavaMailSender} interface instead would have removed
 * the guard from the chain and left its rules untested, which is the opposite of
 * what is wanted: those rules are what stop a rehearsal reaching a real dean.
 *
 * <p>The bean is deliberately named {@code mailSender} — the same name Spring
 * Boot's {@code MailSenderPropertiesConfiguration} gives the real transport — so
 * that it <em>replaces</em> that bean rather than sitting alongside it. Adding a
 * second {@code @Primary JavaMailSender} instead would fail the context outright,
 * because {@code guardedMailSender} is already primary; and leaving the
 * auto-configured one in place would leave a live sender in the context for
 * anything that asked for it by type.
 */
@TestConfiguration
public class NoRealMailConfig {

    @Bean("mailSender")
    JavaMailSenderImpl recordingMailSender() {
        return new RecordingMailSender();
    }
}
