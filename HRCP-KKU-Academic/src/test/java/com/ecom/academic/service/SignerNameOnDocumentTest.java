package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * พิสูจน์ว่าเทมเพลตพิมพ์ชื่อผู้ลงนามออกมาจริง
 *
 * <p>{@link SignerNameResolverTest} ตรวจว่าเติมค่าลง JSON ถูกช่อง เทสต์นี้ตรวจ
 * ปลายทาง: เอกสารที่ 1 มี {@code ({{hr_staff_name}})} อยู่ใต้เส้นลงนามของนัก
 * ทรัพยากรบุคคล ถ้าไม่มีค่าจะได้วงเล็บว่าง "()" ซึ่งเป็นอาการที่ต้องแก้
 */
@DisplayName("ชื่อผู้ลงนามบนไฟล์เอกสารจริง")
class SignerNameOnDocumentTest {

    private final DocumentGenerationService service = new DocumentGenerationService();

    private String textOf(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    @Test
    @DisplayName("เอกสารที่ 1: ไม่มีชื่อนักทรัพยากรบุคคล จะได้วงเล็บว่าง")
    void withoutHrName_leavesEmptyParentheses() throws Exception {
        String json = "{\"title\":\"นาย\",\"applicant_name\":\"สมชาย ใจดีวิชาการ\"}";

        String text = textOf(service.generatePreviewDocx(1, json));

        assertThat(text).contains("(นายสมชาย ใจดีวิชาการ)");
        assertThat(text).contains("()");
    }

    @Test
    @DisplayName("เอกสารที่ 1: เติมชื่อแล้วต้องขึ้นในวงเล็บใต้เส้นลงนามของนักทรัพยากรบุคคล")
    void withHrName_printsItUnderTheSignatureLine() throws Exception {
        String json = "{\"title\":\"นาย\",\"applicant_name\":\"สมชาย ใจดีวิชาการ\","
                + "\"hr_staff_name\":\"นางสาวสมหญิง รักงาน\"}";

        String text = textOf(service.generatePreviewDocx(1, json));

        assertThat(text).contains("(นางสาวสมหญิง รักงาน)");
        assertThat(text).doesNotContain("{{hr_staff_name}}");
        assertThat(text).doesNotContain("()");
    }
}
