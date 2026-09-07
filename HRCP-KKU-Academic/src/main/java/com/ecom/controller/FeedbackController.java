package com.ecom.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.model.UserDtls;
import com.ecom.service.FeedbackService;
import com.ecom.service.UserService;

/**
 * Handles the feedback / issue-report page for ROLE_USER.
 *
 * <p>GET  /user/feedback — shows the form<br>
 * POST /user/feedback — sends the email and redirects back
 */
@Controller
@RequestMapping("/user/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private static final List<String> CATEGORIES = List.of("ปัญหาระบบ", "ข้อเสนอแนะ", "อื่นๆ");

    private final FeedbackService feedbackService;
    private final UserService userService;

    public FeedbackController(FeedbackService feedbackService, UserService userService) {
        this.feedbackService = feedbackService;
        this.userService = userService;
    }

    @GetMapping
    public String showForm(Principal principal, Model model) {
        UserDtls user = userService.getUserByEmail(principal.getName());
        model.addAttribute("user", user);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("maxImages", feedbackService.getMaxImages());
        return "academic/applicant/feedback";
    }

    @PostMapping
    public String submitFeedback(
            Principal principal,
            @RequestParam("category") String category,
            @RequestParam("subject") String subject,
            @RequestParam("detail") String detail,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            RedirectAttributes redirectAttributes) {

        UserDtls user = userService.getUserByEmail(principal.getName());

        // --- Validation ---
        if (subject == null || subject.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMsg", "กรุณากรอกหัวข้อ");
            redirectAttributes.addFlashAttribute("error", "กรุณากรอกหัวข้อ");
            return "redirect:/user/feedback";
        }
        if (subject.length() > 200) {
            redirectAttributes.addFlashAttribute("errorMsg", "หัวข้อต้องไม่เกิน 200 ตัวอักษร");
            redirectAttributes.addFlashAttribute("error", "หัวข้อต้องไม่เกิน 200 ตัวอักษร");
            return "redirect:/user/feedback";
        }
        if (detail == null || detail.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMsg", "กรุณากรอกรายละเอียด");
            redirectAttributes.addFlashAttribute("error", "กรุณากรอกรายละเอียด");
            return "redirect:/user/feedback";
        }
        if (detail.length() > 2000) {
            redirectAttributes.addFlashAttribute("errorMsg", "รายละเอียดต้องไม่เกิน 2,000 ตัวอักษร");
            redirectAttributes.addFlashAttribute("error", "รายละเอียดต้องไม่เกิน 2,000 ตัวอักษร");
            return "redirect:/user/feedback";
        }
        if (!CATEGORIES.contains(category)) {
            redirectAttributes.addFlashAttribute("errorMsg", "ประเภทไม่ถูกต้อง");
            redirectAttributes.addFlashAttribute("error", "ประเภทไม่ถูกต้อง");
            return "redirect:/user/feedback";
        }
        if (!feedbackService.hasRecipients()) {
            redirectAttributes.addFlashAttribute("errorMsg", "ระบบยังไม่ได้กำหนดอีเมลผู้รับเรื่อง กรุณาติดต่อผู้ดูแลระบบ");
            redirectAttributes.addFlashAttribute("error", "ระบบยังไม่ได้กำหนดอีเมลผู้รับเรื่อง กรุณาติดต่อผู้ดูแลระบบ");
            return "redirect:/user/feedback";
        }

        // Filter out empty file inputs
        List<MultipartFile> validImages = new ArrayList<>();
        if (images != null) {
            for (MultipartFile img : images) {
                if (img != null && !img.isEmpty()) {
                    validImages.add(img);
                }
            }
        }

        // Validate image count
        if (validImages.size() > feedbackService.getMaxImages()) {
            String msg = "แนบภาพได้สูงสุด " + feedbackService.getMaxImages() + " ภาพ";
            redirectAttributes.addFlashAttribute("errorMsg", msg);
            redirectAttributes.addFlashAttribute("error", msg);
            return "redirect:/user/feedback";
        }

        // Validate image size and type
        for (MultipartFile img : validImages) {
            if (img.getSize() > feedbackService.getMaxImageBytes()) {
                String msg = "ขนาดภาพ \"" + img.getOriginalFilename() + "\" เกินขีดจำกัด (สูงสุด 5MB ต่อไฟล์)";
                redirectAttributes.addFlashAttribute("errorMsg", msg);
                redirectAttributes.addFlashAttribute("error", msg);
                return "redirect:/user/feedback";
            }
            String contentType = img.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                String msg = "ไฟล์ \"" + img.getOriginalFilename() + "\" ไม่ใช่รูปภาพ";
                redirectAttributes.addFlashAttribute("errorMsg", msg);
                redirectAttributes.addFlashAttribute("error", msg);
                return "redirect:/user/feedback";
            }
        }

        // --- Send ---
        try {
            feedbackService.sendFeedback(user, category, subject, detail, validImages);
            String successText = "ส่งรายงานปัญหาและข้อเสนอแนะเรียบร้อยแล้ว สำเนาจะถูกส่งไปยังอีเมลของท่านด้วย";
            redirectAttributes.addFlashAttribute("succMsg", successText);
            redirectAttributes.addFlashAttribute("success", successText);
        } catch (Exception e) {
            log.error("Error submitting feedback: {}", e.getMessage(), e);
            String errorText = "ไม่สามารถส่งรายงานได้ในขณะนี้ กรุณาลองใหม่ภายหลัง";
            redirectAttributes.addFlashAttribute("errorMsg", errorText);
            redirectAttributes.addFlashAttribute("error", errorText);
        }

        return "redirect:/user/feedback";
    }
}
