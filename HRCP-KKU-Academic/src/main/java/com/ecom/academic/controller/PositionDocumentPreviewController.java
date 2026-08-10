package com.ecom.academic.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST Controller สำหรับ Preview เอกสาร Phase 2 (ขอตำแหน่ง) เป็น DOCX แบบ
 * real-time
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

    @PostMapping("/{docType}")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable int docType,
            @RequestBody Map<String, String> formData,
            Authentication authentication) {

        // Enforce authorization: Non-admin users can only preview applicant-facing position docs
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin && !PositionRequestService.APPLICANT_DOCS.contains(docType)) {
            logger.warn("Unauthorized position document preview attempt: docType {} by user {}",
                    docType, authentication != null ? authentication.getName() : "anonymous");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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
            logger.error("Failed to generate position preview for docType {}: {}", docType, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
