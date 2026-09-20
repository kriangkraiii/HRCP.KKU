package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentGenerationService.VerificationStamp;

/**
 * เทมเพลตบางฉบับพิมพ์เป็นเลขไทยทั้งหน้า เช่น doc_7 ("๑." "๕๗-๗๐") และ p2doc_1
 * ("๑.๑" "๒.๓") ค่าที่กรอกเข้าไปจึงต้องเป็นเลขไทยด้วย ไม่งั้นหน้าเดียวกันมีเลขสองแบบปนกัน
 *
 * <p>ตัวตัดสินคือ <em>เทมเพลต</em> ไม่ใช่รายชื่อเอกสารที่ฮาร์ดโค้ดไว้ — แก้ไฟล์ .docx
 * แล้วพฤติกรรมตามไปเอง ไม่มีรายการให้ลืมอัปเดต
 */
@DisplayName("เลขไทยตามเทมเพลต")
class ThaiNumeralRenderingTest {

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

    @Nested
    @DisplayName("เทมเพลตที่พิมพ์เป็นเลขไทย")
    class ThaiTemplates {

        @Test
        @DisplayName("เอกสารที่ 7: คะแนนที่กรอกต้องออกมาเป็นเลขไทย")
        void doc7_convertsScores() throws IOException {
            String json = "{\"scorex\":\"85.50\",\"applicant_name\":\"สมชาย ใจดีวิชาการ\"}";

            String xml = documentXml(service.generatePreviewDocx(7, json));

            assertThat(xml).contains("๘๕.๕๐");
            assertThat(xml).doesNotContain("85.50");
        }

        @Test
        @DisplayName("เฟส 2 เอกสารที่ 1: วันที่ลงนามต้องเป็นเลขไทย (เดิมพิมพ์อารบิกปนอยู่)")
        void p2doc1_convertsSignDate() throws IOException {
            String json = "{\"sign_date\":\"21 กันยายน 2569\",\"age\":\"45\"}";

            String xml = documentXml(service.generateP2PreviewDocx(1, json));

            assertThat(xml).contains("๒๑ กันยายน ๒๕๖๙");
            assertThat(xml).contains("๔๕");
        }
    }

    @Nested
    @DisplayName("เทมเพลตที่พิมพ์เป็นเลขอารบิก")
    class ArabicTemplates {

        @Test
        @DisplayName("เอกสารที่ 3: ค่าที่กรอกต้องอยู่เป็นอารบิกตามเดิม")
        void doc3_leavesDigitsAlone() throws IOException {
            String json = "{\"dean_comment\":\"ประชุมครั้งที่ 1/2569\"}";

            String xml = documentXml(service.generatePreviewDocx(3, json));

            assertThat(xml).contains("1/2569");
            assertThat(xml).doesNotContain("๑/๒๕๖๙");
        }
    }

    @Nested
    @DisplayName("รหัสตรวจสอบเอกสาร")
    class VerificationFooter {

        /**
         * รหัสท้ายหน้ามีไว้ให้คนพิมพ์ตามเพื่อเปิดหน้าตรวจสอบ ถ้าแปลงเป็นเลขไทย
         * จะพิมพ์ตามไม่ได้ — ต้องเป็นอารบิกเสมอ แม้อยู่บนเอกสารเลขไทย
         */
        @Test
        @DisplayName("เอกสารเลขไทยที่ลงนามครบ รหัสตรวจสอบยังต้องเป็นอารบิก")
        void staysArabicOnAThaiNumeralDocument() throws IOException {
            String json = "{\"scorex\":\"85.50\"}";
            // บล็อกท้ายหน้าจะถูกแนบก็ต่อเมื่อมีรูปให้ฝังอย่างน้อยหนึ่งรูป (QR หรือลายเซ็น)
            VerificationStamp stamp = new VerificationStamp(
                    "HRCP-2569-000123", "ตรวจสอบความถูกต้องของเอกสารได้ที่", new byte[] { 1, 2, 3 });

            String xml = documentXml(
                    service.generateSignedDocx(7, json, List.of(), stamp));

            assertThat(xml).contains("๘๕.๕๐");
            assertThat(xml).contains("HRCP-2569-000123");
        }
    }
}
