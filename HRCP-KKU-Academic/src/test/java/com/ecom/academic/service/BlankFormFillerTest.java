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

    private static final String NEVER_HELD_JSON = """
            {
              "title": "นาย",
              "applicant_name": "ทดสอบ ระบบ",
              "target_position": "ผู้ช่วยศาสตราจารย์",
              "current_position": "อาจารย์",
              "lecturer_appointment_date": "วันที่ ๑ มิถุนายน พ.ศ. ๒๕๖๐"
            }
            """;

    private static final String HELD_BOTH_JSON = """
            {
              "title": "นาย",
              "applicant_name": "ทดสอบ ระบบ",
              "target_position": "ศาสตราจารย์",
              "assistant_method": "ปกติ",
              "assistant_department": "วิทยาการคอมพิวเตอร์",
              "assistant_appointment_date": "วันที่ ๑ สิงหาคม พ.ศ. ๒๕๖๓",
              "associate_method": "ปกติ",
              "associate_department": "วิทยาการคอมพิวเตอร์",
              "associate_appointment_date": "วันที่ ๙ กันยายน พ.ศ. ๒๕๖๙"
            }
            """;

    @Test
    @DisplayName("ผู้ขอที่ยังไม่เคยเป็น ผศ./รศ. — ข้อ ๒.๓/๒.๔ เป็นจุดไข่ปลา")
    void unheldPositionsPrintDotLeaders() throws IOException {
        String xml = documentXml(new DocumentGenerationService()
                .generateSignedP2Docx(1, NEVER_HELD_JSON, List.of()));

        assertFalse(xml.contains("{{"), "ต้องไม่เหลือ placeholder ในเอกสาร");
        assertTrue(xml.contains("วันที่ ๑ มิถุนายน พ.ศ. ๒๕๖๐"), "วันแต่งตั้งอาจารย์ที่กรอกไว้ต้องยังอยู่");
        // ข้อความในเอกสารถูก Word หั่นข้าม run จึงต้องเทียบกับข้อความล้วน
        String text = plainText(xml);
        assertTrue(text.contains("(โดยวิธี............)"), "วิธีแต่งตั้งที่ไม่ได้กรอกต้องเป็นจุดไข่ปลา");
        assertTrue(text.contains("ในสาขาวิชา............"), "สาขาวิชาที่ไม่ได้กรอกต้องเป็นจุดไข่ปลา");
    }

    @Test
    @DisplayName("ข้อ ๒.๔ พิมพ์วันแต่งตั้ง รศ. ไม่ใช่วันของ ผศ.")
    void associateAppointmentDateIsItsOwn() throws IOException {
        String xml = documentXml(new DocumentGenerationService()
                .generateSignedP2Docx(1, HELD_BOTH_JSON, List.of()));

        assertTrue(xml.contains("วันที่ ๑ สิงหาคม พ.ศ. ๒๕๖๓"), "ข้อ ๒.๓ ต้องพิมพ์วันของ ผศ.");
        assertTrue(xml.contains("วันที่ ๙ กันยายน พ.ศ. ๒๕๖๙"), "ข้อ ๒.๔ ต้องพิมพ์วันของ รศ.");
    }

    /** ข้อความล้วนของเอกสาร — ตัดแท็ก XML ที่คั่นกลางคำออก */
    private String plainText(String xml) {
        return xml.replaceAll("<[^>]+>", "");
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
