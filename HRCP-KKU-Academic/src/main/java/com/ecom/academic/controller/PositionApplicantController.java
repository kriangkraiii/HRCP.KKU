package com.ecom.academic.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/user/position")
public class PositionApplicantController {

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicRequestService academicService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ================== Dashboard ==================

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        UserDtls user = getUser(principal);

        List<PositionRequest> requests = positionService.findByApplicant(user.getId())
                .stream()
                .filter(r -> r.getCurrentStatus() != PositionRequestStatus.DRAFT)
                .toList();

        Optional<PositionRequest> draftRequest = positionService.findDraftByApplicant(user.getId());
        boolean hasActiveRequest = positionService.hasActiveRequest(user.getId());

        // Evaluation expiry countdown
        java.time.LocalDateTime expiryDate = academicService.getLatestEvaluationExpiry(user.getId());
        if (expiryDate != null) {
            model.addAttribute("evaluationExpiryDate", expiryDate.toString());
        }

        model.addAttribute("requests", requests);
        model.addAttribute("draftRequest", draftRequest.orElse(null));
        model.addAttribute("hasActiveRequest", hasActiveRequest);
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

        return "academic/position/applicant/dashboard";
    }

    // ================== New Request ==================

    @GetMapping("/new-request")
    public String newRequestForm(Principal principal, Model model) {
        UserDtls user = getUser(principal);

        // Check if user already has active request
        if (positionService.hasActiveRequest(user.getId())) {
            return "redirect:/user/position/dashboard?error=active_exists";
        }

        // Check for existing draft
        Optional<PositionRequest> draft = positionService.findDraftByApplicant(user.getId());
        if (draft.isPresent()) {
            return "redirect:/user/position/request/" + draft.get().getId();
        }

        // Get eligible evaluations from Phase 1
        List<AcademicRequest> eligibleEvals = positionService.getEligibleEvaluations(user.getId());
        model.addAttribute("evaluations", eligibleEvals);
        model.addAttribute("hasEligible", !eligibleEvals.isEmpty());

        return "academic/position/applicant/new_request";
    }

    @PostMapping("/create-request")
    public String createRequest(@RequestParam("evaluationId") Long evaluationId,
            Principal principal) {
        UserDtls user = getUser(principal);

        if (positionService.hasActiveRequest(user.getId())) {
            return "redirect:/user/position/dashboard?error=active_exists";
        }

        PositionRequest request = positionService.createDraftRequest(user, evaluationId);
        return "redirect:/user/position/request/" + request.getId();
    }

    // ================== Request Detail ==================

    @GetMapping("/request/{id}")
    public String requestDetail(@PathVariable Long id, Principal principal, Model model) {
        UserDtls user = getUser(principal);
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/position/dashboard";
        }

        List<Integer> completedDocs = positionService.getCompletedDocTypes(id);
        List<PositionDocument> documents = positionService.getDocuments(id);

        // For applicant, only show applicant-fillable docs
        Map<Integer, String> docLabels = positionService.getApplicantDocLabels();

        model.addAttribute("request", request);
        model.addAttribute("completedDocs", completedDocs);
        model.addAttribute("documents", documents);
        model.addAttribute("docLabels", docLabels);
        model.addAttribute("applicantDocs", PositionRequestService.APPLICANT_DOCS);
        model.addAttribute("statusHistory", positionService.getStatusHistory(id));

        // ดึงข้อมูลตำแหน่งจาก doc_2
        List<PositionDocument> doc2List = positionService.getDocumentsByType(id, 2);
        if (!doc2List.isEmpty()) {
            try {
                java.util.Map<String, String> doc2Data = objectMapper.readValue(doc2List.get(0).getJsonData(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                model.addAttribute("doc2Data", doc2Data);
            } catch (Exception e) { /* ignore */ }
        }

        return "academic/position/applicant/request_detail";
    }

    // ================== Document Forms ==================

    @GetMapping("/request/{id}/document/{type}")
    public String documentForm(@PathVariable Long id, @PathVariable int type,
            Principal principal, Model model) {
        UserDtls user = getUser(principal);
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/position/dashboard";
        }

        // Applicant can only fill docs 1,2,3,4,6,7,9
        if (!PositionRequestService.APPLICANT_DOCS.contains(type)) {
            return "redirect:/user/position/request/" + id;
        }

        // Load existing data
        List<PositionDocument> existing = positionService.getDocumentsByType(id, type);
        String existingData = existing.isEmpty() ? null : existing.get(0).getJsonData();

        model.addAttribute("request", request);
        model.addAttribute("documentType", type);
        model.addAttribute("documentLabel", positionService.getDocLabel(type));
        model.addAttribute("existingData", existingData);
        model.addAttribute("user", user);

        // Load doc 1 data for cross-document auto-fill (for docs other than 1)
        if (type != 1) {
            List<PositionDocument> doc1Docs = positionService.getDocumentsByType(id, 1);
            String doc1Data = doc1Docs.isEmpty() ? null : doc1Docs.get(0).getJsonData();
            model.addAttribute("doc1Data", doc1Data);
        }

        return "academic/position/applicant/doc_form_" + type;
    }

    @PostMapping("/request/{id}/document/{type}")
    public String submitDocument(@PathVariable Long id, @PathVariable int type,
            @RequestParam Map<String, String> formData,
            @RequestParam(value = "action", defaultValue = "submit") String action,
            Principal principal) {
        UserDtls user = getUser(principal);
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/position/dashboard";
        }

        if (!PositionRequestService.APPLICANT_DOCS.contains(type)) {
            return "redirect:/user/position/request/" + id;
        }

        // Remove Spring internals
        formData.remove("_csrf");
        formData.remove("action");

        try {
            String jsonData = objectMapper.writeValueAsString(formData);
            String label = positionService.getDocLabel(type);

            if ("draft".equals(action)) {
                positionService.saveDraft(request, type, jsonData, label, "APPLICANT");
                positionService.logDocumentEdit(request, type, label, user,
                        PositionDocumentEditLog.EditAction.DRAFT_SAVED);
                return "redirect:/user/position/request/" + id + "/document/" + type + "?saved";
            } else {
                boolean isNew = positionService.getDocumentsByType(id, type).isEmpty();
                positionService.saveDocument(request, type, jsonData, null, label, null, "APPLICANT");
                positionService.logDocumentEdit(request, type, label, user,
                        isNew ? PositionDocumentEditLog.EditAction.CREATED : PositionDocumentEditLog.EditAction.UPDATED);
                return "redirect:/user/position/request/" + id + "?success=doc_saved";
            }
        } catch (Exception e) {
            return "redirect:/user/position/request/" + id + "/document/" + type + "?error";
        }
    }

    // ================== Submit Request ==================

    @PostMapping("/request/{id}/submit")
    public String submitRequest(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/position/dashboard";
        }

        if (request.getCurrentStatus() != PositionRequestStatus.DRAFT) {
            return "redirect:/user/position/request/" + id;
        }

        // Check all applicant docs are completed
        List<Integer> completed = positionService.getCompletedDocTypes(id);
        boolean allDone = PositionRequestService.APPLICANT_DOCS.stream().allMatch(completed::contains);
        if (!allDone) {
            return "redirect:/user/position/request/" + id + "?error=incomplete_docs";
        }

        positionService.submitRequest(request);
        return "redirect:/user/position/dashboard?success=submitted";
    }

    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }
}
