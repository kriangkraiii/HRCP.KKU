package com.ecom.academic.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import com.ecom.config.ClientIpUtils;
import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicDocumentEditLog;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicEmailService;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.util.DocumentFileTypeValidator;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;
import com.ecom.util.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/user/academic")
public class AcademicApplicantController {

    private static final Logger log = LoggerFactory.getLogger(AcademicApplicantController.class);

    private final AcademicRequestService requestService;

    private final DocumentGenerationService documentService;

    private final StaffMemberService staffMemberService;

    private final UserRepository userRepository;

    private final AcademicEmailService emailService;

    private final PositionRequestService positionRequestService;

    private final AdminLogService adminLogService;

    private final jakarta.servlet.http.HttpServletRequest httpRequest;

    private final DocumentFileTypeValidator documentFileTypeValidator;

    private final com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper;

    private final com.ecom.academic.service.DocumentPrewarmService documentPrewarmService;

    private final com.ecom.academic.service.SignatureWorkflowService signatureWorkflow;

    private final com.ecom.academic.service.SignedDocumentRenderer signedDocumentRenderer;

    private final com.ecom.external.service.KkuDocumentSyncService kkuDocSyncService;

    public AcademicApplicantController(
            AcademicRequestService requestService,
            DocumentGenerationService documentService,
            StaffMemberService staffMemberService,
            UserRepository userRepository,
            AcademicEmailService emailService,
            PositionRequestService positionRequestService,
            AdminLogService adminLogService,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            DocumentFileTypeValidator documentFileTypeValidator,
            com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper,
            com.ecom.academic.service.DocumentPrewarmService documentPrewarmService,
            com.ecom.academic.service.SignatureWorkflowService signatureWorkflow,
            com.ecom.academic.service.SignedDocumentRenderer signedDocumentRenderer,
            com.ecom.external.service.KkuDocumentSyncService kkuDocSyncService) {
        this.requestService = requestService;
        this.documentService = documentService;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.positionRequestService = positionRequestService;
        this.adminLogService = adminLogService;
        this.httpRequest = httpRequest;
        this.documentFileTypeValidator = documentFileTypeValidator;
        this.autoFillHelper = autoFillHelper;
        this.documentPrewarmService = documentPrewarmService;
        this.signatureWorkflow = signatureWorkflow;
        this.signedDocumentRenderer = signedDocumentRenderer;
        this.kkuDocSyncService = kkuDocSyncService;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** เอกสารที่ผู้ยื่นสามารถเห็นได้ (doc type 0, 1, 8) */
    private static final List<Integer> APPLICANT_VISIBLE_DOC_TYPES = Arrays.asList(0, 1, 8);

    private static final String EDIT_LOCKED_MESSAGE =
            "ไม่สามารถแก้ไขเอกสารได้ เนื่องจากส่งคำร้องไปแล้ว — จะแก้ไขได้ต่อเมื่อแอดมินส่งเอกสารกลับมาให้แก้ไขเท่านั้น";

    /**
     * บอกหน้าเอกสารว่าตอนนี้แก้ไขได้หรือไม่ และแอดมินส่งกลับมาด้วยเหตุผลอะไร
     *
     * <p>หน้าเอกสารยังเปิดดูได้เสมอแม้แก้ไม่ได้ เพราะแผงลงนามอยู่บนหน้าเดียวกัน
     * ผู้ยื่นต้องเข้ามาลงนามได้แม้ฟอร์มจะถูกล็อก
     */
    private void addEditGate(Model model, AcademicRequest request, int documentType) {
        boolean editable = requestService.canApplicantEditDocument(request, documentType);
        model.addAttribute("editable", editable);
        model.addAttribute("revisionNote", requestService.getRevisionNote(request.getId(), documentType));
        model.addAttribute("revisionRequested",
                requestService.isRevisionRequested(request.getId(), documentType));
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        UserDtls user = getUser(principal);
        List<AcademicRequest> allRequests = requestService.findByApplicant(user.getId());
        // แยก draft ออกจาก submitted requests
        AcademicRequest draftRequest = allRequests.stream()
                .filter(r -> r.getCurrentStatus() == RequestStatus.DRAFT)
                .findFirst().orElse(null);
        List<AcademicRequest> requests = allRequests.stream()
                .filter(r -> r.getCurrentStatus() != RequestStatus.DRAFT)
                .collect(Collectors.toList());
        model.addAttribute("requests", requests);
        model.addAttribute("draftRequest", draftRequest);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("progressSteps", RequestStatus.getProgressSteps());
        model.addAttribute("hasActiveRequest", requestService.hasActiveRequest(user.getId()));

        // ดึงข้อมูลรายวิชาจาก doc_0 สำหรับทุกคำร้อง
        Map<Long, Map<String, String>> doc0DataMap = new java.util.HashMap<>();
        for (AcademicRequest req : requests) {
            List<AcademicDocument> doc0List = requestService.getDocumentsByType(req.getId(), 0);
            if (!doc0List.isEmpty()) {
                try {
                    Map<String, String> doc0Data = objectMapper.readValue(doc0List.get(0).getJsonData(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    doc0DataMap.put(req.getId(), doc0Data);
                } catch (Exception e) { /* ignore */ }
            }
        }
        model.addAttribute("doc0DataMap", doc0DataMap);

        // ============ Position Requests ============
        List<PositionRequest> allPositionRequests = positionRequestService.findByApplicant(user.getId());
        PositionRequest positionDraft = allPositionRequests.stream()
                .filter(r -> r.getCurrentStatus() == PositionRequestStatus.DRAFT)
                .findFirst().orElse(null);
        List<PositionRequest> positionRequests = allPositionRequests.stream()
                .filter(r -> r.getCurrentStatus() != PositionRequestStatus.DRAFT)
                .collect(Collectors.toList());
        model.addAttribute("positionRequests", positionRequests);
        model.addAttribute("positionDraft", positionDraft);
        model.addAttribute("positionStatuses", PositionRequestStatus.values());
        model.addAttribute("positionProgressSteps", PositionRequestStatus.getProgressSteps());
        model.addAttribute("hasActivePositionRequest", positionRequestService.hasActiveRequest(user.getId()));

        // ดึงข้อมูลตำแหน่งจาก doc_2 สำหรับทุก position request
        Map<Long, Map<String, String>> posDoc2DataMap = new java.util.HashMap<>();
        for (PositionRequest posReq : positionRequests) {
            List<PositionDocument> doc2List = positionRequestService.getDocumentsByType(posReq.getId(), 2);
            if (!doc2List.isEmpty()) {
                try {
                    Map<String, String> doc2Data = objectMapper.readValue(doc2List.get(0).getJsonData(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    posDoc2DataMap.put(posReq.getId(), doc2Data);
                } catch (Exception e) { /* ignore */ }
            }
        }
        model.addAttribute("posDoc2DataMap", posDoc2DataMap);

        return "academic/applicant/dashboard";
    }

    // ================== Document Library ==================

    @GetMapping("/documents")
    public String documentsLibrary(Model model) {
        List<com.ecom.external.service.KkuDocumentSyncService.CategoryGroup> categories = kkuDocSyncService.getGroupedDocuments();
        model.addAttribute("categories", categories);
        return "academic/applicant/documents";
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        UserDtls user = getUser(principal);
        List<AcademicRequest> allRequests = requestService.findByApplicant(user.getId());
        List<AcademicRequest> requests = allRequests.stream()
                .filter(r -> r.getCurrentStatus() != RequestStatus.DRAFT)
                .collect(Collectors.toList());
        model.addAttribute("requests", requests);
        model.addAttribute("progressSteps", RequestStatus.getProgressSteps());
        return "academic/applicant/request_history";
    }

    @GetMapping("/new-request")
    public String newRequestForm(Principal principal, Model model) {
        UserDtls user = getUser(principal);
        if (requestService.hasActiveRequest(user.getId())) {
            return "redirect:/user/academic/dashboard?error=active-request";
        }

        // หา draft ที่มีอยู่ หรือสร้างใหม่
        AcademicRequest draftRequest = requestService.findDraftByApplicant(user.getId());
        if (draftRequest == null) {
            draftRequest = requestService.createDraftRequest(user);
        }

        // เช็คว่ามี doc 0 และ doc 1 แล้วหรือยัง
        List<AcademicDocument> allDocs = requestService.getDocumentsSorted(draftRequest.getId());
        boolean hasDoc0 = isDoc0Complete(allDocs);
        boolean hasDoc1 = isDoc1Complete(allDocs);

        // เช็คการลงนามของผู้ยื่นใน doc 0 และ doc 1
        boolean doc0Signed = signatureWorkflow.isApplicantSignatureCompleted(com.ecom.academic.model.SignatureModule.ACADEMIC, draftRequest.getId(), 0);
        boolean doc1Signed = signatureWorkflow.isApplicantSignatureCompleted(com.ecom.academic.model.SignatureModule.ACADEMIC, draftRequest.getId(), 1);
        List<Integer> unsignedSigDocs = signatureWorkflow.getUnsignedApplicantDocTypes(com.ecom.academic.model.SignatureModule.ACADEMIC, draftRequest.getId(), List.of(0, 1));
        boolean applicantSignaturesComplete = unsignedSigDocs.isEmpty();

        model.addAttribute("user", user);
        model.addAttribute("request", draftRequest);
        model.addAttribute("hasDoc0", hasDoc0);
        model.addAttribute("hasDoc1", hasDoc1);
        model.addAttribute("doc0Signed", doc0Signed);
        model.addAttribute("doc1Signed", doc1Signed);
        model.addAttribute("unsignedSigDocs", unsignedSigDocs);
        model.addAttribute("applicantSignaturesComplete", applicantSignaturesComplete);

        return "academic/applicant/new_request";
    }

    // ==================== เอกสารที่ 0: บันทึกข้อความ ====================

    /**
     * ฟอร์มกรอกเอกสารที่ 0: บันทึกข้อความ ขอรับการประเมินผลการสอน
     */
    @GetMapping({"/request/{id}/document-0", "/request/{id}/document/0"})
    public String document0Form(@PathVariable Long id, Principal principal, Model model) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        List<AcademicDocument> existingDocs = requestService.getDocumentsByType(id, 0);
        String existingJson = !existingDocs.isEmpty() ? existingDocs.get(0).getJsonData() : null;
        Map<String, String> doc0Data = autoFillHelper.getPreFilledAcademicDocData(request, 0, existingJson);

        model.addAttribute("request", request);
        model.addAttribute("existingDocs", existingDocs);
        model.addAttribute("existingData", existingJson);
        model.addAttribute("doc0Data", doc0Data);
        addEditGate(model, request, 0);

        // แผงลงนามอิเล็กทรอนิกส์ — ผู้ขอส่งเอกสารของตนเองไปลงนามได้
        model.addAttribute("documentType", 0);
        model.addAttribute("signatureModule", com.ecom.academic.model.SignatureModule.ACADEMIC);
        model.addAttribute("signaturePanel", signatureWorkflow.buildPanel(
                com.ecom.academic.model.SignatureModule.ACADEMIC, id, 0, user));

        return "academic/applicant/document_0_form";
    }

    @PostMapping({"/request/{id}/document-0", "/request/{id}/document/0"})
    public String submitDocument0(@PathVariable Long id,
            @RequestParam Map<String, String> formData,
            @RequestParam(value = "action", defaultValue = "submit") String action,
            Principal principal,
            RedirectAttributes redirectAttributes) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        if (!requestService.canApplicantEditDocument(request, 0)) {
            redirectAttributes.addFlashAttribute("error", EDIT_LOCKED_MESSAGE);
            return "redirect:/user/academic/request/" + id + "/document-0";
        }

        formData.remove("action");
        formData.remove("_csrf");
        String jsonData = objectMapper.writeValueAsString(formData);

        boolean isNew0 = requestService.getDocumentsByType(id, 0).isEmpty();

        if ("draft".equals(action)) {
            // บันทึกแบบร่าง
            requestService.saveDraft(request, 0, jsonData,
                    "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน", null);
            requestService.logDocumentEdit(request, 0,
                    "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน", user,
                    AcademicDocumentEditLog.EditAction.DRAFT_SAVED);
            return "redirect:/user/academic/request/" + id + "/document-0?saved=draft";
        }

        String filePath = documentService.generateDocument(request.getId(), 0, jsonData, null);

        requestService.saveDocument(request, 0, jsonData, filePath,
                "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน", null);
        requestService.logDocumentEdit(request, 0,
                "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน", user,
                isNew0 ? AcademicDocumentEditLog.EditAction.CREATED : AcademicDocumentEditLog.EditAction.UPDATED);

        // Async prewarm PDF to cache
        documentPrewarmService.prewarmAcademicDocument(id, 0, jsonData);

        return "redirect:/user/academic/request/" + id + "?success=doc0_submitted";
    }

    // ==================== เอกสารที่ 1: แบบตรวจสอบเบื้องต้น ====================

    /**
     * ฟอร์มกรอกเอกสารที่ 1: แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน
     */
    @GetMapping({"/request/{id}/document-1", "/request/{id}/document/1"})
    public String document1Form(@PathVariable Long id, Principal principal, Model model) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        List<AcademicDocument> existingDocs = requestService.getDocumentsByType(id, 1);
        String existingJson = !existingDocs.isEmpty() ? existingDocs.get(0).getJsonData() : null;
        Map<String, String> doc1Data = autoFillHelper.getPreFilledAcademicDocData(request, 1, existingJson);

        // Slot-based attachments (1-5)
        Map<Integer, List<AcademicAttachment>> attachmentsBySlot = requestService.getAttachmentsGroupedBySlot(id);
        long totalAttachmentCount = attachmentsBySlot.values().stream().mapToLong(List::size).sum();

        // Per-slot sizes for display
        Map<Integer, String> slotSizes = new java.util.LinkedHashMap<>();
        Map<Integer, Integer> slotCounts = new java.util.LinkedHashMap<>();
        Map<Integer, Long> slotBytes = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            long slotSize = attachmentsBySlot.get(i).stream()
                    .mapToLong(a -> a.getFileSize() != null ? a.getFileSize() : 0L).sum();
            slotSizes.put(i, String.format("%.1f", slotSize / (1024.0 * 1024.0)));
            slotCounts.put(i, attachmentsBySlot.get(i).size());
            slotBytes.put(i, slotSize);
        }

        model.addAttribute("request", request);
        model.addAttribute("existingDocs", existingDocs);
        model.addAttribute("existingData", existingJson);
        model.addAttribute("doc1Data", doc1Data);
        model.addAttribute("attachmentsBySlot", attachmentsBySlot);
        model.addAttribute("slotSizes", slotSizes);
        model.addAttribute("slotCounts", slotCounts);
        model.addAttribute("slotBytes", slotBytes);
        model.addAttribute("attachmentCount", totalAttachmentCount);
        addEditGate(model, request, 1);

        // แผงลงนามอิเล็กทรอนิกส์ — ผู้ขอส่งเอกสารของตนเองไปลงนามได้
        model.addAttribute("documentType", 1);
        model.addAttribute("signatureModule", com.ecom.academic.model.SignatureModule.ACADEMIC);
        model.addAttribute("signaturePanel", signatureWorkflow.buildPanel(
                com.ecom.academic.model.SignatureModule.ACADEMIC, id, 1, user));

        return "academic/applicant/document_1_form";
    }

    @PostMapping({"/request/{id}/document-1", "/request/{id}/document/1"})
    public String submitDocument1(@PathVariable Long id,
            @RequestParam Map<String, String> formData,
            @RequestParam(value = "action", defaultValue = "submit") String action,
            Principal principal,
            RedirectAttributes redirectAttributes) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        if (!requestService.canApplicantEditDocument(request, 1)) {
            redirectAttributes.addFlashAttribute("error", EDIT_LOCKED_MESSAGE);
            return "redirect:/user/academic/request/" + id + "/document-1";
        }

        formData.remove("action");
        formData.remove("_csrf");
        String jsonData = objectMapper.writeValueAsString(formData);

        boolean isNew1 = requestService.getDocumentsByType(id, 1).isEmpty();

        if ("draft".equals(action)) {
            requestService.saveDraft(request, 1, jsonData,
                    "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", null);
            requestService.logDocumentEdit(request, 1,
                    "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", user,
                    AcademicDocumentEditLog.EditAction.DRAFT_SAVED);
            return "redirect:/user/academic/request/" + id + "/document-1?saved=draft";
        }

        if ("submit".equals(action)) {
            Map<Integer, List<AcademicAttachment>> attachmentsBySlot = requestService.getAttachmentsGroupedBySlot(id);
            List<Integer> missingSlots = new java.util.ArrayList<>();
            List<Integer> overQuotaSlots = new java.util.ArrayList<>();
            final long MAX_SLOT_BYTES = 75L * 1024L * 1024L;

            for (int i = 1; i <= 5; i++) {
                List<AcademicAttachment> slotAtts = attachmentsBySlot.get(i);
                if (slotAtts == null || slotAtts.isEmpty()) {
                    missingSlots.add(i);
                } else {
                    long slotSize = slotAtts.stream()
                            .mapToLong(a -> a.getFileSize() != null ? a.getFileSize() : 0L).sum();
                    if (slotSize > MAX_SLOT_BYTES) {
                        overQuotaSlots.add(i);
                    }
                }
            }
            if (!missingSlots.isEmpty() || !overQuotaSlots.isEmpty()) {
                List<String> errorParts = new java.util.ArrayList<>();
                if (!missingSlots.isEmpty()) {
                    String missingStr = missingSlots.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
                    errorParts.add("กรุณาแนบไฟล์เอกสารประกอบให้ครบทั้ง 5 ช่อง (ยังขาดช่องที่ " + missingStr + ")");
                }
                if (!overQuotaSlots.isEmpty()) {
                    String overStr = overQuotaSlots.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
                    errorParts.add("ช่องที่ " + overStr + " มีขนาดรวมเกินขีดจำกัด 75 MB ต่อช่อง");
                }
                redirectAttributes.addFlashAttribute("error", String.join(" และ ", errorParts));
                return "redirect:/user/academic/request/" + id + "/document-1";
            }
        }

        String filePath = documentService.generateDocument(request.getId(), 1, jsonData, null);

        requestService.saveDocument(request, 1, jsonData, filePath,
                "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", null);
        requestService.logDocumentEdit(request, 1,
                "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", user,
                isNew1 ? AcademicDocumentEditLog.EditAction.CREATED : AcademicDocumentEditLog.EditAction.UPDATED);

        // Async prewarm PDF to cache
        documentPrewarmService.prewarmAcademicDocument(id, 1, jsonData);

        return "redirect:/user/academic/request/" + id + "?success=doc1_submitted";
    }

    // ==================== แนบไฟล์ประกอบการประเมินผลการสอน (เอกสารที่ 1) ====================

    @PostMapping({
            "/request/{id}/document-1/attachments/{slot}",
            "/request/{id}/document/1/attachments/{slot}",
            "/request/{id}/document-1/attachments",
            "/request/{id}/document/1/attachments"
    })
    public String uploadDocument1Attachments(
            @PathVariable Long id,
            @PathVariable(required = false) Integer slot,
            @RequestParam("files") MultipartFile[] files,
            Principal principal,
            RedirectAttributes redirectAttributes) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        if (!requestService.canApplicantEditDocument(request, 1)) {
            redirectAttributes.addFlashAttribute("error", EDIT_LOCKED_MESSAGE);
            return "redirect:/user/academic/request/" + id + "/document-1";
        }

        if (files == null || files.length == 0) {
            return "redirect:/user/academic/request/" + id + "/document-1";
        }

        int targetSlot = (slot != null && slot >= 1 && slot <= 5) ? slot : 1;

        int uploadedCount = 0;
        String uploadDir = "uploads/academic/" + id + "/attachments/";
        Files.createDirectories(Path.of(uploadDir));

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) continue;

            // If slot was not explicitly given in URL, try to infer from filename prefix like "01-", "02-", etc.
            int resolvedSlot = targetSlot;
            if (slot == null) {
                if (originalFilename.startsWith("01") || originalFilename.startsWith("1-") || originalFilename.startsWith("1_")) resolvedSlot = 1;
                else if (originalFilename.startsWith("02") || originalFilename.startsWith("2-") || originalFilename.startsWith("2_")) resolvedSlot = 2;
                else if (originalFilename.startsWith("03") || originalFilename.startsWith("3-") || originalFilename.startsWith("3_")) resolvedSlot = 3;
                else if (originalFilename.startsWith("04") || originalFilename.startsWith("4-") || originalFilename.startsWith("4_")) resolvedSlot = 4;
                else if (originalFilename.startsWith("05") || originalFilename.startsWith("5-") || originalFilename.startsWith("5_")) resolvedSlot = 5;
                else if (originalFilename.startsWith("06") || originalFilename.startsWith("6-") || originalFilename.startsWith("6_")) resolvedSlot = 3;
                else if (originalFilename.startsWith("07") || originalFilename.startsWith("7-") || originalFilename.startsWith("7_")) resolvedSlot = 4;
                else resolvedSlot = 1;
            }

            long slotCurrentSize = requestService.getSlotTotalSize(id, resolvedSlot);
            final long MAX_SLOT_BYTES = 75L * 1024L * 1024L; // 75 MB per slot

            String lower = originalFilename.toLowerCase();
            boolean isAllowed = lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".zip");
            if (!isAllowed) {
                redirectAttributes.addFlashAttribute("error", "ไฟล์ " + originalFilename + " มีประเภทไฟล์ที่ไม่รองรับ (อนุญาตเฉพาะ .pdf, .docx, .doc, .zip)");
                continue;
            }

            if (slotCurrentSize + file.getSize() > MAX_SLOT_BYTES) {
                String usedMB = String.format("%.1f", slotCurrentSize / (1024.0 * 1024.0));
                String fileMB = String.format("%.1f", file.getSize() / (1024.0 * 1024.0));
                redirectAttributes.addFlashAttribute("error",
                        "ช่องที่ " + resolvedSlot + " มีขนาดไฟล์เดิม " + usedMB + " MB เมื่อเพิ่มไฟล์ " + originalFilename + " (" + fileMB + " MB) จะเกินขนาดรวมสูงสุด 75 MB ต่อช่อง");
                continue;
            }

            String sanitized = FileUtils.sanitizeFilename(originalFilename);
            String uniquePrefix = System.currentTimeMillis() + "_" + (uploadedCount + 1) + "_";
            String storedFilename = uniquePrefix + sanitized;
            String filePath = uploadDir + storedFilename;
            file.transferTo(Path.of(filePath));

            String fileType = "OTHER";
            if (lower.endsWith(".pdf")) fileType = "PDF";
            else if (lower.endsWith(".docx") || lower.endsWith(".doc")) fileType = "DOCX";
            else if (lower.endsWith(".zip")) fileType = "ZIP";

            AcademicAttachment attachment = new AcademicAttachment();
            attachment.setRequest(request);
            attachment.setOriginalFilename(originalFilename);
            attachment.setStoredFilePath(filePath);
            attachment.setFileType(fileType);
            attachment.setFileSize(file.getSize());
            attachment.setChecklistItem(resolvedSlot);
            requestService.saveAttachment(attachment);

            uploadedCount++;
        }

        if (uploadedCount > 0) {
            redirectAttributes.addFlashAttribute("success", "อัปโหลดไฟล์แนบเรียบร้อยแล้ว " + uploadedCount + " ไฟล์");
        }
        return "redirect:/user/academic/request/" + id + "/document-1";
    }

    @GetMapping("/request/{id}/attachment/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        AcademicAttachment attachment = requestService.findAttachmentById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        Path path = Path.of(attachment.getStoredFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = "application/octet-stream";
        String ft = attachment.getFileType() != null ? attachment.getFileType().toUpperCase() : "";
        if ("PDF".equals(ft)) contentType = "application/pdf";
        else if ("DOCX".equals(ft)) contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if ("PPTX".equals(ft)) contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        else if ("XLSX".equals(ft)) contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        else if ("ZIP".equals(ft)) contentType = "application/zip";
        else if ("IMAGE".equals(ft)) {
            try {
                contentType = Files.probeContentType(path);
            } catch (Exception e) {
                contentType = "image/jpeg";
            }
        }

        String rawFilename = attachment.getOriginalFilename() != null ? attachment.getOriginalFilename() : "attachment";
        String safeFilename = java.net.URLEncoder.encode(rawFilename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        String asciiFilename = rawFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + safeFilename)
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                .contentLength(Files.size(path))
                .body(new FileSystemResource(path));
    }

    @GetMapping("/request/{id}/attachment/{attachmentId}/view")
    public ResponseEntity<Resource> viewAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        AcademicAttachment attachment = requestService.findAttachmentById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        Path path = Path.of(attachment.getStoredFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        String rawFilename = attachment.getOriginalFilename() != null ? attachment.getOriginalFilename() : "attachment";
        String ext = attachment.getFileExtension() != null ? attachment.getFileExtension().toUpperCase() : "";

        // สำหรับไฟล์ DOCX แปลงเป็น PDF แบบ On-the-fly เพื่อให้บราวเซอร์เปิดอ่านได้ทันที (แทนการดาวน์โหลด)
        if ("DOCX".equalsIgnoreCase(ext)) {
            try {
                byte[] docxBytes = Files.readAllBytes(path);
                byte[] pdfBytes = documentService.convertDocxToPdfCached(docxBytes);
                if (pdfBytes != null && pdfBytes.length > 0) {
                    String baseName = rawFilename.toLowerCase().endsWith(".docx")
                            ? rawFilename.substring(0, rawFilename.length() - 5)
                            : rawFilename;
                    String pdfFilename = baseName + ".pdf";
                    String safePdfFilename = java.net.URLEncoder.encode(pdfFilename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                    String asciiPdfFilename = pdfFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "inline; filename=\"" + asciiPdfFilename + "\"; filename*=UTF-8''" + safePdfFilename)
                            .contentType(MediaType.APPLICATION_PDF)
                            .contentLength(pdfBytes.length)
                            .body(new org.springframework.core.io.ByteArrayResource(pdfBytes));
                }
            } catch (Exception e) {
                log.warn("Failed to convert DOCX attachment {} to PDF for inline preview, falling back to original file: {}", attachmentId, e.toString());
            }
        }

        String contentType = switch (ext) {
            case "PDF" -> "application/pdf";
            case "PNG" -> "image/png";
            case "JPG", "JPEG" -> "image/jpeg";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };

        String safeFilename = java.net.URLEncoder.encode(rawFilename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        String asciiFilename = rawFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + safeFilename)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(Files.size(path))
                .body(new FileSystemResource(path));
    }

    @PostMapping("/request/{id}/attachment/{attachmentId}/delete")
    public String deleteAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }
        if (!requestService.canApplicantEditDocument(request, 1)) {
            redirectAttributes.addFlashAttribute("error", EDIT_LOCKED_MESSAGE);
            return "redirect:/user/academic/request/" + id + "/document-1";
        }

        AcademicAttachment attachment = requestService.findAttachmentById(attachmentId).orElse(null);
        if (attachment != null && attachment.getRequest().getId().equals(id)) {
            try {
                Files.deleteIfExists(Path.of(attachment.getStoredFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete file on disk: {}", e.getMessage());
            }
            requestService.deleteAttachment(attachmentId);
            redirectAttributes.addFlashAttribute("success", "ลบไฟล์แนบเรียบร้อยแล้ว");
        }
        return "redirect:/user/academic/request/" + id + "/document-1";
    }

    // ==================== ส่งคำร้องทั้งหมด ====================

    /**
     * ส่งคำร้อง (เปลี่ยนจาก DRAFT → RECEIVED) พร้อมส่งอีเมลแจ้งแอดมิน
     */
    @PostMapping("/request/{id}/submit")
    public String submitRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        if (request.getCurrentStatus() != RequestStatus.DRAFT) {
            return "redirect:/user/academic/request/" + id + "?error=already_submitted";
        }

        // ตรวจสอบความครบถ้วนสมบูรณ์ของเอกสารที่ 0 และเอกสารที่ 1
        List<AcademicDocument> allDocs = requestService.getDocumentsSorted(id);
        if (!isDoc0Complete(allDocs) || !isDoc1Complete(allDocs)) {
            redirectAttributes.addFlashAttribute("error", "กรุณากรอกเอกสารที่ 0 และแบบตรวจสอบเอกสารที่ 1 ให้ครบถ้วนสมบูรณ์ก่อนส่งคำร้อง");
            return "redirect:/user/academic/new-request";
        }

        // ตรวจสอบว่ามีไฟล์แนบในเอกสารที่ 1 หรือยัง
        if (requestService.countAttachments(id) == 0) {
            redirectAttributes.addFlashAttribute("error", "กรุณาแนบไฟล์เอกสารประกอบการประเมินผลการสอนอย่างน้อย 1 ไฟล์ในแบบฟอร์มเอกสารที่ 1 ก่อนส่งคำร้อง");
            return "redirect:/user/academic/new-request";
        }

        // ตรวจสอบว่าผู้ยื่นได้ลงนามครบทั้งเอกสาร 0 และ 1 หรือยัง
        List<Integer> unsignedSigDocs = signatureWorkflow.getUnsignedApplicantDocTypes(
                com.ecom.academic.model.SignatureModule.ACADEMIC, id, List.of(0, 1));
        if (!unsignedSigDocs.isEmpty()) {
            String missingStr = unsignedSigDocs.stream().map(String::valueOf).collect(Collectors.joining(", "));
            redirectAttributes.addFlashAttribute("error", "กรุณาลงนามอิเล็กทรอนิกส์ในเอกสารที่ " + missingStr + " ให้ครบถ้วนก่อนส่งคำร้อง");
            return "redirect:/user/academic/new-request";
        }

        // เปลี่ยนสถานะ DRAFT → RECEIVED
        requestService.submitDraftRequest(request);


        // Log activity
        try {
            adminLogService.log(principal.getName(), user.getName(),
                    "SUBMIT_REQUEST",
                    "ส่งคำร้องประเมินผลการสอน #" + id,
                    getClientIpAddress());
        } catch (Exception e) {
            auditLogFailed(e);
        }

        // ส่งอีเมลแจ้งเตือนแอดมิน
        emailService.sendNewRequestNotificationToAdmins(id);

        return "redirect:/user/academic/request/" + id + "?success=submitted";
    }

    /**
     * ยกเลิกแบบร่างคำร้องประเมินผลการสอน (ต้องพิมพ์ยืนยันก่อนลบ)
     */
    @PostMapping("/request/{id}/cancel-draft")
    public String cancelDraftRequest(@PathVariable Long id,
            @RequestParam(value = "confirmCode", required = false) String confirmCode,
            Principal principal, RedirectAttributes redirectAttributes) {
        UserDtls user = getUser(principal);
        Optional<AcademicRequest> reqOpt = requestService.findById(id);
        if (reqOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบคำร้องที่ระบุ");
            return "redirect:/user/academic/dashboard";
        }

        AcademicRequest req = reqOpt.get();
        String expectedCode = req.getRequestCode() != null ? req.getRequestCode() : String.valueOf(req.getId());
        if (confirmCode == null || (!confirmCode.trim().equalsIgnoreCase("DELETE") && !confirmCode.trim().equalsIgnoreCase(expectedCode))) {
            redirectAttributes.addFlashAttribute("errorMsg", "กรุณาพิมพ์ยืนยันด้วย 'DELETE' หรือรหัสคำร้อง '" + expectedCode + "' ให้ถูกต้องก่อนดำเนินการ");
            return "redirect:/user/academic/dashboard";
        }

        boolean deleted = requestService.deleteDraftRequest(id, user.getId());
        if (deleted) {
            try {
                adminLogService.log(principal.getName(), user.getName(),
                        "CANCEL_DRAFT",
                        "ยกเลิกแบบร่างคำร้องประเมินผลการสอน #" + id,
                        getClientIpAddress());
            } catch (Exception ignored) {}
            redirectAttributes.addFlashAttribute("succMsg", "ยกเลิกแบบร่างคำร้องและลบไฟล์เอกสารเรียบร้อยแล้ว");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่สามารถยกเลิกแบบร่างได้ หรือคำร้องไม่ได้อยู่ในสถานะแบบร่าง");
        }
        return "redirect:/user/academic/dashboard";
    }

    @GetMapping("/request/{id}")
    public String viewRequest(@PathVariable Long id, Principal principal, Model model) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        // ผู้ยื่นเห็นเฉพาะเอกสารที่ 0, 1, 8
        List<AcademicDocument> allDocuments = requestService.getDocumentsSorted(id);
        List<AcademicDocument> visibleDocuments = allDocuments.stream()
                .filter(d -> APPLICANT_VISIBLE_DOC_TYPES.contains(d.getDocumentType()))
                .collect(Collectors.toList());

        for (AcademicDocument d : visibleDocuments) {
            String fullLabel = AcademicRequestService.getDocLabel(d.getDocumentType());
            if (d.getDocumentLabel() == null || d.getDocumentLabel().isBlank()
                    || d.getDocumentLabel().matches("^(?:เอกสาร|Document)\\s*ที่?\\s*\\d+$")) {
                d.setDocumentLabel(fullLabel);
            }
        }

        model.addAttribute("request", request);
        model.addAttribute("documents", visibleDocuments);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("progressSteps", RequestStatus.getProgressSteps());
        model.addAttribute("statusHistory", requestService.getStatusHistory(id));

        // ถ้าเป็น DRAFT ให้ redirect ไปหน้า new-request แทน
        if (request.getCurrentStatus() == RequestStatus.DRAFT) {
            return "redirect:/user/academic/new-request";
        }

        // เช็คว่า doc 0, doc 1 ถูกกรอกแล้วหรือยัง
        boolean hasDoc0 = isDoc0Complete(allDocuments);
        boolean hasDoc1 = isDoc1Complete(allDocuments);
        model.addAttribute("hasDoc0", hasDoc0);
        model.addAttribute("hasDoc1", hasDoc1);

        // หลังส่งคำร้องแล้วปุ่มแก้ไขจะโผล่เฉพาะเอกสารที่แอดมินส่งกลับมาให้แก้เท่านั้น
        model.addAttribute("canEditDoc0", requestService.canApplicantEditDocument(request, 0));
        model.addAttribute("canEditDoc1", requestService.canApplicantEditDocument(request, 1));
        model.addAttribute("revisionNoteDoc0", requestService.getRevisionNote(id, 0));
        model.addAttribute("revisionNoteDoc1", requestService.getRevisionNote(id, 1));

        // ดึงข้อมูลจาก doc_0 เพื่อแสดงข้อมูลรายวิชาในหน้ารายละเอียดคำร้อง
        List<AcademicDocument> doc0List = requestService.getDocumentsByType(id, 0);
        if (!doc0List.isEmpty()) {
            try {
                String doc0Json = doc0List.get(0).getJsonData();
                Map<String, String> doc0Data = objectMapper.readValue(doc0Json,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                model.addAttribute("doc0Data", doc0Data);
            } catch (Exception e) {
                // ignore parse errors
            }
        }

        // Background warming for completed docs on view
        documentPrewarmService.prewarmAllRequestDocuments(id, false);

        return "academic/applicant/request_detail";
    }

    @PostMapping("/upload-revision/{id}")
    public String uploadRevision(@PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        // Validate file type (document-only)
        try {
            documentFileTypeValidator.validate(file.getOriginalFilename());
        } catch (IllegalArgumentException e) {
            return "redirect:/user/academic/request/" + id + "?error=" + java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
        }

        String oldRevisionPath = request.getRevisionFilePath();

        String uploadDir = "uploads/academic/" + id + "/revisions/";
        Files.createDirectories(Path.of(uploadDir));
        String safeFilename = FileUtils.sanitizeFilename(file.getOriginalFilename());
        String filePath = uploadDir + safeFilename;
        file.transferTo(Path.of(filePath));

        requestService.setRevisionFile(id, filePath);

        // ข้อ 13 — ส่งเอกสารที่แก้แล้วกลับมายังคณะอนุกรรมการ
        //
        // Until now this stored the file and left the status at "แจ้งผล - แก้ไข",
        // which is neither terminal nor able to move on: the applicant could not
        // start a new request and the officer had nothing telling them work had
        // come back. The revise branch was a dead end (GAP-34).
        if (request.getCurrentStatus() == RequestStatus.COMPLETED_REVISE) {
            requestService.updateStatus(id, RequestStatus.REVISION_SUBMITTED, user,
                    "ผู้ขอกำหนดตำแหน่งส่งเอกสารที่แก้ไขแล้ว: " + file.getOriginalFilename());
        }

        // Delete old revision file from disk
        if (oldRevisionPath != null && !oldRevisionPath.isBlank() && !oldRevisionPath.equals(filePath)) {
            try {
                Files.deleteIfExists(Path.of(oldRevisionPath));
            } catch (Exception e) {
                log.warn("Failed to delete previous revision file: {}", e.getMessage());
            }
        }

        // Log activity
        try {
            adminLogService.log(principal.getName(), user.getName(),
                    "UPLOAD_REVISION",
                    "อัปโหลดเอกสารแก้ไขสำหรับคำร้อง #" + id + " (" + file.getOriginalFilename() + ")",
                    getClientIpAddress());
        } catch (Exception e) {
            auditLogFailed(e);
        }

        return "redirect:/user/academic/request/" + id + "?success=uploaded";
    }

    @GetMapping("/download/{id}/{docId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id,
            @PathVariable Long docId,
            @RequestParam(value = "format", defaultValue = "docx") String format,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        List<AcademicDocument> docs = requestService.getDocuments(id);
        AcademicDocument doc = docs.stream()
                .filter(d -> d.getId().equals(docId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Enforce document visibility for applicant (only doc types 0, 1, 8 allowed)
        if (!APPLICANT_VISIBLE_DOC_TYPES.contains(doc.getDocumentType())) {
            return ResponseEntity.status(403).build();
        }

        byte[] data = null;

        // Check if there is an e-sign envelope (open or completed) with signatures
        java.util.Optional<com.ecom.academic.model.SignatureRequest> optEnvelope =
                signatureWorkflow.findEnvelope(com.ecom.academic.model.SignatureModule.ACADEMIC, id, doc.getDocumentType());
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

        if (data == null && doc.getGeneratedFilePath() != null) {
            data = documentService.getDocumentBytes(doc.getGeneratedFilePath());
        }

        if (data == null && doc.getJsonData() != null) {
            try {
                data = documentService.generatePreviewDocx(doc.getDocumentType(), doc.getJsonData());
            } catch (Exception e) {
                // fallback failed
            }
        }

        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        String docLabel = doc.getDocumentLabel() != null && !doc.getDocumentLabel().isBlank()
                ? doc.getDocumentLabel()
                : DocumentPreviewController.getDocTitle(doc.getDocumentType());
        String cleanDocName = docLabel.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String baseName = request.getRequestCode() + "_เอกสารที่_" + doc.getDocumentType() + "_" + cleanDocName;

        return PreviewResponseFactory.build(documentService, data, format, baseName);
    }

    @GetMapping("/request/{id}/document/{type}/download")
    public ResponseEntity<byte[]> downloadDocumentByType(@PathVariable Long id,
            @PathVariable int type,
            @RequestParam(value = "format", defaultValue = "docx") String format,
            Principal principal) throws IOException {
        List<AcademicDocument> docs = requestService.getDocumentsByType(id, type);
        if (docs.isEmpty()) {
            // Check if envelope exists for auto-generated / submitted document
            java.util.Optional<com.ecom.academic.model.SignatureRequest> optEnvelope =
                    signatureWorkflow.findEnvelope(com.ecom.academic.model.SignatureModule.ACADEMIC, id, type);
            if (optEnvelope.isPresent()) {
                com.ecom.academic.model.SignatureRequest envelope = optEnvelope.get();
                try {
                    byte[] data = "pdf".equalsIgnoreCase(format)
                            ? signedDocumentRenderer.renderPdf(envelope)
                            : signedDocumentRenderer.renderDocx(envelope);
                    if (data != null && data.length > 0) {
                        AcademicRequest req = requestService.findById(id).orElse(null);
                        String code = req != null ? req.getRequestCode() : "REQ";
                        String label = DocumentPreviewController.getDocTitle(type);
                        String baseName = code + "_เอกสารที่_" + type + "_" + label.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
                        return PreviewResponseFactory.build(documentService, data, format, baseName);
                    }
                } catch (Exception e) {
                    // fall through
                }
            }
            return ResponseEntity.notFound().build();
        }
        return downloadDocument(id, docs.get(0).getId(), format, principal);
    }

    @GetMapping("/download-result/{id}")
    public ResponseEntity<Resource> downloadResult(@PathVariable Long id, Principal principal)
            throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        if (request.getResultFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path path = Path.of(request.getResultFilePath());
        byte[] data = Files.readAllBytes(path);
        ByteArrayResource resource = new ByteArrayResource(data);
        String ext = path.getFileName().toString().toLowerCase().endsWith(".pdf") ? ".pdf" : ".docx";
        String baseName = request.getRequestCode() + "_ผลการประเมินผลการสอน" + ext;
        String safeFilename = java.net.URLEncoder.encode(baseName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        String asciiFilename = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");

        String contentType = ext.equals(".pdf") ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + safeFilename)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(resource);
    }

    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }

    /**
     * เช็คว่าเอกสารที่ 1 สมบูรณ์หรือไม่ (ผู้ยื่นติ๊ก ✓ ครบทุก 5 ข้อ)
     */
    private boolean isDoc1Complete(List<AcademicDocument> docs) {
        return docs.stream()
                .filter(d -> d.getDocumentType() == 1)
                .findFirst()
                .map(doc -> {
                    try {
                        Map<String, String> data = objectMapper.readValue(doc.getJsonData(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                        return "✓".equals(data.get("chk_app_1"))
                                && "✓".equals(data.get("chk_app_2"))
                                && "✓".equals(data.get("chk_app_3"))
                                && "✓".equals(data.get("chk_app_4"))
                                && "✓".equals(data.get("chk_app_5"));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .orElse(false);
    }

    /**
     * เช็คว่าเอกสารที่ 0 สมบูรณ์หรือไม่ (กรอกครบทุกช่องที่จำเป็น)
     */
    private boolean isDoc0Complete(List<AcademicDocument> docs) {
        return docs.stream()
                .filter(d -> d.getDocumentType() == 0)
                .findFirst()
                .map(doc -> {
                    try {
                        Map<String, String> data = objectMapper.readValue(doc.getJsonData(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                        return hasValue(data, "date")
                                && hasValue(data, "title")
                                && hasValue(data, "applicant_name")
                                && hasValue(data, "employee_type")
                                && hasValue(data, "current_position")
                                && ("✓".equals(data.get("chk1")) || "✓".equals(data.get("chk2")))
                                && hasValue(data, "course_code")
                                && hasValue(data, "course_name")
                                && hasValue(data, "academic_year");
                    } catch (Exception e) {
                        return false;
                    }
                })
                .orElse(false);
    }

    private boolean hasValue(Map<String, String> data, String key) {
        String v = data.get(key);
        return v != null && !v.trim().isEmpty();
    }

    private String getClientIpAddress() {
        return ClientIpUtils.resolveClientIp(httpRequest);
    }

    /** Audit logging must never break the user's action, but it must leave a trace. */
    private void auditLogFailed(Exception e) {
        log.warn("Failed to write audit log: {}", e.toString());
    }
}
