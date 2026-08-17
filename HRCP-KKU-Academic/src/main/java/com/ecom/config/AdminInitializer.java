package com.ecom.config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:kriangkrai.p@kkumail.com}")
    private String adminEmail;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.user.email:user@user.com}")
    private String userEmail;

    @Value("${app.user.password:user123}")
    private String userPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 1. Create default admin if not exists
        if (!userRepository.existsByEmail(adminEmail)) {
            UserDtls admin = new UserDtls();
            admin.setFirstName("Admin");
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

        // 2. Create default regular test user if not exists
        if (!userRepository.existsByEmail(userEmail)) {
            UserDtls user = new UserDtls();
            user.setTitle("อาจารย์");
            user.setFirstName("ทดสอบ");
            user.setLastName("ผู้ใช้งาน");
            user.setEmail(userEmail);
            user.setPassword(passwordEncoder.encode(userPassword));
            user.setRole("ROLE_USER");
            user.setApplicantId(generateApplicantId());
            user.setAcademicPosition("อาจารย์");
            user.setMobileNumber("0812345678");
            user.setIsEnable(true);
            user.setAccountNonLocked(true);
            user.setFailedAttempt(0);
            user.setIsFirstLogin(false);
            user.setCreatedDate(new Date());
            user.setEmailNotificationEnabled(true);
            user.setAutoDraftEnabled(true);
            user.setTwoFactorEnabled(false);
            user.setEmailVerified(true);

            userRepository.save(user);
            System.out.println("=== Default test user created: " + userEmail + " / " + userPassword + " (Applicant ID: " + user.getApplicantId() + ") ===");
        }

        // 3. Backfill applicant IDs for existing ROLE_USER without one
        List<UserDtls> usersWithoutId = userRepository.findByRoleAndApplicantIdIsNull("ROLE_USER");
        if (!usersWithoutId.isEmpty()) {
            System.out.println("=== Backfilling applicant IDs for " + usersWithoutId.size() + " user(s) ===");
            for (UserDtls u : usersWithoutId) {
                u.setApplicantId(generateApplicantId());
                userRepository.save(u);
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
            try {
                nextSeq = Integer.parseInt(lastSeq) + 1;
            } catch (NumberFormatException e) {
                nextSeq = 1;
            }
        }
        return prefix + String.format("%04d", nextSeq);
    }
}
