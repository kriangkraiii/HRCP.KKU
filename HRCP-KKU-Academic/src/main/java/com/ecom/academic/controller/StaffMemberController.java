package com.ecom.academic.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/academic/staff")
public class StaffMemberController {

    @Autowired
    private StaffMemberService staffMemberService;

    @Autowired
    private AdminLogService adminLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HttpServletRequest httpRequest;

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
            java.security.Principal principal) {
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
        } catch (Exception ignored) {}

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
        } catch (Exception ignored) {}

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
        } catch (Exception ignored) {}

        staffMemberService.softDelete(id);
        return "redirect:/admin/academic/staff?success=deleted";
    }

    @GetMapping("/api/by-role")
    @ResponseBody
    public ResponseEntity<List<StaffMember>> getByRole(@RequestParam("role") String role) {
        return ResponseEntity.ok(staffMemberService.findByRole(role));
    }

    private String getClientIpAddress() {
        String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        return httpRequest.getRemoteAddr();
    }
}
