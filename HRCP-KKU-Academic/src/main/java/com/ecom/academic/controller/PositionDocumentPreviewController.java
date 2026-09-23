package com.ecom.academic.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.TeachingEvaluationPartResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST Controller สำหรับ Preview เอกสาร Phase 2 (ขอตำแหน่ง)
 * ?format=pdf จะแปลงเป็น PDF ก่อน (ดู {@link PreviewResponseFactory})
 */
@RestController
@RequestMapping("/api/position/preview")
public class PositionDocumentPreviewController {

    private static final Logger logger = LoggerFactory.getLogger(PositionDocumentPreviewController.class);

    private final DocumentGenerationService documentService;
    private final PositionRequestService positionService;
    private final TeachingEvaluationPartResolver teachingEvaluationPart;

    public PositionDocumentPreviewController(DocumentGenerationService documentService,
            PositionRequestService positionService,
            TeachingEvaluationPartResolver teachingEvaluationPart) {
        this.documentService = documentService;
        this.positionService = positionService;
        this.teachingEvaluationPart = teachingEvaluationPart;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final Map<Integer, String> POSITION_DOC_TITLES = Map.of(
            1, "แบบ_ก.พ.ว._มข._03_ส่วนที่_1-5",
            2, "หนังสือแจ้งความประสงค์เรื่องการรับรู้ข้อมูล",
            3, "แบบรับรองจริยธรรมและจรรยาบรรณ",
            4, "บันทึกรับรองผลงานทางวิชาการ_วิทยานิพนธ์",
            6, "บันทึกข้อความจริยธรรมการวิจัย_Exemption",
            7, "แบบฟอร์มตรวจสอบคุณสมบัติ_Checklist",
            8, "แบบสรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ",
            9, "ลักษณะการมีส่วนร่วมในผลงาน"
    );

    private static boolean isApplicant(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority()));
    }

    private static boolean mayView(PositionRequest request, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return admin || (request.getApplicant() != null
                && authentication.getName().equalsIgnoreCase(request.getApplicant().getEmail()));
    }

    public static String getPositionDocTitle(int docType) {
        return POSITION_DOC_TITLES.getOrDefault(docType, "เอกสาร");
    }

    @PostMapping("/{docType}")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable int docType,
            @RequestParam(value = "format", defaultValue = "docx") String format,
            @RequestParam(value = "requestId", required = false) Long requestId,
            @RequestBody Map<String, String> formData,
            Authentication authentication) {
        // ผู้ยื่นได้เฉพาะ PDF — ปุ่ม Word ถูกซ่อนแล้ว แต่ยิง URL ตรงก็ต้องไม่ได้ไฟล์ Word
        if (isApplicant(authentication)) {
            format = "pdf";
        }
        try {
            String jsonData = objectMapper.writeValueAsString(formData);
            // ส่วนที่ ๓ ของแบบ ก.พ.ว. มข. ๐๓ มาจากผลประเมินการสอนของคำร้องนั้น ไม่ได้อยู่ในฟอร์ม
            // เติมให้เฉพาะเจ้าของคำร้องหรือแอดมิน — ไม่งั้นใครก็ยิงเลขคำร้องมาอ่านผลประเมินคนอื่นได้
            if (requestId != null) {
                String formJson = jsonData;
                jsonData = positionService.findById(requestId)
                        .filter(request -> mayView(request, authentication))
                        .map(request -> teachingEvaluationPart.fillInto(request, docType, formJson))
                        .orElse(formJson);
            }
            byte[] docxBytes = documentService.generateP2PreviewDocx(docType, jsonData);

            String baseFilename = "เอกสารตำแหน่งที่_" + docType + "_" + getPositionDocTitle(docType);
            return PreviewResponseFactory.build(documentService, docxBytes, format, baseFilename);
        } catch (IOException e) {
            logger.error("Failed to generate position preview for docType {}: {}", docType, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
