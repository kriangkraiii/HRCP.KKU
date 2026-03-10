package com.ecom.academic.controller;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Controller
public class AcademicSettingsController {

    @Autowired
    private UserRepository userRepository;

    /** User settings page */
    @GetMapping("/user/academic/settings")
    public String userSettings(Principal principal, Model model) {
        UserDtls user = userRepository.findByEmail(principal.getName());
        model.addAttribute("user", user);
        model.addAttribute("settingsBasePath", "/user/academic/settings");
        return "academic/settings";
    }

    /** Admin settings page */
    @GetMapping("/admin/academic/settings")
    public String adminSettings(Principal principal, Model model) {
        UserDtls user = userRepository.findByEmail(principal.getName());
        model.addAttribute("user", user);
        model.addAttribute("settingsBasePath", "/admin/academic/settings");
        return "academic/settings";
    }

    /** Save settings (shared) */
    @PostMapping("/user/academic/settings")
    public String saveUserSettings(
            @RequestParam(value = "autoDraftEnabled", required = false) Boolean autoDraftEnabled,
            @RequestParam(value = "emailNotificationEnabled", required = false) Boolean emailNotificationEnabled,
            @RequestParam(value = "expiryAlert6m", required = false) Boolean expiryAlert6m,
            @RequestParam(value = "expiryAlert3m", required = false) Boolean expiryAlert3m,
            @RequestParam(value = "expiryAlert1m", required = false) Boolean expiryAlert1m,
            @RequestParam(value = "expiryAlert1w", required = false) Boolean expiryAlert1w,
            Principal principal,
            RedirectAttributes redirect) {
        return saveSettings(principal, autoDraftEnabled, emailNotificationEnabled,
                expiryAlert6m, expiryAlert3m, expiryAlert1m, expiryAlert1w,
                redirect, "/user/academic/settings");
    }

    @PostMapping("/admin/academic/settings")
    public String saveAdminSettings(
            @RequestParam(value = "autoDraftEnabled", required = false) Boolean autoDraftEnabled,
            @RequestParam(value = "emailNotificationEnabled", required = false) Boolean emailNotificationEnabled,
            @RequestParam(value = "expiryAlert6m", required = false) Boolean expiryAlert6m,
            @RequestParam(value = "expiryAlert3m", required = false) Boolean expiryAlert3m,
            @RequestParam(value = "expiryAlert1m", required = false) Boolean expiryAlert1m,
            @RequestParam(value = "expiryAlert1w", required = false) Boolean expiryAlert1w,
            Principal principal,
            RedirectAttributes redirect) {
        return saveSettings(principal, autoDraftEnabled, emailNotificationEnabled,
                expiryAlert6m, expiryAlert3m, expiryAlert1m, expiryAlert1w,
                redirect, "/admin/academic/settings");
    }

    private String saveSettings(Principal principal,
            Boolean autoDraftEnabled, Boolean emailNotificationEnabled,
            Boolean expiryAlert6m, Boolean expiryAlert3m,
            Boolean expiryAlert1m, Boolean expiryAlert1w,
            RedirectAttributes redirect, String redirectPath) {
        try {
            UserDtls user = userRepository.findByEmail(principal.getName());
            user.setAutoDraftEnabled(autoDraftEnabled != null);
            user.setEmailNotificationEnabled(emailNotificationEnabled != null);
            user.setExpiryAlert6m(expiryAlert6m != null);
            user.setExpiryAlert3m(expiryAlert3m != null);
            user.setExpiryAlert1m(expiryAlert1m != null);
            user.setExpiryAlert1w(expiryAlert1w != null);
            userRepository.save(user);
            redirect.addFlashAttribute("success", "บันทึกการตั้งค่าสำเร็จ");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "บันทึกไม่สำเร็จ: " + e.getMessage());
        }
        return "redirect:" + redirectPath;
    }
}
