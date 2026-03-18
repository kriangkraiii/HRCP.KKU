package com.ecom.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.LockedException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthFailureHandlerImpl extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthFailureHandlerImpl.class);
    private static final long DEACTIVATE_THRESHOLD_MINUTES = 60;
    private static final int MAX_ATTEMPTS = 5;

    private final BruteForceProtection bruteForceProtection;
    private final UserService userService;
    @SuppressWarnings("unused") // wired for future AuthenticationFailureEvent publishing
    private final ApplicationEventPublisher eventPublisher;

    public AuthFailureHandlerImpl(BruteForceProtection bruteForceProtection,
                                   @Lazy UserService userService,
                                   ApplicationEventPublisher eventPublisher) {
        this.bruteForceProtection = bruteForceProtection;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String clientIp = ClientIpUtils.resolveClientIp(request);
        String email = sanitizeEmail(request.getParameter("email"));

        bruteForceProtection.recordFailure("ip:" + clientIp);

        UserDtls user = null;
        if (email != null) {
            bruteForceProtection.recordFailure("user:" + email);
            user = userService.getUserByEmail(email);
            if (user != null) {
                syncFailedAttemptToDb(user, email);
            }
        }

        String errorMessage = resolveErrorMessage(request, exception, clientIp, email, user);

        publishFailureEvent(clientIp, email, errorMessage);

        setDefaultFailureUrl("/signin?error");
        super.onAuthenticationFailure(request, response, exception);
        request.getSession().setAttribute("errorMessage", errorMessage);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void syncFailedAttemptToDb(UserDtls user, String email) {
        userService.increaseFailedAttempt(user);

        int currentAttempts = (user.getFailedAttempt() != null ? user.getFailedAttempt() : 0) + 1;
        if (currentAttempts >= MAX_ATTEMPTS) {
            userService.userAccountLock(user);
            log.warn("Account locked for email={} after {} failed attempts", email, currentAttempts);

            long blockDuration = bruteForceProtection.getBlockDurationMinutes("user:" + email);
            if (blockDuration >= DEACTIVATE_THRESHOLD_MINUTES) {
                user.setIsEnable(false);
                userService.updateUser(user);
                log.warn("Account DEACTIVATED (banned) for email={}, blockDuration={}min", email, blockDuration);
            }
        }
    }

    private String resolveErrorMessage(HttpServletRequest request, AuthenticationException exception,
                                       String clientIp, String email, UserDtls user) {

        // 1) Banned account
        if (user != null && Boolean.FALSE.equals(user.getIsEnable())) {
            log.info("Login attempt on banned account: email={}", email);
            return "บัญชีของคุณถูกระงับถาวร (แบน) "
                    + "เนื่องจากกรอกรหัสผ่านผิดซ้ำจนเกินกำหนด "
                    + "กรุณาติดต่อผู้ดูแลระบบเพื่อเปิดใช้งานบัญชีอีกครั้ง";
        }

        // 2) IP blocked
        if (bruteForceProtection.isBlocked("ip:" + clientIp)) {
            long totalDuration = bruteForceProtection.getBlockDurationMinutes("ip:" + clientIp);
            long blockedUntil = bruteForceProtection.getBlockedUntilMillis("ip:" + clientIp);
            request.getSession().setAttribute("blockedUntilMillis", blockedUntil);
            log.info("IP blocked: ip={}, duration={}min", clientIp, totalDuration);
            return "IP ของคุณถูกระงับชั่วคราว " + totalDuration + " นาที "
                    + "เนื่องจากพยายามเข้าสู่ระบบผิดพลาดหลายครั้ง";
        }

        // 3) User blocked (in-memory)
        if (email != null && bruteForceProtection.isBlocked("user:" + email)) {
            long totalDuration = bruteForceProtection.getBlockDurationMinutes("user:" + email);
            long blockedUntil = bruteForceProtection.getBlockedUntilMillis("user:" + email);
            request.getSession().setAttribute("blockedUntilMillis", blockedUntil);
            log.info("User blocked: email={}, duration={}min", email, totalDuration);
            return "บัญชีถูกระงับชั่วคราว " + totalDuration + " นาที "
                    + "เนื่องจากกรอกรหัสผ่านผิดเกิน " + MAX_ATTEMPTS + " ครั้ง";
        }

        // 4) LockedException from DB (pattern matching Java 16+)
        if (exception instanceof LockedException) {
            return resolveLockedMessage(request, email);
        }

        // 5) Normal failure — show remaining attempts
        return resolveAttemptMessage(clientIp, email);
    }

    private String resolveLockedMessage(HttpServletRequest request, String email) {
        if (email != null) {
            UserDtls lockedUser = userService.getUserByEmail(email);
            if (lockedUser != null && lockedUser.getLockTime() != null) {
                long blockDuration = bruteForceProtection.getBlockDurationMinutes("user:" + email);
                if (blockDuration <= 0) blockDuration = 15;
                long lockTimeMs = lockedUser.getLockTime().getTime();
                long unlockTimeMs = lockTimeMs + (blockDuration * 60 * 1000);
                request.getSession().setAttribute("blockedUntilMillis", unlockTimeMs);
                return "บัญชีถูกระงับชั่วคราว " + blockDuration + " นาที "
                        + "เนื่องจากกรอกรหัสผ่านผิดเกิน " + MAX_ATTEMPTS + " ครั้ง";
            }
        }
        return "บัญชีของคุณถูกระงับชั่วคราว กรุณารอสักครู่แล้วลองใหม่";
    }

    private String resolveAttemptMessage(String clientIp, String email) {
        int remaining = email != null
                ? bruteForceProtection.getRemainingAttempts("user:" + email)
                : bruteForceProtection.getRemainingAttempts("ip:" + clientIp);
        int used = MAX_ATTEMPTS - remaining;
        if (remaining <= 0) {
            return "อีเมลหรือรหัสผ่านไม่ถูกต้อง (ครั้งที่ " + MAX_ATTEMPTS + "/" + MAX_ATTEMPTS + " — บัญชีจะถูกระงับ)";
        }
        return "อีเมลหรือรหัสผ่านไม่ถูกต้อง (ครั้งที่ " + used + "/" + MAX_ATTEMPTS + ")";
    }

    private void publishFailureEvent(String clientIp, String email, String errorMessage) {
        log.debug("Auth failure: ip={}, email={}, reason={}", clientIp,
                email != null ? email : "N/A", errorMessage);
        // eventPublisher available for future AuthenticationFailureEvent publishing
        // e.g. eventPublisher.publishEvent(new CustomAuthFailureEvent(...));
    }

    /**
     * Sanitize email to prevent log injection (CRLF) and strip whitespace.
     * Returns null if input is blank.
     */
    static String sanitizeEmail(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.strip()
                .replaceAll("[\\r\\n\\t]", "")
                .toLowerCase();
    }
}
