package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ช่องติ๊กแบบ "(✓)" ไม่มีกรอบของเฟส 2 — ข้อที่ไม่ได้เลือกเป็นช่องว่างที่กว้างเท่า ✓
 * เอกสารที่ 2 เก็บค่าเป็น ☑/☐ ส่วนเอกสารที่ 4 เก็บเป็น ✔/ว่าง พิมพ์เป็น ✓ ตัวเดียวกับเอกสารอื่น
 */
@DisplayName("เฟส 2: ช่องติ๊กไม่มีกรอบ")
class P2PlainTickRenderingTest {

    private static final String BLANK_TICK =
            "<w:spacing w:val=\"\\d+\"/></w:rPr><w:t xml:space=\"preserve\"> </w:t>";

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
    @DisplayName("ข้อที่เลือกขึ้น ✓ ข้อที่ไม่เลือกเป็นช่องว่างกว้างเท่า ✓ — ไม่มี ☑/☐")
    void ticksWithoutBoxes() throws IOException {
        String xml = documentXml(service.generateP2PreviewDocx(2,
                "{\"wish_to_know_committee_names\":\"☑\",\"not_wish_to_know_committee_names\":\"☐\"}"));

        assertThat(xml).doesNotContain("☑").doesNotContain("☐").doesNotContain("{{");
        assertThat(xml).containsOnlyOnce(">✓</w:t>");
        assertThat(xml).containsPattern("OpenSymbol\"/>(?:(?!</w:rPr>).)*<w:spacing w:val=\"\\d+\"/></w:rPr><w:t xml:space=\"preserve\"> </w:t>");
    }

    @Test
    @DisplayName("ยังไม่เลือกข้อใดเลย ทั้งสองข้อเป็นช่องว่างกว้างเท่า ✓")
    void bothBlankWhenNothingChosen() throws IOException {
        String xml = documentXml(service.generateP2PreviewDocx(2, "{\"not_wish_to_know_committee_names\":\"☐\"}"));

        assertThat(xml).doesNotContain("☑").doesNotContain("☐").doesNotContain(">✓</w:t>");
        assertThat(xml.split("<w:spacing w:val=\"\\d+\"/></w:rPr><w:t xml:space=\"preserve\"> </w:t>", -1))
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("เอกสาร 4: ✔ พิมพ์เป็น ✓ แบบเดียวกับเอกสารอื่น ข้อที่ไม่ติ๊กเป็นช่องว่างกว้างเท่า ✓")
    void doc4UsesTheSameTick() throws IOException {
        String xml = documentXml(service.generateP2PreviewDocx(4, """
                {"master_thesis":"✔","doctoral_thesis":"✔",
                 "academic_paper_not_part_edu":"✔","academic_paper_is_part_edu":"",
                 "research_not_part_edu":"✔","research_is_part_edu":""}"""));

        assertThat(xml).doesNotContain("✔").doesNotContain("☑").doesNotContain("☐");
        assertThat(xml.split(">✓</w:t>", -1)).hasSize(5);
        assertThat(xml.split(BLANK_TICK, -1)).hasSize(3);
    }
}
