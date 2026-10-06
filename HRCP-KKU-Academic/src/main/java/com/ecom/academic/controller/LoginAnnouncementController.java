package com.ecom.academic.controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.config.ClientIpUtils;
import com.ecom.model.LoginAnnouncement;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;
import com.ecom.service.LoginAnnouncementService;

import jakarta.servlet.http.HttpServletRequest;

/** แอดมินพิมพ์ประกาศบนหน้าเข้าสู่ระบบ และเลือกแสดง/ไม่แสดง */
@Controller
@RequestMapping("/admin/academic/settings/login-announcement")
@PreAuthorize("hasRole('ADMIN')")
public class LoginAnnouncementController {

    private static final Logger log = LoggerFactory.getLogger(LoginAnnouncementController.class);

    private final LoginAnnouncementService announcements;
    private final UserRepository userRepository;
    private final AdminLogService adminLogService;

    public LoginAnnouncementController(LoginAnnouncementService announcements, UserRepository userRepository,
            AdminLogService adminLogService) {
        this.announcements = announcements;
        this.userRepository = userRepository;
        this.adminLogService = adminLogService;
    }

    @GetMapping
    public String view(Model model) {
        model.addAttribute("announcement", announcements.current());
        model.addAttribute("maxLength", LoginAnnouncement.MAX_LENGTH);
        return "academic/admin/login_announcement";
    }

    @PostMapping
    public String save(@RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
            @RequestParam(value = "message", required = false) String message,
            Principal principal, HttpServletRequest request, RedirectAttributes redirect) {
        UserDtls admin = userRepository.findByEmail(principal.getName());
        String adminName = admin != null && admin.getName() != null ? admin.getName() : principal.getName();
        try {
            announcements.save(enabled, message, adminName);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMsg", e.getMessage());
            redirect.addFlashAttribute("draftMessage", message);
            return "redirect:/admin/academic/settings/login-announcement";
        }
        try {
            adminLogService.log(principal.getName(), adminName, "LOGIN_ANNOUNCEMENT",
                    (enabled ? "เปิด" : "ปิด") + "ประกาศหน้าเข้าสู่ระบบ",
                    ClientIpUtils.resolveClientIp(request));
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.toString());
        }
        redirect.addFlashAttribute("succMsg",
                enabled ? "บันทึกแล้ว — ประกาศแสดงบนหน้าเข้าสู่ระบบ" : "บันทึกแล้ว — ไม่แสดงประกาศ");
        return "redirect:/admin/academic/settings/login-announcement";
    }
}
