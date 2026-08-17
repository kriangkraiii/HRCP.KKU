package com.ecom.academic.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecom.config.ClientIpUtils;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/academic/staff")
public class StaffMemberController {

    private static final Logger log = LoggerFactory.getLogger(StaffMemberController.class);

    private final StaffMemberService staffMemberService;

    private final AdminLogService adminLogService;

    private final UserRepository userRepository;

    private final HttpServletRequest httpRequest;

    public StaffMemberController(
            StaffMemberService staffMemberService,
            AdminLogService adminLogService,
            UserRepository userRepository,
            HttpServletRequest httpRequest) {
        this.staffMemberService = staffMemberService;
        this.adminLogService = adminLogService;
        this.userRepository = userRepository;
        this.httpRequest = httpRequest;
    }

    @GetMapping("")
    public String listStaff(Model model) {
        model.addAttribute("staffMembers", staffMemberService.findAll());
        return "academic/admin/staff_list";
    }

    @GetMapping("/add")
    public String addStaffForm(Model model) {
        model.addAttribute("staff", new StaffMember());
        return "academic/admin/staff_form";
    }

    @PostMapping("/add")
    public String addStaff(@RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("academicTitle") String academicTitle,
            @RequestParam("staffType") String staffType,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam("staffRole") String staffRole,
            @RequestParam(value = "confirmDuplicate", required = false) Boolean confirmDuplicate,
            Model model,
            java.security.Principal principal) {

        // Two real people can share a name, so this asks rather than refuses —
        // but it asks, because the usual cause is the same person being entered
        // twice and every duplicate becomes an ambiguous choice in every document
        // dropdown from then on.
        if (!Boolean.TRUE.equals(confirmDuplicate)) {
            List<StaffMember> sameName = staffMemberService.findByName(firstName, lastName);
            if (!sameName.isEmpty()) {
                StaffMember staff = new StaffMember();
                staff.setFirstName(firstName);
                staff.setLastName(lastName);
                staff.setAcademicTitle(academicTitle);
                staff.setStaffType(staffType);
                staff.setDepartment(department);
                staff.setStaffRole(staffRole);

                model.addAttribute("staff", staff);
                model.addAttribute("duplicates", sameName);
                model.addAttribute("duplicateWarning",
                        "มีบุคลากรชื่อ \"" + firstName + " " + lastName + "\" อยู่แล้ว "
                                + sameName.size() + " รายการ ตรวจสอบก่อนว่าไม่ใช่คนเดียวกัน");
                return "academic/admin/staff_form";
            }
        }

        StaffMember staff = new StaffMember();
        staff.setFirstName(firstName);
        staff.setLastName(lastName);
        staff.setAcademicTitle(academicTitle);
        staff.setStaffType(staffType);
        staff.setDepartment(department);
        staff.setStaffRole(staffRole);
        staff.setIsActive(true);
        staffMemberService.save(staff);

        // Log activity
        try {
            UserDtls admin = userRepository.findByEmail(principal.getName());
            adminLogService.log(principal.getName(),
                    admin != null ? admin.getName() : principal.getName(),
                    "ADD_STAFF",
                    "เพิ่มบุคลากร: " + academicTitle + firstName + " " + lastName + " (ตำแหน่ง: " + staffRole + ")",
                    getClientIpAddress());
        } catch (Exception e) {
            auditLogFailed(e);
        }

        return "redirect:/admin/academic/staff?success=added";
    }

    @GetMapping("/edit/{id}")
    public String editStaffForm(@PathVariable Long id, Model model) {
        StaffMember staff = staffMemberService.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        model.addAttribute("staff", staff);
        return "academic/admin/staff_form";
    }

    @PostMapping("/edit/{id}")
    public String editStaff(@PathVariable Long id,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("academicTitle") String academicTitle,
            @RequestParam("staffType") String staffType,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam("staffRole") String staffRole,
            java.security.Principal principal) {
        StaffMember staff = staffMemberService.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        staff.setFirstName(firstName);
        staff.setLastName(lastName);
        staff.setAcademicTitle(academicTitle);
        staff.setStaffType(staffType);
        staff.setDepartment(department);
        staff.setStaffRole(staffRole);
        staffMemberService.save(staff);

        // Log activity
        try {
            UserDtls admin = userRepository.findByEmail(principal.getName());
            adminLogService.log(principal.getName(),
                    admin != null ? admin.getName() : principal.getName(),
                    "EDIT_STAFF",
                    "แก้ไขบุคลากร ID:" + id + " (" + firstName + " " + lastName + ")",
                    getClientIpAddress());
        } catch (Exception e) {
            auditLogFailed(e);
        }

        return "redirect:/admin/academic/staff?success=updated";
    }

    @PostMapping("/delete/{id}")
    public String deleteStaff(@PathVariable Long id, java.security.Principal principal) {
        // Log before deletion
        try {
            StaffMember staff = staffMemberService.findById(id).orElse(null);
            UserDtls admin = userRepository.findByEmail(principal.getName());
            adminLogService.log(principal.getName(),
                    admin != null ? admin.getName() : principal.getName(),
                    "DELETE_STAFF",
                    "ลบบุคลากร ID:" + id
                            + (staff != null ? " (" + staff.getFirstName() + " " + staff.getLastName() + ")" : ""),
                    getClientIpAddress());
        } catch (Exception e) {
            auditLogFailed(e);
        }

        staffMemberService.softDelete(id);
        return "redirect:/admin/academic/staff?success=deleted";
    }

    @GetMapping("/api/by-role")
    @ResponseBody
    public ResponseEntity<List<StaffMember>> getByRole(@RequestParam("role") String role) {
        return ResponseEntity.ok(staffMemberService.findByRole(role));
    }

    private String getClientIpAddress() {
        return ClientIpUtils.resolveClientIp(httpRequest);
    }

    /** Audit logging must never break the user's action, but it must leave a trace. */
    private void auditLogFailed(Exception e) {
        log.warn("Failed to write audit log: {}", e.toString());
    }
}
