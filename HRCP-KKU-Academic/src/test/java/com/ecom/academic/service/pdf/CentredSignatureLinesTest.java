package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;

/**
 * ชื่อและตำแหน่งใต้ลายเซ็นที่ระบบวาดลง PDF ทีหลัง ต้องหน้าตาเหมือนข้อความที่ Word วางเอง — กึ่งกลางบรรทัด
 * ขนาดเท่าแบบฟอร์ม และชื่ออยู่ในวงเล็บ ไม่ว่าใครเป็นคนเติม (บันทึกข้อความจริยธรรมการวิจัย เฟส 2: เจ้าหน้าที่เลือกรองคณบดี
 * แล้วชื่อขึ้นตัวเล็ก ชิดซ้าย ไม่มีวงเล็บ ต่างจากชื่อผู้ขอด้านบน)
 */
@DisplayName("ชื่อและตำแหน่งใต้ลายเซ็นที่วาดลง PDF — กึ่งกลาง ขนาดเท่าแบบฟอร์ม มีวงเล็บ")
class CentredSignatureLinesTest {

    private static final DocumentGenerationService GEN = new DocumentGenerationService();
    private static final String JSON = "{\"applicant_title\":\"อาจารย์\",\"applicant_name\":\"ผู้ยื่นคำขอ ทดสอบ\","
            + "\"target_position\":\"ผู้ช่วยศาสตราจารย์\"}";
    private static final String LONG_NAME = "รองศาสตราจารย์ ดร.รองคณบดี ทดสอบ";
    private static final String POSITION = "รองคณบดีฝ่ายวิชาการและวิจัย";

    @Test
    @DisplayName("แยกได้ว่าช่องไหนเป็นทั้งย่อหน้ากึ่งกลาง และช่องไหนตามหลังข้อความ")
    void detectsFieldsAloneOnACentredLine() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>(@@OVL_dean_name@@)</w:t></w:r></w:p>"
                + "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>ตำแหน่ง @@OVL_dean_position_line@@</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>@@OVL_memo_no@@</w:t></w:r></w:p>"
                + "</w:body></w:document>";
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(bout)) {
            zout.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zout.write(xml.getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();
        }
        java.util.Set<String> centred = new java.util.HashSet<>();
        java.util.Set<String> bracketed = new java.util.HashSet<>();

        BasePdfBuilder.reserve(bout.toByteArray(),
                Map.of("dean_name", 45, "dean_position_line", 48, "memo_no", 32),
                Map.of(), new java.util.LinkedHashMap<>(), new java.util.ArrayList<>(), new java.util.HashSet<>(),
                centred, bracketed);

        assertThat(centred).as("ชื่อในวงเล็บเป็นทั้งย่อหน้ากึ่งกลาง").contains("dean_name")
                .as("ตำแหน่งที่ตามหลัง \"ตำแหน่ง\" ต้องวาดชิดคำนั้น ไม่ใช่กึ่งกลางช่อง").doesNotContain("dean_position_line")
                .as("ย่อหน้าไม่ได้จัดกึ่งกลาง").doesNotContain("memo_no");
        assertThat(bracketed).containsExactly("dean_name");
    }

    @Test
    @DisplayName("เจ้าหน้าที่เติมชื่อและตำแหน่งคณบดี (บันทึกข้อความจริยธรรมการวิจัย): ขนาดเท่าแบบฟอร์ม กึ่งกลาง มีวงเล็บ")
    void officerFilledDeanLinesLookLikeTheTemplate() throws Exception {
        assumeTrue(GEN.isPdfConversionAvailable(), "LibreOffice is not installed");
        byte[] base = new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                StringBuilder j = new StringBuilder(JSON.substring(0, JSON.length() - 1));
                overrides.forEach((k, v) -> j.append(",\"").append(k).append("\":\"").append(v).append('"'));
                return GEN.generateSignedP2Docx(6, j.append('}').toString(), pictures.stream()
                        .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height())).toList(),
                        DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
            }

            @Override
            public byte[] toPdf(byte[] docx) throws IOException {
                return GEN.convertDocxToPdf(docx);
            }
        }, List.of(new BasePdfBuilder.TextSpec("dean_signature_name", IncrementalSigningService.reserveFor("dean_signature_name")),
                new BasePdfBuilder.TextSpec("position_title", IncrementalSigningService.reserveFor("position_title"))),
                List.of()).pdf();

        byte[] filled = new PdfIncrementService(ThaiText.sarabun())
                .fill(base, Map.of("dean_signature_name", LONG_NAME, "position_title", POSITION));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "incremental"));
        java.nio.file.Files.write(java.nio.file.Path.of("target", "incremental", "p2doc6-dean-lines.pdf"), filled);

        assertThat(IncrementalSigningService.values(filled))
                .as("ค่าที่เก็บไว้เป็นชื่อล้วน — เทียบกับค่าที่บันทึกในระบบแล้วไม่เห็นว่าเปลี่ยน")
                .containsEntry("dean_signature_name", LONG_NAME).containsEntry("position_title", POSITION);
        for (String field : List.of("dean_signature_name", "position_title")) {
            Drawn d = drawn(filled, field);
            assertThat(d.size).as("%s ต้องไม่ถูกย่อ", field).isEqualTo(d.templateSize);
            assertThat(Math.abs(d.leftGap - d.rightGap)).as("%s ต้องอยู่กึ่งกลางช่อง", field).isLessThan(1.0f);
        }
        assertThat(drawn(filled, "dean_signature_name").text).isEqualTo("(" + LONG_NAME + ")");
        assertThat(drawn(filled, "position_title").text).isEqualTo(POSITION);
    }

    private record Drawn(String text, float size, float templateSize, float leftGap, float rightGap) {
    }

    /** What a field's appearance draws: its text (/ActualText), size, and the space either side of it. */
    private static Drawn drawn(byte[] pdf, String field) throws IOException {
        try (var doc = Loader.loadPDF(pdf)) {
            var f = doc.getDocumentCatalog().getAcroForm().getField(field);
            var w = f.getWidgets().get(0);
            String ops;
            try (var in = w.getAppearance().getNormalAppearance().getAppearanceStream().getCOSObject().createInputStream()) {
                ops = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
            Matcher actual = Pattern.compile("/ActualText <FEFF([0-9A-F]+)>").matcher(ops);
            Matcher size = Pattern.compile("([0-9.]+) Tf").matcher(ops);
            Matcher da = Pattern.compile("([0-9.]+)\\s+Tf").matcher(f.getCOSObject().getString(COSName.DA));
            assertThat(actual.find() && size.find() && da.find()).as("%s ไม่มีข้อความ", field).isTrue();
            String text = new String(HexFormat.of().parseHex(actual.group(1)), StandardCharsets.UTF_16BE);
            float s = Float.parseFloat(size.group(1));
            float width = ThaiText.sarabun().width(text, s);
            Matcher x = Pattern.compile("1 0 0 1 ([0-9.\\-]+) ").matcher(ops);
            float left = x.find() ? Float.parseFloat(x.group(1)) : 0;
            return new Drawn(text, s, Float.parseFloat(da.group(1)), left, w.getRectangle().getWidth() - left - width);
        }
    }
}
