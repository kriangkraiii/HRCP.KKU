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
 *
 * <p>ผู้เรียกบางรายมีไฟล์ที่เป็น PDF อยู่แล้ว (เอกสารที่ลงนามแล้ว ซึ่ง
 * {@code SignedDocumentRenderer.renderPdf} เรนเดอร์ไว้) จึงต้องตรวจก่อนเสมอว่า
 * ไบต์ที่ได้รับเป็น PDF หรือยัง ถ้าเผลอส่งเข้า LibreOffice ซ้ำ มันจะ import PDF
 * กลับเข้ามาแล้ว export ใหม่ ซึ่งทำลายการ map ตัวอักษรไทยจนอ่านไม่ออก
 */
final class PreviewResponseFactory {

    private static final Logger log = LoggerFactory.getLogger(PreviewResponseFactory.class);

    static final String FORMAT_HEADER = "X-Preview-Format";

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private PreviewResponseFactory() {
    }

    /**
     * @param documentBytes ไฟล์เอกสาร จะเป็น DOCX หรือ PDF ที่เรนเดอร์มาแล้วก็ได้
     */
    static ResponseEntity<byte[]> build(DocumentGenerationService service, byte[] documentBytes,
            String format, String baseFilename) {

        String safeBase = (baseFilename == null || baseFilename.isBlank()) ? "document" : baseFilename;
        String encodedFilename = URLEncoder.encode(safeBase, StandardCharsets.UTF_8).replace("+", "%20");
        String asciiFilename = safeBase.replaceAll("[^a-zA-Z0-9._-]", "_");

        // เอกสารที่ลงนามแล้วมาถึงที่นี่ในรูป PDF อยู่แล้ว ส่งต่อได้เลย
        if (isPdf(documentBytes)) {
            return pdfResponse(documentBytes, asciiFilename, encodedFilename);
        }

        if ("pdf".equalsIgnoreCase(format) && service.isPdfConversionAvailable()) {
            try {
                return pdfResponse(service.convertDocxToPdfCached(documentBytes),
                        asciiFilename, encodedFilename);
            } catch (Exception e) {
                // แปลงไม่สำเร็จ → ตกไปใช้ DOCX ด้านล่าง
                log.warn("PDF preview conversion failed, falling back to DOCX: {}", e.getMessage());
            }
        }

        String formatHeader = "pdf".equalsIgnoreCase(format) ? "docx-fallback" : "docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + asciiFilename + ".docx\"; filename*=UTF-8''" + encodedFilename + ".docx")
                .header(FORMAT_HEADER, formatHeader)
                .contentType(MediaType.parseMediaType(DOCX_MIME))
                .contentLength(documentBytes.length)
                .body(documentBytes);
    }

    /** ไบต์ชุดนี้เป็นไฟล์ PDF อยู่แล้วหรือไม่ */
    private static boolean isPdf(byte[] bytes) {
        return bytes != null && bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] pdfBytes, String asciiFilename,
            String encodedFilename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + asciiFilename + ".pdf\"; filename*=UTF-8''" + encodedFilename + ".pdf")
                .header(FORMAT_HEADER, "pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}
