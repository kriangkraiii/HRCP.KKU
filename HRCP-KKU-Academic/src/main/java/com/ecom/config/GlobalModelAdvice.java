package com.ecom.config;

import java.security.Principal;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ecom.academic.model.AcademicRank;
import com.ecom.model.UserDtls;
import com.ecom.service.UserService;
import com.ecom.util.NameTitleOption;

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

        // The four academic ranks, for the position pickers in admin/_academic_position_fields.html.
        // Serving them from the enum is what keeps the Thai option and its English
        // counterpart from drifting apart — they used to be hardcoded in five
        // templates that no longer agreed with each other. It is a constant list,
        // so there is nothing to look up and no reason to scope it to a few pages.
        model.addAttribute("academicRanks", java.util.List.of(AcademicRank.values()));

        // The same four, as plain label lists. The fragment needs to ask "is this
        // stored value one of the four?" so it can keep an off-list value (say
        // "อาจารย์ ดร." from a directory sync) as an option instead of dropping it
        // on the next save. A SpEL selection over academicRanks cannot ask that:
        // inside .?[...] the root becomes the element, so the fragment parameter
        // holding the stored value is out of scope. #lists.contains over these
        // works, and is easier to read than what it replaces.
        model.addAttribute("academicRankThaiLabels",
                java.util.Arrays.stream(AcademicRank.values()).map(AcademicRank::thaiLabel).toList());
        model.addAttribute("academicRankEnglishLabels",
                java.util.Arrays.stream(AcademicRank.values()).map(AcademicRank::englishLabel).toList());

        // คำนำหน้าชื่อ สำหรับ admin/_title_field.html ด้วยเหตุผลเดียวกัน — รายการนี้เคยถูก
        // hardcode 4 ที่แล้วไม่ตรงกัน จนหน้าโปรไฟล์แอดมินลบคำนำหน้าของตัวเองทิ้งได้
        model.addAttribute("nameTitleOptions", NameTitleOption.all());
        model.addAttribute("nameTitleKnownValues", NameTitleOption.knownValues());

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
