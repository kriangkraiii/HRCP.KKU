package com.ecom.academic.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicEmailService;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/user/academic")
public class AcademicApplicantController {

    @Autowired
    private AcademicRequestService requestService;

    @Autowired
    private DocumentGenerationService documentService;

    @Autowired
    private StaffMemberService staffMemberService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicEmailService emailService;

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
        return "academic/applicant/dashboard";
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
        boolean hasDoc0 = allDocs.stream().anyMatch(d -> d.getDocumentType() == 0);
        boolean hasDoc1 = allDocs.stream().anyMatch(d -> d.getDocumentType() == 1);

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
        model.addAttribute("request", request);
        model.addAttribute("existingDocs", existingDocs);
        if (!existingDocs.isEmpty()) {
            String jsonData = existingDocs.get(0).getJsonData();
            model.addAttribute("existingData", jsonData);
            try {
                Map<String, String> doc0Data = objectMapper.readValue(jsonData,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                model.addAttribute("doc0Data", doc0Data);
            } catch (Exception e) {
                // ignore parse errors – page will render with empty defaults
            }
        }
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
        model.addAttribute("request", request);
        model.addAttribute("existingDocs", existingDocs);
        if (!existingDocs.isEmpty()) {
            String jsonData = existingDocs.get(0).getJsonData();
            model.addAttribute("existingData", jsonData);
            try {
                Map<String, String> doc1Data = objectMapper.readValue(jsonData,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                model.addAttribute("doc1Data", doc1Data);
            } catch (Exception e) {
                // ignore parse errors
            }
        }
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

        // ส่งอีเมลแจ้งเตือนแอดมิน
        emailService.sendNewRequestNotificationToAdmins(request);

        return "redirect:/user/academic/request/" + id + "?success=submitted";
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
        boolean hasDoc0 = allDocuments.stream().anyMatch(d -> d.getDocumentType() == 0);
        boolean hasDoc1 = allDocuments.stream().anyMatch(d -> d.getDocumentType() == 1);
        model.addAttribute("hasDoc0", hasDoc0);
        model.addAttribute("hasDoc1", hasDoc1);

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

        String uploadDir = "uploads/academic/" + id + "/revisions/";
        Files.createDirectories(Path.of(uploadDir));
        String filePath = uploadDir + file.getOriginalFilename();
        file.transferTo(Path.of(filePath));

        requestService.setRevisionFile(id, filePath);
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

        byte[] data = documentService.getDocumentBytes(doc.getGeneratedFilePath());

        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdfData = documentService.convertDocxToPdf(data);
            ByteArrayResource resource = new ByteArrayResource(pdfData);
            String filename = Path.of(doc.getGeneratedFilePath()).getFileName().toString()
                    .replace(".docx", ".pdf");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfData.length)
                    .body(resource);
        }

        ByteArrayResource resource = new ByteArrayResource(data);
        String filename = Path.of(doc.getGeneratedFilePath()).getFileName().toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    }

    @GetMapping("/download-result/{id}")
    public ResponseEntity<ByteArrayResource> downloadResult(@PathVariable Long id, Principal principal)
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

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + path.getFileName().toString() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    }

    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }
}
