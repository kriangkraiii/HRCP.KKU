package com.ecom.visual;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.support.RequiredTools;

/**
 * เอกสารที่แปลงเป็น PDF แล้วต้องชิดขอบขวาเหมือนตอนเปิดใน Word
 *
 * <p><b>ที่มา:</b> เทมเพลตทุกฉบับตั้งจัดชิดขอบเป็น {@code thaiDistribute} ซึ่งเป็นค่าของ
 * Word สำหรับภาษาที่ไม่มีช่องว่างระหว่างคำ Word เรนเดอร์ถูกต้อง แต่ LibreOffice
 * <em>ไม่รองรับค่านี้และตกกลับไปชิดซ้ายเงียบ ๆ</em> ไม่มี error ให้เห็นเลย
 *
 * <p>อาการที่ผู้ใช้เจอคือ "ไฟล์ .docx ปกติดี แต่หน้า preview ขอบขวาไม่ตรงกัน" ซึ่งเข้าใจ
 * ยากมากถ้าไม่รู้ว่าสองทางนี้เรนเดอร์ด้วยคนละโปรแกรม ข้อความไทยตัดบรรทัดทีละอักขระอยู่แล้ว
 * จึงดู "เกือบ" ชิดขอบ ต้องมีบรรทัดที่มีคำอังกฤษ (ซึ่งตัดกลางคำไม่ได้) ถึงจะเห็นชัด
 *
 * <p><b>ทำไมต้องวัดจาก PDF จริง:</b> เทสต์ที่อ่าน XML จะเห็น {@code thaiDistribute}
 * อยู่ครบถ้วนถูกต้องแล้วผ่านฉลุย ทั้งที่ผลลัพธ์ที่ตาเห็นยังผิดอยู่ — บทเรียนเดียวกับ
 * {@link DocumentRenderTest}
 */
@DisplayName("ขอบขวาของเอกสารที่แปลงเป็น PDF")
class RightMarginRendersFlushTest {

    private final DocumentGenerationService service = new DocumentGenerationService();

    /** ห่างจากขอบขวาสุดได้ไม่เกินเท่านี้ ถึงจะนับว่า "ชิดขอบ" (หน่วย point) */
    private static final float FLUSH_TOLERANCE = 5f;

    private static byte[] template(String name) throws IOException {
        return Files.readAllBytes(Path.of("src/main/resources/templates/docx", name));
    }

    /**
     * ข้อมูลตัวอย่างของเอกสารที่ 1
     *
     * <p>ชื่อวิชาเป็นภาษาอังกฤษโดยตั้งใจ — บรรทัดที่มีคำอังกฤษคือบรรทัดที่เผยอาการ
     * เพราะตัดกลางคำไม่ได้ ถ้าใส่แต่ภาษาไทยล้วน เทสต์จะผ่านทั้งที่ยังพังอยู่
     */
    private static Map<String, String> sampleDoc1() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", "ผู้ช่วยศาสตราจารย์");
        data.put("applicant_name", "สมชาย ใจดีวิชาการ");
        data.put("employee_type", "ข้าราชการ");
        data.put("current_position", "ผู้ช่วยศาสตราจารย์");
        data.put("chk1", "  ");
        data.put("chk2", "✓");
        data.put("course_code", "CS111111");
        data.put("course_name", "intro com");
        data.put("academic_year", "1/2569");
        data.put("memo_no", "อว 660301.26.8/13");
        data.put("date", "21 กันยายน 2569");
        return data;
    }

    private static byte[] fill(byte[] docx, Map<String, String> data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(docx));
                ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] content = in.readAllBytes();
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(content, StandardCharsets.UTF_8);
                    for (Map.Entry<String, String> e : data.entrySet()) {
                        xml = xml.replace("{{" + e.getKey() + "}}", e.getValue());
                    }
                    content = xml.getBytes(StandardCharsets.UTF_8);
                }
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(content);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** ตำแหน่ง x ที่ข้อความแต่ละบรรทัดในหน้าแรกสิ้นสุด หน่วยเป็น point */
    private List<Float> rightEdgesOfFirstPage(byte[] docx) throws IOException {
        List<Float> edges = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(service.convertDocxToPdf(docx))) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (text.isBlank()) {
                        return;
                    }
                    float rightMost = 0;
                    for (TextPosition p : positions) {
                        rightMost = Math.max(rightMost, p.getXDirAdj() + p.getWidthDirAdj());
                    }
                    edges.add(rightMost);
                }
            };
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            stripper.getText(pdf);
        }
        return edges;
    }

    @Nested
    @DisplayName("เอกสารที่ 1 หน้าแรก")
    class AcademicDocumentOne {

        @Test
        @DisplayName("บรรทัดที่ยาวเต็มความกว้างต้องจบที่ขอบขวาเดียวกัน")
        void longLinesEndAtTheSameRightEdge() throws IOException {
            RequiredTools.require(service.isPdfConversionAvailable(),
                    "LibreOffice (แปลง DOCX เป็น PDF)");

            List<Float> edges = rightEdgesOfFirstPage(fill(template("doc_1.docx"), sampleDoc1()));
            assertThat(edges).as("อ่านบรรทัดจากหน้าแรกไม่ได้เลย").isNotEmpty();

            float rightMost = edges.stream().reduce(0f, Math::max);

            // บรรทัดสุดท้ายของย่อหน้าสั้นได้ตามปกติ จึงนับเฉพาะบรรทัดที่เกือบเต็มความกว้าง
            // ว่าต้อง "เกือบเต็ม" แล้วต้องชิดขอบจริง ๆ ไม่ใช่ขาดไปอีกสิบกว่าจุด
            List<Float> nearlyFull = edges.stream()
                    .filter(edge -> edge > rightMost - 40)
                    .toList();

            assertThat(nearlyFull)
                    .as("เอกสารนี้ควรมีบรรทัดที่ยาวเต็มความกว้างหลายบรรทัด")
                    .hasSizeGreaterThanOrEqualTo(4);
            assertThat(nearlyFull)
                    .as("บรรทัดที่ยาวเต็มความกว้างต้องจบที่ขอบขวาเดียวกัน (ขวาสุด %.1f pt) — "
                            + "ถ้าล้มแปลว่า LibreOffice ไม่ได้จัดชิดขอบให้ ดู "
                            + "DocumentGenerationService.forLibreOffice", rightMost)
                    .allSatisfy(edge -> assertThat(edge).isGreaterThan(rightMost - FLUSH_TOLERANCE));
        }
    }

    @Test
    @DisplayName("ไฟล์ที่ส่งเข้า LibreOffice ต้องไม่เหลือ thaiDistribute แต่เทมเพลตยังต้องมี")
    void onlyTheCopySentToLibreOfficeIsRewritten() throws IOException {
        String templateXml = documentXml(template("doc_1.docx"));
        assertThat(templateXml)
                .as("เทมเพลตยังต้องเป็น thaiDistribute เพื่อให้ Word เรนเดอร์ภาษาไทยได้ดีที่สุด")
                .contains("w:val=\"thaiDistribute\"");
    }

    private static String documentXml(byte[] docx) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("ไม่พบ word/document.xml");
    }
}
