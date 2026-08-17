package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * เอกสารที่ ๗ วาง placeholder ของช่องติ๊กไว้ในบล็อกที่ตั้งใจให้ซ้ำได้
 * ({{?research_working_list}}) จึงตั้งชื่อแบบไม่มีเลขต่อท้าย เช่น {{is_published}}
 * ขณะที่ฟอร์มส่งชื่อรายการแรกมาเป็น is_published_1 เสมอ
 *
 * ถ้าไม่มีการเชื่อมชื่อทั้งสองแบบ placeholder จะถูกลบทิ้งตอน cleanup ทำให้
 * ช่องติ๊กหายไปทั้งกล่อง — ไม่ขึ้นทั้ง ☑ (ติ๊กแล้ว) และ ☐ (ยังไม่ติ๊ก)
 */
@DisplayName("เอกสาร ๗: ช่องติ๊กต้องแสดงกล่องเสมอ")
class Doc7CheckboxRenderingTest {

    /** ติ๊ก is_published กับ diss_journal, ที่เหลือส่ง ☐ มาแบบที่ฟอร์มทำจริง */
    private static final String DOC7_JSON = """
            {"research_working_title_1":"ผลงานวิจัยทดสอบ",
             "is_published_1":"☑",
             "pending_letter_1":"☐",
             "content_certified_1":"☐",
             "diss_journal_1":"☑",
             "diss_proceeding_1":"☐",
             "diss_monograph_1":"☐",
             "diss_book_1":"☐",
             "diss_report_1":"☐"}
            """;

    private final DocumentGenerationService service = new DocumentGenerationService();

    private String documentXml(byte[] docx) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zis.transferTo(out);
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("ไม่พบ word/document.xml ใน DOCX");
    }

    @Test
    @DisplayName("ช่องที่ยังไม่ติ๊กต้องขึ้นกล่องว่าง ☐ ไม่ใช่หายไปเฉย ๆ")
    void rendersEmptyBoxForUncheckedFields() throws IOException {
        byte[] docx = service.generateP2PreviewDocx(7, DOC7_JSON);
        assertEquals('P', (char) docx[0], "ต้องเป็นไฟล์ DOCX");

        String xml = documentXml(docx);

        assertFalse(xml.contains("{{is_published}}"),
                "placeholder ต้องถูกแทนค่าแล้ว");
        assertFalse(xml.contains("{{diss_book}}"),
                "placeholder ต้องถูกแทนค่าแล้ว");

        assertTrue(xml.contains("☑"),
                "ช่องที่ติ๊กแล้วต้องแสดง ☑");
        assertTrue(xml.contains("☐"),
                "ช่องที่ยังไม่ติ๊กต้องแสดงกล่องว่าง ☐ — ไม่ใช่ถูกลบทิ้ง");
    }
}
