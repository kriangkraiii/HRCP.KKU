package com.ecom.academic.controller;

import java.security.Principal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.academic.model.AcademicCommitteeMember;
import com.ecom.academic.model.CommitteeType;
import com.ecom.academic.service.AcademicCommitteeService;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/academic/committee")
@PreAuthorize("hasRole('ADMIN')")
public class AcademicCommitteeController {

    private static final Logger log = LoggerFactory.getLogger(AcademicCommitteeController.class);

    private final AcademicCommitteeService committeeService;
    private final AdminLogService adminLogService;
    private final HttpServletRequest httpRequest;

    public AcademicCommitteeController(
            AcademicCommitteeService committeeService,
            AdminLogService adminLogService,
            HttpServletRequest httpRequest) {
        this.committeeService = committeeService;
        this.adminLogService = adminLogService;
        this.httpRequest = httpRequest;
    }

    @GetMapping
    public String listCommittee(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CommitteeType type,
            Model model) {

        List<AcademicCommitteeMember> members;
        if (keyword != null && !keyword.isBlank()) {
            members = committeeService.search(keyword);
        } else if (type != null) {
            members = committeeService.findByType(type);
        } else {
            members = committeeService.findAllActive();
        }

        model.addAttribute("members", members);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedType", type);
        model.addAttribute("committeeTypes", CommitteeType.values());
        model.addAttribute("newMember", new AcademicCommitteeMember());

        return "academic/admin/committee";
    }

    @PostMapping("/save")
    public String saveMember(
            @ModelAttribute AcademicCommitteeMember member,
            Principal principal,
            RedirectAttributes redirect) {

        try {
            boolean isNew = (member.getId() == null);
            committeeService.save(member);

            String adminEmail = principal != null ? principal.getName() : "admin@kku.ac.th";
            adminLogService.log(
                    adminEmail,
                    adminEmail,
                    isNew ? "CREATE_COMMITTEE" : "UPDATE_COMMITTEE",
                    (isNew ? "เพิ่มข้อมูลกรรมการ: " : "แก้ไขข้อมูลกรรมการ: ") + member.getFullName() + " (" + member.getEmail() + ")",
                    httpRequest.getRemoteAddr());

            redirect.addFlashAttribute("succMsg", isNew ? "เพิ่มข้อมูลกรรมการสำเร็จ" : "บันทึกการแก้ไขเรียบร้อยแล้ว");
        } catch (Exception e) {
            log.error("Failed to save committee member: {}", e.getMessage());
            redirect.addFlashAttribute("errorMsg", "เกิดข้อผิดพลาดในการบันทึก: " + e.getMessage());
        }

        return "redirect:/admin/academic/committee";
    }

    @PostMapping("/{id}/delete")
    public String deleteMember(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirect) {

        try {
            var opt = committeeService.findById(id);
            if (opt.isPresent()) {
                committeeService.delete(id);
                String adminEmail = principal != null ? principal.getName() : "admin@kku.ac.th";
                adminLogService.log(
                        adminEmail,
                        adminEmail,
                        "DELETE_COMMITTEE",
                        "ลบข้อมูลกรรมการ: " + opt.get().getFullName(),
                        httpRequest.getRemoteAddr());
                redirect.addFlashAttribute("succMsg", "ลบข้อมูลกรรมการเรียบร้อยแล้ว");
            } else {
                redirect.addFlashAttribute("errorMsg", "ไม่พบข้อมูลกรรมการ");
            }
        } catch (Exception e) {
            log.error("Failed to delete committee member: {}", e.getMessage());
            redirect.addFlashAttribute("errorMsg", "เกิดข้อผิดพลาดในการลบ: " + e.getMessage());
        }

        return "redirect:/admin/academic/committee";
    }
}
