package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ✓ ในเอกสารพิมพ์ด้วย OpenSymbol เสมอ ให้ตรงกับติ๊กที่วาดตอนเซ็น */
class TickFontTest {

    private static final String SARABUN = "<w:rFonts w:ascii=\"TH Sarabun New\" w:hAnsi=\"TH Sarabun New\"/><w:sz w:val=\"28\"/>";

    @Test
    @DisplayName("แยก ✓ ออกเป็น run ของตัวเองที่ใช้ OpenSymbol ข้อความรอบข้างคงฟอนต์และขนาดเดิม")
    void theTickGetsItsOwnRun() {
        String xml = "<w:r><w:rPr>" + SARABUN + "</w:rPr><w:t xml:space=\"preserve\">( ✓ ) เพื่อโปรดพิจารณา</w:t></w:r>";

        String out = DocumentGenerationService.pinTickFont(xml);

        assertThat(out).isEqualTo(
                "<w:r><w:rPr>" + SARABUN + "</w:rPr><w:t xml:space=\"preserve\">( </w:t></w:r>"
                        + "<w:r><w:rPr><w:rFonts w:ascii=\"OpenSymbol\" w:hAnsi=\"OpenSymbol\" w:eastAsia=\"OpenSymbol\""
                        + " w:cs=\"OpenSymbol\"/><w:sz w:val=\"28\"/></w:rPr><w:t xml:space=\"preserve\">✓</w:t></w:r>"
                        + "<w:r><w:rPr>" + SARABUN + "</w:rPr><w:t xml:space=\"preserve\"> ) เพื่อโปรดพิจารณา</w:t></w:r>");
    }

    @Test
    @DisplayName("run ที่ไม่มี rPr ก็ได้ฟอนต์ OpenSymbol")
    void aRunWithoutPropertiesStillGetsTheFont() {
        String out = DocumentGenerationService.pinTickFont("<w:r><w:t>✓</w:t></w:r>");

        assertThat(out).contains("<w:rFonts w:ascii=\"OpenSymbol\"").contains(">✓</w:t>");
    }

    @Test
    @DisplayName("run ที่ไม่มี ✓ ไม่ถูกแตะ")
    void otherRunsAreUntouched() {
        String xml = "<w:r><w:rPr>" + SARABUN + "</w:rPr><w:t>เห็นชอบ</w:t></w:r>";

        assertThat(DocumentGenerationService.pinTickFont(xml)).isEqualTo(xml);
    }
}
