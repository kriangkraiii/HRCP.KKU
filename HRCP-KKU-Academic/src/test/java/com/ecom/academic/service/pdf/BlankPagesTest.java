package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ตัดหน้าว่างที่ล้นออกมาจากการแปลง PDF")
class BlankPagesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    /** What to paint on one page. */
    private interface Paint {
        void on(PDDocument doc, PDPageContentStream page) throws IOException;
    }

    private static Paint text(float y, String s) {
        return (doc, page) -> {
            page.beginText();
            page.setFont(FONT, 12);
            page.newLineAtOffset(72, y);
            page.showText(s);
            page.endText();
        };
    }

    private static final Paint NOTHING = (doc, page) -> {
    };

    private static byte[] pdf(Paint... pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (Paint paint : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    paint.on(doc, cs);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static Paint both(Paint a, Paint b) {
        return (doc, page) -> {
            a.on(doc, page);
            b.on(doc, page);
        };
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private static void withText(byte[] pdf, Consumer<String> check) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            check.accept(new PDFTextStripper().getText(doc));
        }
    }

    @Test
    @DisplayName("หน้าว่างท้ายเอกสารถูกตัด")
    void dropsTrailingEmptyPage() throws IOException {
        byte[] out = BlankPages.drop(pdf(text(700, "Memo"), NOTHING));

        assertThat(pages(out)).isEqualTo(1);
        withText(out, t -> assertThat(t).contains("Memo"));
    }

    @Test
    @DisplayName("หน้าว่างระหว่างฉบับถูกตัด เนื้อหาฉบับถัดไปยังอยู่ครบ")
    void dropsEmptyPageBetweenLetters() throws IOException {
        byte[] out = BlankPages.drop(pdf(text(700, "Letter one"), NOTHING, text(700, "Letter two")));

        assertThat(pages(out)).isEqualTo(2);
        withText(out, t -> assertThat(t).contains("Letter one").contains("Letter two"));
    }

    @Test
    @DisplayName("หน้าที่มีแค่หัวกระดาษซ้ำกับหน้าก่อนและเลขหน้า ถือเป็นหน้าว่าง")
    void headerAndPageNumberAreFurniture() throws IOException {
        Paint header = text(760, "For officers only");
        byte[] out = BlankPages.drop(pdf(both(header, text(700, "Body")),
                both(header, text(40, "- 2 -"))));

        assertThat(pages(out)).isEqualTo(1);
    }

    @Test
    @DisplayName("หน้าที่มีข้อความเนื้อหาไม่ถูกตัด แม้จะมีหัวกระดาษด้วย")
    void keepsPageWithBody() throws IOException {
        Paint header = text(760, "For officers only");
        byte[] in = pdf(both(header, text(700, "Body")), both(header, text(700, "Signature of the dean")));

        assertThat(BlankPages.drop(in)).isSameAs(in);
    }

    @Test
    @DisplayName("ข้อความเดียวกันแต่อยู่คนละระดับ ไม่ใช่หัวกระดาษ")
    void sameTextElsewhereIsBody() throws IOException {
        byte[] in = pdf(text(760, "Approved"), text(300, "Approved"));

        assertThat(BlankPages.drop(in)).isSameAs(in);
    }

    @Test
    @DisplayName("หน้าที่มีรูป (เช่นรูปลายเซ็นหรือช่องลงนามใส) ไม่ถูกตัด")
    void keepsPageWithPicture() throws IOException {
        byte[] in = pdf(text(700, "Body"), (doc, page) -> {
            PDImageXObject image = LosslessFactory.createFromImage(doc,
                    new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB));
            page.drawImage(image, 72, 600, 100, 30);
        });

        assertThat(BlankPages.drop(in)).isSameAs(in);
    }

    @Test
    @DisplayName("หน้าที่มีแค่เส้นตาราง ไม่ถูกตัด")
    void keepsPageWithRuling() throws IOException {
        byte[] in = pdf(text(700, "Body"), (doc, page) -> {
            page.addRect(72, 300, 400, 200);
            page.stroke();
        });

        assertThat(BlankPages.drop(in)).isSameAs(in);
    }

    @Test
    @DisplayName("ช่องที่จองด้วย non-breaking space เป็นเนื้อหา ไม่ใช่ช่องว่าง")
    void reservedSpaceIsContent() throws IOException {
        byte[] in = pdf(text(700, "Body"), text(500, "    "));

        assertThat(BlankPages.drop(in)).isSameAs(in);
    }

    @Test
    @DisplayName("หน้าแรกไม่ถูกตัดแม้ว่าง และเอกสารหน้าเดียวคืนไฟล์เดิม")
    void keepsFirstPage() throws IOException {
        byte[] one = pdf(NOTHING);
        assertThat(BlankPages.drop(one)).isSameAs(one);

        assertThat(pages(BlankPages.drop(pdf(NOTHING, NOTHING)))).isEqualTo(1);
    }
}
