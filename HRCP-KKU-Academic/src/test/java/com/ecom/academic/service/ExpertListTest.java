package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureModule;

/**
 * รายชื่อผู้ทรงคุณวุฒิ เอกสารที่ 8 เฟส 2: ต้องมีอย่างน้อย 1 คน และเอกสารไม่พิมพ์แถวว่างที่เหลือจาก 10 แถวในแบบฟอร์ม
 */
@DisplayName("เอกสารที่ 8 เฟส 2: รายชื่อผู้ทรงคุณวุฒิ")
class ExpertListTest {

    private static final DocumentGenerationService GEN = new DocumentGenerationService();

    @Test
    @DisplayName("ไม่มีผู้ทรงคุณวุฒิสักคน ส่งไปลงนามไม่ได้ — มีคนเดียวก็ส่งได้")
    void atLeastOneExpertIsRequired() {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.POSITION, 8,
                "{\"name1\":\"\",\"name_admin\":\"เจ้าหน้าที่ ทดสอบ\",\"count_meet\":\"1/2569\"}"))
                .as("ชื่อเจ้าหน้าที่ผู้บันทึก (name_admin) ไม่ใช่ผู้ทรงคุณวุฒิ")
                .containsExactly("รายชื่อผู้ทรงคุณวุฒิ: ต้องระบุอย่างน้อย 1 คน");
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.POSITION, 8, "{\"name1\":\"ศ.ดร.หนึ่ง ทดสอบ\"}"))
                .isEmpty();
    }

    @Test
    @DisplayName("ประธานกรรมการต้องเป็นหนึ่งในผู้ทรงคุณวุฒิที่ระบุ — ยังไม่เลือกก็ได้")
    void chairMustBeOneOfTheExperts() {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.POSITION, 8,
                "{\"name1\":\"ศ.ดร.หนึ่ง ทดสอบ\",\"committee_name\":\"สมชาย ทดสอบ\"}"))
                .containsExactly("ชื่อประธานกรรมการต้องเป็นหนึ่งในรายชื่อผู้ทรงคุณวุฒิ — กรุณาเลือกใหม่จากรายชื่อ");
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.POSITION, 8,
                "{\"name1\":\"ศ.ดร.หนึ่ง ทดสอบ\",\"name2\":\"ศ.ดร.สอง ทดสอบ\",\"committee_name\":\"ศ.ดร.สอง ทดสอบ\"}"))
                .isEmpty();
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.POSITION, 8,
                "{\"name1\":\"ศ.ดร.หนึ่ง ทดสอบ\",\"committee_name\":\"\"}")).isEmpty();
    }

    @Test
    @DisplayName("ตารางมีแถวเท่าจำนวนคนที่กรอก — แถวว่างกลางตารางเลื่อนคนถัดไปขึ้นมาแทน แถวแรกเก็บไว้เสมอ")
    void keepsOneRowPerExpert() {
        StringBuilder xml = new StringBuilder("<w:tbl>");
        for (int i = 1; i <= DocumentGenerationService.EXPERT_ROWS; i++) {
            xml.append("<w:tr w:rsidR=\"1\"><w:tc><w:p><w:r><w:t>").append(i).append(".</w:t></w:r></w:p></w:tc>")
                    .append("<w:tc><w:p><w:r><w:t>{{name").append(i).append("}}</w:t></w:r></w:p></w:tc>")
                    .append("<w:tc><w:p><w:r><w:t>{{Information").append(i).append("}}</w:t></w:r></w:p></w:tc></w:tr>");
        }
        xml.append("</w:tbl>");

        Map<String, String> twoWithGap = new HashMap<>(Map.of("name1", "หนึ่ง", "name3", "สาม", "Information3", "มข."));
        String pruned = GEN.pruneTrailingExpertRows(xml.toString(), twoWithGap);
        assertThat(rows(pruned)).as("กรอก 2 คน ได้ 2 แถว แม้แถวที่ 2 ในฟอร์มจะว่าง").isEqualTo(2);
        assertThat(twoWithGap).as("คนในแถว 3 เลื่อนขึ้นมาเป็นลำดับ 2 ลำดับจึงไม่กระโดด")
                .containsEntry("name2", "สาม").containsEntry("Information2", "มข.").containsEntry("name3", "");

        assertThat(rows(GEN.pruneTrailingExpertRows(xml.toString(), new HashMap<>())))
                .as("ไม่มีใครเลยก็ยังเหลือแถวแรก").isEqualTo(1);
    }

    @Test
    @DisplayName("แบบฟอร์มจริง: ผู้ทรงคุณวุฒิ 2 คน ตารางสั้นกว่า 10 คนอยู่ 8 แถว")
    void realTemplateDropsTheUnusedRows() throws Exception {
        Map<String, String> two = new HashMap<>();
        Map<String, String> ten = new HashMap<>();
        for (int i = 1; i <= DocumentGenerationService.EXPERT_ROWS; i++) {
            ten.put("name" + i, "ผู้ทรงคุณวุฒิ " + i);
            if (i <= 2) {
                two.put("name" + i, "ผู้ทรงคุณวุฒิ " + i);
            }
        }
        assertThat(rows(documentXml(two))).isEqualTo(rows(documentXml(ten)) - 8);
    }

    private static String documentXml(Map<String, String> data) throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        byte[] docx = GEN.generateSignedP2Docx(8, json, List.of(), DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null;) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("ไม่พบ word/document.xml");
    }

    private static int rows(String xml) {
        return (int) Pattern.compile("<w:tr[ >]").matcher(xml).results().count();
    }
}
