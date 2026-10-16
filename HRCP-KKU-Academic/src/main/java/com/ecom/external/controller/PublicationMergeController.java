package com.ecom.external.controller;

import java.security.Principal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.config.ClientIpUtils;
import com.ecom.external.service.PublicationMergeService;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ตรวจและรวมผลงานตีพิมพ์ที่ซ้ำกันในฐานข้อมูล — แสดงรายการให้แอดมินดูก่อน แล้วรวมเมื่อกดยืนยัน
 * (docs/PLAN-bughunt-decisions.md ข้อ 2b)
 */
@Controller
@RequestMapping("/admin/external-sync/publication-duplicates")
@PreAuthorize("hasRole('ADMIN')")
public class PublicationMergeController {

    private static final String REDIRECT = "redirect:/admin/external-sync/publication-duplicates";

    private final PublicationMergeService mergeService;
    private final AdminLogService adminLogService;

    public PublicationMergeController(PublicationMergeService mergeService, AdminLogService adminLogService) {
        this.mergeService = mergeService;
        this.adminLogService = adminLogService;
    }

    @GetMapping
    public String review(Model model) {
        model.addAttribute("review", mergeService.findDuplicates());
        return "admin/publication_duplicates";
    }

    @PostMapping("/merge")
    public String merge(Principal principal, HttpServletRequest request, RedirectAttributes redirect) {
        int hidden = mergeService.mergeAll();
        if (hidden == 0) {
            redirect.addFlashAttribute("warnMsg", "ไม่พบผลงานซ้ำที่รวมได้");
            return REDIRECT;
        }
        String who = principal != null ? principal.getName() : "unknown";
        adminLogService.log(who, who, "MERGE_PUBLICATIONS",
                "รวมผลงานตีพิมพ์ซ้ำ: ซ่อน " + hidden + " แถว (ย้ายลิงก์ของคำร้องไปแถวที่เหลือแล้ว)",
                ClientIpUtils.resolveClientIp(request));
        redirect.addFlashAttribute("succMsg", "รวมผลงานซ้ำแล้ว " + hidden + " รายการ");
        return REDIRECT;
    }
}
