package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * บันทึกข้อความ ขอรับการประเมินผลการสอน (เอกสารที่ 1 เฟส 1) และการจัดแนวข้อความของทุกเอกสาร
 */
@DisplayName("เอกสารที่ 1 เฟส 1 — ตำแหน่งที่ขอ หน้าว่าง และการจัดแนว")
class AcademicDoc1RenderingTest {

    private final DocumentGenerationService service = new DocumentGenerationService();
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, String> applicant(String chk1, String chk2) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("memo_no", "อว 660301.26.3/123");
        d.put("date", "1 ตุลาคม 2569");
        d.put("title", "ผู้ช่วยศาสตราจารย์");
        d.put("applicant_name", "สมชาย ทดสอบยื่น");
        d.put("employee_type", "ข้าราชการ");
        d.put("current_position", "ผู้ช่วยศาสตราจารย์");
        d.put("chk1", chk1);
        d.put("chk2", chk2);
        d.put("course_name", "โครงสร้างข้อมูล");
        d.put("course_code", "CP123456");
        d.put("academic_year", "1/2569");
        return d;
    }

    private String text(byte[] docx) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String render(Map<String, String> data) throws IOException {
        return text(service.generatePreviewDocx(1, mapper.writeValueAsString(data)));
    }

    @Test
    @DisplayName("ขอ รศ. — พิมพ์แค่ \"รองศาสตราจารย์\" ไม่มีช่องติ๊กและไม่มีตำแหน่งที่ไม่ได้ขอ")
    void associateShowsOnlyTheChosenRank() throws IOException {
        String doc = render(applicant("☐", "✓"));

        assertThat(doc)
                .contains("ขอกำหนดตำแหน่งทางวิชาการระดับ รองศาสตราจารย์ พร้อมนี้ได้แนบ")
                .doesNotContain("[").doesNotContain("☐").doesNotContain("✓")
                .doesNotContain("{{");
        // "ผู้ช่วยศาสตราจารย์" ยังอยู่ในคำนำหน้าชื่อและตำแหน่งปัจจุบันของผู้ยื่น — ตรวจเฉพาะประโยคตำแหน่งที่ขอ
        assertThat(doc).doesNotContain("ระดับ ผู้ช่วยศาสตราจารย์").doesNotContain("ศาสตราจารย์ รองศาสตราจารย์");
    }

    @Test
    @DisplayName("ขอ ผศ. — พิมพ์แค่ \"ผู้ช่วยศาสตราจารย์\"")
    void assistantShowsOnlyTheChosenRank() throws IOException {
        String doc = render(applicant("✓", "☐"));

        assertThat(doc)
                .contains("ขอกำหนดตำแหน่งทางวิชาการระดับ ผู้ช่วยศาสตราจารย์ พร้อมนี้ได้แนบ")
                .doesNotContain("รองศาสตราจารย์");
    }

    @Test
    @DisplayName("ยังไม่เลือกตำแหน่ง — ขึ้นจุดไข่ปลาแบบฟอร์มเปล่า")
    void noRankYetLeavesABlank() throws IOException {
        String doc = render(applicant("", ""));

        assertThat(doc).contains("ขอกำหนดตำแหน่งทางวิชาการระดับ ...................................");
    }

    @Test
    @DisplayName("ไม่มีเอกสารฉบับไหนที่ยังจัดแนวแบบกระจายแบบไทย (Thai Distributed)")
    void noDocumentKeepsThaiDistributedAlignment() throws IOException {
        for (int type = 1; type <= 9; type++) {
            assertThat(xmlParts(service.generatePreviewDocx(type, "{}")))
                    .as("เอกสารเฟส 1 ที่ %d", type)
                    .doesNotContain("thaiDistribute");
        }
        for (int type : new int[] { 1, 2, 3, 4, 6, 7, 8, 9 }) {
            assertThat(xmlParts(service.generateP2PreviewDocx(type, "{}")))
                    .as("เอกสารเฟส 2 ที่ %d", type)
                    .doesNotContain("thaiDistribute");
        }
    }

    @Test
    @DisplayName("ลงนามแล้วยังเป็นหน้าเดียว ไม่มีหน้าว่างงอกท้ายเอกสาร")
    void signedDocumentStaysOnOnePage() throws IOException {
        Assumptions.assumeTrue(service.isPdfConversionAvailable(), "ต้องมี LibreOffice ถึงจะนับหน้าได้");

        byte[] docx = service.generateSignedDocx(1, mapper.writeValueAsString(applicant("☐", "✓")),
                List.of(new StampedSignature("applicant_name", signaturePng(), 900, 300)));
        byte[] pdf = service.convertDocxToPdf(docx);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    private static byte[] signaturePng() throws IOException {
        BufferedImage image = new BufferedImage(900, 300, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** ข้อความ XML ของทุกส่วนในไฟล์ รวม styles.xml ซึ่งบางเทมเพลตตั้งการจัดแนวไว้ที่นั่น */
    private static String xmlParts(byte[] docx) throws IOException {
        StringBuilder all = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    all.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return all.toString();
    }
}
