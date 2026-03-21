package com.ecom.academic.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.TwoFactorService;

@Controller
public class AcademicSettingsController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TwoFactorService twoFactorService;

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

    // =================== 2FA AJAX Endpoints ===================

    /** Send OTP to user's email for verification */
    @PostMapping({"/user/academic/settings/send-email-otp", "/admin/academic/settings/send-email-otp"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendEmailOtp(Principal principal) {
        try {
            UserDtls user = userRepository.findByEmail(principal.getName());
            twoFactorService.generateOtp(user);
            twoFactorService.sendOtpEmail(user, "EMAIL_VERIFY");
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "ส่ง OTP ไปที่อีเมลของคุณแล้ว"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false,
                    "message", "เกิดข้อผิดพลาดในการส่ง OTP"));
        }
    }

    /** Verify email OTP */
    @PostMapping({"/user/academic/settings/verify-email-otp", "/admin/academic/settings/verify-email-otp"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyEmailOtp(
            @RequestParam("otp") String otp,
            Principal principal) {
        UserDtls user = userRepository.findByEmail(principal.getName());
        String result = twoFactorService.verifyOtp(user, otp.trim());

        switch (result) {
            case "OK":
                user.setEmailVerified(true);
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("success", true,
                        "message", "ยืนยันอีเมลสำเร็จ"));
            case "EXPIRED":
                return ResponseEntity.ok(Map.of("success", false,
                        "message", "รหัส OTP หมดอายุแล้ว กรุณาส่งรหัสใหม่"));
            default:
                return ResponseEntity.ok(Map.of("success", false,
                        "message", "รหัส OTP ไม่ถูกต้อง"));
        }
    }

    /** Toggle 2FA on/off */
    @PostMapping({"/user/academic/settings/toggle-2fa", "/admin/academic/settings/toggle-2fa"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggle2fa(
            @RequestParam("enabled") Boolean enabled,
            Principal principal) {
        UserDtls user = userRepository.findByEmail(principal.getName());

        if (Boolean.TRUE.equals(enabled) && !Boolean.TRUE.equals(user.getEmailVerified())) {
            return ResponseEntity.ok(Map.of("success", false,
                    "message", "ต้องยืนยันอีเมลก่อนจึงจะเปิด 2FA ได้"));
        }

        user.setTwoFactorEnabled(enabled);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true,
                "message", enabled ? "เปิดใช้งาน 2FA แล้ว" : "ปิดใช้งาน 2FA แล้ว"));
    }

    // =================== Private Helper ===================

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

    // =================== AJAX Auto-save Setting ===================

    @PostMapping({"/user/academic/settings/toggle-setting", "/admin/academic/settings/toggle-setting"})
    @ResponseBody
    public ResponseEntity<?> toggleSetting(
            @RequestParam String key,
            @RequestParam String value,
            Principal principal) {
        try {
            UserDtls user = userRepository.findByEmail(principal.getName());
            switch (key) {
                case "autoDraftEnabled" -> user.setAutoDraftEnabled(Boolean.parseBoolean(value));
                case "emailNotificationEnabled" -> user.setEmailNotificationEnabled(Boolean.parseBoolean(value));
                case "expiryAlert6m" -> user.setExpiryAlert6m(Boolean.parseBoolean(value));
                case "expiryAlert3m" -> user.setExpiryAlert3m(Boolean.parseBoolean(value));
                case "expiryAlert1m" -> user.setExpiryAlert1m(Boolean.parseBoolean(value));
                case "expiryAlert1w" -> user.setExpiryAlert1w(Boolean.parseBoolean(value));
                case "themePreference" -> {
                    if ("light".equals(value) || "dark".equals(value) || "system".equals(value)) {
                        user.setThemePreference(value);
                    } else {
                        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid theme value"));
                    }
                }
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "ไม่รู้จักการตั้งค่า: " + key));
                }
            }

            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "บันทึกแล้ว"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "เกิดข้อผิดพลาด: " + e.getMessage()));
        }
    }
}

