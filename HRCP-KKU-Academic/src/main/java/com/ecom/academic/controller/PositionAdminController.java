package com.ecom.academic.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import com.ecom.util.FileUtils;
import com.ecom.config.ClientIpUtils;
import com.ecom.academic.model.PositionAttachment;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import com.ecom.service.AdminLogService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/position")
public class PositionAdminController {

    private static final Logger logger = LoggerFactory.getLogger(PositionAdminController.class);

    private final PositionRequestService positionService;

    private final DocumentGenerationService documentService;

    private final StaffMemberService staffMemberService;

    private final UserRepository userRepository;

    private final AdminLogService adminLogService;

    private final HttpServletRequest httpRequest;

    private final com.ecom.academic.repository.AcademicRequestRepository academicRequestRepository;

    private final com.ecom.academic.repository.AcademicDocumentRepository academicDocumentRepository;

    private final com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper;

    private final com.ecom.academic.service.DocumentPrewarmService documentPrewarmService;

    private final com.ecom.academic.service.SignatureWorkflowService signatureWorkflow;
    private final com.ecom.academic.service.AcademicCommitteeService committeeService;
    private final com.ecom.academic.service.SignedDocumentRenderer signedDocumentRenderer;

    public PositionAdminController(
            PositionRequestService positionService,
            DocumentGenerationService documentService,
            StaffMemberService staffMemberService,
            UserRepository userRepository,
            AdminLogService adminLogService,
            HttpServletRequest httpRequest,
            com.ecom.academic.repository.AcademicRequestRepository academicRequestRepository,
            com.ecom.academic.repository.AcademicDocumentRepository academicDocumentRepository,
            com.ecom.academic.service.DocumentDataAutoFillHelper autoFillHelper,
            com.ecom.academic.service.DocumentPrewarmService documentPrewarmService,
            com.ecom.academic.service.SignatureWorkflowService signatureWorkflow,
            com.ecom.academic.service.AcademicCommitteeService committeeService,
            com.ecom.academic.service.SignedDocumentRenderer signedDocumentRenderer) {
        this.positionService = positionService;
        this.documentService = documentService;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
        this.adminLogService = adminLogService;
        this.httpRequest = httpRequest;
        this.academicRequestRepository = academicRequestRepository;
        this.academicDocumentRepository = academicDocumentRepository;
        this.autoFillHelper = autoFillHelper;
        this.documentPrewarmService = documentPrewarmService;
        this.signatureWorkflow = signatureWorkflow;
        this.committeeService = committeeService;
        this.signedDocumentRenderer = signedDocumentRenderer;
    }

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
        for (PositionDocument d : documents) {
            String fullLabel = positionService.getDocLabel(d.getDocumentType());
            if (fullLabel != null && (d.getDocumentLabel() == null || d.getDocumentLabel().isBlank()
                    || d.getDocumentLabel().matches("^(?:เอกสาร|Document)\\s*ที่?\\s*\\d+$"))) {
                d.setDocumentLabel(fullLabel);
            }
        }
        List<Integer> completedDocs = positionService.getCompletedDocTypes(id);

        model.addAttribute("request", request);
        model.addAttribute("documents", documents);
        model.addAttribute("completedDocs", completedDocs);
        model.addAttribute("docLabels", positionService.getAdminDocLabels());
        model.addAttribute("statuses", PositionRequestStatus.values());
        // Only the moves the process allows from where this request stands.
        // Offering the whole list invited exactly the mistake the guard now
        // refuses, and left the officer to find out from an error message.
        model.addAttribute("allowedNextStatuses", request.getCurrentStatus().allowedNext());
        model.addAttribute("statusHistory", positionService.getStatusHistory(id));
        model.addAttribute("editHistory", positionService.getEditHistory(id));
        model.addAttribute("progressSteps", PositionRequestStatus.getProgressSteps());

        // Attachments
        model.addAttribute("attachments", positionService.getAttachments(id));
        model.addAttribute("attachmentCount", positionService.countAttachments(id));

        // Progress percentage
        PositionRequestStatus[] steps = PositionRequestStatus.getProgressSteps();
        int currentIdx = Math.max(0, request.getCurrentStatus().progressIndex());
        int progressPercent = steps.length > 1 ? (currentIdx * 100) / (steps.length - 1) : 0;
        model.addAttribute("progressPercent", progressPercent);

        // ดึงข้อมูลตำแหน่งจาก doc_2
        List<PositionDocument> doc2List = positionService.getDocumentsByType(id, 2);
        if (!doc2List.isEmpty()) {
            try {
                java.util.Map<String, String> doc2Data = objectMapper.readValue(doc2List.get(0).getJsonData(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                model.addAttribute("doc2Data", doc2Data);
            } catch (Exception e) { /* ignore */ }
        }

        // ดึงเอกสารที่ 9 (ผลประเมินการสอน) จากคำร้องประเมินผลการสอน (AcademicRequest) ของผู้ยื่นคนเดียวกัน
        Integer applicantId = request.getApplicant().getId();
        List<com.ecom.academic.model.AcademicRequest> evalRequests = academicRequestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId);
        for (com.ecom.academic.model.AcademicRequest evalReq : evalRequests) {
            List<com.ecom.academic.model.AcademicDocument> evalDoc9List = academicDocumentRepository.findByRequestIdAndDocumentType(evalReq.getId(), 9);
            if (!evalDoc9List.isEmpty() && evalDoc9List.get(0).getJsonData() != null) {
                try {
                    java.util.Map<String, String> evalDoc9Data = objectMapper.readValue(
                            evalDoc9List.get(0).getJsonData(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                    model.addAttribute("evalDoc9Data", evalDoc9Data);
                    model.addAttribute("linkedEvalRequest", evalReq);
                    model.addAttribute("evalDoc9GeneratedFile", evalDoc9List.get(0).getGeneratedFilePath());
                    break;
                } catch (Exception e) { /* ignore parse error */ }
            }
        }

        // Background warming for completed docs on view
        documentPrewarmService.prewarmAllRequestDocuments(id, true);

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

            // Log activity
            try {
                adminLogService.log(principal.getName(), admin.getName(),
                        "UPDATE_POSITION_STATUS",
                        "อัพเดทสถานะคำร้องตำแหน่ง #" + id + " เป็น " + newStatus.name()
                                + (note != null ? " (" + note + ")" : ""),
                        getClientIpAddress());
            } catch (Exception logEx) { /* ignore */ }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorDetail", e.getClass().getSimpleName() + ": " + e.getMessage());
            return "redirect:/admin/position/request/" + id + "?error=status_update_failed";
        }

        return "redirect:/admin/position/request/" + id + "?success=status_updated";
    }

    // ================== Document Forms ==================

    @GetMapping("/request/{id}/document/{type}")
    public String documentForm(@PathVariable Long id, @PathVariable int type, Model model,
            Principal principal) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        // แบบร่างยังเป็นของผู้ยื่น เจ้าหน้าที่จึงยังเปิดแบบฟอร์มไม่ได้ — แต่ต้องบอก
        // เหตุผลนั้นตรง ๆ เดิมใช้ error=status_update_failed ซึ่งขึ้นข้อความว่า
        // "ไม่สามารถอัปเดตสถานะได้ กรุณาลองใหม่อีกครั้ง" ทั้งที่ไม่มีการอัปเดตสถานะใด ๆ
        // เกิดขึ้นเลย และการ "ลองใหม่" ก็ได้ผลเดิมทุกครั้งไม่ว่าจะกดกี่หน
        if (request.getCurrentStatus().isDraft()) {
            return "redirect:/admin/position/request/" + id + "?error=still_a_draft";
        }

        List<PositionDocument> existing = positionService.getDocumentsByType(id, type);
        String existingData = existing.isEmpty() ? null : existing.get(0).getJsonData();
        Map<String, String> autoFilledData = autoFillHelper.getPreFilledPositionDocData(request, type, existingData);

        model.addAttribute("request", request);
        model.addAttribute("documentType", type);
        model.addAttribute("documentLabel", positionService.getDocLabel(type));
        model.addAttribute("existingData", existingData);
        model.addAttribute("autoFilledData", autoFilledData);
        model.addAttribute("docData", autoFilledData);
        // Role-specific lists so each signer dropdown offers the right people.
        // These were all one undifferentiated "deans" list, which put the whole
        // staff into the department-head and HR pickers too.
        model.addAttribute("staffMembers", staffMemberService.findAll());
        model.addAttribute("deans", staffMemberService.findByRoleOrAll("DEAN"));
        model.addAttribute("heads", staffMemberService.findByRoleOrAll("HEAD"));
        model.addAttribute("committee", staffMemberService.findByRoleOrAll("COMMITTEE"));
        model.addAttribute("hrStaff", staffMemberService.findByRoleOrAll("HR"));
        model.addAttribute("externalExperts", committeeService.findAllActive());

        if (type == 5) {
            List<PositionDocument> doc1List = positionService.getDocumentsByType(id, 1);
            String doc1Data = doc1List.isEmpty() ? null : doc1List.get(0).getJsonData();
            model.addAttribute("doc1Data", doc1Data);
        }

        if (type == 8) {
            List<PositionDocument> doc6List = positionService.getDocumentsByType(id, 6);
            String doc6Data = doc6List.isEmpty() ? null : doc6List.get(0).getJsonData();
            model.addAttribute("doc6Data", doc6Data);
        }

        // แผงลงนามอิเล็กทรอนิกส์ (fragment ใช้ร่วมกันทั้งสอง phase)
        model.addAttribute("documentType", type);
        model.addAttribute("signatureModule", SignatureModule.POSITION);
        model.addAttribute("signaturePanel",
                signatureWorkflow.buildPanel(SignatureModule.POSITION, id, type, getUser(principal)));

        return "academic/position/admin/doc_form_" + type;
    }

    @PostMapping("/request/{id}/document/{type}")
    public String saveDocument(@PathVariable Long id, @PathVariable int type,
            @RequestParam Map<String, String> formData, Principal principal) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        if (request.getCurrentStatus().isDraft()) {
            return "redirect:/admin/position/request/" + id + "?error=still_a_draft";
        }

        // เอกสารที่กำลังเวียนลงนามอยู่ (หรือลงนามครบแล้ว) ห้ามแก้ — ดูเหตุผลใน
        // AcademicAdminController.generateDocument
        if (signatureWorkflow.isDocumentLocked(SignatureModule.POSITION, id, type)) {
            return "redirect:/admin/position/request/" + id
                    + "/document/" + type + "?error=document_locked_for_signing";
        }

        boolean sendNotify = "true".equals(formData.getOrDefault("sendNotify", "false"));
        formData.remove("_csrf");
        formData.remove("sendNotify");
        formData.remove("action");

        try {
            if (type == 7) {
                formData = mergeWithExistingData(request, type, formData);
            }

            String jsonData = objectMapper.writeValueAsString(formData);
            String label = positionService.getDocLabel(type);

            String filePath = null;
            try {
                filePath = documentService.generateP2Document(request, type, jsonData);
            } catch (Exception e) {
                logger.warn("Phase2 doc generation failed for type {}: {}", type, e.getMessage());
            }

            boolean isNew = positionService.getDocumentsByType(id, type).isEmpty();
            positionService.saveDocument(request, type, jsonData, filePath, label, null, "ADMIN");
            documentPrewarmService.prewarmPositionDocument(id, type, jsonData);

            // Log document edit
            UserDtls admin = getUser(principal);
            positionService.logDocumentEdit(request, type, label, admin,
                    isNew ? PositionDocumentEditLog.EditAction.CREATED : PositionDocumentEditLog.EditAction.UPDATED);

            // Log activity
            try {
                adminLogService.log(principal.getName(),
                        admin != null ? admin.getName() : principal.getName(),
                        "GENERATE_POSITION_DOCUMENT",
                        "สร้างเอกสารที่ " + type + " (" + label + ") สำหรับคำร้องตำแหน่ง #" + id,
                        getClientIpAddress());
            } catch (Exception logEx) { /* ignore */ }

            // Advance the status; notify only if the officer asked to.
            // Same reasoning as the Phase 1 controller: the checkbox decides who
            // gets an e-mail, not whether the step counts as done.
            try {
                positionService.autoUpdateStatusByDocument(id, type, admin, jsonData, sendNotify);
            } catch (Exception e) {
                logger.warn("Phase2 auto status update failed for request #{}, doc type {}: {}", id, type, e.getMessage());
                return "redirect:/admin/position/request/" + id + "?success=doc_generated&warn=notify_failed";
            }

            return "redirect:/admin/position/request/" + id + "?success=doc_generated";
        } catch (Exception e) {
            return "redirect:/admin/position/request/" + id + "?error=doc_save_failed";
        }
    }

    @PostMapping("/request/{id}/document/{type}/request-resign")
    public String requestDocumentResign(
            @PathVariable Long id,
            @PathVariable int type,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        UserDtls admin = getUser(principal);
        positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง ID: " + id));

        var actorContext = new com.ecom.academic.service.SignatureWorkflowService.ActorContext(
                getClientIpAddress(), httpRequest.getHeader("User-Agent"));

        signatureWorkflow.requestDocumentResign(
                com.ecom.academic.model.SignatureModule.POSITION,
                id,
                type,
                reason,
                admin,
                actorContext);

        try {
            adminLogService.log(principal.getName(),
                    admin != null ? admin.getName() : principal.getName(),
                    "REQUEST_DOC_RESIGN",
                    "ขอให้แก้ไขและลงนามใหม่ เอกสารที่ " + type + " สำหรับคำร้องตำแหน่ง #" + id + (reason != null && !reason.isBlank() ? " เหตุผล: " + reason : ""),
                    getClientIpAddress());
        } catch (Exception logEx) { /* ignore */ }

        redirectAttributes.addFlashAttribute("success", "ส่งคำขอแก้ไขและลงนามใหม่สำหรับเอกสารที่ " + type + " เรียบร้อยแล้ว");
        return "redirect:/admin/position/request/" + id;
    }

    // ================== Document Download ==================

    @GetMapping("/request/{id}/document/{type}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id,
            @PathVariable int type,
            @RequestParam(value = "format", defaultValue = "docx") String format) throws IOException {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

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

        // Try using existing generated file first
        if (data == null && doc != null && doc.getGeneratedFilePath() != null) {
            Path filePath = Path.of(doc.getGeneratedFilePath());
            if (Files.exists(filePath)) {
                data = Files.readAllBytes(filePath);
            }
        }

        // If no file exists, generate on-the-fly from jsonData + template
        if (data == null && doc != null && doc.getJsonData() != null) {
            try {
                String generatedPath = documentService.generateP2Document(request, type, doc.getJsonData());
                if (generatedPath != null) {
                    Path filePath = Path.of(generatedPath);
                    if (Files.exists(filePath)) {
                        data = Files.readAllBytes(filePath);
                        // Save the path for future downloads
                        doc.setGeneratedFilePath(generatedPath);
                        positionService.saveDocument(request, type, doc.getJsonData(),
                                generatedPath, label, doc.getCopyNumber(), doc.getFilledBy());
                    }
                }
            } catch (Exception e) {
                logger.warn("On-the-fly DOCX generation failed for doc {}: {}", type, e.getMessage());
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

    @GetMapping("/request/{id}/download-all")
    public ResponseEntity<ByteArrayResource> downloadAll(@PathVariable Long id) throws IOException {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        List<PositionDocument> documents = positionService.getDocuments(id);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (PositionDocument doc : documents) {
                byte[] docBytes = null;

                // Check signed envelope first
                java.util.Optional<com.ecom.academic.model.SignatureRequest> optEnvelope =
                        signatureWorkflow.findEnvelope(com.ecom.academic.model.SignatureModule.POSITION, id, doc.getDocumentType());
                if (optEnvelope.isPresent()) {
                    try {
                        docBytes = signedDocumentRenderer.renderDocx(optEnvelope.get());
                    } catch (Exception e) {
                        // ignore
                    }
                }

                // Try existing file
                if (docBytes == null && doc.getGeneratedFilePath() != null) {
                    Path filePath = Path.of(doc.getGeneratedFilePath());
                    if (Files.exists(filePath)) {
                        docBytes = Files.readAllBytes(filePath);
                    }
                }

                // Generate on-the-fly if needed
                if (docBytes == null && doc.getJsonData() != null) {
                    try {
                        String generatedPath = documentService.generateP2Document(
                                request, doc.getDocumentType(), doc.getJsonData());
                        if (generatedPath != null) {
                            Path filePath = Path.of(generatedPath);
                            if (Files.exists(filePath)) {
                                docBytes = Files.readAllBytes(filePath);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("ZIP: doc gen failed for type {}: {}", doc.getDocumentType(), e.getMessage());
                    }
                }

                if (docBytes != null) {
                    String docLabel = doc.getDocumentLabel() != null && !doc.getDocumentLabel().isBlank()
                            ? doc.getDocumentLabel()
                            : positionService.getDocLabel(doc.getDocumentType());
                    String cleanDocName = docLabel.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
                    String entryName = "เอกสารตำแหน่งที่_" + doc.getDocumentType() + "_" + cleanDocName + ".docx";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(docBytes);
                    zos.closeEntry();
                }
            }
        }

        String zipBaseName = request.getRequestCode() + "_เอกสารขอกำหนดตำแหน่งทั้งหมด.zip";
        String encodedZip = java.net.URLEncoder.encode(zipBaseName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String asciiZip = request.getRequestCode() + "_position_documents.zip";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiZip + "\"; filename*=UTF-8''" + encodedZip)
                .body(new ByteArrayResource(baos.toByteArray()));
    }

    // ================== Attachment Management ==================

    @PostMapping("/request/{id}/upload-attachment")
    public String uploadAttachment(@PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes,
            Principal principal) {
        try {
            PositionRequest request = positionService.findById(id)
                    .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

            if (positionService.countAttachments(id) >= 20) {
                return "redirect:/admin/position/request/" + id + "?error=max_attachments";
            }

            long currentTotalSize = positionService.getTotalAttachmentSize(id);
            if (currentTotalSize + file.getSize() > 75L * 1024L * 1024L) {
                return "redirect:/admin/position/request/" + id + "?error=total_size_exceeded";
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return "redirect:/admin/position/request/" + id + "?error=invalid_file_type";
            }
            String lower = originalFilename.toLowerCase();
            boolean isAllowed = lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".zip");
            if (!isAllowed) {
                return "redirect:/admin/position/request/" + id + "?error=invalid_file_type";
            }

            String uploadDir = "uploads/position/" + id + "/attachments/";
            Files.createDirectories(Path.of(uploadDir));
            String storedName = System.currentTimeMillis() + "_" + FileUtils.sanitizeFilename(originalFilename);
            Path storedPath = Path.of(uploadDir, storedName);
            file.transferTo(storedPath.toFile());

            String fileType = "OTHER";
            if (lower.endsWith(".pdf")) fileType = "PDF";
            else if (lower.endsWith(".docx") || lower.endsWith(".doc")) fileType = "DOCX";
            else if (lower.endsWith(".zip")) fileType = "ZIP";

            PositionAttachment attachment = new PositionAttachment();
            attachment.setRequest(request);
            attachment.setOriginalFilename(originalFilename);
            attachment.setStoredFilePath(storedPath.toString());
            attachment.setFileType(fileType);
            attachment.setFileSize(file.getSize());
            positionService.saveAttachment(attachment);

            // Log activity
            try {
                UserDtls admin = principal != null ? getUser(principal) : null;
                adminLogService.log(
                        principal != null ? principal.getName() : "admin",
                        admin != null ? admin.getName() : "Admin",
                        "UPLOAD_POSITION_ATTACHMENT",
                        "อัปโหลดเอกสารเพิ่มเติม \"" + originalFilename + "\" สำหรับคำร้องตำแหน่ง #" + id,
                        getClientIpAddress());
            } catch (Exception logEx) { /* ignore */ }

            return "redirect:/admin/position/request/" + id + "?success=attachment_uploaded";
        } catch (Exception e) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorDetail", e.getMessage());
            }
            return "redirect:/admin/position/request/" + id + "?error=upload_failed";
        }
    }

    @GetMapping("/request/{id}/attachment/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id,
            @PathVariable Long attachmentId) throws IOException {
        PositionAttachment attachment = positionService.findAttachmentById(attachmentId)
                .orElseThrow(() -> new RuntimeException("ไม่พบเอกสาร"));

        Path path = Path.of(attachment.getStoredFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(path);

        String contentType = attachment.getFileType().equalsIgnoreCase("PDF")
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        String rawFilename = attachment.getOriginalFilename() != null ? attachment.getOriginalFilename() : "attachment";
        String safeFilename = java.net.URLEncoder.encode(rawFilename,
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String asciiFilename = rawFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + safeFilename)
                .contentLength(Files.size(path))
                .body(resource);
    }

    @GetMapping("/request/{id}/attachment/{attachmentId}/view")
    public ResponseEntity<Resource> viewAttachment(@PathVariable Long id,
            @PathVariable Long attachmentId) throws IOException {
        PositionAttachment attachment = positionService.findAttachmentById(attachmentId)
                .orElseThrow(() -> new RuntimeException("ไม่พบเอกสาร"));

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
                logger.warn("Failed to convert DOCX attachment {} to PDF for inline preview, falling back to original file: {}", attachmentId, e.toString());
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

        String safeFilename = java.net.URLEncoder.encode(rawFilename,
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String asciiFilename = rawFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + safeFilename)
                .contentLength(Files.size(path))
                .body(new FileSystemResource(path));
    }

    @PostMapping("/request/{id}/attachment/{attachmentId}/delete")
    public String deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId, Principal principal) {
        // Log activity before deleting
        try {
            PositionAttachment att = positionService.findAttachmentById(attachmentId).orElse(null);
            UserDtls admin = getUser(principal);
            adminLogService.log(principal.getName(),
                    admin != null ? admin.getName() : principal.getName(),
                    "DELETE_POSITION_ATTACHMENT",
                    "ลบเอกสารเพิ่มเติม" + (att != null ? " \"" + att.getOriginalFilename() + "\"" : "") + " จากคำร้องตำแหน่ง #" + id,
                    getClientIpAddress());
        } catch (Exception logEx) { /* ignore */ }

        positionService.deleteAttachment(attachmentId);
        return "redirect:/admin/position/request/" + id + "?success=attachment_deleted";
    }

    // ================== Utility ==================

    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }

    private String getClientIpAddress() {
        return ClientIpUtils.resolveClientIp(httpRequest);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> mergeWithExistingData(PositionRequest request, int type,
            Map<String, String> formData) {
        try {
            List<PositionDocument> existing = positionService.getDocumentsByType(request.getId(), type);
            if (!existing.isEmpty()) {
                String existingJson = existing.get(0).getJsonData();
                if (existingJson != null && !existingJson.isEmpty()) {
                    Map<String, String> existingData = objectMapper.readValue(existingJson, Map.class);
                    for (Map.Entry<String, String> entry : formData.entrySet()) {
                        existingData.put(entry.getKey(), entry.getValue());
                    }
                    return new java.util.LinkedHashMap<>(existingData);
                }
            }
        } catch (Exception e) {
            logger.warn("Merge failed for doc {}: {}", type, e.getMessage());
        }
        return formData;
    }
}
