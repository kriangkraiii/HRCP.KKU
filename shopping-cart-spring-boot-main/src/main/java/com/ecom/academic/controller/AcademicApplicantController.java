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
        List<AcademicRequest> requests = requestService.findByApplicant(user.getId())
                .stream().filter(r -> r.getCurrentStatus() != RequestStatus.DRAFT)
                .collect(Collectors.toList());
        model.addAttribute("requests", requests);
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
        model.addAttribute("staffMembers", staffMemberService.findAll());
        model.addAttribute("user", user);

        // โหลดแบบร่าง doc0 และ doc1 (ถ้ามี) - ใช้ request id = 0 เป็น marker สำหรับแบบร่างก่อนสร้าง request
        // ค้นหา draft จาก request ที่ยังเป็น draft (status = null)
        AcademicRequest draftRequest = requestService.findDraftByApplicant(user.getId());
        if (draftRequest != null) {
            model.addAttribute("draftRequestId", draftRequest.getId());
            List<AcademicDocument> doc0Drafts = requestService.getDocumentsByType(draftRequest.getId(), 0);
            List<AcademicDocument> doc1Drafts = requestService.getDocumentsByType(draftRequest.getId(), 1);
            if (!doc0Drafts.isEmpty()) {
                model.addAttribute("doc0Draft", doc0Drafts.get(0).getJsonData());
            }
            if (!doc1Drafts.isEmpty()) {
                model.addAttribute("doc1Draft", doc1Drafts.get(0).getJsonData());
            }
        }

        return "academic/applicant/new_request";
    }

    /**
     * บันทึกแบบร่างเอกสารที่ 0 หรือ 1 (ยังไม่ส่งคำร้อง)
     */
    @PostMapping("/save-draft")
    public String saveDraft(@RequestParam Map<String, String> formData,
            Principal principal) throws IOException {
        UserDtls user = getUser(principal);
        if (requestService.hasActiveRequest(user.getId())) {
            return "redirect:/user/academic/dashboard?error=active-request";
        }

        // ดึง docType จาก formData
        int docType = Integer.parseInt(formData.getOrDefault("docType", "0"));

        // หา existing draft request หรือสร้างใหม่
        AcademicRequest draftRequest = requestService.findDraftByApplicant(user.getId());
        if (draftRequest == null) {
            draftRequest = requestService.createDraftRequest(user);
        }

        // ลบ key ที่ไม่เกี่ยวข้อง
        formData.remove("docType");
        formData.remove("_csrf");
        String jsonData = objectMapper.writeValueAsString(formData);

        String label = docType == 0
                ? "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน"
                : "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน";

        requestService.saveDraft(draftRequest, docType, jsonData, label, null);

        return "redirect:/user/academic/new-request?saved=doc" + docType;
    }

    @PostMapping("/new-request")
    public String submitNewRequest(@RequestParam Map<String, String> formData,
            Principal principal,
            Model model) throws IOException {
        UserDtls user = getUser(principal);
        if (requestService.hasActiveRequest(user.getId())) {
            return "redirect:/user/academic/dashboard?error=active-request";
        }

        // ค้นหา draft request ที่มีอยู่
        AcademicRequest draftRequest = requestService.findDraftByApplicant(user.getId());
        AcademicRequest request;

        if (draftRequest != null) {
            // แปลง draft เป็น request จริง
            request = requestService.submitDraftRequest(draftRequest);
        } else {
            request = requestService.createRequest(user);
        }

        // บันทึก doc 1 จากฟอร์ม (ข้อมูลล่าสุด)
        // แยก doc1 fields ออกจาก doc0 fields
        Map<String, String> doc1Data = new java.util.LinkedHashMap<>();
        Map<String, String> doc0Data = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_csrf")) continue;
            if (key.startsWith("doc0_")) {
                doc0Data.put(key.substring(5), entry.getValue()); // ตัด prefix "doc0_"
            } else {
                doc1Data.put(key, entry.getValue());
            }
        }

        // สร้าง doc 1
        String doc1Json = objectMapper.writeValueAsString(doc1Data);
        String doc1Path = documentService.generateDocument(request.getId(), 1, doc1Json, null);
        requestService.saveDocument(request, 1, doc1Json, doc1Path,
                "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", null);

        // สร้าง doc 0 (ถ้ามีข้อมูล)
        if (!doc0Data.isEmpty() && doc0Data.values().stream().anyMatch(v -> v != null && !v.isEmpty())) {
            String doc0Json = objectMapper.writeValueAsString(doc0Data);
            String doc0Path = documentService.generateDocument(request.getId(), 0, doc0Json, null);
            requestService.saveDocument(request, 0, doc0Json, doc0Path,
                    "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน", null);
        }

        // ส่งอีเมลแจ้งเตือนแอดมิน
        emailService.sendNewRequestNotificationToAdmins(request);

        return "redirect:/user/academic/request/" + request.getId();
    }

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
            model.addAttribute("existingData", existingDocs.get(0).getJsonData());
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

        // ส่งอีเมลแจ้งเตือนแอดมิน
        emailService.sendNewRequestNotificationToAdmins(request);

        return "redirect:/user/academic/request/" + id + "?success=doc0_submitted";
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

        // เช็คว่า doc 0 ถูกกรอกแล้วหรือยัง
        boolean hasDoc0 = allDocuments.stream().anyMatch(d -> d.getDocumentType() == 0);
        model.addAttribute("hasDoc0", hasDoc0);

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
