package com.ecom.academic.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        model.addAttribute("attachments", requestService.getAttachments(id));
        model.addAttribute("attachmentCount", requestService.countAttachments(id));
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

        // สำหรับ doc_7 หรือ doc_8: ดึงผลจาก doc_6 มา auto-fill (default)
        if (type == 7 || type == 8) {
            List<AcademicDocument> doc6List = requestService.getDocumentsByType(id, 6);
            if (!doc6List.isEmpty()) {
                try {
                    String doc6Json = doc6List.get(0).getJsonData();
                    Map<String, Object> doc6Data = objectMapper.readValue(doc6Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    model.addAttribute("doc6Data", doc6Data);
                } catch (Exception e) {
                    // ignore
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

        // ============ Document 6: คำนวณคะแนนถ่วงน้ำหนักฝั่ง server ============
        if (type == 6) {
            // ค่าน้ำหนักแต่ละส่วน: ส่วนที่ 1=20, 2=30, 3=30, 4=20
            int[] weights = {20, 30, 30, 20};
            double grandTotal = 0;

            for (int sec = 1; sec <= 4; sec++) {
                // Admin กรอกคะแนนรวมต่อส่วน (1 ค่าต่อส่วน) ในช่อง sec_score_X
                String secScoreVal = formData.getOrDefault("sec_score_" + sec, "0");
                double secScore = 0;
                try {
                    secScore = Double.parseDouble(secScoreVal);
                } catch (NumberFormatException e) {
                    secScore = 0;
                }

                // วิเคราะห์ว่าคะแนนตกอยู่ในช่วงไหน (5 ช่วง)
                // ช่วง: 0-1 = score_1, 1.01-2 = score_2, 2.01-3 = score_3, 3.01-4 = score_4, 4.01-5 = score_5
                // placeholder ใน template DOCX: {{score11}}, {{score12}}, ..., {{score45}}
                for (int range = 1; range <= 5; range++) {
                    String key = "score" + sec + range;
                    boolean inRange = false;
                    if (range == 1) inRange = (secScore > 0 && secScore <= 1);
                    else if (range == 2) inRange = (secScore > 1 && secScore <= 2);
                    else if (range == 3) inRange = (secScore > 2 && secScore <= 3);
                    else if (range == 4) inRange = (secScore > 3 && secScore <= 4);
                    else if (range == 5) inRange = (secScore > 4 && secScore <= 5);
                    // ช่วงที่ตรง → ใส่คะแนน, ช่วงอื่น → ว่าง
                    formData.put(key, inRange ? String.valueOf(secScore) : "");
                }

                // สูตร: (คะแนน / 5) × ค่าน้ำหนัก
                double weighted = (secScore / 5.0) * weights[sec - 1];
                formData.put("score" + sec + "x", "%.2f".formatted(weighted));
                grandTotal += weighted;
            }

            // คะแนนรวม
            formData.put("scorex", "%.2f".formatted(grandTotal));

            // สรุปผลการประเมิน: ติ้กช่องตามเกณฑ์
            long roundedTotal = Math.round(grandTotal);
            formData.put("ch1", roundedTotal < 56 ? "☑" : "☐");
            formData.put("ch2", (roundedTotal >= 57 && roundedTotal <= 70) ? "☑" : "☐");
            formData.put("ch3", (roundedTotal >= 71 && roundedTotal <= 85) ? "☑" : "☐");
            formData.put("ch4", roundedTotal >= 86 ? "☑" : "☐");

            // กำหนด eval_level จากผลคะแนนเพื่อส่งต่อไป doc7/doc8
            String evalLevel = "";
            if (roundedTotal < 56) evalLevel = "ไม่ผ่าน";
            else if (roundedTotal <= 70) evalLevel = "ชำนาญ";
            else if (roundedTotal <= 85) evalLevel = "ชำนาญพิเศษ";
            else evalLevel = "เชี่ยวชาญ";
            formData.put("eval_result_level", evalLevel);
        }

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
        String filename = Path.of(doc.getGeneratedFilePath()).getFileName().toString();

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
                Path filePath = Path.of(doc.getGeneratedFilePath());
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
            @RequestParam("file") MultipartFile file,
            Principal principal) throws IOException {

        // ตรวจสอบจำนวนไฟล์ไม่เกิน 10
        long currentCount = requestService.countAttachments(id);
        if (currentCount >= 10) {
            return "redirect:/admin/academic/request/" + id + "?error=max_attachments";
        }

        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // ตรวจสอบประเภทไฟล์
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null ||
                (!originalFilename.toLowerCase().endsWith(".pdf") && !originalFilename.toLowerCase().endsWith(".docx"))) {
            return "redirect:/admin/academic/request/" + id + "?error=invalid_file_type";
        }

        String uploadDir = "uploads/academic/" + id + "/attachments/";
        Files.createDirectories(Path.of(uploadDir));

        // สร้างชื่อไฟล์ไม่ซ้ำ
        String storedFilename = System.currentTimeMillis() + "_" + originalFilename;
        String filePath = uploadDir + storedFilename;
        file.transferTo(Path.of(filePath));

        // บันทึกข้อมูลลง DB
        com.ecom.academic.model.AcademicAttachment attachment = new com.ecom.academic.model.AcademicAttachment();
        attachment.setRequest(request);
        attachment.setOriginalFilename(originalFilename);
        attachment.setStoredFilePath(filePath);
        attachment.setFileType(originalFilename.toLowerCase().endsWith(".pdf") ? "PDF" : "DOCX");
        attachment.setFileSize(file.getSize());
        requestService.saveAttachment(attachment);

        // Log activity
        UserDtls admin = getUser(principal);
        adminLogService.log(principal.getName(),
                admin != null ? admin.getName() : principal.getName(),
                "UPLOAD_ATTACHMENT",
                "อัปโหลดเอกสารเพิ่มเติม \"" + originalFilename + "\" สำหรับคำร้อง #" + id,
                getClientIpAddress());

        return "redirect:/admin/academic/request/" + id + "?success=attachment_uploaded";
    }

    @GetMapping("/request/{id}/attachment/{attachmentId}/download")
    public ResponseEntity<ByteArrayResource> downloadAttachment(@PathVariable Long id,
            @PathVariable Long attachmentId) throws IOException {
        com.ecom.academic.model.AcademicAttachment attachment = requestService.findAttachmentById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        byte[] data = Files.readAllBytes(Path.of(attachment.getStoredFilePath()));
        ByteArrayResource resource = new ByteArrayResource(data);

        String contentType = attachment.getFileType().equalsIgnoreCase("PDF")
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(resource);
    }

    @PostMapping("/request/{id}/attachment/{attachmentId}/delete")
    public String deleteAttachment(@PathVariable Long id,
            @PathVariable Long attachmentId,
            Principal principal) {
        com.ecom.academic.model.AcademicAttachment attachment = requestService.findAttachmentById(attachmentId)
                .orElse(null);

        if (attachment != null) {
            // ลบไฟล์จริง
            try {
                Files.deleteIfExists(Path.of(attachment.getStoredFilePath()));
            } catch (IOException e) {
                // ignore
            }
            requestService.deleteAttachment(attachmentId);

            // Log activity
            UserDtls admin = getUser(principal);
            adminLogService.log(principal.getName(),
                    admin != null ? admin.getName() : principal.getName(),
                    "DELETE_ATTACHMENT",
                    "ลบเอกสารเพิ่มเติม \"" + attachment.getOriginalFilename() + "\" จากคำร้อง #" + id,
                    getClientIpAddress());
        }

        return "redirect:/admin/academic/request/" + id + "?success=attachment_deleted";
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
