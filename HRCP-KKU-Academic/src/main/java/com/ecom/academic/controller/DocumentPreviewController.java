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
    private static final Set<Integer> APPLICANT_ALLOWED_DOCS = Set.of(1, 2, 9);

    private final DocumentGenerationService documentService;

    private final UserRepository userRepository;

    public DocumentPreviewController(DocumentGenerationService documentService,
            UserRepository userRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final Map<Integer, String> DOC_TITLES = Map.of(
            1, "บันทึกข้อความ_ขอรับการประเมินผลการสอน",
            2, "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน",
            3, "การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ",
            4, "คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน",
            5, "บันทึกข้อความ_ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ",
            6, "ข้อเสนอแนะจากคณะอนุกรรมการ",
            7, "แบบฟอร์มประเมินการสอน_ตามประกาศ_มข_1607-66",
            8, "ส่วนที่_3_แบบประเมินผลการสอน",
            9, "บันทึกข้อความ_แจ้งผลการประเมินผลการสอน"
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
        // ผู้ยื่นได้เฉพาะ PDF — ปุ่ม Word ถูกซ่อนแล้ว แต่ยิง URL ตรงก็ต้องไม่ได้ไฟล์ Word
        if ("ROLE_USER".equals(user.getRole())) {
            format = "pdf";
        }

        try {
            // ============ Document 7: คำนวณคะแนนถ่วงน้ำหนักฝั่ง server ============
            // ใช้ตัวคำนวณเดียวกับตอนบันทึก — เดิมคำนวณซ้ำแบบปัดเศษก่อน preview จึงสรุประดับไม่ตรงกับเอกสารจริง
            if (docType == 7) {
                com.ecom.academic.service.Doc7Scoring.derive(formData);
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
            if (docType == 5) {
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

}
