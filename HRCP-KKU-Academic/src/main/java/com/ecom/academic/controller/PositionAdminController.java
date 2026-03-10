package com.ecom.academic.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/admin/position")
public class PositionAdminController {

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private DocumentGenerationService documentService;

    @Autowired
    private StaffMemberService staffMemberService;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ================== Requests List ==================

    @GetMapping("/requests")
    public String listRequests(Model model) {
        List<PositionRequest> requests = positionService.findAll();
        model.addAttribute("requests", requests);
        model.addAttribute("statuses", PositionRequestStatus.values());
        return "academic/position/admin/requests";
    }

    // ================== Request Detail ==================

    @GetMapping("/request/{id}")
    public String requestDetail(@PathVariable Long id, Model model) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        List<PositionDocument> documents = positionService.getDocuments(id);
        List<Integer> completedDocs = positionService.getCompletedDocTypes(id);

        model.addAttribute("request", request);
        model.addAttribute("documents", documents);
        model.addAttribute("completedDocs", completedDocs);
        model.addAttribute("docLabels", positionService.getAdminDocLabels());
        model.addAttribute("statuses", PositionRequestStatus.values());
        model.addAttribute("statusHistory", positionService.getStatusHistory(id));
        model.addAttribute("progressSteps", PositionRequestStatus.getProgressSteps());

        return "academic/position/admin/request_detail";
    }

    // ================== Status Update ==================

    @PostMapping("/request/{id}/status")
    public String updateStatus(@PathVariable Long id,
            @RequestParam("status") String statusStr,
            @RequestParam(value = "note", required = false) String note,
            Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            UserDtls admin = getUser(principal);
            if (admin == null) {
                redirectAttributes.addFlashAttribute("errorDetail",
                        "ไม่พบข้อมูลผู้ใช้ในระบบ (email: " + principal.getName() + ")");
                return "redirect:/admin/position/request/" + id + "?error=status_update_failed";
            }

            PositionRequestStatus newStatus = PositionRequestStatus.valueOf(statusStr);
            positionService.updateStatus(id, newStatus, admin, note);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorDetail", e.getClass().getSimpleName() + ": " + e.getMessage());
            return "redirect:/admin/position/request/" + id + "?error=status_update_failed";
        }

        return "redirect:/admin/position/request/" + id + "?success=status_updated";
    }

    // ================== Document Forms (Admin fills docs 5, 8) ==================

    @GetMapping("/request/{id}/document/{type}")
    public String documentForm(@PathVariable Long id, @PathVariable int type, Model model) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        List<PositionDocument> existing = positionService.getDocumentsByType(id, type);
        String existingData = existing.isEmpty() ? null : existing.get(0).getJsonData();

        model.addAttribute("request", request);
        model.addAttribute("documentType", type);
        model.addAttribute("documentLabel", positionService.getDocLabel(type));
        model.addAttribute("existingData", existingData);
        model.addAttribute("deans", staffMemberService.findAll());

        // Admin-specific forms for docs 5 and 8, generic view for others
        if (type == 5 || type == 8) {
            return "academic/position/admin/doc_form_" + type;
        }
        return "academic/position/admin/doc_form_generic";
    }

    @PostMapping("/request/{id}/document/{type}")
    public String saveDocument(@PathVariable Long id, @PathVariable int type,
            @RequestParam Map<String, String> formData,
            Principal principal) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        formData.remove("_csrf");

        try {
            String jsonData = objectMapper.writeValueAsString(formData);
            String label = positionService.getDocLabel(type);

            // Generate DOCX
            String filePath = null;
            try {
                filePath = documentService.generateP2Document(request, type, jsonData);
            } catch (Exception e) {
                System.err.println("Phase2 doc generation failed for type " + type + ": " + e.getMessage());
            }

            positionService.saveDocument(request, type, jsonData, filePath, label, null, "ADMIN");

            return "redirect:/admin/position/request/" + id + "?success=doc_generated";
        } catch (Exception e) {
            return "redirect:/admin/position/request/" + id + "?error=doc_save_failed";
        }
    }

    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }
}
