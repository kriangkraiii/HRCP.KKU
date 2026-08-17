package com.ecom.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.service.SignInService;
import com.ecom.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after a password has been accepted.
 *
 * <p>Only the parts specific to password login live here — clearing brute-force
 * counters and the lock state that only password attempts can set. Deciding
 * about a second factor, establishing the session and writing the audit entry
 * are common to every way in and belong to {@link SignInService}, so that an
 * account with 2FA enabled is treated the same whether the person arrived here
 * or through KKU SSO.
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final BruteForceProtection bruteForceProtection;
    private final UserService userService;
    private final SignInService signInService;

    public CustomAuthenticationSuccessHandler(BruteForceProtection bruteForceProtection,
                                              UserService userService,
                                              SignInService signInService) {
        this.bruteForceProtection = bruteForceProtection;
        this.userService = userService;
        this.signInService = signInService;
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
        if (user == null) {
            // The row backing the credential disappeared mid-request. Everything
            // downstream reads that row, so there is no session to hand out.
            response.sendRedirect("/signin?expired=true");
            return;
        }

        if (user.getFailedAttempt() != null && user.getFailedAttempt() > 0) {
            user.setFailedAttempt(0);
            user.setAccountNonLocked(true);
            user.setLockTime(null);
            userService.updateUser(user);
        }
        // Update last login date
        user.setLastLoginDate(LocalDateTime.now());
        userService.updateUser(user);

        // === 2FA CHECK ===
        if (signInService.requiresTwoFactor(user)) {
            signInService.startTwoFactor(request, user, SignInService.Method.PASSWORD, null);
            response.sendRedirect(SignInService.VERIFY_PATH);
            return;
        }

        // The password filter chain already produced the authentication; all that
        // is left is the session rotation, the audit entry and the destination.
        request.changeSessionId();
        signInService.recordSignIn(request, user, SignInService.Method.PASSWORD);
        response.sendRedirect(signInService.landingPageFor(user));
    }
}
