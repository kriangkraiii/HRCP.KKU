package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ข้อความที่คัดลอกมาจาก Word หรือ PDF มักมีอักขระควบคุมติดมา — Shift+Enter ใน Word คัดลอกออกมาเป็น U+000B
 * ขึ้นหน้าใหม่เป็น U+000C — อักขระเหล่านี้ใช้ใน XML 1.0 ไม่ได้ เดิมถูกใส่ลง document.xml ตรง ๆ
 * ไฟล์ Word ที่ได้จึงเปิดไม่ได้ และแปลงเป็น PDF ไม่ได้ (ตัวอย่างเอกสารขึ้นข้อผิดพลาด)
 */
@DisplayName("อักขระควบคุมในค่าที่กรอก ไม่ทำให้ไฟล์ Word เสีย")
class DocxControlCharacterTest {

    private final DocumentGenerationService service = new DocumentGenerationService();

    @Test
    @DisplayName("ค่าที่มี U+000B / U+000C / U+0001 — ไฟล์ยังเปิดได้ และข้อความยังอยู่ครบ")
    void controlCharactersDoNotCorruptTheDocument() throws IOException {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("applicant_name", "สมชาย ทดสอบยื่น");
        d.put("course_name", "โครงสร้าง\u000Bข้อมูล\u000C\u0001และอัลกอริทึม");
        d.put("course_code", "CP123456");

        byte[] docx = service.generatePreviewDocx(1, new ObjectMapper().writeValueAsString(d));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            assertThat(extractor.getText()).contains("โครงสร้าง").contains("และอัลกอริทึม");
        }
    }
}
