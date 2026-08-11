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

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
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

    public PositionDocumentPreviewController(DocumentGenerationService documentService) {
        this.documentService = documentService;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final Map<Integer, String> POSITION_DOC_TITLES = Map.of(
            0, "บันทึกข้อความ_ขอรับการประเมินผลการสอน",
            1, "แบบ_ก.พ.ว._มข._03_ประวัติและผลงาน",
            2, "หนังสือแจ้งความประสงค์เรื่องการรับรู้ข้อมูล",
            3, "แบบรับรองจริยธรรมและจรรยาบรรณ",
            4, "บันทึกรับรองผลงานทางวิชาการ_วิทยานิพนธ์",
            5, "แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา",
            6, "บันทึกข้อความจริยธรรมการวิจัย_Exemption",
            7, "แบบฟอร์มตรวจสอบคุณสมบัติ_Checklist",
            8, "แบบสรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ",
            9, "ลักษณะการมีส่วนร่วมในผลงาน"
    );

    public static String getPositionDocTitle(int docType) {
        return POSITION_DOC_TITLES.getOrDefault(docType, "เอกสาร");
    }

    @PostMapping("/{docType}")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable int docType,
            @RequestParam(value = "format", defaultValue = "docx") String format,
            @RequestBody Map<String, String> formData) {
        try {
            String jsonData = objectMapper.writeValueAsString(formData);
            byte[] docxBytes = documentService.generateP2PreviewDocx(docType, jsonData);

            String baseFilename = "เอกสารตำแหน่งที่_" + docType + "_" + getPositionDocTitle(docType);
            return PreviewResponseFactory.build(documentService, docxBytes, format, baseFilename);
        } catch (IOException e) {
            logger.error("Failed to generate position preview for docType {}: {}", docType, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
