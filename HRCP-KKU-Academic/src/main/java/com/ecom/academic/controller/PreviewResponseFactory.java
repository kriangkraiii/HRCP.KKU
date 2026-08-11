package com.ecom.academic.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.ecom.academic.service.DocumentGenerationService;

/**
 * สร้าง HTTP response ของ preview ให้เหมือนกันทั้ง Phase 1 และ Phase 2
 *
 * preview หลักเป็น PDF (แปลงด้วย LibreOffice) เพราะเป็นทางเดียวที่เลย์เอาต์
 * ตรงกับ Word จริง — ถ้าเครื่องไม่มี LibreOffice หรือแปลงไม่สำเร็จ จะส่ง DOCX
 * กลับไปให้ frontend render ด้วย docx-preview แทน (ตัวอย่างแบบประมาณ)
 * โดยบอกผ่านหัว X-Preview-Format ไม่ตอบ error เพื่อให้จบใน round trip เดียว
 */
final class PreviewResponseFactory {

    private static final Logger log = LoggerFactory.getLogger(PreviewResponseFactory.class);

    static final String FORMAT_HEADER = "X-Preview-Format";

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private PreviewResponseFactory() {
    }

    static ResponseEntity<byte[]> build(DocumentGenerationService service, byte[] docxBytes,
            String format, String baseFilename) {

        String safeBase = (baseFilename == null || baseFilename.isBlank()) ? "document" : baseFilename;
        String encodedFilename = URLEncoder.encode(safeBase, StandardCharsets.UTF_8).replace("+", "%20");
        String asciiFilename = safeBase.replaceAll("[^a-zA-Z0-9._-]", "_");

        if ("pdf".equalsIgnoreCase(format) && service.isPdfConversionAvailable()) {
            try {
                byte[] pdfBytes = service.convertDocxToPdfCached(docxBytes);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + asciiFilename + ".pdf\"; filename*=UTF-8''" + encodedFilename + ".pdf")
                        .header(FORMAT_HEADER, "pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .contentLength(pdfBytes.length)
                        .body(pdfBytes);
            } catch (IOException e) {
                // แปลงไม่สำเร็จ → ตกไปใช้ DOCX ด้านล่าง
                log.warn("PDF preview conversion failed, falling back to DOCX: {}", e.getMessage());
            }
        }

        String formatHeader = "pdf".equalsIgnoreCase(format) ? "docx-fallback" : "docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + asciiFilename + ".docx\"; filename*=UTF-8''" + encodedFilename + ".docx")
                .header(FORMAT_HEADER, formatHeader)
                .contentType(MediaType.parseMediaType(DOCX_MIME))
                .contentLength(docxBytes.length)
                .body(docxBytes);
    }
}
