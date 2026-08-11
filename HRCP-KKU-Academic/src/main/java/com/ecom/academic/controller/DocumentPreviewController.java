package com.ecom.academic.controller;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST Controller สำหรับ Preview เอกสาร
 * ไม่บันทึกไฟล์ — สร้าง DOCX ใน memory แล้วส่งกลับ
 * ?format=pdf จะแปลงเป็น PDF ก่อน (ดู {@link PreviewResponseFactory})
 */
@RestController
@RequestMapping("/api/academic/preview")
public class DocumentPreviewController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentPreviewController.class);
    private static final Set<Integer> APPLICANT_ALLOWED_DOCS = Set.of(0, 1, 8);

    private final DocumentGenerationService documentService;

    private final UserRepository userRepository;

    public DocumentPreviewController(DocumentGenerationService documentService,
            UserRepository userRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final Map<Integer, String> DOC_TITLES = Map.of(
            0, "บันทึกข้อความ_ขอรับการประเมินผลการสอน",
            1, "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน",
            2, "การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ",
            3, "คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน",
            4, "บันทึกข้อความ_ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ",
            5, "ข้อเสนอแนะจากคณะอนุกรรมการ",
            6, "แบบฟอร์มประเมินการสอน_ตามประกาศ_มข_1607-66",
            7, "ส่วนที่_3_แบบประเมินผลการสอน",
            8, "บันทึกข้อความ_แจ้งผลการประเมินผลการสอน"
    );

    public static String getDocTitle(int docType) {
        return DOC_TITLES.getOrDefault(docType, "เอกสาร");
    }

    /**
     * POST /api/academic/preview/{docType}
     * รับ form data เป็น JSON, สร้าง docx จาก template, ส่ง DOCX กลับ
     *
     * Applicants may only render their own document types; the rest belong to
     * the admin side of the workflow.
     */
    @PostMapping("/{docType}")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable int docType,
            @RequestParam(value = "format", defaultValue = "docx") String format,
            @RequestBody Map<String, String> formData,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserDtls user = userRepository.findByEmail(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"ROLE_ADMIN".equals(user.getRole()) && !APPLICANT_ALLOWED_DOCS.contains(docType)) {
            logger.warn("Applicant {} attempted to preview admin document type {}", user.getEmail(), docType);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // ============ Document 6: คำนวณคะแนนถ่วงน้ำหนักฝั่ง server ============
            if (docType == 6) {
                int[] weights = { 20, 30, 30, 20 };
                double grandTotal = 0;

                for (int sec = 1; sec <= 4; sec++) {
                    String secScoreVal = formData.getOrDefault("sec_score_" + sec, "0");
                    double secScore = 0;
                    try {
                        secScore = Double.parseDouble(secScoreVal);
                    } catch (NumberFormatException e) {
                        secScore = 0;
                    }

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
                        formData.put(key, inRange ? toThaiDigits(String.valueOf(secScore)) : "");
                    }

                    double weighted = (secScore / 5.0) * weights[sec - 1];
                    formData.put("score" + sec + "x", toThaiDigits("%.2f".formatted(weighted)));
                    grandTotal += weighted;
                }

                formData.put("scorex", toThaiDigits("%.2f".formatted(grandTotal)));

                long roundedTotal = Math.round(grandTotal);
                formData.put("ch1", roundedTotal <= 56 ? "☑" : "☐");
                formData.put("ch2", (roundedTotal >= 57 && roundedTotal <= 70) ? "☑" : "☐");
                formData.put("ch3", (roundedTotal >= 71 && roundedTotal <= 85) ? "☑" : "☐");
                formData.put("ch4", (roundedTotal >= 86 && roundedTotal <= 100) ? "☑" : "☐");
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

            String jsonData = objectMapper.writeValueAsString(formData);

            byte[] docxBytes;
            String baseFilename = "เอกสารที่_" + docType + "_" + getDocTitle(docType);
            if (docType == 4) {
                String committeeIdx = formData.getOrDefault("committee_index", "1");
                String committeeName = formData.getOrDefault("committee_name_" + committeeIdx, "");
                String committeePosition = formData.getOrDefault("committee_position_" + committeeIdx, "");
                docxBytes = documentService.generatePreviewDocxForCopy(docType, jsonData, committeeName,
                        committeePosition);
                if (!committeeName.isBlank()) {
                    baseFilename += "_" + committeeName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
                }
            } else {
                docxBytes = documentService.generatePreviewDocx(docType, jsonData);
            }

            return PreviewResponseFactory.build(documentService, docxBytes, format, baseFilename);

        } catch (IOException e) {
            logger.error("Failed to generate preview for docType {}: {}", docType, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static String toThaiDigits(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.replace("0", "๐").replace("1", "๑").replace("2", "๒")
                .replace("3", "๓").replace("4", "๔").replace("5", "๕")
                .replace("6", "๖").replace("7", "๗").replace("8", "๘")
                .replace("9", "๙");
    }
}
