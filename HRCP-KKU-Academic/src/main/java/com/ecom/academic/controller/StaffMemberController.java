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

@Controller
@RequestMapping("/admin/academic/staff")
public class StaffMemberController {

    @Autowired
    private StaffMemberService staffMemberService;

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
    public String addStaff(@RequestParam("fullName") String fullName,
            @RequestParam("academicTitle") String academicTitle,
            @RequestParam("staffType") String staffType,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam("staffRole") String staffRole) {
        StaffMember staff = new StaffMember();
        staff.setFullName(fullName);
        staff.setAcademicTitle(academicTitle);
        staff.setStaffType(staffType);
        staff.setDepartment(department);
        staff.setStaffRole(staffRole);
        staff.setIsActive(true);
        staffMemberService.save(staff);
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
            @RequestParam("fullName") String fullName,
            @RequestParam("academicTitle") String academicTitle,
            @RequestParam("staffType") String staffType,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam("staffRole") String staffRole) {
        StaffMember staff = staffMemberService.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        staff.setFullName(fullName);
        staff.setAcademicTitle(academicTitle);
        staff.setStaffType(staffType);
        staff.setDepartment(department);
        staff.setStaffRole(staffRole);
        staffMemberService.save(staff);
        return "redirect:/admin/academic/staff?success=updated";
    }

    @PostMapping("/delete/{id}")
    public String deleteStaff(@PathVariable Long id) {
        staffMemberService.softDelete(id);
        return "redirect:/admin/academic/staff?success=deleted";
    }

    @GetMapping("/api/by-role")
    @ResponseBody
    public ResponseEntity<List<StaffMember>> getByRole(@RequestParam("role") String role) {
        return ResponseEntity.ok(staffMemberService.findByRole(role));
    }
}
