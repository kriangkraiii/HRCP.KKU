package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LibreOffice ที่เปิดค้างไว้ต้องให้ PDF เหมือนการเปิด soffice ใหม่ทุกครั้ง — ข้อความ จำนวนหน้า
 * และฟอนต์ที่ฝังตรงกัน (ฟอนต์ไทยต้องเป็น TH Sarabun New ไม่ใช่ฟอนต์แทน) แค่เร็วกว่า
 */
@DisplayName("LibreOffice pool — ผลเหมือน CLI แต่เร็วกว่า")
class LibreOfficeProcessPoolTest {

    private final LibreOfficeProcessPool pool = new LibreOfficeProcessPool(true, 1, false);

    @AfterEach
    void stopPool() {
        pool.stop();
    }

    @Test
    @DisplayName("ข้อความ หน้า และฟอนต์ตรงกับ CLI; รอบที่สองขึ้นไปเร็วกว่า CLI")
    void pooledOutputMatchesTheCommandLine() throws IOException {
        DocumentGenerationService cli = new DocumentGenerationService();
        Assumptions.assumeTrue(cli.isPdfConversionAvailable(), "LibreOffice is not installed");
        DocumentGenerationService pooled = new DocumentGenerationService();
        pooled.setOfficePool(pool);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("memo_no", "อว 660301.26.3/123");
        data.put("date", "1 ตุลาคม 2569");
        data.put("applicant_name", "สมชาย ทดสอบยื่น");
        data.put("course_name", "โครงสร้างข้อมูล");
        byte[] docx = cli.generatePreviewDocx(1, new ObjectMapper().writeValueAsString(data));

        long t0 = System.nanoTime();
        byte[] fromCli = cli.convertDocxToPdf(docx);
        long cliMs = (System.nanoTime() - t0) / 1_000_000;

        pooled.convertDocxToPdf(docx); // เปิดโปรแกรมครั้งแรก
        assertThat(pool.isRunning()).as("pool started instead of falling back to the CLI").isTrue();
        long t1 = System.nanoTime();
        byte[] fromPool = pooled.convertDocxToPdf(docx);
        long poolMs = (System.nanoTime() - t1) / 1_000_000;

        Summary a = summarize(fromCli);
        Summary b = summarize(fromPool);
        assertThat(b.pages).isEqualTo(a.pages);
        assertThat(b.text).isEqualTo(a.text).contains("สมชาย ทดสอบยื่น");
        assertThat(b.fonts).isEqualTo(a.fonts);
        assertThat(b.fonts).anyMatch(f -> f.contains("THSarabunNew"));
        assertThat(poolMs).as("pooled %d ms vs CLI %d ms", poolMs, cliMs).isLessThan(cliMs);
    }

    private record Summary(int pages, String text, TreeSet<String> fonts) {
    }

    private static Summary summarize(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            TreeSet<String> fonts = new TreeSet<>();
            for (PDPage page : doc.getPages()) {
                for (COSName name : page.getResources().getFontNames()) {
                    String font = page.getResources().getFont(name).getName();
                    // ตัด prefix ของ subset (ABCDEF+) ที่สุ่มต่างกันได้ในแต่ละรอบ
                    fonts.add(font.contains("+") ? font.substring(font.indexOf('+') + 1) : font);
                }
            }
            return new Summary(doc.getNumberOfPages(), new PDFTextStripper().getText(doc), fonts);
        }
    }
}
