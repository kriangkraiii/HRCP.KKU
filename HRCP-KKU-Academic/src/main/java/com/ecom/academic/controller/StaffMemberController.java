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
import com.ecom.academic.dto.SignerOptionDTO;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.academic.service.StaffDirectorySync;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/academic/staff")
public class StaffMemberController {

    private static final Logger log = LoggerFactory.getLogger(StaffMemberController.class);

    private final StaffMemberService staffMemberService;
    private final StaffDirectorySync staffDirectorySync;
    private final AdminLogService adminLogService;
    private final UserRepository userRepository;
    private final HttpServletRequest httpRequest;

    public StaffMemberController(
            StaffMemberService staffMemberService,
            StaffDirectorySync staffDirectorySync,
            AdminLogService adminLogService,
            UserRepository userRepository,
            HttpServletRequest httpRequest) {
        this.staffMemberService = staffMemberService;
        this.staffDirectorySync = staffDirectorySync;
        this.adminLogService = adminLogService;
        this.userRepository = userRepository;
        this.httpRequest = httpRequest;
    }

    @GetMapping("")
    public String listStaff(Model model) {
        model.addAttribute("staffMembers", staffMemberService.findAllWithAccounts());
        return "academic/admin/staff_list";
    }

    @PostMapping("/auto-link")
    public String autoLinkStaff(
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
            java.security.Principal principal) {
        try {
            StaffMemberService.AutoLinkReport report = staffMemberService.autoLinkAllAccounts();
            redirectAttributes.addFlashAttribute("infoMessage", report.describe());

            UserDtls admin = principal != null ? userRepository.findByEmail(principal.getName()) : null;
            adminLogService.log(
                    principal != null ? principal.getName() : "SYSTEM",
                    admin != null ? admin.getName() : "Administrator",
                    "AUTO_LINK_STAFF",
                    "รันระบบ Auto-Link บุคลากร (" + report.describe() + ")",
                    getClientIpAddress());
        } catch (Exception e) {
            log.error("Failed to auto-link staff members: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "เกิดข้อผิดพลาดในการผูกบัญชีอัตโนมัติ: " + e.getMessage());
        }
        return "redirect:/admin/academic/staff";
    }

    @PostMapping("/sync")
    public String syncStaffFromDirectory(
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
            java.security.Principal principal) {
        try {
            StaffDirectorySync.Result syncResult = staffDirectorySync.importFromDirectory();
            StaffMemberService.AutoLinkReport linkReport = staffMemberService.autoLinkAllAccounts();

            String combinedMessage = syncResult.describe();
            if (linkReport.hasChanges()) {
                combinedMessage += " | " + linkReport.describe();
            }
            redirectAttributes.addFlashAttribute("infoMessage", combinedMessage);

            UserDtls admin = principal != null ? userRepository.findByEmail(principal.getName()) : null;
            adminLogService.log(
                    principal != null ? principal.getName() : "SYSTEM",
                    admin != null ? admin.getName() : "Administrator",
                    "SYNC_STAFF_DIRECTORY",
                    "ซิงก์ข้อมูลบุคลากรจากต้นทาง (" + combinedMessage + ")",
                    getClientIpAddress());
        } catch (Exception e) {
            log.error("Failed to sync staff directory: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "เกิดข้อผิดพลาดในการซิงก์ข้อมูล: " + e.getMessage());
        }
        return "redirect:/admin/academic/staff";
    }

    @GetMapping("/add")
    public String addStaffForm(Model model) {
        model.addAttribute("staff", new StaffMember());
        addLinkableUsers(model);
        return "academic/admin/staff_form";
    }

    /**
     * Accounts an administrator may link this person to.
     *
     * <p>Every account is offered, not just unlinked ones: the form has to be able
     * to show the account a staff row is *already* linked to as the selected
     * option, and hiding taken accounts would make an existing link render as
     * "ไม่ผูกบัญชี" and silently clear itself on the next save.
     */
    private void addLinkableUsers(Model model) {
        model.addAttribute("linkableUsers", userRepository.findAll(
                org.springframework.data.domain.Sort.by("firstName").ascending()
                        .and(org.springframework.data.domain.Sort.by("lastName").ascending())));
    }

    @PostMapping("/add")
    public String addStaff(@RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("academicTitle") String academicTitle,
            @RequestParam(value = "firstNameEn", required = false) String firstNameEn,
            @RequestParam(value = "lastNameEn", required = false) String lastNameEn,
            @RequestParam(value = "academicTitleEn", required = false) String academicTitleEn,
            @RequestParam("staffType") String staffType,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam("staffRole") String staffRole,
            @RequestParam(value = "userId", required = false) Integer userId,
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
                staff.setFirstNameEn(firstNameEn);
                staff.setLastNameEn(lastNameEn);
                staff.setAcademicTitleEn(academicTitleEn);
                staff.setStaffType(staffType);
                staff.setDepartment(department);
                staff.setStaffRole(staffRole);

                staff.setUser(resolveUser(userId));

                model.addAttribute("staff", staff);
                model.addAttribute("duplicates", sameName);
                model.addAttribute("duplicateWarning",
                        "มีบุคลากรชื่อ \"" + firstName + " " + lastName + "\" อยู่แล้ว "
                                + sameName.size() + " รายการ ตรวจสอบก่อนว่าไม่ใช่คนเดียวกัน");
                addLinkableUsers(model);
                return "academic/admin/staff_form";
            }
        }

        StaffMember staff = new StaffMember();
        staff.setFirstName(firstName);
        staff.setLastName(lastName);
        staff.setAcademicTitle(academicTitle);
        staff.setFirstNameEn(firstNameEn);
        staff.setLastNameEn(lastNameEn);
        staff.setAcademicTitleEn(academicTitleEn);
        staff.setStaffType(staffType);
        staff.setDepartment(department);
        staff.setStaffRole(staffRole);
        staff.setIsActive(true);

        String linkError = applyAccountLink(staff, userId);
        if (linkError != null) {
            model.addAttribute("staff", staff);
            model.addAttribute("linkError", linkError);
            addLinkableUsers(model);
            return "academic/admin/staff_form";
        }

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
        addLinkableUsers(model);
        return "academic/admin/staff_form";
    }

    @PostMapping("/edit/{id}")
    public String editStaff(@PathVariable Long id,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("academicTitle") String academicTitle,
            @RequestParam(value = "firstNameEn", required = false) String firstNameEn,
            @RequestParam(value = "lastNameEn", required = false) String lastNameEn,
            @RequestParam(value = "academicTitleEn", required = false) String academicTitleEn,
            @RequestParam("staffType") String staffType,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam("staffRole") String staffRole,
            @RequestParam(value = "userId", required = false) Integer userId,
            Model model,
            java.security.Principal principal) {
        StaffMember staff = staffMemberService.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        staff.setFirstName(firstName);
        staff.setLastName(lastName);
        staff.setAcademicTitle(academicTitle);
        staff.setFirstNameEn(firstNameEn);
        staff.setLastNameEn(lastNameEn);
        staff.setAcademicTitleEn(academicTitleEn);
        staff.setStaffType(staffType);
        staff.setDepartment(department);
        staff.setStaffRole(staffRole);

        String linkError = applyAccountLink(staff, userId);
        if (linkError != null) {
            model.addAttribute("staff", staff);
            model.addAttribute("linkError", linkError);
            addLinkableUsers(model);
            return "academic/admin/staff_form";
        }

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

    /**
     * Role holders for a signer picker.
     *
     * <p>Returns people who cannot sign as well, flagged {@code signable=false}.
     * Filtering them out server-side would leave an administrator looking at a
     * list with someone conspicuously missing and no way to find out why; the
     * picker shows them greyed out with "ยังไม่ได้ผูกบัญชี" instead.
     */
    @GetMapping("/api/by-role")
    @ResponseBody
    public ResponseEntity<List<SignerOptionDTO>> getByRole(
            @RequestParam("role") String role,
            @RequestParam(value = "signableOnly", defaultValue = "false") boolean signableOnly) {
        List<StaffMember> staff = signableOnly
                ? staffMemberService.findSignableByRole(role)
                : staffMemberService.findByRoleWithAccountStatus(role);
        return ResponseEntity.ok(staff.stream().map(SignerOptionDTO::from).toList());
    }

    private UserDtls resolveUser(Integer userId) {
        return userId == null ? null : userRepository.findById(userId).orElse(null);
    }

    /**
     * Applies the chosen account to a staff row.
     *
     * @return a Thai message to show the administrator, or null when the link was
     *         applied cleanly
     *
     *         <p>The uniqueness check happens here rather than being left to the
     *         database constraint so the message can name the person already
     *         holding the account. Two staff rows sharing one account would make
     *         "who is the dean" ambiguous at exactly the moment a signature
     *         request is being addressed.
     */
    private String applyAccountLink(StaffMember staff, Integer userId) {
        if (userId == null) {
            staff.setUser(null);
            return null;
        }

        UserDtls account = resolveUser(userId);
        if (account == null) {
            return "ไม่พบบัญชีผู้ใช้ที่เลือก";
        }

        StaffMember conflict = staffMemberService
                .findConflictingAccountOwner(userId, staff.getId())
                .orElse(null);
        if (conflict != null) {
            return "บัญชี " + account.getEmail() + " ถูกผูกกับบุคลากร \""
                    + conflict.getDisplayName() + "\" อยู่แล้ว — หนึ่งบัญชีผูกได้กับบุคลากรคนเดียวเท่านั้น";
        }

        staff.setUser(account);
        return null;
    }

    private String getClientIpAddress() {
        return ClientIpUtils.resolveClientIp(httpRequest);
    }

    /** Audit logging must never break the user's action, but it must leave a trace. */
    private void auditLogFailed(Exception e) {
        log.warn("Failed to write audit log: {}", e.toString());
    }
}
