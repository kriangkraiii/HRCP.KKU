package com.ecom.academic.controller;

import java.io.IOException;
import java.util.Map;

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

    private final DocumentGenerationService documentService;

    public DocumentPreviewController(DocumentGenerationService documentService) {
        this.documentService = documentService;
    }

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
            if (docType == 4) {
                String committeeIdx = formData.getOrDefault("committee_index", "1");
                String committeeName = formData.getOrDefault("committee_name_" + committeeIdx, "");
                String committeePosition = formData.getOrDefault("committee_position_" + committeeIdx, "");
                docxBytes = documentService.generatePreviewDocxForCopy(docType, jsonData, committeeName,
                        committeePosition);
            } else {
                docxBytes = documentService.generatePreviewDocx(docType, jsonData);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview_doc_" + docType + ".docx\"")
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .contentLength(docxBytes.length)
                    .body(docxBytes);

        } catch (IOException e) {
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
