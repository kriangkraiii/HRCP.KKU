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
 * REST Controller สำหรับ Preview เอกสาร Phase 2 (ขอตำแหน่ง) เป็น DOCX แบบ
 * real-time
 */
@RestController
@RequestMapping("/api/position/preview")
public class PositionDocumentPreviewController {

    @Autowired
    private DocumentGenerationService documentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/{docType}")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable int docType,
            @RequestBody Map<String, String> formData) {
        try {
            String jsonData = objectMapper.writeValueAsString(formData);
            byte[] docxBytes = documentService.generateP2PreviewDocx(docType, jsonData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"preview_p2doc_" + docType + ".docx\"")
                    .contentType(MediaType
                            .parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .contentLength(docxBytes.length)
                    .body(docxBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
