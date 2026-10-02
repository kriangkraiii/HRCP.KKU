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
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.support.TestCertificates;

/**
 * เอกสารที่ 7 ของเฟส 1: แบบประเมินผลการสอน กรรมการทั้งสามคนลงนาม บล็อกลงนามอยู่ในกล่องข้อความ
 * ลายเซ็นต้องขึ้นครบทั้งสามช่องโดยชื่อทั้งสามยังอยู่ในหน้า และลายเซ็นดิจิทัลทั้งสามยังถูกต้อง
 */
@DisplayName("เอกสารที่ 7: กรรมการสามคนลงนามในกล่องข้อความ")
class CommitteeEvaluationSigningTest {

    private static final DocumentGenerationService GEN = new DocumentGenerationService();
    private static final PdfIncrementService PDF = new PdfIncrementService(ThaiText.sarabun());

    private static final String JSON = "{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"สมชาย ทดสอบยื่น\","
            + "\"requested_position\":\"รองศาสตราจารย์\",\"score1x\":\"16\",\"score2x\":\"24\",\"score3x\":\"24\","
            + "\"score4x\":\"16\",\"scorex\":\"80\",\"ch3\":\"✓\","
            + "\"committee_1_name\":\"ศาสตราจารย์ ดร.หนึ่ง ทดสอบ\","
            + "\"committee_2_name\":\"รองศาสตราจารย์ ดร.สอง ทดสอบ\","
            + "\"committee_3_name\":\"ผู้ช่วยศาสตราจารย์ ดร.สาม ทดสอบ\"}";

    private static final List<String> SLOTS = List.of("committee_chair", "committee_member_2", "committee_member_3");

    @BeforeAll
    static void needsLibreOffice() {
        assumeTrue(GEN.isPdfConversionAvailable(), "LibreOffice is not installed");
    }

    private static BasePdfBuilder.Result base() throws Exception {
        return new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                return GEN.generateSignedDocx(7, JSON, pictures.stream()
                        .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height())).toList());
            }

            @Override
            public byte[] toPdf(byte[] docx) throws IOException {
                return GEN.convertDocxToPdf(docx);
            }
        }, List.of(), List.of(
                new BasePdfBuilder.SlotSpec("committee_chair", "committee_1_name", List.of()),
                new BasePdfBuilder.SlotSpec("committee_member_2", "committee_2_name", List.of()),
                new BasePdfBuilder.SlotSpec("committee_member_3", "committee_3_name", List.of())));
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
    @DisplayName("มีช่องลงนามครบสามช่อง และชื่อกรรมการทั้งสามยังพิมพ์อยู่")
    void threeSignatureBoxesAndAllNamesPrinted() throws Exception {
        var base = base();
        assertThat(base.layout().signatures()).extracting(BasePdfBuilder.Box::field)
                .containsExactlyInAnyOrder("sig_committee_chair", "sig_committee_member_2", "sig_committee_member_3");
        try (var doc = Loader.loadPDF(base.pdf())) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("หนึ่ง", "สอง", "สาม");
        }
    }

    @Test
    @DisplayName("ประธานลงนามก่อน แล้วกรรมการคนที่ 2 และ 3 — ลายเซ็นดิจิทัลถูกต้องครบสามรายการ")
    void chairThenMembersAllValid() throws Exception {
        byte[] pdf = base().pdf();
        String[] who = { "ประธาน ทดสอบ", "กรรมการสอง ทดสอบ", "กรรมการสาม ทดสอบ" };
        for (int i = 0; i < SLOTS.size(); i++) {
            String field = "sig_" + SLOTS.get(i);
            pdf = PDF.sign(pdf, new PdfIncrementService.SignSpec(
                    CmsSigner.open(TestCertificates.validP12(who[i]), TestCertificates.PIN.toCharArray()),
                    field, Map.of(), List.of(field), i == 0, signatureImage(), who[i], "ลงนามในฐานะกรรมการ",
                    "มหาวิทยาลัยขอนแก่น", Calendar.getInstance(TimeZone.getTimeZone("Asia/Bangkok"))));
        }
        assertThat(PdfIncrementService.verify(pdf)).hasSize(3).allSatisfy(c -> assertThat(c.valid()).isTrue());

        java.nio.file.Path dir = java.nio.file.Path.of("target", "incremental");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.write(dir.resolve("doc7-signed.pdf"), pdf);
        try (var doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                ImageIO.write(renderer.renderImageWithDPI(p, 110), "png", dir.resolve("doc7-signed-p" + (p + 1) + ".png").toFile());
            }
        }
    }
}
