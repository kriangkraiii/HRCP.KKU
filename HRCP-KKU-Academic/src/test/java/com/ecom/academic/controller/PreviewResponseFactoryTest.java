package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.ecom.academic.service.DocumentGenerationService;

/**
 * A document that is already a PDF must be served, not converted again.
 *
 * <p>Signed documents arrive here as PDF, because {@code renderPdf} has already
 * run LibreOffice over them. Passing those bytes back to LibreOffice makes it
 * import the PDF and export a fresh one, and that round trip destroys the Thai
 * character-to-glyph mapping — the text comes out as a jumble of Latin
 * punctuation. It renders as damage rather than an error, on documents people
 * are about to sign, which is why it went unnoticed for so long.
 */
@DisplayName("เอกสารที่เป็น PDF อยู่แล้วต้องไม่ถูกแปลงซ้ำ")
class PreviewResponseFactoryTest {

    private final DocumentGenerationService service = mock(DocumentGenerationService.class);

    private static byte[] pdf() {
        return "%PDF-1.7\nสมมติว่าเป็นเอกสารที่ลงนามแล้ว".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] docx() {
        // DOCX เป็น zip จึงขึ้นต้นด้วย PK
        return new byte[] { 'P', 'K', 3, 4, 0, 0, 0, 0 };
    }

    @Test
    @DisplayName("ได้ PDF มาแล้ว ต้องส่งไบต์เดิมออกไปตรงๆ ไม่เรียก LibreOffice")
    void anAlreadyRenderedPdfIsPassedThrough() throws Exception {
        byte[] signed = pdf();

        ResponseEntity<byte[]> response =
                PreviewResponseFactory.build(service, signed, "pdf", "เอกสารที่_0");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(signed);
        assertThat(response.getHeaders().getFirst(PreviewResponseFactory.FORMAT_HEADER)).isEqualTo("pdf");
        verify(service, never()).convertDocxToPdfCached(any());
    }

    @Test
    @DisplayName("ได้ DOCX มา ต้องแปลงเป็น PDF ตามเดิม")
    void aDocxIsStillConverted() throws Exception {
        byte[] converted = pdf();
        when(service.isPdfConversionAvailable()).thenReturn(true);
        when(service.convertDocxToPdfCached(any())).thenReturn(converted);

        ResponseEntity<byte[]> response =
                PreviewResponseFactory.build(service, docx(), "pdf", "เอกสารที่_0");

        assertThat(response.getBody()).isEqualTo(converted);
        verify(service).convertDocxToPdfCached(any());
    }

    @Test
    @DisplayName("ขอเป็น Word ก็ยังได้ DOCX ตามเดิม")
    void askingForWordStillReturnsTheDocx() throws Exception {
        byte[] source = docx();

        ResponseEntity<byte[]> response =
                PreviewResponseFactory.build(service, source, "docx", "เอกสารที่_0");

        assertThat(response.getBody()).isEqualTo(source);
        assertThat(response.getHeaders().getFirst(PreviewResponseFactory.FORMAT_HEADER)).isEqualTo("docx");
        verify(service, never()).convertDocxToPdfCached(any());
    }

    @Test
    @DisplayName("กันชนชั้นสุดท้าย: ตัวแปลงเองต้องไม่แปลง PDF ซ้ำ ไม่ว่าใครเรียก")
    void theConverterItselfRefusesToConvertAPdfAgain() throws Exception {
        // จุดกลางนี้ครอบทุกทางเรียกในโปรเจค รวมถึง endpoint ที่อาจเพิ่มทีหลัง
        com.ecom.academic.service.DocumentGenerationService real =
                new com.ecom.academic.service.DocumentGenerationService();
        byte[] alreadyPdf = pdf();

        assertThat(real.convertDocxToPdfCached(alreadyPdf))
                .as("ต้องคืนไฟล์เดิม ไม่ส่งเข้า LibreOffice อีกรอบ")
                .isSameAs(alreadyPdf);
    }
}
