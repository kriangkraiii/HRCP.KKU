package com.ecom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Dedicated configuration for PasswordEncoder.
 * Decoupled from SecurityConfig to prevent circular dependency issues during
 * early initialization of UserDetailsService, UserService, and handlers.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
