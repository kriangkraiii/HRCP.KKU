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
        List<PositionRequest> requests = positionService.findAll()
                .stream()
                .filter(r -> r.getCurrentStatus() != PositionRequestStatus.DRAFT)
                .toList();
        model.addAttribute("requests", requests);
        model.addAttribute("statuses", PositionRequestStatus.values());

        // ดึงข้อมูลตำแหน่งจาก doc_2 สำหรับทุกคำร้อง
        java.util.Map<Long, java.util.Map<String, String>> doc2DataMap = new java.util.HashMap<>();
        for (PositionRequest req : requests) {
            List<PositionDocument> doc2List = positionService.getDocumentsByType(req.getId(), 2);
            if (!doc2List.isEmpty()) {
                try {
                    java.util.Map<String, String> doc2Data = objectMapper.readValue(doc2List.get(0).getJsonData(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                    doc2DataMap.put(req.getId(), doc2Data);
                } catch (Exception e) { /* ignore */ }
            }
        }
        model.addAttribute("doc2DataMap", doc2DataMap);

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

        // ดึงข้อมูลตำแหน่งจาก doc_2
        List<PositionDocument> doc2List = positionService.getDocumentsByType(id, 2);
        if (!doc2List.isEmpty()) {
            try {
                java.util.Map<String, String> doc2Data = objectMapper.readValue(doc2List.get(0).getJsonData(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                model.addAttribute("doc2Data", doc2Data);
            } catch (Exception e) { /* ignore */ }
        }

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
            PositionRequest request = positionService.findById(id)
                    .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

            // Block status update for draft requests
            if (request.getCurrentStatus().isDraft()) {
                redirectAttributes.addFlashAttribute("errorDetail",
                        "ไม่สามารถอัพเดทสถานะได้ คำร้องยังเป็นแบบร่าง");
                return "redirect:/admin/position/request/" + id + "?error=status_update_failed";
            }

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

        // Block document editing for draft requests
        if (request.getCurrentStatus().isDraft()) {
            return "redirect:/admin/position/request/" + id + "?error=status_update_failed";
        }

        List<PositionDocument> existing = positionService.getDocumentsByType(id, type);
        String existingData = existing.isEmpty() ? null : existing.get(0).getJsonData();

        model.addAttribute("request", request);
        model.addAttribute("documentType", type);
        model.addAttribute("documentLabel", positionService.getDocLabel(type));
        model.addAttribute("existingData", existingData);
        model.addAttribute("deans", staffMemberService.findAll());

        // For doc 8: also load doc 6 (applicant research) data for auto-population
        if (type == 8) {
            List<PositionDocument> doc6List = positionService.getDocumentsByType(id, 6);
            String doc6Data = doc6List.isEmpty() ? null : doc6List.get(0).getJsonData();
            model.addAttribute("doc6Data", doc6Data);
        }

        // All document types have dedicated admin forms
        return "academic/position/admin/doc_form_" + type;
    }

    @PostMapping("/request/{id}/document/{type}")
    public String saveDocument(@PathVariable Long id, @PathVariable int type,
            @RequestParam Map<String, String> formData,
            Principal principal) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        // Block document saving for draft requests
        if (request.getCurrentStatus().isDraft()) {
            return "redirect:/admin/position/request/" + id + "?error=status_update_failed";
        }

        formData.remove("_csrf");

        try {
            // For doc 7: merge admin data with existing applicant data
            // to prevent overwriting applicant's sections when admin only fills section 3
            if (type == 7) {
                formData = mergeWithExistingData(request, type, formData);
            }

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

    /**
     * Merge form data with existing saved document data.
     * Existing data (e.g., from applicant) is loaded first,
     * then admin's form data is laid on top (non-empty values override).
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> mergeWithExistingData(PositionRequest request, int type,
            Map<String, String> formData) {
        try {
            List<PositionDocument> existing = positionService.getDocumentsByType(request.getId(), type);
            if (!existing.isEmpty()) {
                String existingJson = existing.get(0).getJsonData();
                if (existingJson != null && !existingJson.isEmpty()) {
                    Map<String, String> existingData = objectMapper.readValue(existingJson, Map.class);
                    // Overlay form data on top of existing data
                    for (Map.Entry<String, String> entry : formData.entrySet()) {
                        existingData.put(entry.getKey(), entry.getValue());
                    }
                    return new java.util.LinkedHashMap<>(existingData);
                }
            }
        } catch (Exception e) {
            System.err.println("Merge failed for doc " + type + ": " + e.getMessage());
        }
        return formData;
    }
}
