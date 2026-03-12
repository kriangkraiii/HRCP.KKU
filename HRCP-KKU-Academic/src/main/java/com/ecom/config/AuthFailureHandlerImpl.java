package com.ecom.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private BruteForceProtection bruteForceProtection;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private UserService userService;

    private static final long DEACTIVATE_THRESHOLD_MINUTES = 60;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String clientIp = getClientIp(request);
        String email = request.getParameter("email");

        // Record failure by IP
        bruteForceProtection.recordFailure("ip:" + clientIp);

        // Record failure by username (if provided) + sync to DB
        if (email != null && !email.isBlank()) {
            bruteForceProtection.recordFailure("user:" + email);

            // Sync failed attempts to DB for persistence across restarts
            UserDtls user = userService.getUserByEmail(email);
            if (user != null) {
                userService.increaseFailedAttempt(user);

                // Lock account in DB if threshold reached
                if (user.getFailedAttempt() >= 5) {
                    userService.userAccountLock(user);

                    // Deactivate account if lock duration >= 60 minutes
                    long blockDuration = bruteForceProtection.getBlockDurationMinutes("user:" + email);
                    if (blockDuration >= DEACTIVATE_THRESHOLD_MINUTES) {
                        user.setIsEnable(false);
                        userService.updateUser(user);
                    }
                }
            }
        }

        String errorMessage;

        // Check if account was deactivated (banned) due to excessive failed attempts
        if (email != null && !email.isBlank()) {
            UserDtls user = userService.getUserByEmail(email);
            if (user != null && Boolean.FALSE.equals(user.getIsEnable())) {
                errorMessage = "บัญชีของคุณถูกระงับถาวร (แบน) "
                        + "เนื่องจากกรอกรหัสผ่านผิดซ้ำจนเกินกำหนด "
                        + "กรุณาติดต่อผู้ดูแลระบบเพื่อเปิดใช้งานบัญชีอีกครั้ง";
                setDefaultFailureUrl("/signin?error");
                super.onAuthenticationFailure(request, response, exception);
                request.getSession().setAttribute("errorMessage", errorMessage);
                return;
            }
        }

        // Check if IP is now blocked
        if (bruteForceProtection.isBlocked("ip:" + clientIp)) {
            long totalDuration = bruteForceProtection.getBlockDurationMinutes("ip:" + clientIp);
            long blockedUntil = bruteForceProtection.getBlockedUntilMillis("ip:" + clientIp);
            errorMessage = "IP ของคุณถูกระงับชั่วคราว " + totalDuration + " นาที "
                    + "เนื่องจากพยายามเข้าสู่ระบบผิดพลาดหลายครั้ง";
            request.getSession().setAttribute("blockedUntilMillis", blockedUntil);
        } else if (email != null && bruteForceProtection.isBlocked("user:" + email)) {
            long totalDuration = bruteForceProtection.getBlockDurationMinutes("user:" + email);
            long blockedUntil = bruteForceProtection.getBlockedUntilMillis("user:" + email);
            errorMessage = "บัญชีถูกระงับชั่วคราว " + totalDuration + " นาที "
                    + "เนื่องจากกรอกรหัสผ่านผิดเกิน 5 ครั้ง";
            request.getSession().setAttribute("blockedUntilMillis", blockedUntil);
        } else if (exception instanceof LockedException) {
            // Calculate remaining lockout time from DB + BruteForceProtection
            if (email != null && !email.isBlank()) {
                UserDtls lockedUser = userService.getUserByEmail(email);
                if (lockedUser != null && lockedUser.getLockTime() != null) {
                    long blockDuration = bruteForceProtection.getBlockDurationMinutes("user:" + email);
                    if (blockDuration <= 0) blockDuration = 15;
                    long lockTimeMs = lockedUser.getLockTime().getTime();
                    long unlockTimeMs = lockTimeMs + (blockDuration * 60 * 1000);

                    errorMessage = "บัญชีถูกระงับชั่วคราว " + blockDuration + " นาที "
                            + "เนื่องจากกรอกรหัสผ่านผิดเกิน 5 ครั้ง";
                    request.getSession().setAttribute("blockedUntilMillis", unlockTimeMs);
                } else {
                    errorMessage = "บัญชีของคุณถูกระงับชั่วคราว กรุณารอสักครู่แล้วลองใหม่";
                }
            } else {
                errorMessage = "บัญชีของคุณถูกระงับชั่วคราว กรุณารอสักครู่แล้วลองใหม่";
            }
        } else {
            // Show attempt count
            int remaining = email != null
                    ? bruteForceProtection.getRemainingAttempts("user:" + email)
                    : bruteForceProtection.getRemainingAttempts("ip:" + clientIp);
            int used = 5 - remaining;
            if (remaining <= 0) {
                errorMessage = "อีเมลหรือรหัสผ่านไม่ถูกต้อง (ครั้งที่ 5/5 — บัญชีจะถูกระงับ)";
            } else {
                errorMessage = "อีเมลหรือรหัสผ่านไม่ถูกต้อง (ครั้งที่ " + used + "/5)";
            }
        }

        setDefaultFailureUrl("/signin?error");
        super.onAuthenticationFailure(request, response, exception);

        request.getSession().setAttribute("errorMessage", errorMessage);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
