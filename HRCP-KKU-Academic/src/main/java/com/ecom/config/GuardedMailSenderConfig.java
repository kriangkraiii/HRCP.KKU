package com.ecom.config;

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

    @Bean
    @Primary
    @ConditionalOnBean(JavaMailSenderImpl.class)
    public JavaMailSender guardedMailSender(JavaMailSenderImpl delegate, TestAccountRegistry testAccounts) {
        return new GuardedJavaMailSender(delegate, testAccounts);
    }
}
