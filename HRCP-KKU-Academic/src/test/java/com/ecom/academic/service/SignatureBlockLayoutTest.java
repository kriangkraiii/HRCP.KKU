package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * เอกสารที่ 9: บล็อกลงนามของผู้ประพันธ์บรรณกิจที่เพิ่ม — เว้นบรรทัดเหมือนบล็อกในแบบฟอร์ม และไม่แยกหน้า
 */
@DisplayName("บล็อกลงนามที่โคลนเพิ่ม — เว้นบรรทัดและไม่แยกหน้า")
class SignatureBlockLayoutTest {

    private static final String PPR = "<w:pPr><w:spacing w:after=\"0\"/><w:jc w:val=\"center\"/></w:pPr>";
    private static final String BLOCK = "<w:p>" + PPR + "<w:r><w:t>ลงชื่อ......</w:t></w:r></w:p>"
            + "<w:p w:rsidR=\"1\">" + PPR + "<w:r><w:t>({{corres_name}})</w:t></w:r></w:p>"
            + "<w:p>" + PPR + "<w:r><w:t>ผู้ประพันธ์บรรณกิจ (Corresponding author)</w:t></w:r></w:p>";

    @Test
    @DisplayName("ลงชื่อและชื่อติดกับบรรทัดถัดไป ย่อหน้าสุดท้าย (ตำแหน่ง) ไม่ต้อง")
    void everyLineButTheLastKeepsWithNext() {
        String kept = DocumentGenerationService.keepSignatureBlockTogether(BLOCK);

        assertThat(kept.split("<w:keepNext/>", -1)).hasSize(3);
        assertThat(kept).contains("<w:pPr><w:keepNext/><w:spacing")
                .endsWith("<w:p>" + PPR + "<w:r><w:t>ผู้ประพันธ์บรรณกิจ (Corresponding author)</w:t></w:r></w:p>");
        assertThat(DocumentGenerationService.keepSignatureBlockTogether(kept)).as("ทำซ้ำไม่เพิ่มซ้อน").isEqualTo(kept);
    }

    @Test
    @DisplayName("ย่อหน้าเว้นบรรทัดใช้รูปแบบเดียวกับบล็อก ไม่มีข้อความ และไม่ติดกับบรรทัดถัดไป")
    void spacerCopiesTheBlockFormatting() {
        String spacer = DocumentGenerationService.emptyParagraphLike(
                DocumentGenerationService.keepSignatureBlockTogether(BLOCK));

        assertThat(spacer).isEqualTo("<w:p>" + PPR + "</w:p>");
    }
}
