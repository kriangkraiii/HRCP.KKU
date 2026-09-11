package com.ecom.visual;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.support.RequiredTools;

/**
 * เรนเดอร์เอกสารออกมาเป็นภาพจริง แล้วตรวจสิ่งที่ตาเห็น
 *
 * <p><b>ทำไมต้องมี:</b> {@code SignatureStampingTest} ตรวจเอกสารด้วยการอ่านสตริงใน
 * XML และมันยืนยันว่า {@code w:lineRule="exact"} อยู่ครบถ้วนถูกต้อง — ทั้งที่สตริงนั้นเอง
 * คือสาเหตุที่ลายเซ็นถูกเฉือนส่วนบนหายไปราวหนึ่งในสามตอน LibreOffice แปลงเป็น PDF
 * (ดู {@code docs/PLAN-signature-crop-fix.md}) เทสเขียวสนิท เอกสารราชการออกมาพัง
 * และคนที่พบคือผู้ใช้
 *
 * <p>บทเรียนคือ <b>เทสที่อ่าน markup ไม่มีทางจับบั๊กการเรนเดอร์ได้</b> ไม่ว่าจะเขียน
 * ละเอียดแค่ไหน ต้องเดินท่อจนสุด: template → DOCX → PDF → พิกเซล
 *
 * <p><b>วิธีตรวจว่าลายเซ็นถูกตัด:</b> ประทับภาพที่เป็น <em>สีม่วงแดงล้วน</em> ซึ่งไม่มี
 * ในเอกสารจริงเลย ทำให้แยกหมึกลายเซ็นออกจากตัวอักษรในหน้าได้อย่างแน่นอน ภาพต้นฉบับ
 * มีแถบสีอยู่ทั้งขอบบนสุดและขอบล่างสุด ถ้าส่วนบนถูกเฉือน แถบบนจะหายไปทั้งแถบ และ
 * อัตราส่วน สูง:กว้าง ของกรอบหมึกที่วัดได้จากหน้า PDF จะผิดไปจากต้นฉบับทันที
 * การเทียบเป็นอัตราส่วนทำให้ไม่ต้องสนใจว่าเอกสารย่อขยายภาพไปเท่าไร
 */
@DisplayName("เอกสารที่เรนเดอร์ออกมาจริง")
class DocumentRenderTest {

    private final DocumentGenerationService service = new DocumentGenerationService();
    private final SignatureAnchorRegistry registry = new SignatureAnchorRegistry();

    /** ความละเอียดตอนแปลงหน้า PDF เป็นภาพ — พอให้เห็นการเฉือนระดับไม่กี่มิลลิเมตร */
    private static final float DPI = 150;

    private static final int INK_WIDTH = 300;
    private static final int INK_HEIGHT = 100;

    /** อัตราส่วน สูง:กว้าง ของหมึกในภาพต้นฉบับ */
    private static final double SOURCE_RATIO = (double) INK_HEIGHT / INK_WIDTH;

    /**
     * ยอมให้คลาดได้ 12% ของอัตราส่วน
     *
     * <p>กว้างพอสำหรับการปัดเศษของ EMU และขอบภาพที่เบลอจากการ resample
     * แต่แคบกว่าการเฉือนที่รายงานไว้มาก (25-30%) อย่างชัดเจน
     */
    private static final double RATIO_TOLERANCE = 0.12;

    /**
     * ภาพลายเซ็นจำลอง: แถบสีม่วงแดงชิดขอบบนสุดและขอบล่างสุด
     *
     * <p>ชิดขอบโดยตั้งใจ — ลายเซ็นจริงที่มีเส้นตวัดสูงก็กินพื้นที่จนถึงขอบบนแบบนี้
     * และนั่นคือเงื่อนไขเดียวที่ทำให้บั๊กการเฉือนแสดงตัวออกมา
     */
    private static byte[] inkThatTouchesBothEdges() throws IOException {
        BufferedImage image = new BufferedImage(INK_WIDTH, INK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, INK_WIDTH, 10);                    // ขอบบนสุด
        g.fillRect(0, INK_HEIGHT - 10, INK_WIDTH, 10);      // ขอบล่างสุด
        g.fillRect(INK_WIDTH / 2 - 5, 0, 10, INK_HEIGHT);   // เส้นเชื่อมกลาง
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * ประทับลายเซ็น <b>ช่องเดียว</b> ต่อการเรนเดอร์หนึ่งครั้ง
     *
     * <p>ทีละช่องโดยตั้งใจ ไม่ใช่ประทับครบทุกช่องพร้อมกัน เพราะหมึกที่ใช้วัดเป็นสีเดียวกัน
     * หมด ถ้าหน้าหนึ่งมีลายเซ็นสองอันกรอบที่วัดได้จะครอบทั้งคู่รวมกัน แล้วอัตราส่วนที่
     * ออกมาก็ไม่ได้บอกอะไรเกี่ยวกับลายเซ็นอันใดอันหนึ่งเลย
     *
     * <p>ยังตรงกับสภาพจริงมากกว่าด้วย: เอกสารเดินทางผ่านมือผู้ลงนามทีละคน
     * สภาพ "เซ็นแล้วบางช่อง" จึงเป็นสภาพที่พบบ่อยที่สุด
     */
    private byte[] renderWithOneSignature(SignatureModule module, int docType, SignatureSlot slot,
            byte[] ink) throws IOException {

        List<StampedSignature> stamps =
                List.of(new StampedSignature(slot.anchorPlaceholder(), ink, INK_WIDTH, INK_HEIGHT));

        return module == SignatureModule.ACADEMIC
                ? service.generateSignedDocx(docType, "{\"dummy\":\"x\"}", stamps)
                : service.generateSignedP2Docx(docType, "{\"dummy\":\"x\"}", stamps);
    }

    /** กรอบสี่เหลี่ยมที่ล้อมพิกเซลสีม่วงแดงทั้งหมดในภาพ */
    private record InkBounds(int minX, int minY, int maxX, int maxY, int pixels) {
        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }

        double ratio() {
            return (double) height() / width();
        }
    }

    private static InkBounds findInk(BufferedImage page) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        int count = 0;

        for (int y = 0; y < page.getHeight(); y++) {
            for (int x = 0; x < page.getWidth(); x++) {
                int rgb = page.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // ม่วงแดง: แดงและน้ำเงินสูง เขียวต่ำ — ไม่มีทางชนกับหมึกดำหรือกระดาษขาว
                if (r > 180 && b > 180 && g < 100) {
                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new InkBounds(minX, minY, maxX, maxY, count);
    }

    /** แปลงเป็น PDF แล้วเรนเดอร์ทุกหน้าเป็นภาพ */
    private List<BufferedImage> pagesOf(byte[] docx) throws IOException {
        byte[] pdf = service.convertDocxToPdf(docx);
        assertThat(pdf).as("ผลลัพธ์ต้องเป็นไฟล์ PDF จริง").startsWith((byte) '%', (byte) 'P');

        List<BufferedImage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                pages.add(renderer.renderImageWithDPI(i, DPI));
            }
        }
        return pages;
    }

    private String textOf(byte[] docx) throws IOException {
        try (PDDocument document = Loader.loadPDF(service.convertDocxToPdf(docx))) {
            return new PDFTextStripper().getText(document);
        }
    }

    @TestFactory
    @DisplayName("ลายเซ็นในเอกสารที่แปลงเป็น PDF แล้ว ต้องไม่ถูกเฉือนขอบ")
    List<DynamicTest> signaturesAreNotClipped() throws IOException {
        RequiredTools.require(service.isPdfConversionAvailable(), "LibreOffice (แปลง DOCX เป็น PDF)");

        byte[] ink = inkThatTouchesBothEdges();
        List<DynamicTest> tests = new ArrayList<>();

        for (SignatureModule module : registry.modules()) {
            for (int docType : registry.signableDocumentTypes(module)) {
                for (SignatureSlot slot : registry.slotsFor(module, docType)) {
                    String name = module + "-doc" + docType + "-" + slot.anchorPlaceholder()
                            .replaceAll("[^A-Za-z0-9ก-๙_]", "");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    List<BufferedImage> pages =
                            pagesOf(renderWithOneSignature(module, docType, slot, ink));

                    InkBounds widest = null;
                    for (BufferedImage page : pages) {
                        InkBounds found = findInk(page);
                        if (found.pixels() > 0 && (widest == null || found.pixels() > widest.pixels())) {
                            widest = found;
                        }
                    }

                    assertThat(widest)
                            .as("ประทับลายเซ็นไปแล้วต้องมองเห็นในหน้าที่เรนเดอร์ออกมา")
                            .isNotNull();

                    double ratio = widest.ratio();
                    double drift = Math.abs(ratio - SOURCE_RATIO) / SOURCE_RATIO;

                    if (drift > RATIO_TOLERANCE) {
                        // เก็บหน้าที่มีปัญหาไว้ให้เปิดดูด้วยตา
                        BufferedImage evidence = pages.get(0);
                        ImageDiff.write(ImageDiff.OUTPUT_DIR.resolve(name + "-clipped.png"), evidence);
                    }

                    assertThat(drift)
                            .as("""
                                    กรอบหมึกลายเซ็นในหน้า PDF ผิดสัดส่วนไปจากภาพต้นฉบับ %.1f%%
                                    ต้นฉบับ สูง:กว้าง = %.3f แต่ในเอกสารได้ %.3f (%dx%d พิกเซล)
                                    อาการนี้แปลว่าภาพถูกเฉือน ซึ่งเกิดเมื่อบรรทัดลายเซ็นถูกตรึงความสูง
                                    ไว้ด้วย w:lineRule="exact" ที่เตี้ยกว่าตัวรูป
                                    ภาพหลักฐานอยู่ที่ target/visual/%s-clipped.png""",
                                    drift * 100, SOURCE_RATIO, ratio,
                                    widest.width(), widest.height(), name)
                            .isLessThanOrEqualTo(RATIO_TOLERANCE);
                }));
                }
            }
        }
        return tests;
    }

    @Test
    @DisplayName("เอกสารที่เรนเดอร์แล้วต้องไม่มี placeholder ที่แทนค่าไม่สำเร็จหลงเหลือ")
    void noPlaceholderSurvivesIntoThePdf() throws IOException {
        RequiredTools.require(service.isPdfConversionAvailable(), "LibreOffice (แปลง DOCX เป็น PDF)");

        String text = textOf(service.generatePreviewDocx(7, """
                {"committee_1_name":"ศ.ดร.ทดสอบ หนึ่ง",
                 "committee_2_name":"รศ.ดร.ทดสอบ สอง",
                 "committee_3_name":"ผศ.ดร.ทดสอบ สาม",
                 "score1x":"๒๐.๐๐","score2x":"๓๐.๐๐","score3x":"๓๐.๐๐","score4x":"๒๐.๐๐",
                 "scorex":"๑๐๐.๐๐","ch1":"☐","ch2":"☐","ch3":"☐","ch4":"☑"}
                """));

        assertThat(text)
                .as("""
                        มีเครื่องหมาย placeholder หลงเหลือในเอกสารที่ออกไปถึงผู้ใช้
                        แปลว่ามีฟิลด์ที่ระบบไม่รู้จักหรือแทนค่าไม่สำเร็จ""")
                .doesNotContain("{{")
                .doesNotContain("${");

        assertThat(text)
                .as("ค่าที่ส่งเข้าไปต้องปรากฏในเอกสารจริง ไม่ใช่หายไปเงียบ ๆ")
                .contains("ศ.ดร.ทดสอบ หนึ่ง")
                .contains("รศ.ดร.ทดสอบ สอง")
                .contains("ผศ.ดร.ทดสอบ สาม");
    }
}
