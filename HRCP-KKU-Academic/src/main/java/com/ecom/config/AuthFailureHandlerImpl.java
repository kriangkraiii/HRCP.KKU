package com.ecom.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthFailureHandlerImpl extends SimpleUrlAuthenticationFailureHandler {

    @Autowired
    private BruteForceProtection bruteForceProtection;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String clientIp = getClientIp(request);
        String email = request.getParameter("email");

        // Record failure by IP
        bruteForceProtection.recordFailure("ip:" + clientIp);

        // Record failure by username (if provided)
        if (email != null && !email.isBlank()) {
            bruteForceProtection.recordFailure("user:" + email);
        }

        String errorMessage;

        // Check if IP is now blocked
        if (bruteForceProtection.isBlocked("ip:" + clientIp)) {
            long minutes = bruteForceProtection.getBlockMinutesRemaining("ip:" + clientIp);
            errorMessage = "IP ของคุณถูกระงับชั่วคราว กรุณารอ " + minutes + " นาที";
        } else if (email != null && bruteForceProtection.isBlocked("user:" + email)) {
            long minutes = bruteForceProtection.getBlockMinutesRemaining("user:" + email);
            errorMessage = "บัญชีถูกระงับชั่วคราว กรุณารอ " + minutes + " นาที";
        } else if (exception instanceof LockedException) {
            errorMessage = "บัญชีของคุณถูกล็อก! เข้าสู่ระบบผิดพลาด 3 ครั้ง";
        } else {
            errorMessage = "อีเมลหรือรหัสผ่านไม่ถูกต้อง";
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
