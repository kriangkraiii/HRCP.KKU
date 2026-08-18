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
import com.ecom.academic.model.AcademicDocument;
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
import com.ecom.academic.service.UserStorageService;
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

    private final UserStorageService userStorageService;

    private final com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper;

    public AcademicApplicantController(
            AcademicRequestService requestService,
            DocumentGenerationService documentService,
            StaffMemberService staffMemberService,
            UserRepository userRepository,
            AcademicEmailService emailService,
            PositionRequestService positionRequestService,
            AdminLogService adminLogService,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            UserStorageService userStorageService,
            com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper) {
        this.requestService = requestService;
        this.documentService = documentService;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.positionRequestService = positionRequestService;
        this.adminLogService = adminLogService;
        this.httpRequest = httpRequest;
        this.userStorageService = userStorageService;
        this.autoFillHelper = autoFillHelper;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** เอกสารที่ผู้ยื่นสามารถเห็นได้ (doc type 0, 1, 8) */
    private static final List<Integer> APPLICANT_VISIBLE_DOC_TYPES = Arrays.asList(0, 1, 8);

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
    public String documentsLibrary() {
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

        model.addAttribute("user", user);
        model.addAttribute("request", draftRequest);
        model.addAttribute("hasDoc0", hasDoc0);
        model.addAttribute("hasDoc1", hasDoc1);

        return "academic/applicant/new_request";
    }

    // ==================== เอกสารที่ 0: บันทึกข้อความ ====================

    /**
     * ฟอร์มกรอกเอกสารที่ 0: บันทึกข้อความ ขอรับการประเมินผลการสอน
     */
    @GetMapping("/request/{id}/document-0")
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

        return "academic/applicant/document_0_form";
    }

    @PostMapping("/request/{id}/document-0")
    public String submitDocument0(@PathVariable Long id,
            @RequestParam Map<String, String> formData,
            @RequestParam(value = "action", defaultValue = "submit") String action,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        formData.remove("action");
        formData.remove("_csrf");
        String jsonData = objectMapper.writeValueAsString(formData);

        if ("draft".equals(action)) {
            // บันทึกแบบร่าง
            requestService.saveDraft(request, 0, jsonData,
                    "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน", null);
            return "redirect:/user/academic/request/" + id + "/document-0?saved=draft";
        }

        String filePath = documentService.generateDocument(request.getId(), 0, jsonData, null);

        requestService.saveDocument(request, 0, jsonData, filePath,
                "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน", null);

        return "redirect:/user/academic/request/" + id + "?success=doc0_submitted";
    }

    // ==================== เอกสารที่ 1: แบบตรวจสอบเบื้องต้น ====================

    /**
     * ฟอร์มกรอกเอกสารที่ 1: แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน
     */
    @GetMapping("/request/{id}/document-1")
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

        model.addAttribute("request", request);
        model.addAttribute("existingDocs", existingDocs);
        model.addAttribute("existingData", existingJson);
        model.addAttribute("doc1Data", doc1Data);

        return "academic/applicant/document_1_form";
    }

    @PostMapping("/request/{id}/document-1")
    public String submitDocument1(@PathVariable Long id,
            @RequestParam Map<String, String> formData,
            @RequestParam(value = "action", defaultValue = "submit") String action,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        formData.remove("action");
        formData.remove("_csrf");
        String jsonData = objectMapper.writeValueAsString(formData);

        if ("draft".equals(action)) {
            requestService.saveDraft(request, 1, jsonData,
                    "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", null);
            return "redirect:/user/academic/request/" + id + "/document-1?saved=draft";
        }

        String filePath = documentService.generateDocument(request.getId(), 1, jsonData, null);

        requestService.saveDocument(request, 1, jsonData, filePath,
                "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", null);

        return "redirect:/user/academic/request/" + id + "?success=doc1_submitted";
    }

    // ==================== ส่งคำร้องทั้งหมด ====================

    /**
     * ส่งคำร้อง (เปลี่ยนจาก DRAFT → RECEIVED) พร้อมส่งอีเมลแจ้งแอดมิน
     */
    @PostMapping("/request/{id}/submit")
    public String submitRequest(@PathVariable Long id, Principal principal) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        if (request.getCurrentStatus() != RequestStatus.DRAFT) {
            return "redirect:/user/academic/request/" + id + "?error=already_submitted";
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
        emailService.sendNewRequestNotificationToAdmins(request);

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
            userStorageService.validateFileType(file.getOriginalFilename());
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
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable Long id,
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

        byte[] data = documentService.getDocumentBytes(doc.getGeneratedFilePath());

        String docLabel = doc.getDocumentLabel() != null && !doc.getDocumentLabel().isBlank()
                ? doc.getDocumentLabel()
                : DocumentPreviewController.getDocTitle(doc.getDocumentType());
        String cleanDocName = docLabel.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String baseName = request.getRequestCode() + "_เอกสารที่_" + doc.getDocumentType() + "_" + cleanDocName;

        String safeFilename = java.net.URLEncoder.encode(baseName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        String asciiFilename = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");

        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdfData = documentService.convertDocxToPdf(data);
            ByteArrayResource resource = new ByteArrayResource(pdfData);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + asciiFilename + ".pdf\"; filename*=UTF-8''" + safeFilename + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfData.length)
                    .body(resource);
        }

        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiFilename + ".docx\"; filename*=UTF-8''" + safeFilename + ".docx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(data.length)
                .body(resource);
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
