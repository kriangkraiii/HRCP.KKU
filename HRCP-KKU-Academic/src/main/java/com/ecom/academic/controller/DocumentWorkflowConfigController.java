package com.ecom.academic.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.academic.dto.DocumentWorkflowSlotDTO;
import com.ecom.academic.dto.SignerOptionDTO;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.service.ActingSignerService;
import com.ecom.academic.service.DocumentWorkflowConfigService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Controller
@RequestMapping("/admin/academic/settings/signers")
@PreAuthorize("hasRole('ADMIN')")
public class DocumentWorkflowConfigController {

    private final DocumentWorkflowConfigService workflowConfigService;
    private final StaffMemberService staffMemberService;
    private final UserRepository userRepository;
    private final ActingSignerService actingSigners;

    public DocumentWorkflowConfigController(
            DocumentWorkflowConfigService workflowConfigService,
            StaffMemberService staffMemberService,
            UserRepository userRepository,
            ActingSignerService actingSigners) {
        this.workflowConfigService = workflowConfigService;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
        this.actingSigners = actingSigners;
    }

    @GetMapping
    public String viewSettings(Model model) {
        model.addAttribute("academicGroups", workflowConfigService.getGroupedConfigs(SignatureModule.ACADEMIC));
        model.addAttribute("positionGroups", workflowConfigService.getGroupedConfigs(SignatureModule.POSITION));

        // Available signers for default signer dropdowns:
        // Include staff members with accounts AND all active system users (Admins, Staff, general users)
        java.util.Map<Integer, SignerOptionDTO> map = new java.util.LinkedHashMap<>();

        // 1. First add all signable staff members
        staffMemberService.findAllWithAccounts().stream()
                .filter(StaffMember::isSignable)
                .forEach(s -> {
                    SignerOptionDTO dto = SignerOptionDTO.from(s);
                    if (dto.userId() != null) {
                        map.put(dto.userId(), dto);
                    }
                });

        // 2. Add all active users from userRepository (Admins, Staff, etc.)
        userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsEnable()))
                .forEach(u -> {
                    if (!map.containsKey(u.getId())) {
                        map.put(u.getId(), SignerOptionDTO.fromUser(u));
                    }
                });

        List<SignerOptionDTO> availableSigners = new java.util.ArrayList<>(map.values());
        availableSigners.sort(java.util.Comparator.comparing(SignerOptionDTO::displayName,
                java.util.Comparator.nullsLast(String::compareToIgnoreCase)));
        model.addAttribute("availableSigners", availableSigners);
        model.addAttribute("actingRows", actingSigners.rows());

        return "academic/admin/signer_settings";
    }

    @PostMapping
    public String saveSettings(
            @RequestParam("modules") List<SignatureModule> modules,
            @RequestParam("documentTypes") List<Integer> documentTypes,
            @RequestParam("slotKeys") List<String> slotKeys,
            @RequestParam("roleLabels") List<String> roleLabels,
            @RequestParam("anchorPlaceholders") List<String> anchorPlaceholders,
            @RequestParam("defaultStaffRoles") List<String> defaultStaffRoles,
            @RequestParam("stepOrders") List<Integer> stepOrders,
            @RequestParam(value = "isEnableds", required = false) List<String> isEnableds,
            @RequestParam(value = "defaultSignerUserIds", required = false) List<String> defaultSignerUserIds,
            RedirectAttributes redirectAttributes) {

        int size = modules.size();
        List<DocumentWorkflowSlotDTO> dtos = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            boolean enabled = isEnableds != null && isEnableds.size() > i && "true".equalsIgnoreCase(isEnableds.get(i));
            Integer defaultUserId = null;
            if (defaultSignerUserIds != null && defaultSignerUserIds.size() > i) {
                String val = defaultSignerUserIds.get(i);
                if (val != null && !val.isBlank()) {
                    try {
                        defaultUserId = Integer.valueOf(val.trim());
                    } catch (NumberFormatException ignored) {}
                }
            }

            String staffRole = defaultStaffRoles.size() > i ? defaultStaffRoles.get(i) : null;
            if (staffRole != null && staffRole.isBlank()) {
                staffRole = null;
            }

            dtos.add(new DocumentWorkflowSlotDTO(
                    modules.get(i),
                    documentTypes.get(i),
                    slotKeys.get(i),
                    roleLabels.get(i),
                    anchorPlaceholders.get(i),
                    staffRole,
                    stepOrders.get(i),
                    enabled,
                    defaultUserId,
                    null));
        }

        workflowConfigService.saveConfigs(dtos);
        redirectAttributes.addFlashAttribute("succMsg", "บันทึกการตั้งค่าผู้ลงนามและลำดับขั้นตอนเรียบร้อยแล้ว");
        return "redirect:/admin/academic/settings/signers";
    }

    /**
     * เปิด/ปิดการรักษาการแทนของหนึ่งตำแหน่ง — มีผลกับช่องของตำแหน่งนั้นในทุกเอกสาร
     * เฉพาะซองที่ส่งลงนามหลังจากนี้ ซองที่ส่งไปแล้วยังรอผู้ลงนามเดิม
     */
    @PostMapping("/acting")
    public String saveActing(
            @RequestParam("slotKey") String slotKey,
            @RequestParam(value = "active", defaultValue = "false") boolean active,
            @RequestParam(value = "actingUserId", required = false) String actingUserId,
            @RequestParam(value = "positionTitle", required = false) String positionTitle,
            java.security.Principal principal,
            RedirectAttributes redirectAttributes) {
        Integer userId = null;
        if (actingUserId != null && !actingUserId.isBlank()) {
            try {
                userId = Integer.valueOf(actingUserId.trim());
            } catch (NumberFormatException ignored) {
                // เลือกไม่ได้ก็เหมือนไม่ได้เลือก — save() บอกให้เลือกเอง
            }
        }
        String label = ActingSignerService.ROLES.stream()
                .filter(r -> r.slotKey().equals(slotKey))
                .map(ActingSignerService.Role::label)
                .findFirst()
                .orElse("");
        try {
            UserDtls actor = principal == null ? null : userRepository.findByEmail(principal.getName());
            actingSigners.save(slotKey, active, userId, positionTitle, actor);
            redirectAttributes.addFlashAttribute("succMsg", active
                    ? "เปิดการรักษาการแทน" + label + "แล้ว — มีผลกับเอกสารที่ส่งลงนามหลังจากนี้"
                    : "ปิดการรักษาการแทน" + label + "แล้ว — เอกสารที่ส่งลงนามหลังจากนี้กลับไปใช้ผู้ลงนามตามปกติ");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/academic/settings/signers";
    }

    @PostMapping("/reset")
    public String resetDocument(
            @RequestParam("module") SignatureModule module,
            @RequestParam("documentType") int documentType,
            RedirectAttributes redirectAttributes) {

        workflowConfigService.resetToDefaults(module, documentType);
        redirectAttributes.addFlashAttribute("succMsg", "คืนค่าเริ่มต้นของเอกสารนี้เรียบร้อยแล้ว");
        return "redirect:/admin/academic/settings/signers";
    }
}
