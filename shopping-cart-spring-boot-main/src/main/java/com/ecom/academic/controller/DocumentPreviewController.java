package com.ecom.academic.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.academic.service.DocumentGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST Controller สำหรับ Preview เอกสารเป็น DOCX แบบ real-time
 * ไม่บันทึกไฟล์ — สร้าง DOCX ใน memory แล้วส่งกลับ
 */
@RestController
@RequestMapping("/api/academic/preview")
public class DocumentPreviewController {

    @Autowired
    private DocumentGenerationService documentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * POST /api/academic/preview/{docType}
     * รับ form data เป็น JSON, สร้าง docx จาก template, ส่ง DOCX กลับ
     */
    @PostMapping("/{docType}")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable int docType,
            @RequestBody Map<String, String> formData) {

        try {
            // ============ Document 6: คำนวณคะแนนถ่วงน้ำหนักฝั่ง server ============
            if (docType == 6) {
                int[] weights = {20, 30, 30, 20};
                double grandTotal = 0;

                for (int sec = 1; sec <= 4; sec++) {
                    double sum = 0;
                    for (int item = 1; item <= 5; item++) {
                        String key = "re" + sec + item;
                        String val = formData.getOrDefault(key, "0");
                        try {
                            sum += Double.parseDouble(val);
                        } catch (NumberFormatException e) {
                            sum += 0;
                        }
                    }
                    double weighted = (sum / 25.0) * weights[sec - 1];
                    formData.put("score" + sec + "x", "%.2f".formatted(weighted));
                    grandTotal += weighted;
                }

                formData.put("scorex", "%.2f".formatted(grandTotal));

                long roundedTotal = Math.round(grandTotal);
                formData.put("ch1", roundedTotal < 56 ? "☑" : "☐");
                formData.put("ch2", (roundedTotal >= 57 && roundedTotal <= 70) ? "☑" : "☐");
                formData.put("ch3", (roundedTotal >= 71 && roundedTotal <= 85) ? "☑" : "☐");
                formData.put("ch4", roundedTotal >= 86 ? "☑" : "☐");
            }

            String jsonData = objectMapper.writeValueAsString(formData);
            byte[] docxBytes = documentService.generatePreviewDocx(docType, jsonData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview_doc_" + docType + ".docx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .contentLength(docxBytes.length)
                    .body(docxBytes);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
