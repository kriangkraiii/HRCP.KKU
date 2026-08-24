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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${app.admin.email:kriangkrai.p@kkumail.com}")
    private String adminEmail;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.user.email:user@user.com}")
    private String userEmail;

    @Value("${app.user.password:user123}")
    private String userPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // 0. Auto-drop stale check constraint on notifications table if present
        try {
            jdbcTemplate.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
        } catch (Exception e) {
            log.debug("Auto-cleanup notifications_type_check notice: {}", e.getMessage());
        }

        // 0.1 Clean up orphan signature requests for deleted academic/position requests
        try {
            jdbcTemplate.execute("""
                UPDATE signature_step SET status = 'SKIPPED'
                WHERE signature_request_id IN (
                    SELECT sr.id FROM signature_request sr
                    LEFT JOIN academic_request ar ON sr.request_id = ar.id
                    WHERE sr.module = 'ACADEMIC' AND sr.status = 'IN_PROGRESS' AND ar.id IS NULL
                ) AND status IN ('WAITING', 'ACTIVE')
            """);
            jdbcTemplate.execute("""
                UPDATE signature_request SET status = 'CANCELLED', cancelled_at = NOW(), cancel_reason = 'แบบร่างคำร้องถูกยกเลิก'
                WHERE id IN (
                    SELECT sr.id FROM signature_request sr
                    LEFT JOIN academic_request ar ON sr.request_id = ar.id
                    WHERE sr.module = 'ACADEMIC' AND sr.status = 'IN_PROGRESS' AND ar.id IS NULL
                )
            """);

            jdbcTemplate.execute("""
                UPDATE signature_step SET status = 'SKIPPED'
                WHERE signature_request_id IN (
                    SELECT sr.id FROM signature_request sr
                    LEFT JOIN position_request pr ON sr.request_id = pr.id
                    WHERE sr.module = 'POSITION' AND sr.status = 'IN_PROGRESS' AND pr.id IS NULL
                ) AND status IN ('WAITING', 'ACTIVE')
            """);
            jdbcTemplate.execute("""
                UPDATE signature_request SET status = 'CANCELLED', cancelled_at = NOW(), cancel_reason = 'แบบร่างคำร้องถูกยกเลิก'
                WHERE id IN (
                    SELECT sr.id FROM signature_request sr
                    LEFT JOIN position_request pr ON sr.request_id = pr.id
                    WHERE sr.module = 'POSITION' AND sr.status = 'IN_PROGRESS' AND pr.id IS NULL
                )
            """);
        } catch (Exception e) {
            log.debug("Auto-cleanup orphan signature requests notice: {}", e.getMessage());
        }

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
            log.info("Default admin account created for: {}", adminEmail);
        }

        // 2. Create or enrich default regular test user
        UserDtls user = userRepository.findByEmail(userEmail);
        if (user == null) {
            user = new UserDtls();
            user.setEmail(userEmail);
            user.setPassword(passwordEncoder.encode(userPassword));
            user.setRole("ROLE_USER");
            user.setApplicantId(generateApplicantId());
            user.setIsEnable(true);
            user.setAccountNonLocked(true);
            user.setFailedAttempt(0);
            user.setIsFirstLogin(false);
            user.setCreatedDate(new Date());
            user.setEmailNotificationEnabled(true);
            user.setAutoDraftEnabled(true);
            user.setTwoFactorEnabled(false);
            user.setEmailVerified(true);
        }

        // Set realistic Thai academic profile data
        user.setTitle("ผู้ช่วยศาสตราจารย์");
        user.setFirstName("สมชาย");
        user.setLastName("ใจดีวิชาการ");
        user.setFirstNameEn("Somchai");
        user.setLastNameEn("Jaideewichakan");
        user.setAcademicPosition("ผู้ช่วยศาสตราจารย์");
        user.setAcademicPositionEn("Assistant Professor");
        user.setMobileNumber("081-234-5678");

        userRepository.save(user);
        log.info("Default test user initialized: {} (Applicant ID: {})", userEmail, user.getApplicantId());

        // 3. Backfill applicant IDs and missing Titles for existing users
        List<UserDtls> allUsers = userRepository.findAll();
        for (UserDtls u : allUsers) {
            boolean updated = false;
            if ("ROLE_USER".equals(u.getRole()) && u.getApplicantId() == null) {
                u.setApplicantId(generateApplicantId());
                updated = true;
            }
            if (u.getTitle() == null || u.getTitle().isBlank()) {
                String resolved = com.ecom.util.AcademicTitleResolver.resolveShortTitle(
                        u.getTitle(),
                        u.getAcademicPosition(),
                        u.getAcademicPositionEn());
                if (resolved != null && !resolved.isBlank()) {
                    u.setTitle(resolved);
                    updated = true;
                }
            }
            if (updated) {
                userRepository.save(u);
            }
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
