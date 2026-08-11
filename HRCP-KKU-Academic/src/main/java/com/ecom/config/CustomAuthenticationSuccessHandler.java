package com.ecom.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.service.AdminLogService;
import com.ecom.service.TwoFactorService;
import com.ecom.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

    private final BruteForceProtection bruteForceProtection;
    private final UserService userService;
    private final TwoFactorService twoFactorService;
    private final AdminLogService adminLogService;

    public CustomAuthenticationSuccessHandler(BruteForceProtection bruteForceProtection,
                                              UserService userService,
                                              TwoFactorService twoFactorService,
                                              AdminLogService adminLogService) {
        this.bruteForceProtection = bruteForceProtection;
        this.userService = userService;
        this.twoFactorService = twoFactorService;
        this.adminLogService = adminLogService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        // Clear brute-force records on successful login
        String clientIp = ClientIpUtils.resolveClientIp(request);
        String email = request.getParameter("email");
        bruteForceProtection.resetAttempts("ip:" + clientIp);
        if (email != null) {
            bruteForceProtection.resetAttempts("user:" + email);
        }

        // Reset DB failed attempt counter
        String username = authentication.getName();
        UserDtls user = userService.getUserByEmail(username);
        if (user != null) {
            if (user.getFailedAttempt() != null && user.getFailedAttempt() > 0) {
                user.setFailedAttempt(0);
                user.setAccountNonLocked(true);
                user.setLockTime(null);
                userService.updateUser(user);
            }
            // Update last login date
            user.setLastLoginDate(LocalDateTime.now());
            userService.updateUser(user);
        }

        // Determine redirect URL
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String redirectUrl = "/signin";
        for (GrantedAuthority authority : authorities) {
            if (authority.getAuthority().equals("ROLE_ADMIN")) {
                redirectUrl = "/admin/academic/requests";
                break;
            } else if (authority.getAuthority().equals("ROLE_USER")) {
                redirectUrl = "/user/academic/dashboard";
                break;
            }
        }

        // === 2FA CHECK ===
        if (user != null && Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            HttpSession session = request.getSession();
            session.setAttribute("2FA_USER_EMAIL", user.getEmail());
            session.setAttribute("2FA_REDIRECT", redirectUrl);

            // Generate and send OTP
            twoFactorService.generateOtp(user);
            twoFactorService.sendOtpEmail(user, "LOGIN");

            // Clear security context — user is not fully authenticated yet
            SecurityContextHolder.clearContext();

            response.sendRedirect("/2fa/verify");
            return;
        }

        // Log successful login
        try {
            String role = authorities.stream().findFirst().map(GrantedAuthority::getAuthority).orElse("UNKNOWN");
            adminLogService.logWithDetails(username, user != null ? user.getName() : username,
                    "LOGIN_SUCCESS", "เข้าสู่ระบบสำเร็จ (" + role + ")",
                    clientIp, "/signin", request.getHeader("User-Agent"));
        } catch (Exception e) {
            auditLogFailed(e);
        }

        // Regenerate session to prevent session fixation
        request.changeSessionId();
        response.sendRedirect(redirectUrl);
    }

    /** Audit logging must never break the user's action, but it must leave a trace. */
    private void auditLogFailed(Exception e) {
        log.warn("Failed to write audit log: {}", e.toString());
    }
}

