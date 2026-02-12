package com.ecom.academic.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/academic")
public class AcademicAdminController {

    @Autowired
    private AcademicRequestService requestService;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<Integer, String> DOC_LABELS = Map.of(
            1, "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน",
            2, "การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ",
            3, "คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน",
            4, "บันทึกข้อความ ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ",
            5, "ประชุมกรรมการประเมินผลการสอน",
            6, "แบบฟอร์มประเมินการสอน ตามประกาศ มข.1607-66",
            7, "ส่วนที่ 3 แบบประเมินผลการสอน",
            8, "บันทึกข้อความ แจ้งผลการประเมินผลการสอน");

    @GetMapping("/requests")
    public String listRequests(Model model) {
        model.addAttribute("requests", requestService.findAll());
        model.addAttribute("statuses", RequestStatus.values());
        return "academic/admin/requests";
    }

    @GetMapping("/request/{id}")
    public String viewRequest(@PathVariable Long id, Model model) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        List<AcademicDocument> documents = requestService.getDocuments(id);
        model.addAttribute("request", request);
        model.addAttribute("documents", documents);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("statusHistory", requestService.getStatusHistory(id));
        model.addAttribute("docLabels", DOC_LABELS);
        return "academic/admin/request_detail";
    }

    @PostMapping("/request/{id}/status")
    public String updateStatus(@PathVariable Long id,
            @RequestParam("status") String status,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "meetingDate", required = false) String meetingDateStr,
            @RequestParam(value = "meetingLocation", required = false) String meetingLocation,
            Principal principal) {
        UserDtls admin = getUser(principal);
        RequestStatus newStatus = RequestStatus.valueOf(status);

        if (newStatus == RequestStatus.MEETING_SCHEDULED && meetingDateStr != null && !meetingDateStr.isEmpty()) {
            LocalDateTime meetingDate = LocalDateTime.parse(meetingDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            requestService.setMeetingDate(id, meetingDate, meetingLocation, admin);
        } else {
            requestService.updateStatus(id, newStatus, admin, note);
        }

        // Log activity
        adminLogService.log(principal.getName(),
                admin != null ? admin.getName() : principal.getName(),
                "UPDATE_REQUEST_STATUS",
                "อัพเดทสถานะคำร้อง #" + id + " เป็น " + newStatus.name() + (note != null ? " (" + note + ")" : ""),
                getClientIpAddress());

        return "redirect:/admin/academic/request/" + id + "?success=status_updated";
    }

    @GetMapping("/request/{id}/document/{type}")
    public String documentForm(@PathVariable Long id, @PathVariable int type, Model model) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        List<AcademicDocument> existingDocs = requestService.getDocumentsByType(id, type);
        model.addAttribute("request", request);
        model.addAttribute("documentType", type);
        model.addAttribute("documentLabel", DOC_LABELS.getOrDefault(type, "Document " + type));
        model.addAttribute("existingDocs", existingDocs);
        model.addAttribute("staffMembers", staffMemberService.findAll());
        model.addAttribute("deans", staffMemberService.findDeans());
        model.addAttribute("heads", staffMemberService.findHeads());
        model.addAttribute("committee", staffMemberService.findCommittee());
        model.addAttribute("hrStaff", staffMemberService.findHR());

        // Load existing JSON data if available
        if (!existingDocs.isEmpty()) {
            model.addAttribute("existingData", existingDocs.get(0).getJsonData());
        }

        // ดึงรายชื่อกรรมการ 3 คนจาก doc_2 เพื่อ auto-fill ในเอกสารถัดไป
        if (type != 2) {
            List<AcademicDocument> doc2List = requestService.getDocumentsByType(id, 2);
            if (!doc2List.isEmpty()) {
                try {
                    String doc2Json = doc2List.get(0).getJsonData();
                    Map<String, Object> doc2Data = objectMapper.readValue(doc2Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    String c1 = doc2Data.getOrDefault("n_1", "").toString();
                    String c2 = doc2Data.getOrDefault("n_2", "").toString();
                    String c3 = doc2Data.getOrDefault("n_3", "").toString();
                    model.addAttribute("defaultCommittee1", c1);
                    model.addAttribute("defaultCommittee2", c2);
                    model.addAttribute("defaultCommittee3", c3);
                } catch (Exception e) {
                    // ignore parse errors
                }
            }
        }

        // สำหรับ doc_1 (admin): ดึงข้อมูลที่ผู้ยื่นกรอกมาแสดง
        if (type == 1) {
            List<AcademicDocument> doc1List = requestService.getDocumentsByType(id, 1);
            if (!doc1List.isEmpty()) {
                try {
                    String doc1Json = doc1List.get(0).getJsonData();
                    Map<String, String> doc1Data = objectMapper.readValue(doc1Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    model.addAttribute("doc1Data", doc1Data);
                } catch (Exception e) {
                    // ignore parse errors
                }
            }
        }

        return "academic/admin/document_form";
    }

    @PostMapping("/request/{id}/document/{type}")
    public String generateDocument(@PathVariable Long id, @PathVariable int type,
            @RequestParam Map<String, String> formData,
            Principal principal) throws IOException {

        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        String jsonData = objectMapper.writeValueAsString(formData);

        if (type == 4) {
            // Document 4: Generate 3 copies for committee members
            List<Map<String, String>> committeeMembers = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                Map<String, String> member = new HashMap<>();
                member.put("name", formData.getOrDefault("committee_name_" + i, ""));
                member.put("position", formData.getOrDefault("committee_position_" + i, ""));
                committeeMembers.add(member);
            }

            List<String> paths = documentService.generateDocument4Copies(id, jsonData, committeeMembers);
            for (int i = 0; i < paths.size(); i++) {
                requestService.saveDocument(request, type, jsonData, paths.get(i),
                        DOC_LABELS.get(type) + " (สำเนาที่ " + (i + 1) + ")", i + 1);
            }
        } else {
            String filePath = documentService.generateDocument(id, type, jsonData, null);
            requestService.saveDocument(request, type, jsonData, filePath,
                    DOC_LABELS.getOrDefault(type, "Document " + type), null);
        }

        // Log activity
        UserDtls admin = getUser(principal);
        adminLogService.log(principal.getName(),
                admin != null ? admin.getName() : principal.getName(),
                "GENERATE_DOCUMENT",
                "สร้างเอกสารที่ " + type + " (" + DOC_LABELS.getOrDefault(type, "Document " + type) + ") สำหรับคำร้อง #" + id,
                getClientIpAddress());

        return "redirect:/admin/academic/request/" + id + "?success=doc_generated";
    }

    @GetMapping("/request/{id}/download/{docId}")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable Long id,
            @PathVariable Long docId) throws IOException {
        List<AcademicDocument> docs = requestService.getDocuments(id);
        AcademicDocument doc = docs.stream()
                .filter(d -> d.getId().equals(docId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Document not found"));

        byte[] data = documentService.getDocumentBytes(doc.getGeneratedFilePath());
        ByteArrayResource resource = new ByteArrayResource(data);
        String filename = Paths.get(doc.getGeneratedFilePath()).getFileName().toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    }

    @GetMapping("/request/{id}/download-all")
    public ResponseEntity<ByteArrayResource> downloadAll(@PathVariable Long id) throws IOException {
        List<AcademicDocument> docs = requestService.getDocuments(id);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (AcademicDocument doc : docs) {
                if (doc.getGeneratedFilePath() == null)
                    continue;
                Path filePath = Paths.get(doc.getGeneratedFilePath());
                if (!Files.exists(filePath))
                    continue;

                String entryName = filePath.getFileName().toString();
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(Files.readAllBytes(filePath));
                zos.closeEntry();
            }
        }

        byte[] zipBytes = baos.toByteArray();
        ByteArrayResource resource = new ByteArrayResource(zipBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"request_" + id + "_documents.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zipBytes.length)
                .body(resource);
    }

    @PostMapping("/request/{id}/upload-result")
    public String uploadResult(@PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        String uploadDir = "uploads/academic/" + id + "/";
        Files.createDirectories(Paths.get(uploadDir));
        String filePath = uploadDir + "result_" + file.getOriginalFilename();
        file.transferTo(Paths.get(filePath));

        requestService.setResultFile(id, filePath);
        return "redirect:/admin/academic/request/" + id + "?success=result_uploaded";
    }

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
}
