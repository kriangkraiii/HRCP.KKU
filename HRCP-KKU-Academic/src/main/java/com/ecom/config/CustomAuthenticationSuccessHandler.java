package com.ecom.config;

import java.io.IOException;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private BruteForceProtection bruteForceProtection;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        // Clear brute-force records on successful login
        String clientIp = getClientIp(request);
        String email = request.getParameter("email");
        bruteForceProtection.resetAttempts("ip:" + clientIp);
        if (email != null) {
            bruteForceProtection.resetAttempts("user:" + email);
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

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
