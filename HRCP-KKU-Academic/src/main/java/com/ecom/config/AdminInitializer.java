package com.ecom.config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;

@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:kriangkrai.p@kkumail.com}")
    private String adminEmail;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        // 1. Create default admin if not exists
        if (!userRepository.existsByEmail(adminEmail)) {
            UserDtls admin = new UserDtls();
            admin.setFirstName("Kriangkrai");
            admin.setLastName("Admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ROLE_ADMIN");
            admin.setIsEnable(true);
            admin.setAccountNonLocked(true);
            admin.setFailedAttempt(0);
            admin.setIsFirstLogin(false);
            admin.setCreatedDate(new Date());
            admin.setEmailNotificationEnabled(true);

            userRepository.save(admin);
            System.out.println("=== Default admin created: " + adminEmail + " / " + adminPassword + " ===");
        }

        // 2. Backfill applicant IDs for existing ROLE_USER without one
        List<UserDtls> usersWithoutId = userRepository.findByRoleAndApplicantIdIsNull("ROLE_USER");
        if (!usersWithoutId.isEmpty()) {
            System.out.println("=== Backfilling applicant IDs for " + usersWithoutId.size() + " user(s) ===");
            for (UserDtls user : usersWithoutId) {
                user.setApplicantId(generateApplicantId());
                userRepository.save(user);
            }
            System.out.println("=== Backfill complete ===");
        }
    }

    private String generateApplicantId() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "APP-" + dateStr + "-";

        UserDtls lastUser = userRepository.findTopByApplicantIdStartingWithOrderByApplicantIdDesc(prefix);
        int nextSeq = 1;
        if (lastUser != null && lastUser.getApplicantId() != null) {
            String lastSeq = lastUser.getApplicantId().substring(prefix.length());
            nextSeq = Integer.parseInt(lastSeq) + 1;
        }
        return prefix + String.format("%04d", nextSeq);
    }
}
