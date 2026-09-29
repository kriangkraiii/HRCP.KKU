package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.support.TestCertificates;

/**
 * ลงนามทีละคนแบบใส่ทับ บนเอกสารที่ 1 จริง: ผู้ยื่นเซ็น (รับรองเอกสาร) → เจ้าหน้าที่ใส่เลขที่/วันที่
 * → เจ้าหน้าที่ปิดเอกสาร ทุกขั้นต้องต่อท้ายไฟล์เดิมโดยไม่แตะไบต์เดิม และลายเซ็นทุกอันยังตรวจผ่าน
 */
@DisplayName("ลงนามแบบใส่ทับ (PAdES incremental) บนเอกสารที่ 1")
class IncrementalSigningTest {

    private static final DocumentGenerationService GEN = new DocumentGenerationService();
    private static final ThaiText THAI = ThaiText.sarabun();
    private static final PdfIncrementService PDF = new PdfIncrementService(THAI);

    private static final String JSON = "{\"applicant_name\":\"นายสมชาย ใจดี\",\"title\":\"ขอรับการประเมินผลการสอน\","
            + "\"current_position\":\"อาจารย์\",\"requested_rank\":\"ผู้ช่วยศาสตราจารย์\","
            + "\"employee_type\":\"พนักงานมหาวิทยาลัย\",\"course_code\":\"CP353001\","
            + "\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"2569\"}";

    @BeforeAll
    static void needsLibreOffice() {
        assumeTrue(GEN.isPdfConversionAvailable(), "LibreOffice is not installed");
    }

    private static BasePdfBuilder.Renderer doc1() {
        return new BasePdfBuilder.Renderer() {
            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                StringBuilder json = new StringBuilder(JSON.substring(0, JSON.length() - 1));
                overrides.forEach((k, v) -> json.append(",\"").append(k).append("\":\"").append(v).append('"'));
                json.append('}');
                return GEN.generateSignedDocx(1, json.toString(), pictures.stream()
                        .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height())).toList());
            }

            @Override
            public byte[] toPdf(byte[] docx) throws IOException {
                return GEN.convertDocxToPdf(docx);
            }
        };
    }

    private static BasePdfBuilder.Result base() throws Exception {
        return new BasePdfBuilder().build(doc1(),
                List.of(new BasePdfBuilder.TextSpec("memo_no", 32), new BasePdfBuilder.TextSpec("date", 25)),
                List.of(new BasePdfBuilder.SlotSpec("applicant", "applicant_name", List.of())));
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

    private static CmsSigner signer(String name) throws Exception {
        return CmsSigner.open(TestCertificates.validP12(name), TestCertificates.PIN.toCharArray());
    }

    private static Calendar now() {
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Bangkok"));
    }

    private static void assertPrefix(byte[] before, byte[] after) {
        assertThat(after.length).isGreaterThan(before.length);
        assertThat(java.util.Arrays.equals(before, 0, before.length, after, 0, before.length))
                .as("revision must not rewrite earlier bytes").isTrue();
    }

    @Test
    @DisplayName("ไฟล์ตั้งต้นมีช่องเลขที่ วันที่ ช่องลงนามของผู้ยื่น และช่องปิดเอกสาร ครบในหน้าเดียว")
    void baseHasEveryFieldInPlace() throws Exception {
        var base = base();
        String layout = BasePdfBuilder.describe(base.layout());
        assertThat(base.layout().texts()).extracting(BasePdfBuilder.Box::field).containsExactlyInAnyOrder("memo_no", "date");
        assertThat(base.layout().signatures()).extracting(BasePdfBuilder.Box::field).containsExactly("sig_applicant");
        base.layout().texts().forEach(b -> assertThat(b.width()).as(layout).isGreaterThan(60));
        assertThat(PdfIncrementService.verify(base.pdf())).isEmpty();
    }

    @Test
    @DisplayName("ผู้ยื่นเซ็น → ใส่เลขที่/วันที่ → เจ้าหน้าที่ปิดเอกสาร: ต่อท้ายทุกครั้งและลายเซ็นทุกอันยังถูกต้อง")
    void wholeChain() throws Exception {
        byte[] r0 = base().pdf();
        byte[] r1 = PDF.sign(r0, new PdfIncrementService.SignSpec(signer("นายสมชาย ใจดี"), "sig_applicant", Map.of(),
                List.of("sig_applicant"), true, signatureImage(), "นายสมชาย ใจดี", "ผู้ขอประเมินผลการสอน",
                "มหาวิทยาลัยขอนแก่น", now()));
        Map<String, String> office = new LinkedHashMap<>();
        office.put("memo_no", "อว 660301.26.8/1234");
        office.put("date", "1 ตุลาคม 2569");
        byte[] r2 = PDF.fill(r1, office);
        byte[] r3 = PDF.sign(r2, new PdfIncrementService.SignSpec(signer("เจ้าหน้าที่ ทดสอบ"), BasePdfBuilder.LOCK_FIELD,
                Map.of(), null, false, null, "เจ้าหน้าที่ ทดสอบ", "ออกเลขและปิดเอกสาร", "มหาวิทยาลัยขอนแก่น", now()));

        assertPrefix(r0, r1);
        assertPrefix(r1, r2);
        assertPrefix(r2, r3);

        var checks = PdfIncrementService.verify(r3);
        assertThat(checks).extracting(PdfIncrementService.SignatureCheck::field)
                .containsExactly("sig_applicant", BasePdfBuilder.LOCK_FIELD);
        assertThat(checks).allMatch(PdfIncrementService.SignatureCheck::valid);
        assertThat(checks.get(1).coversWholeFile()).isTrue();

        try (var doc = Loader.loadPDF(r3)) {
            var form = doc.getDocumentCatalog().getAcroForm(null);
            assertThat(PdfIncrementService.valueOf(form.getField("memo_no"))).isEqualTo("อว 660301.26.8/1234");
            assertThat(PdfIncrementService.valueOf(form.getField("date"))).isEqualTo("1 ตุลาคม 2569");
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "incremental"));
        java.nio.file.Files.write(java.nio.file.Path.of("target", "incremental", "doc1-locked.pdf"), r3);
    }

    @Test
    @DisplayName("ข้อความยาวเกินช่องที่จองไว้ — ปฏิเสธ ไม่ใส่ทับจนล้นช่อง")
    void tooLongIsRefused() throws Exception {
        byte[] r0 = base().pdf();
        assertThatThrownBy(() -> PDF.fill(r0, Map.of("date", "วันพฤหัสบดีที่ ๓๐ พฤศจิกายน พุทธศักราช ๒๕๖๙ เวลา ๑๒.๐๐ น.")))
                .isInstanceOf(PdfIncrementService.DoesNotFitException.class);
    }

    @Test
    @DisplayName("ช่องลงนามที่เซ็นไปแล้วเซ็นซ้ำไม่ได้")
    void aSignedFieldCannotBeSignedAgain() throws Exception {
        byte[] r1 = PDF.sign(base().pdf(), new PdfIncrementService.SignSpec(signer("ก"), "sig_applicant", Map.of(),
                List.of("sig_applicant"), true, signatureImage(), "ก", "ทดสอบ", "มข.", now()));
        assertThatThrownBy(() -> PDF.sign(r1, new PdfIncrementService.SignSpec(signer("ข"), "sig_applicant", Map.of(),
                List.of("sig_applicant"), false, signatureImage(), "ข", "ทดสอบ", "มข.", now())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ช่องติ๊ก ✓ ในตาราง (เอกสารที่ 2): หาเจอตามลำดับ และเติมเครื่องหมายได้")
    void tickBoxes() throws Exception {
        String json = "{\"applicant_name\":\"นายสมชาย ใจดี\",\"title\":\"นาย\"}";
        var base = new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                StringBuilder j = new StringBuilder(json.substring(0, json.length() - 1));
                overrides.forEach((k, v) -> j.append(",\"").append(k).append("\":\"").append(v).append('"'));
                return GEN.generateSignedDocx(2, j.append('}').toString(), pictures.stream()
                        .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height())).toList());
            }

            @Override
            public byte[] toPdf(byte[] docx) throws IOException {
                return GEN.convertDocxToPdf(docx);
            }
        }, List.of(new BasePdfBuilder.TextSpec("chk_off_1", 0, true), new BasePdfBuilder.TextSpec("chk_off_2", 0, true),
                new BasePdfBuilder.TextSpec("chk_off_3", 0, true)), List.of());

        var ticks = base.layout().texts();
        assertThat(ticks).extracting(BasePdfBuilder.Box::field).containsExactly("chk_off_1", "chk_off_2", "chk_off_3");
        assertThat(ticks.get(0).y()).as("เรียงจากบนลงล่างตามเอกสาร").isGreaterThan(ticks.get(2).y());

        byte[] filled = PDF.fill(base.pdf(), Map.of("chk_off_2", PdfIncrementService.TICK));
        assertThat(IncrementalSigningService.values(filled)).containsEntry("chk_off_2", PdfIncrementService.TICK)
                .containsEntry("chk_off_1", "");
    }

    @Test
    @DisplayName("หมายเหตุหลายบรรทัดในตาราง (เอกสารที่ 2): ตัดบรรทัดตามคำ ไม่เกินพื้นที่ที่จอง")
    void multiLineRemarks() throws Exception {
        String json = "{\"applicant_name\":\"นายสมชาย ใจดี\",\"title\":\"นาย\"}";
        List<BasePdfBuilder.TextSpec> texts = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            texts.add(new BasePdfBuilder.TextSpec("chk_off_" + i, 0, true));
            texts.add(new BasePdfBuilder.TextSpec("text_" + i, 30, false, IncrementalSigningService.linesFor("text_" + i)));
        }
        var base = new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                StringBuilder j = new StringBuilder(json.substring(0, json.length() - 1));
                overrides.forEach((k, v) -> j.append(",\"").append(k).append("\":\"").append(v).append('"'));
                return GEN.generateSignedDocx(2, j.append('}').toString(), pictures.stream()
                        .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height())).toList());
            }

            @Override
            public byte[] toPdf(byte[] docx) throws IOException {
                return GEN.convertDocxToPdf(docx);
            }
        }, texts, List.of());

        var remark = base.layout().texts().stream().filter(b -> b.field().equals("text_1")).findFirst().orElseThrow();
        assertThat(remark.lines()).isEqualTo(3);
        assertThat(remark.pitch()).isGreaterThan(10);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("chk_off_1", PdfIncrementService.TICK);
        values.put("text_1", "เอกสารครบถ้วน");
        values.put("chk_off_2", PdfIncrementService.TICK);
        values.put("text_2", "ขาดสำเนาคำสั่งแต่งตั้ง");
        byte[] filled = PDF.fill(base.pdf(), values);
        assertThat(IncrementalSigningService.values(filled)).containsEntry("text_2", "ขาดสำเนาคำสั่งแต่งตั้ง");
        assertThatThrownBy(() -> PDF.fill(base.pdf(), Map.of("text_3",
                "ข้อความยาวมากเกินกว่าที่ช่องหมายเหตุสามบรรทัดในตารางนี้จะรับได้แม้จะย่อขนาดตัวอักษรลงแล้วก็ตาม")))
                .isInstanceOf(PdfIncrementService.DoesNotFitException.class);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "incremental"));
        java.nio.file.Files.write(java.nio.file.Path.of("target", "incremental", "doc2-remarks.pdf"), filled);
    }
}
