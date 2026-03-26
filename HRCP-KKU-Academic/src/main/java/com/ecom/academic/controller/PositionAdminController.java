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

import org.springframework.beans.factory.annotation.Autowired;
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

import com.ecom.academic.model.PositionAttachment;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
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

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private DocumentGenerationService documentService;

    @Autowired
    private StaffMemberService staffMemberService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogService adminLogService;

    @Autowired
    private HttpServletRequest httpRequest;

    @Autowired
    private com.ecom.academic.repository.AcademicRequestRepository academicRequestRepository;

    @Autowired
    private com.ecom.academic.repository.AcademicDocumentRepository academicDocumentRepository;

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
        List<Integer> completedDocs = positionService.getCompletedDocTypes(id);

        model.addAttribute("request", request);
        model.addAttribute("documents", documents);
        model.addAttribute("completedDocs", completedDocs);
        model.addAttribute("docLabels", positionService.getAdminDocLabels());
        model.addAttribute("statuses", PositionRequestStatus.values());
        model.addAttribute("statusHistory", positionService.getStatusHistory(id));
        model.addAttribute("editHistory", positionService.getEditHistory(id));
        model.addAttribute("progressSteps", PositionRequestStatus.getProgressSteps());

        // Attachments
        model.addAttribute("attachments", positionService.getAttachments(id));
        model.addAttribute("attachmentCount", positionService.countAttachments(id));

        // Progress percentage
        PositionRequestStatus[] steps = PositionRequestStatus.getProgressSteps();
        int currentIdx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (request.getCurrentStatus().ordinal() >= steps[i].ordinal()) {
                currentIdx = i;
            }
        }
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

        // ดึงเอกสารที่ 8 (ผลประเมินการสอน) จากคำร้องประเมินผลการสอน (AcademicRequest) ของผู้ยื่นคนเดียวกัน
        Integer applicantId = request.getApplicant().getId();
        List<com.ecom.academic.model.AcademicRequest> evalRequests = academicRequestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId);
        for (com.ecom.academic.model.AcademicRequest evalReq : evalRequests) {
            List<com.ecom.academic.model.AcademicDocument> evalDoc8List = academicDocumentRepository.findByRequestIdAndDocumentType(evalReq.getId(), 8);
            if (!evalDoc8List.isEmpty() && evalDoc8List.get(0).getJsonData() != null) {
                try {
                    java.util.Map<String, String> evalDoc8Data = objectMapper.readValue(
                            evalDoc8List.get(0).getJsonData(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                    model.addAttribute("evalDoc8Data", evalDoc8Data);
                    model.addAttribute("linkedEvalRequest", evalReq);
                    model.addAttribute("evalDoc8GeneratedFile", evalDoc8List.get(0).getGeneratedFilePath());
                    break;
                } catch (Exception e) { /* ignore parse error */ }
            }
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
    public String documentForm(@PathVariable Long id, @PathVariable int type, Model model) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

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

        return "academic/position/admin/doc_form_" + type;
    }

    @PostMapping("/request/{id}/document/{type}")
    public String saveDocument(@PathVariable Long id, @PathVariable int type,
            @RequestParam Map<String, String> formData, Principal principal) {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        if (request.getCurrentStatus().isDraft()) {
            return "redirect:/admin/position/request/" + id + "?error=status_update_failed";
        }

        formData.remove("_csrf");

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
                System.err.println("Phase2 doc generation failed for type " + type + ": " + e.getMessage());
            }

            boolean isNew = positionService.getDocumentsByType(id, type).isEmpty();
            positionService.saveDocument(request, type, jsonData, filePath, label, null, "ADMIN");

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

            return "redirect:/admin/position/request/" + id + "?success=doc_generated";
        } catch (Exception e) {
            return "redirect:/admin/position/request/" + id + "?error=doc_save_failed";
        }
    }

    // ================== Document Download ==================

    @GetMapping("/request/{id}/document/{type}/download")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable Long id,
            @PathVariable int type,
            @RequestParam(value = "format", defaultValue = "docx") String format) throws IOException {
        PositionRequest request = positionService.findById(id)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

        List<PositionDocument> docs = positionService.getDocumentsByType(id, type);
        if (docs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PositionDocument doc = docs.get(0);
        byte[] data = null;
        String label = doc.getDocumentLabel() != null ? doc.getDocumentLabel() : "document";

        // Try using existing generated file first
        if (doc.getGeneratedFilePath() != null) {
            Path filePath = Path.of(doc.getGeneratedFilePath());
            if (Files.exists(filePath)) {
                data = Files.readAllBytes(filePath);
            }
        }

        // If no file exists, generate on-the-fly from jsonData + template
        if (data == null && doc.getJsonData() != null) {
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
                System.err.println("On-the-fly DOCX generation failed for doc " + type + ": " + e.getMessage());
            }
        }

        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        // PDF: แปลง DOCX → PDF ผ่าน LibreOffice (เหมือนฝั่งประเมินผลการสอน)
        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdfData = documentService.convertDocxToPdf(data);
            String pdfFilename = label + ".pdf";
            String safePdfFilename = java.net.URLEncoder.encode(pdfFilename, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + safePdfFilename)
                    .contentLength(pdfData.length)
                    .body(new ByteArrayResource(pdfData));
        }

        String filename = label + ".docx";
        String safeFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + safeFilename)
                .body(new ByteArrayResource(data));
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

                // Try existing file
                if (doc.getGeneratedFilePath() != null) {
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
                        System.err.println("ZIP: doc gen failed for type " + doc.getDocumentType() + ": " + e.getMessage());
                    }
                }

                if (docBytes != null) {
                    String entryName = "doc_" + doc.getDocumentType() + "_" +
                            (doc.getDocumentLabel() != null ? doc.getDocumentLabel() : "document") + ".docx";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(docBytes);
                    zos.closeEntry();
                }
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"position_request_" + id + "_documents.zip\"")
                .body(new ByteArrayResource(baos.toByteArray()));
    }

    // ================== Attachment Management ==================

    @PostMapping("/request/{id}/upload-attachment")
    public String uploadAttachment(@PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            PositionRequest request = positionService.findById(id)
                    .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง"));

            if (positionService.countAttachments(id) >= 10) {
                return "redirect:/admin/position/request/" + id + "?error=max_attachments";
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".pdf")
                    && !originalFilename.toLowerCase().endsWith(".docx"))) {
                return "redirect:/admin/position/request/" + id + "?error=invalid_file_type";
            }

            String uploadDir = "uploads/position/" + id + "/attachments/";
            Files.createDirectories(Path.of(uploadDir));
            String storedName = System.currentTimeMillis() + "_" + originalFilename;
            Path storedPath = Path.of(uploadDir, storedName);
            file.transferTo(storedPath.toFile());

            PositionAttachment attachment = new PositionAttachment();
            attachment.setRequest(request);
            attachment.setOriginalFilename(originalFilename);
            attachment.setStoredFilePath(storedPath.toString());
            attachment.setFileType(originalFilename.toLowerCase().endsWith(".pdf") ? "PDF" : "DOCX");
            attachment.setFileSize(file.getSize());
            positionService.saveAttachment(attachment);

            // Log activity
            try {
                adminLogService.log(
                        redirectAttributes != null ? "admin" : "admin",
                        "Admin",
                        "UPLOAD_POSITION_ATTACHMENT",
                        "อัปโหลดเอกสารเพิ่มเติม \"" + originalFilename + "\" สำหรับคำร้องตำแหน่ง #" + id,
                        getClientIpAddress());
            } catch (Exception logEx) { /* ignore */ }

            return "redirect:/admin/position/request/" + id + "?success=attachment_uploaded";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorDetail", e.getMessage());
            return "redirect:/admin/position/request/" + id + "?error=upload_failed";
        }
    }

    @GetMapping("/request/{id}/attachment/{attachmentId}/download")
    public ResponseEntity<ByteArrayResource> downloadAttachment(@PathVariable Long id,
            @PathVariable Long attachmentId) throws IOException {
        PositionAttachment attachment = positionService.findAttachmentById(attachmentId)
                .orElseThrow(() -> new RuntimeException("ไม่พบเอกสาร"));

        byte[] data = Files.readAllBytes(Path.of(attachment.getStoredFilePath()));

        String contentType = attachment.getFileType().equalsIgnoreCase("PDF")
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        String safeFilename = java.net.URLEncoder.encode(attachment.getOriginalFilename(),
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + safeFilename)
                .body(new ByteArrayResource(data));
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
        String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        return httpRequest.getRemoteAddr();
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
            System.err.println("Merge failed for doc " + type + ": " + e.getMessage());
        }
        return formData;
    }
}
