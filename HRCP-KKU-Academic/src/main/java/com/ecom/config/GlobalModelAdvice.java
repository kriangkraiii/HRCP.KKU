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
    private final com.ecom.academic.service.SignatureWorkflowService signatureWorkflowService;

    public GlobalModelAdvice(UserService userService,
            com.ecom.service.NotificationService notificationService,
            AuthModeProperties authProperties,
            com.ecom.academic.service.SignatureWorkflowService signatureWorkflowService,
            com.ecom.sso.KkuSsoProperties ssoProperties) {
        this.userService = userService;
        this.notificationService = notificationService;
        this.authProperties = authProperties;
        this.signatureWorkflowService = signatureWorkflowService;
        this.ssoProperties = ssoProperties;
    }

    private final com.ecom.sso.KkuSsoProperties ssoProperties;

    @ModelAttribute
    public void addGlobalAttributes(jakarta.servlet.http.HttpServletRequest request, Principal principal, Model model) {
        // Drives which sign-in controls the login page renders.
        model.addAttribute("ssoMode", authProperties.isSsoMode());

        // The signed-out page loads this in a hidden frame so the provider's
        // session ends too, without making anyone wait for its app to boot.
        if (authProperties.isSsoMode() && ssoProperties.isConfigured()) {
            model.addAttribute("ssoLogoutUrl", ssoProperties.logoutUrl());
        }

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

                // Drives the "รอลงนาม" sidebar badge. Anyone can be asked to
                // sign, so this is resolved for every signed-in user rather than
                // by role. A failure here must not take down an unrelated page.
                try {
                    model.addAttribute("pendingSignatureCount", signatureWorkflowService.countPending(user));
                } catch (Exception e) {
                    model.addAttribute("pendingSignatureCount", 0L);
                }
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
