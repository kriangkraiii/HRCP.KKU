package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ช่องผลงานของตำแหน่งที่ผู้ขอไม่ได้เสนอ ต้องพิมพ์ออกมาเป็นจุดไข่ปลาและช่องติ๊กว่าง
 * เหมือนแบบฟอร์มเปล่า ไม่ใช่บรรทัดว่าง
 */
class BlankFormFillerTest {

    private static final String ASST_ONLY_JSON = """
            {
              "title": "นาย",
              "applicant_name": "ทดสอบ ระบบ",
              "asst_research_working_no_1": "๑",
              "asst_research_working_1": "งานวิจัยทดสอบ",
              "asst_used_research_1": "not_used",
              "assoc_research_working_no_1": "๑",
              "assoc_research_working_1": "",
              "prof_research_working_1": ""
            }
            """;

    @Test
    @DisplayName("ช่องที่ไม่ได้กรอกคืนจุดไข่ปลาและช่องติ๊กว่าง")
    void unusedSlotsKeepDotLeadersAndCheckboxes() throws IOException {
        DocumentGenerationService service = new DocumentGenerationService();
        byte[] docx = service.generateSignedP2Docx(1, ASST_ONLY_JSON, List.of());
        String xml = documentXml(docx);

        assertFalse(xml.contains("{{"), "ต้องไม่เหลือ placeholder ในเอกสาร");
        assertTrue(xml.contains("........."), "ช่องผลงานที่ไม่ได้กรอกต้องมีจุดไข่ปลา");
        assertTrue(xml.contains("MS Gothic"), "ช่องติ๊กที่ไม่ได้เลือกต้องยังพิมพ์เป็นกล่อง");
        assertTrue(xml.contains("งานวิจัยทดสอบ"), "ค่าที่กรอกไว้ต้องยังอยู่");
    }

    private String documentXml(byte[] docx) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("ไม่พบ word/document.xml");
    }
}
