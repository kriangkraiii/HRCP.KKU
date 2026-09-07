package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ecom.search.index.FileTextExtractor;
import com.ecom.search.model.ExtractionState;

/**
 * Reading uploaded documents, including the ways that goes wrong.
 *
 * <p>Real files rather than mocks: the whole question is whether POI and PDFBox
 * hand back usable text from bytes on disk, and a mock of either would only
 * confirm the test's own assumptions.
 *
 * <p>A pure unit test with no Spring context — extraction has no dependencies
 * beyond the two libraries and two size limits.
 */
class FileTextExtractorTest {

    private final FileTextExtractor extractor = new FileTextExtractor(50L * 1024 * 1024, 200_000);

    private Path writeDocx(Path dir, String name, String text) throws Exception {
        Path file = dir.resolve(name);
        try (XWPFDocument document = new XWPFDocument();
                OutputStream out = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText(text);
            document.write(out);
        }
        return file;
    }

    private Path writePdf(Path dir, String name, String text) throws Exception {
        Path file = dir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            document.save(file.toFile());
        }
        return file;
    }

    @Test
    @DisplayName("อ่านข้อความจาก .docx ได้")
    void readsDocx(@TempDir Path dir) throws Exception {
        Path file = writeDocx(dir, "attachment.docx", "รายงานผลการประเมินการสอน ภาคการศึกษาที่ 1");

        FileTextExtractor.Extraction result = extractor.extract(file.toString());

        assertThat(result.state()).isEqualTo(ExtractionState.DONE);
        assertThat(result.text()).contains("รายงานผลการประเมินการสอน");
    }

    @Test
    @DisplayName("อ่านข้อความจาก .pdf ได้")
    void readsPdf(@TempDir Path dir) throws Exception {
        // Latin only: the standard 14 fonts cannot encode Thai, and a font file
        // is not what this test is about. Thai is covered by the docx case.
        Path file = writePdf(dir, "attachment.pdf", "Teaching Evaluation Report 2568");

        FileTextExtractor.Extraction result = extractor.extract(file.toString());

        assertThat(result.state()).isEqualTo(ExtractionState.DONE);
        assertThat(result.text()).contains("Teaching Evaluation Report");
    }

    @Test
    @DisplayName("ไฟล์ที่หายไปคือ SKIPPED ไม่ใช่ FAILED")
    void missingFileIsSkipped() {
        FileTextExtractor.Extraction result =
                extractor.extract("/no/such/place/attachment.docx");

        assertThat(result.state())
                .as("ไฟล์ถูกลบตามรอบเก็บกวาดเป็นเรื่องปกติ ไม่ใช่ความผิดพลาดที่ต้องตามแก้")
                .isEqualTo(ExtractionState.SKIPPED);
    }

    @Test
    @DisplayName("ชนิดไฟล์ที่ไม่รองรับถูกข้าม")
    void unsupportedTypeIsSkipped(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("archive.zip");
        Files.writeString(file, "not really a zip");

        assertThat(extractor.extract(file.toString()).state())
                .isEqualTo(ExtractionState.SKIPPED);
    }

    @Test
    @DisplayName("ไฟล์เสียทำให้ FAILED โดยไม่โยน exception ออกมา")
    void corruptFileFailsQuietly(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("broken.docx");
        Files.writeString(file, "นี่ไม่ใช่ไฟล์ docx จริง");

        FileTextExtractor.Extraction result = extractor.extract(file.toString());

        assertThat(result.state()).isEqualTo(ExtractionState.FAILED);
        assertThat(result.error())
                .as("เหตุผลต้องถูกบันทึกไว้ ไม่งั้นแถวที่ค้างจะไม่มีใครรู้ว่าค้างเพราะอะไร")
                .isNotBlank();
    }

    @Test
    @DisplayName("ไฟล์ใหญ่เกินขีดจำกัดถูกข้าม")
    void oversizedFileIsSkipped(@TempDir Path dir) throws Exception {
        FileTextExtractor tinyLimit = new FileTextExtractor(10, 200_000);
        Path file = writeDocx(dir, "big.docx", "ข้อความยาวกว่าสิบไบต์แน่นอน");

        assertThat(tinyLimit.extract(file.toString()).state())
                .isEqualTo(ExtractionState.SKIPPED);
    }

    @Test
    @DisplayName("ข้อความถูกตัดตามขีดจำกัดและยุบช่องว่าง")
    void textIsCappedAndCollapsed(@TempDir Path dir) throws Exception {
        FileTextExtractor shortCap = new FileTextExtractor(50L * 1024 * 1024, 40);
        Path file = writeDocx(dir, "long.docx", "ประเมิน   การสอน ".repeat(50));

        FileTextExtractor.Extraction result = shortCap.extract(file.toString());

        assertThat(result.text()).hasSize(40);
        assertThat(result.text())
                .as("ช่องว่างซ้ำ ๆ ทำให้ index บวมโดยไม่เพิ่มความสามารถในการค้น")
                .doesNotContain("   ");
    }

    @Test
    @DisplayName("พาธว่างถูกข้าม")
    void blankPathIsSkipped() {
        assertThat(extractor.extract(null).state()).isEqualTo(ExtractionState.SKIPPED);
        assertThat(extractor.extract("  ").state()).isEqualTo(ExtractionState.SKIPPED);
    }
}
