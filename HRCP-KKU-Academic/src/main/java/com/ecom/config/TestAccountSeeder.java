package com.ecom.config;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Dev mode only: one account per role in the workflow, so a request can be
 * walked from applicant to dean with the account switcher.
 *
 * <p>Every address starts with "test", which makes it a test account to
 * {@link TestAccountRegistry}: it shows in the switcher and its mail is held
 * back. Rows that already exist are left as they are, so edits made while
 * testing survive a restart.
 */
@Component
@Order(100)
// Off in the test suite: tests wipe user_dtls, and a seeded staff row pointing at it blocks the delete.
@ConditionalOnProperty(name = "app.dev.seed-test-accounts", havingValue = "true", matchIfMissing = true)
public class TestAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestAccountSeeder.class);

    static final String PASSWORD = "test1234";

    /** {@code staffRole} links the account to a staff row so the workflow can pick it as signer. */
    record Seed(String email, String firstName, String lastName, String role, String staffRole) {
    }

    static final List<Seed> SEEDS = List.of(
            new Seed("test.applicant@kku.ac.th", "ผู้ยื่นคำขอ", "ทดสอบ", "ROLE_USER", null),
            new Seed("test.hr@kku.ac.th", "เจ้าหน้าที่HR", "ทดสอบ", "ROLE_ADMIN", "HR"),
            new Seed("test.head@kku.ac.th", "หัวหน้าสาขา", "ทดสอบ", "ROLE_USER", "HEAD"),
            new Seed("test.assocdean@kku.ac.th", "รองคณบดี", "ทดสอบ", "ROLE_USER", "DEAN"),
            new Seed("test.dean@kku.ac.th", "คณบดี", "ทดสอบ", "ROLE_ADMIN", "DEAN"),
            new Seed("test.committee@kku.ac.th", "ประธานกรรมการ", "ทดสอบ", "ROLE_USER", "COMMITTEE"),
            new Seed("test.author1@kku.ac.th", "ผู้ประพันธ์อันดับแรก", "ทดสอบ", "ROLE_USER", null),
            new Seed("test.author2@kku.ac.th", "ผู้ประพันธ์บรรณกิจ", "ทดสอบ", "ROLE_USER", null));

    private final AuthModeProperties authMode;
    private final UserRepository userRepository;
    private final StaffMemberRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAccountSeeder(AuthModeProperties authMode, UserRepository userRepository,
            StaffMemberRepository staffRepository, PasswordEncoder passwordEncoder) {
        this.authMode = authMode;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!authMode.isPasswordLoginEnabled()) {
            return;
        }
        for (Seed seed : SEEDS) {
            try {
                UserDtls user = userRepository.findByEmailIgnoreCase(seed.email());
                if (user == null) {
                    user = userRepository.save(newUser(seed));
                    log.info("Test account created: {} ({})", seed.email(), seed.role());
                }
                if (seed.staffRole() != null && staffRepository.findByUserId(user.getId()).isEmpty()) {
                    staffRepository.save(newStaff(seed, user));
                }
            } catch (Exception e) {
                // A seed that will not fit must not stop the application starting.
                log.warn("Could not seed test account {}: {}", seed.email(), e.toString());
            }
        }
    }

    private UserDtls newUser(Seed seed) {
        UserDtls u = new UserDtls();
        u.setEmail(seed.email());
        u.setFirstName(seed.firstName());
        u.setLastName(seed.lastName());
        u.setName(seed.firstName() + " " + seed.lastName());
        u.setPassword(passwordEncoder.encode(PASSWORD));
        u.setRole(seed.role());
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        u.setFailedAttempt(0);
        u.setIsFirstLogin(false);
        u.setCreatedDate(new Date());
        u.setEmailNotificationEnabled(true);
        u.setTwoFactorEnabled(false);
        u.setEmailVerified(true);
        return u;
    }

    private StaffMember newStaff(Seed seed, UserDtls user) {
        StaffMember s = new StaffMember();
        s.setFirstName(seed.firstName());
        s.setLastName(seed.lastName());
        s.setAcademicTitle("");
        s.setStaffType("พนักงานมหาวิทยาลัย");
        s.setStaffRole(seed.staffRole());
        s.setIsActive(true);
        s.setUser(user);
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }
}
