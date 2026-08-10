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

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;

import com.ecom.util.FileUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;
import com.ecom.util.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/academic")
public class AcademicAdminController {

    private final AcademicRequestService requestService;

    private final DocumentGenerationService documentService;

    private final StaffMemberService staffMemberService;

    private final UserRepository userRepository;

    private final AdminLogService adminLogService;

    private final PositionRequestService positionRequestService;

    private final HttpServletRequest httpRequest;

    public AcademicAdminController(
            AcademicRequestService requestService,
            DocumentGenerationService documentService,
            StaffMemberService staffMemberService,
            UserRepository userRepository,
            AdminLogService adminLogService,
            PositionRequestService positionRequestService,
            HttpServletRequest httpRequest) {
        this.requestService = requestService;
        this.documentService = documentService;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
        this.adminLogService = adminLogService;
        this.positionRequestService = positionRequestService;
        this.httpRequest = httpRequest;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // คำอธิบายประเภทเอกสาร (type) เพื่อแสดงใน UI
    private static final Map<Integer, String> DOC_LABELS;
    static {
        DOC_LABELS = new java.util.LinkedHashMap<>();
        DOC_LABELS.put(0, "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน");
        DOC_LABELS.put(1, "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน");
        DOC_LABELS.put(2, "การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ");
        DOC_LABELS.put(3, "คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน");
        DOC_LABELS.put(4, "บันทึกข้อความ ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ");
        DOC_LABELS.put(5, "ข้อเสนอแนะจากคณะอนุกรรมการ");
        DOC_LABELS.put(6, "แบบฟอร์มประเมินการสอน ตามประกาศ มข.1607-66");
        DOC_LABELS.put(7, "ส่วนที่ 3 แบบประเมินผลการสอน");
        DOC_LABELS.put(8, "บันทึกข้อความ แจ้งผลการประเมินผลการสอน");
    }

    @GetMapping("/requests")
    public String listRequests(@RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String statusFilter,
            @RequestParam(value = "type", required = false) String typeFilter,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Model model) {
        List<AcademicRequest> allRequests;
        if (search != null && !search.trim().isEmpty()) {
            allRequests = requestService.searchByApplicantName(search.trim());
            model.addAttribute("searchQuery", search.trim());
        } else {
            allRequests = requestService.findAll();
        }

        // Status counts for dashboard cards (count from ALL requests, before filtering)
        java.util.Map<String, Long> statusCounts = new java.util.LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) {
            if (!status.isDraft()) {
                long count = allRequests.stream()
                        .filter(r -> r.getCurrentStatus() == status)
                        .count();
                statusCounts.put(status.name(), count);
            }
        }

        // Apply status filter if provided
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            try {
                RequestStatus filterStatus = RequestStatus.valueOf(statusFilter);
                allRequests = allRequests.stream()
                        .filter(r -> r.getCurrentStatus() == filterStatus)
                        .collect(java.util.stream.Collectors.toList());
                model.addAttribute("activeStatus", statusFilter);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Split into pending vs completed/rejected
        List<AcademicRequest> pendingRequests = allRequests.stream()
                .filter(r -> !r.getCurrentStatus().isTerminal() && !r.getCurrentStatus().isDraft())
                .sorted((a, b) -> {
                    if (a.getSubmissionDate() == null && b.getSubmissionDate() == null)
                        return 0;
                    if (a.getSubmissionDate() == null)
                        return 1;
                    if (b.getSubmissionDate() == null)
                        return -1;
                    return a.getSubmissionDate().compareTo(b.getSubmissionDate());
                })
                .collect(java.util.stream.Collectors.toList());

        List<AcademicRequest> completedRequests = allRequests.stream()
                .filter(r -> r.getCurrentStatus().isTerminal())
                .sorted((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null)
                        return 0;
                    if (a.getUpdatedAt() == null)
                        return 1;
                    if (b.getUpdatedAt() == null)
                        return -1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("completedRequests", completedRequests);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("requests", allRequests);
        model.addAttribute("activeType", typeFilter);

        // Phase 2: Position requests data
        try {
            String posSearch = httpRequest.getParameter("posSearch");
            String posStatus = httpRequest.getParameter("posStatus");

            List<PositionRequest> positionRequests;
            if (posSearch != null && !posSearch.trim().isEmpty()) {
                positionRequests = positionRequestService.searchByNameOrEmail(posSearch.trim());
                model.addAttribute("posSearch", posSearch.trim());
            } else {
                positionRequests = positionRequestService.findAll();
            }

            // Filter out drafts
            positionRequests = positionRequests.stream()
                    .filter(r -> r.getCurrentStatus() != PositionRequestStatus.DRAFT)
                    .collect(java.util.stream.Collectors.toList());

            // Per-status counts for dashboard cards (before status filtering)
            java.util.Map<String, Long> posStatusCounts = new java.util.LinkedHashMap<>();
            for (PositionRequestStatus ps : PositionRequestStatus.values()) {
                if (!ps.isDraft()) {
                    long c = positionRequests.stream()
                            .filter(r -> r.getCurrentStatus() == ps)
                            .count();
                    posStatusCounts.put(ps.name(), c);
                }
            }
            model.addAttribute("posStatusCounts", posStatusCounts);

            // Apply status filter if provided
            if (posStatus != null && !posStatus.trim().isEmpty()) {
                try {
                    PositionRequestStatus filterSt = PositionRequestStatus.valueOf(posStatus);
                    positionRequests = positionRequests.stream()
                            .filter(r -> r.getCurrentStatus() == filterSt)
                            .collect(java.util.stream.Collectors.toList());
                    model.addAttribute("activePosStatus", posStatus);
                } catch (IllegalArgumentException ignored) {
                }
            }

            long positionPendingCount = positionRequests.stream()
                    .filter(r -> !r.getCurrentStatus().isTerminal() && !r.getCurrentStatus().isEditable())
                    .count();
            long positionCompletedCount = positionRequests.stream()
                    .filter(r -> r.getCurrentStatus().isTerminal())
                    .count();
            model.addAttribute("positionRequests", positionRequests);
            model.addAttribute("positionStatuses", PositionRequestStatus.values());
            model.addAttribute("positionPendingCount", positionPendingCount);
            model.addAttribute("positionCompletedCount", positionCompletedCount);
            model.addAttribute("positionTotalCount", (long) positionRequests.size());
        } catch (Exception e) {
            model.addAttribute("positionRequests", java.util.Collections.emptyList());
            model.addAttribute("positionPendingCount", 0L);
            model.addAttribute("positionCompletedCount", 0L);
            model.addAttribute("positionTotalCount", 0L);
        }

        // Evaluation counts
        long evalTotal = allRequests.stream().filter(r -> !r.getCurrentStatus().isDraft()).count();
        model.addAttribute("evaluationTotalCount", evalTotal);

        // ดึงข้อมูลรายวิชาจาก doc_0 สำหรับทุกคำร้องประเมินผล
        Map<Long, Map<String, String>> doc0DataMap = new HashMap<>();
        for (AcademicRequest req : allRequests) {
            if (!req.getCurrentStatus().isDraft()) {
                List<AcademicDocument> doc0List = requestService.getDocumentsByType(req.getId(), 0);
                if (!doc0List.isEmpty()) {
                    try {
                        Map<String, String> doc0Data = objectMapper.readValue(doc0List.get(0).getJsonData(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                        doc0DataMap.put(req.getId(), doc0Data);
                    } catch (Exception e) { /* ignore */ }
                }
            }
        }
        model.addAttribute("doc0DataMap", doc0DataMap);

        // Build autocomplete suggestions from all applicants
        java.util.Set<String> suggestionsSet = new java.util.LinkedHashSet<>();
        for (AcademicRequest req : allRequests) {
            if (!req.getCurrentStatus().isDraft() && req.getApplicant() != null) {
                if (req.getApplicant().getName() != null) suggestionsSet.add(req.getApplicant().getName());
                if (req.getApplicant().getEmail() != null) suggestionsSet.add(req.getApplicant().getEmail());
            }
        }
        try {
            List<PositionRequest> posAll = positionRequestService.findAll();
            for (PositionRequest pr : posAll) {
                if (!pr.getCurrentStatus().isDraft() && pr.getApplicant() != null) {
                    if (pr.getApplicant().getName() != null) suggestionsSet.add(pr.getApplicant().getName());
                    if (pr.getApplicant().getEmail() != null) suggestionsSet.add(pr.getApplicant().getEmail());
                }
            }
        } catch (Exception ignored) {}
        try {
            String suggestionsJson = objectMapper.writeValueAsString(new java.util.ArrayList<>(suggestionsSet));
            model.addAttribute("searchSuggestionsJson", suggestionsJson);
        } catch (Exception e) {
            model.addAttribute("searchSuggestionsJson", "[]");
        }

        return "academic/admin/requests";
    }

    @GetMapping("/request/{id}")
    public String viewRequest(@PathVariable Long id, Model model) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        List<AcademicDocument> documents = requestService.getDocumentsSorted(id);
        model.addAttribute("request", request);
        model.addAttribute("documents", documents);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("progressSteps", RequestStatus.getProgressSteps());
        model.addAttribute("statusHistory", requestService.getStatusHistory(id));
        model.addAttribute("docLabels", DOC_LABELS);
        model.addAttribute("attachments", requestService.getAttachments(id));
        model.addAttribute("attachmentCount", requestService.countAttachments(id));

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

        return "academic/admin/request_detail";
    }

    @PostMapping("/request/{id}/status")
    public String updateStatus(@PathVariable Long id,
            @RequestParam("status") String status,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "meetingDate", required = false) String meetingDateStr,
            @RequestParam(value = "meetingLocation", required = false) String meetingLocation,
            Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            UserDtls admin = getUser(principal);
            if (admin == null) {
                redirectAttributes.addFlashAttribute("errorDetail",
                        "ไม่พบข้อมูลผู้ใช้ในระบบ (email: " + principal.getName() + ")");
                return "redirect:/admin/academic/request/" + id + "?error=status_update_failed";
            }

            RequestStatus newStatus = RequestStatus.valueOf(status);

            if (newStatus == RequestStatus.MEETING_SCHEDULED && meetingDateStr != null && !meetingDateStr.isEmpty()) {
                LocalDateTime meetingDate = LocalDateTime.parse(meetingDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                requestService.setMeetingDate(id, meetingDate, meetingLocation, admin);
            } else {
                requestService.updateStatus(id, newStatus, admin, note);
            }

            // Log activity
            try {
                adminLogService.log(principal.getName(),
                        admin.getName(),
                        "UPDATE_REQUEST_STATUS",
                        "อัพเดทสถานะคำร้อง #" + id + " เป็น " + newStatus.name()
                                + (note != null ? " (" + note + ")" : ""),
                        getClientIpAddress());
            } catch (Exception logEx) {
                System.err.println("Admin log failed: " + logEx.getMessage());
            }

            return "redirect:/admin/academic/request/" + id + "?success=status_updated";
        } catch (Exception e) {
            System.err.println("Status update failed for request #" + id + ": " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorDetail", e.getClass().getSimpleName() + ": " + e.getMessage());
            return "redirect:/admin/academic/request/" + id + "?error=status_update_failed";
        }
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
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
                    String c1 = doc2Data.getOrDefault("committee_1_name", "").toString();
                    String c2 = doc2Data.getOrDefault("committee_2_name", "").toString();
                    String c3 = doc2Data.getOrDefault("committee_3_name", "").toString();
                    model.addAttribute("defaultCommittee1", c1);
                    model.addAttribute("defaultCommittee2", c2);
                    model.addAttribute("defaultCommittee3", c3);
                } catch (Exception e) {
                    // ignore parse errors
                }
            }
        }

        // สำหรับ doc_3, doc_6, doc_7, doc_8: ดึงข้อมูลจาก doc_0 มา auto-fill
        if (type == 3 || type == 6 || type == 7 || type == 8) {
            List<AcademicDocument> doc0List = requestService.getDocumentsByType(id, 0);
            if (!doc0List.isEmpty()) {
                try {
                    String doc0Json = doc0List.get(0).getJsonData();
                    Map<String, String> doc0Data = objectMapper.readValue(doc0Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                            });
                    model.addAttribute("doc0Data", doc0Data);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        // สำหรับ doc_4: ดึงข้อมูลจาก doc_3 มา auto-fill (applicant info)
        if (type == 4) {
            List<AcademicDocument> doc3List = requestService.getDocumentsByType(id, 3);
            if (!doc3List.isEmpty()) {
                try {
                    String doc3Json = doc3List.get(0).getJsonData();
                    Map<String, String> doc3Data = objectMapper.readValue(doc3Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                            });
                    model.addAttribute("doc3Data", doc3Data);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        // สำหรับ doc_8: ดึงข้อมูลจาก doc_7 มา auto-fill (meeting_date, meeting_no)
        if (type == 8) {
            List<AcademicDocument> doc7List = requestService.getDocumentsByType(id, 7);
            if (!doc7List.isEmpty()) {
                try {
                    String doc7Json = doc7List.get(0).getJsonData();
                    Map<String, Object> doc7Data = objectMapper.readValue(doc7Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
                    model.addAttribute("doc7Data", doc7Data);
                } catch (Exception e) {
                    // ignore
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
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
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
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                            });
                    model.addAttribute("doc1Data", doc1Data);
                } catch (Exception e) {
                    // ignore parse errors
                }
            }
        }

        // สำหรับ doc_0 (admin): ดึงข้อมูลที่ผู้ยื่นกรอกมาแสดง
        if (type == 0) {
            List<AcademicDocument> doc0List = requestService.getDocumentsByType(id, 0);
            if (!doc0List.isEmpty()) {
                try {
                    String doc0Json = doc0List.get(0).getJsonData();
                    Map<String, String> doc0Data = objectMapper.readValue(doc0Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                            });
                    model.addAttribute("doc0Data", doc0Data);
                } catch (Exception e) {
                    // ignore parse errors
                }
            }
        }

        return "academic/admin/doc_fragments/" + type;
    }

    @PostMapping("/request/{id}/document/{type}")
    public String generateDocument(@PathVariable Long id, @PathVariable int type,
            @RequestParam Map<String, String> formData,
            Principal principal) throws IOException {

        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // ดึง action (draft / submit) แล้วเอาออกจาก formData
        String action = formData.getOrDefault("action", "submit");
        boolean sendNotify = "true".equals(formData.getOrDefault("sendNotify", "false"));
        formData.remove("action");
        formData.remove("_csrf");
        formData.remove("sendNotify");

        // ============ Draft: บันทึกแบบร่าง (เก็บ JSON ไม่สร้างไฟล์) ============
        if ("draft".equals(action)) {
            String jsonData = objectMapper.writeValueAsString(formData);
            requestService.saveDraft(request, type, jsonData,
                    DOC_LABELS.getOrDefault(type, "Document " + type), null);
            return "redirect:/admin/academic/request/" + id + "/document/" + type + "?saved=draft";
        }

        // ============ chk checkbox: ติ๊กอันเดียว อันอื่นเป็น " " ============
        String[] chkKeys = { "chk1", "chk2", "chk3" };
        boolean hasAnyChk = false;
        for (String k : chkKeys) {
            if ("✓".equals(formData.get(k))) {
                hasAnyChk = true;
                break;
            }
        }
        if (hasAnyChk) {
            for (String k : chkKeys) {
                if (!"✓".equals(formData.get(k))) {
                    formData.put(k, " ");
                }
            }
        }

        // ============ Document 6: คำนวณคะแนนถ่วงน้ำหนักฝั่ง server ============
        if (type == 6) {
            // ค่าน้ำหนักแต่ละส่วน: ส่วนที่ 1=20, 2=30, 3=30, 4=20
            int[] weights = { 20, 30, 30, 20 };
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
                // ช่วง: 0-1 = score_1, 1.01-2 = score_2, 2.01-3 = score_3, 3.01-4 = score_4,
                // 4.01-5 = score_5
                // placeholder ใน template DOCX: {{score11}}, {{score12}}, ..., {{score45}}
                for (int range = 1; range <= 5; range++) {
                    String key = "score" + sec + range;
                    boolean inRange = false;
                    if (range == 1)
                        inRange = (secScore > 0 && secScore <= 1);
                    else if (range == 2)
                        inRange = (secScore > 1 && secScore <= 2);
                    else if (range == 3)
                        inRange = (secScore > 2 && secScore <= 3);
                    else if (range == 4)
                        inRange = (secScore > 3 && secScore <= 4);
                    else if (range == 5)
                        inRange = (secScore > 4 && secScore <= 5);
                    // ช่วงที่ตรง → ใส่คะแนน, ช่วงอื่น → ว่าง
                    formData.put(key, inRange ? String.valueOf(secScore) : "");
                }

                // สูตร: (คะแนน / 5) × ค่าน้ำหนัก
                double weighted = (secScore / 5.0) * weights[sec - 1];
                formData.put("score" + sec + "x", "%.2f".formatted(weighted));
                grandTotal += weighted;
            }

            // คะแนนรวม
            formData.put("scorex", toThaiDigits("%.2f".formatted(grandTotal)));

            // แปลง score fields ทั้งหมดเป็นเลขไทยสำหรับ DOCX
            for (int sec = 1; sec <= 4; sec++) {
                for (int range = 1; range <= 5; range++) {
                    String key = "score" + sec + range;
                    String val = formData.get(key);
                    if (val != null && !val.isEmpty()) {
                        formData.put(key, toThaiDigits(val));
                    }
                }
                formData.put("score" + sec + "x", toThaiDigits(formData.get("score" + sec + "x")));
            }

            // สรุปผลการประเมิน: ติ้กช่องตามเกณฑ์
            long roundedTotal = Math.round(grandTotal);
            formData.put("ch1", roundedTotal <= 56 ? "☑" : "☐");
            formData.put("ch2", (roundedTotal >= 57 && roundedTotal <= 70) ? "☑" : "☐");
            formData.put("ch3", (roundedTotal >= 71 && roundedTotal <= 85) ? "☑" : "☐");
            formData.put("ch4", (roundedTotal >= 86 && roundedTotal <= 100) ? "☑" : "☐");

            // กำหนด eval_level จากผลคะแนนเพื่อส่งต่อไป doc7/doc8
            String evalLevel = "";
            if (roundedTotal <= 56)
                evalLevel = "ไม่ผ่าน";
            else if (roundedTotal <= 70)
                evalLevel = "ชำนาญ";
            else if (roundedTotal <= 85)
                evalLevel = "ชำนาญพิเศษ";
            else
                evalLevel = "เชี่ยวชาญ";
            formData.put("eval_result_level", evalLevel);

            // auto-fill title/applicant_name/requested_position จาก doc0 ถ้า form
            // ไม่ได้ส่งมา
            if (formData.getOrDefault("title", " ").isBlank()) {
                List<AcademicDocument> doc0List = requestService.getDocumentsByType(id, 0);
                if (!doc0List.isEmpty()) {
                    try {
                        Map<String, String> doc0Data = objectMapper.readValue(
                                doc0List.get(0).getJsonData(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                                });
                        formData.put("title", doc0Data.getOrDefault("title", " "));
                        formData.put("applicant_name", doc0Data.getOrDefault("full_name", " "));
                        String pos = " ";
                        if ("✓".equals(doc0Data.get("chk1")))
                            pos = "ผู้ช่วยศาสตราจารย์";
                        else if ("✓".equals(doc0Data.get("chk2")))
                            pos = "รองศาสตราจารย์";
                        else if ("✓".equals(doc0Data.get("chk3")))
                            pos = "ศาสตราจารย์";
                        formData.put("requested_position", pos);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Document 7: แปลงเลขอาราบิกเป็นเลขไทยสำหรับ DOCX (ทำฝั่ง server เท่านั้น)
        if (type == 7) {
            String[] thaiConvertFields = {"meeting_no"};
            for (String field : thaiConvertFields) {
                String val = formData.get(field);
                if (val != null && !val.isEmpty()) {
                    formData.put(field + "_thai", toThaiDigits(val));
                }
            }
            // แปลงวันที่เป็นเลขไทยสำหรับเอกสาร
            String[] dateFields = {"meeting_date", "sign_date"};
            for (String field : dateFields) {
                String val = formData.get(field);
                if (val != null && !val.isEmpty()) {
                    formData.put(field + "_thai", toThaiDigits(val));
                }
            }
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
                "สร้างเอกสารที่ " + type + " (" + DOC_LABELS.getOrDefault(type, "Document " + type) + ") สำหรับคำร้อง #"
                        + id,
                getClientIpAddress());

        // Auto-update status + notify only if admin chose to
        if (sendNotify) {
            try {
                requestService.autoUpdateStatusByDocument(id, type, admin, jsonData);
            } catch (Exception e) {
                System.err.println(
                        "Auto status update failed for request #" + id + ", doc type " + type + ": " + e.getMessage());
                return "redirect:/admin/academic/request/" + id + "?success=doc_generated&warn=notify_failed";
            }
        }

        return "redirect:/admin/academic/request/" + id + "?success=doc_generated";
    }

    @PostMapping("/request/{id}/document/5/send-suggestion")
    public String sendSuggestionEmail(@PathVariable Long id, Principal principal,
            RedirectAttributes redirectAttributes) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        UserDtls admin = getUser(principal);

        // ดึงข้อเสนอแนะจาก doc_5 JSON
        String suggestionsText = "";
        List<AcademicDocument> doc5List = requestService.getDocumentsByType(id, 5);
        if (!doc5List.isEmpty()) {
            try {
                Map<String, String> doc5Data = objectMapper.readValue(doc5List.get(0).getJsonData(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                        });
                suggestionsText = doc5Data.getOrDefault("suggestions_text", "");
            } catch (Exception e) {
                // ignore
            }
        }

        // เปลี่ยนสถานะเป็น แจ้งผล - แก้ไข
        requestService.updateStatus(id, RequestStatus.COMPLETED_REVISE, admin,
                "ส่งข้อเสนอแนะเพื่อแก้ไขเอกสาร", true);

        // ส่งอีเมลข้อเสนอแนะ (async - non-blocking)
        requestService.sendSuggestionEmail(request, suggestionsText);
        redirectAttributes.addFlashAttribute("successMsg",
                "ส่งข้อเสนอแนะแล้ว กำลังส่งอีเมลถึงผู้ยื่น: " + request.getApplicant().getEmail());

        // Log activity
        adminLogService.log(principal.getName(),
                admin != null ? admin.getName() : principal.getName(),
                "SEND_SUGGESTION",
                "ส่งข้อเสนอแนะถึงผู้ยื่นคำร้อง #" + id + " เพื่อแก้ไขเอกสาร",
                getClientIpAddress());

        return "redirect:/admin/academic/request/" + id + "/document/5?success";
    }

    @GetMapping("/request/{id}/download/{docId}")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable Long id,
            @PathVariable Long docId,
            @RequestParam(value = "format", defaultValue = "docx") String format) throws IOException {
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
                (!originalFilename.toLowerCase().endsWith(".pdf")
                        && !originalFilename.toLowerCase().endsWith(".docx"))) {
            return "redirect:/admin/academic/request/" + id + "?error=invalid_file_type";
        }

        String uploadDir = "uploads/academic/" + id + "/attachments/";
        Files.createDirectories(Path.of(uploadDir));

        // สร้างชื่อไฟล์ไม่ซ้ำ (sanitize เพื่อป้องกัน Path Traversal)
        String storedFilename = FileUtils.sanitizeFilename(originalFilename);
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

        String safeFilename = java.net.URLEncoder.encode(attachment.getOriginalFilename(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + safeFilename)
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

    /** แปลงตัวเลข Arabic เป็นเลขไทย เช่น "3.50" → "๓.๕๐" */
    private static String toThaiDigits(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.replace("0", "๐").replace("1", "๑").replace("2", "๒")
                .replace("3", "๓").replace("4", "๔").replace("5", "๕")
                .replace("6", "๖").replace("7", "๗").replace("8", "๘")
                .replace("9", "๙");
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
