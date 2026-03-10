package com.ecom.config;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = "admin@admin.com";

        if (!userRepository.existsByEmail(adminEmail)) {
            UserDtls admin = new UserDtls();
            admin.setName("Admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setRole("ROLE_ADMIN");
            admin.setIsEnable(true);
            admin.setAccountNonLocked(true);
            admin.setFailedAttempt(0);
            admin.setIsFirstLogin(false);
            admin.setCreatedDate(new Date());
            admin.setEmailNotificationEnabled(true);

            userRepository.save(admin);
            System.out.println("=== Default admin created: " + adminEmail + " / admin ===");
        }
    }
}
