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

    public DocumentWorkflowConfigController(
            DocumentWorkflowConfigService workflowConfigService,
            StaffMemberService staffMemberService,
            UserRepository userRepository) {
        this.workflowConfigService = workflowConfigService;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String viewSettings(Model model) {
        model.addAttribute("academicGroups", workflowConfigService.getGroupedConfigs(SignatureModule.ACADEMIC));
        model.addAttribute("positionGroups", workflowConfigService.getGroupedConfigs(SignatureModule.POSITION));

        // Available staff members for default signer dropdowns
        List<SignerOptionDTO> availableSigners = staffMemberService.findAllWithAccounts().stream()
                .filter(StaffMember::isSignable)
                .map(SignerOptionDTO::from)
                .toList();
        model.addAttribute("availableSigners", availableSigners);

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
