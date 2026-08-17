package com.ecom.config;

import java.security.Principal;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;

import jakarta.servlet.http.HttpSession;

@ControllerAdvice
public class GlobalModelAdvice {

    private final UserService userService;
    private final com.ecom.service.NotificationService notificationService;
    private final AuthModeProperties authProperties;

    public GlobalModelAdvice(UserService userService,
            com.ecom.service.NotificationService notificationService,
            AuthModeProperties authProperties) {
        this.userService = userService;
        this.notificationService = notificationService;
        this.authProperties = authProperties;
    }

    @ModelAttribute
    public void addGlobalAttributes(jakarta.servlet.http.HttpServletRequest request, Principal principal, Model model) {
        // Drives which sign-in controls the login page renders.
        model.addAttribute("ssoMode", authProperties.isSsoMode());

        // Expose CSP Nonce for templates
        if (request != null) {
            String nonce = (String) request.getAttribute("cspNonce");
            if (nonce != null) {
                model.addAttribute("cspNonce", nonce);
            }
        }

        // Add logged-in user to model for ALL controllers
        if (principal != null) {
            UserDtls user = userService.getUserByEmail(principal.getName());
            if (user != null) {
                model.addAttribute("user", user);
                model.addAttribute("unreadNotificationCount", notificationService.getUnreadCount(user));
            }
        }

        // Transfer session messages to model and clear them only if a session exists
        if (request != null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                String succMsg = (String) session.getAttribute("succMsg");
                String errorMsg = (String) session.getAttribute("errorMsg");

                if (succMsg != null) {
                    model.addAttribute("succMsg", succMsg);
                    session.removeAttribute("succMsg");
                }
                if (errorMsg != null) {
                    model.addAttribute("errorMsg", errorMsg);
                    session.removeAttribute("errorMsg");
                }
            }
        }
    }
}
