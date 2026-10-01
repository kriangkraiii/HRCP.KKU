package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.support.TestCertificates;

/**
 * เอกสารที่ 5 ของเฟส 1: หนังสือเชิญกรรมการสามท่าน ท่านละฉบับ อยู่ในไฟล์เดียว คณบดีลงนามครั้งเดียว
 * ลายเซ็นต้องขึ้นครบทุกฉบับ และเลขที่/วันที่จากฟอร์มต้องพิมพ์อยู่ตั้งแต่แรก
 */
@DisplayName("เอกสารที่ 5: หนังสือถึงกรรมการสามฉบับในไฟล์เดียว")
class CommitteeLettersSigningTest {

    private static final DocumentGenerationService GEN = new DocumentGenerationService();
    private static final PdfIncrementService PDF = new PdfIncrementService(ThaiText.sarabun());

    private static final String JSON = "{\"memo_no\":\"อว 660301.26.4/ว.99\",\"date\":\"1 ตุลาคม 2569\","
            + "\"ref_order_no\":\"777/2569\",\"ref_order_date\":\"1 ตุลาคม พ.ศ. 2569\","
            + "\"applicant_title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"สมชาย ทดสอบยื่น\","
            + "\"committee_name_1\":\"ศาสตราจารย์ ดร.หนึ่ง ทดสอบ\",\"committee_position_1\":\"ประธานอนุกรรมการ\","
            + "\"committee_name_2\":\"รองศาสตราจารย์ ดร.สอง ทดสอบ\",\"committee_position_2\":\"อนุกรรมการ\","
            + "\"committee_name_3\":\"ผู้ช่วยศาสตราจารย์ ดร.สาม ทดสอบ\",\"committee_position_3\":\"อนุกรรมการและเลขานุการ\","
            + "\"dean_name\":\"รองศาสตราจารย์ ดร.คณบดี ทดสอบ\",\"dropdownDean\":\"คณบดีวิทยาลัยการคอมพิวเตอร์\"}";

    @BeforeAll
    static void needsLibreOffice() {
        assumeTrue(GEN.isPdfConversionAvailable(), "LibreOffice is not installed");
    }

    private static BasePdfBuilder.Result base() throws Exception {
        return new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                return GEN.generateSignedDocx(5, JSON, pictures.stream()
                        .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height())).toList());
            }

            @Override
            public byte[] toPdf(byte[] docx) throws IOException {
                return GEN.convertDocxToPdf(docx);
            }
        }, List.of(), List.of(new BasePdfBuilder.SlotSpec("dean", "dean_name", List.of())));
    }

    private static byte[] signatureImage() throws IOException {
        BufferedImage img = new BufferedImage(340, 110, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 40, 140));
        g.setStroke(new BasicStroke(6));
        g.drawPolyline(new int[] { 20, 80, 130, 190, 250, 320 }, new int[] { 80, 25, 90, 20, 85, 40 }, 6);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("สามหน้า หน้าละกรรมการหนึ่งท่าน พร้อมเลขที่และวันที่จากฟอร์ม")
    void oneLetterPerCommitteeMember() throws Exception {
        try (var doc = Loader.loadPDF(base().pdf())) {
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
            for (int page = 1; page <= 3; page++) {
                PDFTextStripper s = new PDFTextStripper();
                s.setStartPage(page);
                s.setEndPage(page);
                String text = s.getText(doc);
                String[] names = { "หนึ่ง", "สอง", "สาม" };
                for (int other = 1; other <= 3; other++) {
                    assertThat(text.contains(names[other - 1]))
                            .as("หน้า %d มีชื่อกรรมการคนที่ %d", page, other).isEqualTo(other == page);
                }
                assertThat(text).as("หน้า %d", page).contains("ว.99").contains("777/2569");
            }
        }
    }

    @Test
    @DisplayName("คณบดีลงนามครั้งเดียว ลายเซ็นขึ้นทั้งสามหน้า และลายเซ็นดิจิทัลยังถูกต้อง")
    void oneSignatureShowsOnEveryLetter() throws Exception {
        var base = base();
        assertThat(base.layout().signatures()).hasSize(3);

        byte[] signed = PDF.sign(base.pdf(), new PdfIncrementService.SignSpec(
                CmsSigner.open(TestCertificates.validP12("คณบดี ทดสอบ"), TestCertificates.PIN.toCharArray()),
                "sig_dean", Map.of(), List.of("sig_dean"), true, signatureImage(), "คณบดี ทดสอบ", "ลงนามในตำแหน่ง คณบดี",
                "มหาวิทยาลัยขอนแก่น", Calendar.getInstance(TimeZone.getTimeZone("Asia/Bangkok"))));

        assertThat(PdfIncrementService.verify(signed)).singleElement()
                .satisfies(c -> assertThat(c.valid()).isTrue());
        try (var doc = Loader.loadPDF(signed)) {
            var form = doc.getDocumentCatalog().getAcroForm(null);
            for (int copy = 2; copy <= 3; copy++) {
                var mirror = form.getField(BasePdfBuilder.mirrorName("sig_dean", copy));
                assertThat(mirror).as("สำเนาที่ %d", copy).isNotNull();
                var widget = mirror.getWidgets().get(0);
                assertThat(widget.getAppearance()).as("ลายเซ็นในสำเนาที่ %d", copy).isNotNull();
                assertThat(doc.getPages().indexOf(widget.getPage())).isEqualTo(copy - 1);
            }
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "incremental"));
        java.nio.file.Files.write(java.nio.file.Path.of("target", "incremental", "doc5-signed.pdf"), signed);
    }
}
