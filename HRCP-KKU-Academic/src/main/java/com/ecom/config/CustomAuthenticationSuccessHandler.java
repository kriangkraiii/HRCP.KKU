package com.ecom.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final BruteForceProtection bruteForceProtection;
    private final UserService userService;

    public CustomAuthenticationSuccessHandler(BruteForceProtection bruteForceProtection,
                                              UserService userService) {
        this.bruteForceProtection = bruteForceProtection;
        this.userService = userService;
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

        // Regenerate session to prevent session fixation
        request.changeSessionId();

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
        response.sendRedirect(redirectUrl);
    }
}
