package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ตรวจเส้นทาง preview: template → DOCX → PDF (LibreOffice)
 *
 * ข้ามอัตโนมัติเมื่อเครื่องไม่มี LibreOffice — ระบบจะ fallback ไป docx-preview
 * ซึ่งเป็นพฤติกรรมที่ยอมรับได้ ไม่ควรทำให้ build พัง
 */
@DisplayName("Preview: DOCX → PDF conversion")
class DocumentPdfPreviewTest {

    private static final String DOC6_JSON = """
            {"committee_1_name":"ศ.ดร.ทดสอบ หนึ่ง",
             "committee_2_name":"รศ.ดร.ทดสอบ สอง",
             "committee_3_name":"ผศ.ดร.ทดสอบ สาม",
             "score1x":"๒๐.๐๐","score2x":"๓๐.๐๐","score3x":"๓๐.๐๐","score4x":"๒๐.๐๐",
             "scorex":"๑๐๐.๐๐","ch1":"☐","ch2":"☐","ch3":"☐","ch4":"☑"}
            """;

    private final DocumentGenerationService service = new DocumentGenerationService();

    private byte[] doc6Docx() throws IOException {
        byte[] docx = service.generatePreviewDocx(6, DOC6_JSON);
        assertTrue(docx.length > 0, "DOCX ต้องไม่ว่าง");
        assertEquals('P', (char) docx[0], "DOCX ต้องขึ้นต้นด้วย ZIP magic");
        assertEquals('K', (char) docx[1], "DOCX ต้องขึ้นต้นด้วย ZIP magic");
        return docx;
    }

    @Test
    @DisplayName("แปลงเอกสาร ๖ เป็น PDF ได้ และมีข้อความอ่านออก")
    void convertsDoc6ToPdf() throws IOException {
        Assumptions.assumeTrue(service.isPdfConversionAvailable(),
                "ไม่พบ LibreOffice — ข้ามการทดสอบ PDF");

        byte[] pdf = service.convertDocxToPdf(doc6Docx());

        assertTrue(pdf.length > 1000, "PDF ต้องมีเนื้อหา (ได้ " + pdf.length + " bytes)");
        String header = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", header, "ต้องเป็นไฟล์ PDF จริง");

        // เขียนไฟล์ไว้ให้เปิดดูเทียบกับ Word ด้วยตา
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "preview_doc_6.pdf");
        Files.write(out, pdf);
        System.out.println("PDF written to: " + out);
    }

    @Test
    @DisplayName("cache คืนผลเดิมโดยไม่เรียก LibreOffice ซ้ำ")
    void cachesConvertedPdf() throws IOException {
        Assumptions.assumeTrue(service.isPdfConversionAvailable(),
                "ไม่พบ LibreOffice — ข้ามการทดสอบ PDF");

        byte[] docx = doc6Docx();

        long firstStart = System.nanoTime();
        byte[] first = service.convertDocxToPdfCached(docx);
        long firstMs = (System.nanoTime() - firstStart) / 1_000_000;

        long secondStart = System.nanoTime();
        byte[] second = service.convertDocxToPdfCached(docx);
        long secondMs = (System.nanoTime() - secondStart) / 1_000_000;

        assertArrayEquals(first, second, "ผลจาก cache ต้องเหมือนเดิม");
        assertTrue(secondMs < firstMs, "ครั้งที่สองต้องมาจาก cache (" + firstMs + "ms → " + secondMs + "ms)");
    }

    @Test
    @DisplayName("แปลงพร้อมกันหลาย request ได้ครบ ไม่ชนกัน")
    void handlesConcurrentConversions() throws Exception {
        Assumptions.assumeTrue(service.isPdfConversionAvailable(),
                "ไม่พบ LibreOffice — ข้ามการทดสอบ PDF");

        byte[] docx = doc6Docx();
        int requests = 5;

        // ใช้ convertDocxToPdf ตรง ๆ (ไม่ผ่าน cache) เพื่อบังคับให้ soffice
        // ทำงานพร้อมกันจริง — ตรวจว่า UserInstallation แยกกันได้ผล
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Callable<byte[]>> jobs = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                jobs.add(() -> service.convertDocxToPdf(docx));
            }
            List<Future<byte[]>> results = pool.invokeAll(jobs);
            for (Future<byte[]> f : results) {
                byte[] pdf = f.get();
                assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                        "ทุก request ต้องได้ PDF");
            }
        } finally {
            pool.shutdown();
        }
    }
}
