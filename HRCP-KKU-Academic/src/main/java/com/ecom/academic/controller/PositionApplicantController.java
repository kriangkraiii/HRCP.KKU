package com.ecom.academic.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/user/position")
public class PositionApplicantController {

    private final PositionRequestService positionService;

    private final UserRepository userRepository;

    private final AcademicRequestService academicService;

    private final DocumentGenerationService documentService;

    private final com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper;

    private final com.ecom.academic.service.DocumentPrewarmService documentPrewarmService;

    private final com.ecom.academic.service.SignatureWorkflowService signatureWorkflow;
    private final com.ecom.academic.service.SignedDocumentRenderer signedDocumentRenderer;
    private final jakarta.servlet.http.HttpServletRequest httpRequest;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PositionApplicantController.class);

    public PositionApplicantController(
            PositionRequestService positionService,
            UserRepository userRepository,
            AcademicRequestService academicService,
            DocumentGenerationService documentService,
            com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper,
            com.ecom.academic.service.DocumentPrewarmService documentPrewarmService,
            com.ecom.academic.service.SignatureWorkflowService signatureWorkflow,
            com.ecom.academic.service.SignedDocumentRenderer signedDocumentRenderer,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        this.positionService = positionService;
        this.userRepository = userRepository;
        this.academicService = academicService;
        this.documentService = documentService;
        this.autoFillHelper = autoFillHelper;
        this.documentPrewarmService = documentPrewarmService;
        this.signatureWorkflow = signatureWorkflow;
        this.signedDocumentRenderer = signedDocumentRenderer;
        this.httpRequest = httpRequest;
    }

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
        model.addAttribute("progressSteps", PositionRequestStatus.getProgressSteps());

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

        // ดึงข้อมูลรายวิชาและผลประเมินจากเอกสารที่ 8 (Phase 1) ผ่าน linkedEvaluation
        java.util.Map<Long, java.util.Map<String, String>> evalDoc8DataMap = new java.util.HashMap<>();
        java.util.Map<Long, String> evalRequestCodeMap = new java.util.HashMap<>();
        for (PositionRequest req : requests) {
            if (req.getLinkedEvaluation() != null) {
                evalRequestCodeMap.put(req.getId(), req.getLinkedEvaluation().getRequestCode());
                try {
                    List<com.ecom.academic.model.AcademicDocument> doc8List = academicService
                            .getDocumentsByType(req.getLinkedEvaluation().getId(), 8);
                    if (!doc8List.isEmpty() && doc8List.get(0).getJsonData() != null) {
                        java.util.Map<String, String> doc8Data = objectMapper.readValue(
                                doc8List.get(0).getJsonData(),
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                        evalDoc8DataMap.put(req.getId(), doc8Data);
                    }
                } catch (Exception e) { /* ignore */ }
            }
        }
        // Also check draft request
        if (draftRequest.isPresent() && draftRequest.get().getLinkedEvaluation() != null) {
            evalRequestCodeMap.put(draftRequest.get().getId(),
                    draftRequest.get().getLinkedEvaluation().getRequestCode());
            try {
                List<com.ecom.academic.model.AcademicDocument> doc8List = academicService
                        .getDocumentsByType(draftRequest.get().getLinkedEvaluation().getId(), 8);
                if (!doc8List.isEmpty() && doc8List.get(0).getJsonData() != null) {
                    java.util.Map<String, String> doc8Data = objectMapper.readValue(
                            doc8List.get(0).getJsonData(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                    evalDoc8DataMap.put(draftRequest.get().getId(), doc8Data);
                }
            } catch (Exception e) { /* ignore */ }
        }
        model.addAttribute("evalDoc8DataMap", evalDoc8DataMap);
        model.addAttribute("evalRequestCodeMap", evalRequestCodeMap);

        return "academic/position/applicant/dashboard";
    }

    // ================== New Request ==================

    @GetMapping("/new-request")
    public String newRequestForm(Principal principal, Model model) {
        UserDtls user = getUser(principal);

        // An unfinished draft comes first. It counts as an active request, so
        // checking the other way round meant this branch never ran and someone
        // opening this page with a draft waiting was turned away with an error
        // instead of being taken back to their own form.
        Optional<PositionRequest> draft = positionService.findDraftByApplicant(user.getId());
        if (draft.isPresent()) {
            return "redirect:/user/position/request/" + draft.get().getId();
        }

        // Check if user already has active request
        if (positionService.hasActiveRequest(user.getId())) {
            return "redirect:/user/position/dashboard?error=active_exists";
        }

        // Every usable result from Phase 1, including the ones whose course this
        // applicant has already spent — those are shown, and explained, rather
        // than quietly left out.
        List<com.ecom.academic.dto.EvaluationChoice> choices =
                positionService.getEvaluationChoices(user.getId());
        model.addAttribute("choices", choices);
        model.addAttribute("hasEligible", choices.stream()
                .anyMatch(com.ecom.academic.dto.EvaluationChoice::selectable));
        model.addAttribute("hasAny", !choices.isEmpty());

        return "academic/position/applicant/new_request";
    }

    @PostMapping("/create-request")
    public String createRequest(@RequestParam("evaluationId") Long evaluationId,
            Principal principal) {
        UserDtls user = getUser(principal);

        if (positionService.hasActiveRequest(user.getId())) {
            return "redirect:/user/position/dashboard?error=active_exists";
        }

        // The selection screen already leaves out a course this applicant has
        // spent, but a screen is not the enforcement: a stale tab or a
        // hand-built POST arrives with an id the screen would no longer offer.
        if (!positionService.canUseEvaluation(user.getId(), evaluationId)) {
            return "redirect:/user/position/dashboard?error=course_already_used";
        }

        PositionRequest request = positionService.createDraftRequest(user, evaluationId);
        return "redirect:/user/position/request/" + request.getId();
    }

    /**
     * ยกเลิกแบบร่างคำร้องขอตำแหน่งทางวิชาการ (ต้องพิมพ์ยืนยันก่อนลบ)
     */
    @PostMapping("/request/{id}/cancel-draft")
    public String cancelDraftRequest(@PathVariable Long id,
            @RequestParam(value = "confirmCode", required = false) String confirmCode,
            Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Referer", required = false) String referer) {
        UserDtls user = getUser(principal);
        Optional<PositionRequest> reqOpt = positionService.findById(id);
        if (reqOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบคำร้องที่ระบุ");
            return referer != null && referer.contains("/user/academic/dashboard") ? "redirect:/user/academic/dashboard" : "redirect:/user/position/dashboard";
        }

        PositionRequest req = reqOpt.get();
        String expectedCode = req.getRequestCode() != null ? req.getRequestCode() : String.valueOf(req.getId());
        if (confirmCode == null || (!confirmCode.trim().equalsIgnoreCase("DELETE") && !confirmCode.trim().equalsIgnoreCase(expectedCode))) {
            redirectAttributes.addFlashAttribute("errorMsg", "กรุณาพิมพ์ยืนยันด้วย 'DELETE' หรือรหัสคำร้อง '" + expectedCode + "' ให้ถูกต้องก่อนดำเนินการ");
            return referer != null && referer.contains("/user/academic/dashboard") ? "redirect:/user/academic/dashboard" : "redirect:/user/position/dashboard";
        }

        boolean deleted = positionService.deleteDraftRequest(id, user.getId());
        if (deleted) {
            redirectAttributes.addFlashAttribute("succMsg", "ยกเลิกแบบร่างคำร้องขอตำแหน่งและลบไฟล์เอกสารเรียบร้อยแล้ว");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่สามารถยกเลิกแบบร่างได้ หรือคำร้องไม่ได้อยู่ในสถานะแบบร่าง");
        }
        if (referer != null && referer.contains("/user/academic/dashboard")) {
            return "redirect:/user/academic/dashboard";
        }
        return "redirect:/user/position/dashboard";
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

        // Which teaching evaluation this request was built on, so the applicant
        // can check it here rather than by going back to the other phase.
        model.addAttribute("evaluationSummary",
                academicService.summarize(request.getLinkedEvaluation()));

        List<Integer> completedDocs = positionService.getCompletedDocTypes(id);
        List<PositionDocument> documents = positionService.getDocuments(id);
        for (PositionDocument d : documents) {
            String fullLabel = positionService.getDocLabel(d.getDocumentType());
            if (fullLabel != null && (d.getDocumentLabel() == null || d.getDocumentLabel().isBlank()
                    || d.getDocumentLabel().matches("^(?:เอกสาร|Document)\\s*ที่?\\s*\\d+$"))) {
                d.setDocumentLabel(fullLabel);
            }
        }

        // For applicant, only show applicant-fillable docs
        Map<Integer, String> docLabels = positionService.getApplicantDocLabels();

        // ตรวจสอบลายเซ็นผู้ยื่นในแต่ละเอกสาร
        Map<Integer, Boolean> docSignedMap = new HashMap<>();
        for (Integer docType : PositionRequestService.APPLICANT_DOCS) {
            docSignedMap.put(docType, signatureWorkflow.isApplicantSignatureCompleted(
                    com.ecom.academic.model.SignatureModule.POSITION, id, docType));
        }
        List<Integer> unsignedSigDocs = signatureWorkflow.getUnsignedApplicantDocTypes(
                com.ecom.academic.model.SignatureModule.POSITION, id, PositionRequestService.APPLICANT_DOCS);
        boolean applicantSignaturesComplete = unsignedSigDocs.isEmpty();

        model.addAttribute("request", request);
        model.addAttribute("completedDocs", completedDocs);
        model.addAttribute("documents", documents);
        model.addAttribute("docLabels", docLabels);
        model.addAttribute("applicantDocs", PositionRequestService.APPLICANT_DOCS);
        model.addAttribute("docSignedMap", docSignedMap);
        model.addAttribute("unsignedSigDocs", unsignedSigDocs);
        model.addAttribute("applicantSignaturesComplete", applicantSignaturesComplete);
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

        // Background warming for completed docs on view
        documentPrewarmService.prewarmAllRequestDocuments(id, true);

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
        Map<String, String> preFilledData = autoFillHelper.getPreFilledPositionDocData(request, type, existingData);

        model.addAttribute("request", request);
        model.addAttribute("documentType", type);
        model.addAttribute("documentLabel", positionService.getDocLabel(type));
        model.addAttribute("existingData", existingData);
        model.addAttribute("preFilledData", preFilledData);
        model.addAttribute("docData", preFilledData);
        model.addAttribute("user", user);

        // Load doc 1 data for cross-document auto-fill (for docs other than 1)
        if (type != 1) {
            List<PositionDocument> doc1Docs = positionService.getDocumentsByType(id, 1);
            String doc1Data = doc1Docs.isEmpty() ? null : doc1Docs.get(0).getJsonData();
            model.addAttribute("doc1Data", doc1Data);
        }

        // แผงลงนามอิเล็กทรอนิกส์ — ผู้ขอส่งเอกสารของตนเองไปลงนามได้
        model.addAttribute("documentType", type);
        model.addAttribute("signatureModule", com.ecom.academic.model.SignatureModule.POSITION);
        model.addAttribute("signaturePanel", signatureWorkflow.buildPanel(
                com.ecom.academic.model.SignatureModule.POSITION, id, type, user));

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

        // Staff-only fields (e.g. doc 7 verification result) must survive an
        // applicant save untouched — the applicant may neither forge nor erase them.
        positionService.preserveStaffOnlyFields(type, formData,
                positionService.getLatestDocumentData(id, type));

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

                // Async prewarm PDF to cache
                documentPrewarmService.prewarmPositionDocument(id, type, jsonData);

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

        // Asked before the document checks on purpose. A course taken by another
        // request in the meantime is not something filling in more documents can
        // fix, so reporting "documents incomplete" would send the applicant off
        // to do work that cannot help.
        if (!positionService.isEvaluationStillAvailable(request)) {
            return "redirect:/user/position/request/" + id + "?error=course_already_used";
        }

        // Check all applicant docs are completed
        List<Integer> completed = positionService.getCompletedDocTypes(id);
        boolean allDone = PositionRequestService.APPLICANT_DOCS.stream().allMatch(completed::contains);
        if (!allDone) {
            return "redirect:/user/position/request/" + id + "?error=incomplete_docs";
        }

        // Check all applicant docs requiring signature are signed
        List<Integer> unsignedSigDocs = signatureWorkflow.getUnsignedApplicantDocTypes(
                com.ecom.academic.model.SignatureModule.POSITION, id, PositionRequestService.APPLICANT_DOCS);
        if (!unsignedSigDocs.isEmpty()) {
            return "redirect:/user/position/request/" + id + "?error=unsigned_docs";
        }

        positionService.submitRequest(request);


        return "redirect:/user/position/dashboard?success=submitted";
    }

    // ================== Document Download & Preview ==================

    @GetMapping("/request/{id}/document/{type}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @PathVariable int type,
            @RequestParam(value = "format", defaultValue = "docx") String format,
            Principal principal) throws IOException {
        UserDtls user = getUser(principal);
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        if (!request.getApplicant().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<PositionDocument> docs = positionService.getDocumentsByType(id, type);
        PositionDocument doc = docs.isEmpty() ? null : docs.get(0);
        byte[] data = null;
        String label = (doc != null && doc.getDocumentLabel() != null && !doc.getDocumentLabel().isBlank())
                ? doc.getDocumentLabel()
                : positionService.getDocLabel(type);

        // Check if there is an e-sign envelope (open or completed) with signatures
        java.util.Optional<com.ecom.academic.model.SignatureRequest> optEnvelope =
                signatureWorkflow.findEnvelope(com.ecom.academic.model.SignatureModule.POSITION, id, type);
        if (optEnvelope.isPresent()) {
            com.ecom.academic.model.SignatureRequest envelope = optEnvelope.get();
            try {
                if ("pdf".equalsIgnoreCase(format)) {
                    data = signedDocumentRenderer.renderPdf(envelope);
                } else {
                    data = signedDocumentRenderer.renderDocx(envelope);
                }
            } catch (Exception e) {
                // fall through to saved draft file if render fails
            }
        }

        if (data == null && doc != null && doc.getGeneratedFilePath() != null) {
            Path filePath = Path.of(doc.getGeneratedFilePath());
            if (Files.exists(filePath)) {
                data = Files.readAllBytes(filePath);
            }
        }

        if (data == null && doc != null && doc.getJsonData() != null) {
            try {
                String generatedPath = documentService.generateP2Document(request, type, doc.getJsonData());
                if (generatedPath != null) {
                    Path filePath = Path.of(generatedPath);
                    if (Files.exists(filePath)) {
                        data = Files.readAllBytes(filePath);
                        doc.setGeneratedFilePath(generatedPath);
                        positionService.saveDocument(request, type, doc.getJsonData(),
                                generatedPath, label, doc.getCopyNumber(), doc.getFilledBy());
                    }
                }
            } catch (Exception e) {
                // fall through
            }
        }

        if (data == null && docs.isEmpty()) {
            Map<String, String> autoData = autoFillHelper.getPreFilledPositionDocData(request, type, null);
            String jsonData = objectMapper.writeValueAsString(autoData);
            data = documentService.generateP2PreviewDocx(type, jsonData);
        }

        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        String cleanDocName = label.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String baseName = request.getRequestCode() + "_เอกสารตำแหน่งที่_" + type + "_" + cleanDocName;

        return PreviewResponseFactory.build(documentService, data, format, baseName);
    }

    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }
}
