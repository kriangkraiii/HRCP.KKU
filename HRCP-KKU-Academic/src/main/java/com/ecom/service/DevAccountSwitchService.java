package com.ecom.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ecom.config.AuthModeProperties;
import com.ecom.config.TestAccountRegistry;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

/**
 * Lets an administrator step into a test account without its password, so a
 * workflow can be walked through as applicant and as admin in one browser.
 *
 * <p>Three limits keep it a testing aid rather than a back door:
 * <ul>
 *   <li>It exists only while password login does (dev mode). Under SSO the
 *       tool is gone along with the password form.</li>
 *   <li>Only the accounts in {@link TestAccountRegistry} can be entered. Those
 *       are also the accounts whose actions send no real mail, so a rehearsal
 *       run through this switch cannot reach a real dean either.</li>
 *   <li>Only an administrator can start it. The session remembers who that was,
 *       so the switch stays reachable from inside a non-admin test account and
 *       always offers the way back.</li>
 * </ul>
 */
@Service
public class DevAccountSwitchService {

    /** E-mail of the administrator who started switching in this session. */
    public static final String SESSION_ORIGINAL_EMAIL = "DEV_SWITCH_ORIGINAL_EMAIL";

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** One row in the switcher. */
    public record Option(String email, String name, String role, boolean current) {
    }

    private final AuthModeProperties authMode;
    private final TestAccountRegistry testAccounts;
    private final UserRepository userRepository;

    public DevAccountSwitchService(AuthModeProperties authMode, TestAccountRegistry testAccounts,
            UserRepository userRepository) {
        this.authMode = authMode;
        this.testAccounts = testAccounts;
        this.userRepository = userRepository;
    }

    /** The administrator this session started as, or {@code null} if no switch happened. */
    public String originalEmail(HttpSession session) {
        return session == null ? null : (String) session.getAttribute(SESSION_ORIGINAL_EMAIL);
    }

    /** Whether the switcher is offered to this user in this session. */
    public boolean isAvailable(UserDtls current, HttpSession session) {
        if (!authMode.isPasswordLoginEnabled() || current == null) {
            return false;
        }
        return ROLE_ADMIN.equals(current.getRole()) || originalEmail(session) != null;
    }

    /**
     * The accounts that can be switched to: every test account that exists here
     * (listed, or starting with "test"), plus the original administrator so the
     * way back is always listed.
     */
    public List<Option> options(UserDtls current, HttpSession session) {
        // แถบนี้อยู่ในทุกหน้าของแอดมิน บัญชีที่ขึ้นต้นด้วย test ได้มาครบจาก query แรกแล้ว
        // จึงค้นทีละอีเมลเฉพาะบัญชีที่เหลือ — เดิมค้นใหม่ทุกบัญชี สิบกว่า query ต่อหน้า
        List<UserDtls> users = new ArrayList<>(
                userRepository.findByEmailStartingWithIgnoreCase(TestAccountRegistry.TEST_PREFIX));
        List<String> remaining = new ArrayList<>(testAccounts.addresses());
        String origin = originalEmail(session);
        if (origin != null) {
            remaining.add(origin);
        }
        remaining.stream()
                .filter(e -> users.stream().noneMatch(u -> e.equalsIgnoreCase(u.getEmail())))
                .distinct()
                .map(userRepository::findByEmailIgnoreCase)
                .filter(u -> u != null && users.stream().noneMatch(seen -> seen.getEmail().equalsIgnoreCase(u.getEmail())))
                .forEach(users::add);

        return users.stream()
                .filter(u -> u != null && Boolean.TRUE.equals(u.getIsEnable()))
                .map(u -> new Option(u.getEmail(),
                        u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail(),
                        u.getRole(),
                        current != null && u.getEmail().equalsIgnoreCase(current.getEmail())))
                .sorted(Comparator.comparing((Option o) -> !ROLE_ADMIN.equals(o.role()))
                        .thenComparing(Option::email))
                .toList();
    }

    /**
     * The account to switch to, if this user may enter it.
     *
     * <p>A test account, or the administrator the session started as — the
     * latter need not be a test account, it is simply the way home.
     */
    public Optional<UserDtls> resolveTarget(UserDtls current, HttpSession session, String email) {
        if (!isAvailable(current, session) || email == null || email.isBlank()) {
            return Optional.empty();
        }
        String wanted = email.trim();
        boolean goingHome = wanted.equalsIgnoreCase(originalEmail(session));
        if (!goingHome && !testAccounts.isTestAccount(wanted)) {
            return Optional.empty();
        }
        UserDtls target = userRepository.findByEmailIgnoreCase(wanted);
        if (target == null || !Boolean.TRUE.equals(target.getIsEnable())) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    /**
     * Keeps track of where the session started. Arriving back at that account
     * ends the switch, so an ordinary admin session no longer shows the banner.
     */
    public void rememberOrigin(HttpSession session, UserDtls current, UserDtls target) {
        String origin = originalEmail(session);
        if (origin == null) {
            origin = current.getEmail();
        }
        if (target.getEmail().equalsIgnoreCase(origin)) {
            session.removeAttribute(SESSION_ORIGINAL_EMAIL);
        } else {
            session.setAttribute(SESSION_ORIGINAL_EMAIL, origin);
        }
    }
}
