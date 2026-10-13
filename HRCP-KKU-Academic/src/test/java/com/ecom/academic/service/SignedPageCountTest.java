package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.academic.service.pdf.BasePdfBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Signing must not spoil the layout: no empty page after the last line, and no
 * signature block split across a page break — the signature on one page, the
 * signer's name and position on the next.
 *
 * <p>The page count stays the same, with one exception: a block that sat right
 * at the bottom of a page has no room for the taller signature line, and then it
 * moves to the next page whole. That page carries the block, so it is not empty
 * and nothing is split. p2doc_7 rendered with empty data used to split the dean's
 * block instead.
 */
@DisplayName("ลงนามแล้วไม่มีหน้าว่างงอก และบล็อกลงนามไม่ถูกแยกหน้า")
class SignedPageCountTest {

    private final DocumentGenerationService service = new DocumentGenerationService();
    private final SignatureAnchorRegistry registry = new SignatureAnchorRegistry();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void needsLibreOffice() {
        Assumptions.assumeTrue(service.isPdfConversionAvailable(), "ต้องมี LibreOffice ถึงจะนับหน้าได้");
    }

    /** A wide, dark signature — the shape that makes a line tallest. */
    private static byte[] signaturePng() throws IOException {
        BufferedImage image = new BufferedImage(900, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(12));
        g.drawLine(20, 280, 880, 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] render(SignatureModule module, int docType, Map<String, String> values,
            List<StampedSignature> signatures, long signatureHeightEmu) throws IOException {
        Map<String, String> data = new LinkedHashMap<>(values);
        data.putIfAbsent("dummy", "x");
        String json = mapper.writeValueAsString(data);
        return module == SignatureModule.ACADEMIC
                ? service.generateSignedDocx(docType, json, signatures, signatureHeightEmu)
                : service.generateSignedP2Docx(docType, json, signatures, signatureHeightEmu);
    }

    private byte[] unsigned(SignatureModule module, int docType, Map<String, String> names) throws IOException {
        return render(module, docType, names, List.of(), DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
    }

    /** A marker per signer, printed where the name goes — finds which page each signature block ended up on. */
    private static Map<String, String> signerMarkers(List<SignatureSlot> slots) {
        Map<String, String> names = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            names.putIfAbsent(slots.get(i).anchorPlaceholder(), "ZSIG" + (char) ('A' + i) + "Q");
        }
        return names;
    }

    private static List<String> pageTexts(byte[] pdf) throws IOException {
        List<String> texts = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                texts.add(stripper.getText(doc));
            }
        }
        return texts;
    }

    /**
     * Images drawn on each page — every {@code Do}, not every XObject: LibreOffice stores identical
     * pictures once, so three copies of the same signature are one XObject drawn three times.
     */
    private static List<Integer> imagesPerPage(byte[] pdf) throws IOException {
        List<Integer> counts = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                List<Object> tokens = new org.apache.pdfbox.pdfparser.PDFStreamParser(page).parse();
                int images = 0;
                for (int i = 1; i < tokens.size(); i++) {
                    if (tokens.get(i) instanceof org.apache.pdfbox.contentstream.operator.Operator op
                            && "Do".equals(op.getName())
                            && tokens.get(i - 1) instanceof org.apache.pdfbox.cos.COSName name
                            && page.getResources().isImageXObject(name)) {
                        images++;
                    }
                }
                counts.add(images);
            }
        }
        return counts;
    }

    /**
     * The page printing this signer's name under the signature: "(name)" in brackets, the same test the
     * stamping uses — the name also runs through the body text (p2doc_1 repeats the applicant's on later pages).
     */
    private static int pageOf(List<String> texts, String marker) {
        java.util.regex.Pattern underSignature = java.util.regex.Pattern.compile(
                "\\([^()\\n]*" + marker + "[^()\\n]*\\)");
        for (int p = 0; p < texts.size(); p++) {
            if (underSignature.matcher(texts.get(p)).find()) {
                return p;
            }
        }
        return -1;
    }

    /**
     * Every signer's name is on a page that also holds a signature image, and the
     * page count grows by at most the one page a moved block takes.
     */
    private static void assertLayoutKept(String label, byte[] unsignedPdf, byte[] signedPdf,
            Map<String, String> names) throws IOException {
        List<String> texts = pageTexts(signedPdf);
        List<Integer> images = imagesPerPage(signedPdf);
        Map<Integer, Integer> namesPerPage = new LinkedHashMap<>();
        for (String marker : names.values()) {
            int page = pageOf(texts, marker);
            if (page >= 0) {
                namesPerPage.merge(page, 1, Integer::sum);
            }
        }
        namesPerPage.forEach((page, count) -> assertThat(images.get(page))
                .as("%s: page %d prints %d signer name(s) but holds %d image(s) — a signature block was split",
                        label, page + 1, count, images.get(page))
                .isGreaterThanOrEqualTo(count));

        int before = pages(unsignedPdf);
        int after = pages(signedPdf);
        if (after != before) {
            assertThat(after).as("%s: pages signed vs unsigned", label).isEqualTo(before + 1);
            assertThat(namesPerPage).as("%s: the added page must carry a signature block that moved whole,"
                    + " not be an empty page", label).containsKey(after - 1);
        }
    }

    /** One dynamic test per signable document. */
    private interface PerDocument {
        void check(SignatureModule module, int docType, List<SignatureSlot> slots) throws Exception;
    }

    private List<DynamicTest> everySignableDocument(PerDocument check) {
        List<DynamicTest> tests = new ArrayList<>();
        for (SignatureModule module : registry.modules()) {
            for (int docType : registry.signableDocumentTypes(module)) {
                // ช่องแบบแถว (บรรณกิจเพิ่มเติมของเอกสารที่ 9) มีเฉพาะเมื่อผู้ขอเพิ่มแถว — ตัวอย่างนี้ไม่มีแถว
                List<SignatureSlot> slots = registry.slotsFor(module, docType).stream()
                        .filter(s -> !com.ecom.academic.service.SignatureAnchorRegistry.isRowSlot(module, docType, s.slotKey()))
                        .toList();
                tests.add(DynamicTest.dynamicTest(module + " doc " + docType + " (" + slots.size() + " จุดลงนาม)",
                        () -> check.check(module, docType, slots)));
            }
        }
        return tests;
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @TestFactory
    @DisplayName("ลงนามแบบเดิม (ฝังรูปใน DOCX): เซ็นครบทุกช่องแล้วไม่มีหน้าว่างงอกและบล็อกลงนามไม่แยกหน้า")
    List<DynamicTest> stampedDocumentKeepsItsPageCount() throws IOException {
        byte[] png = signaturePng();
        return everySignableDocument((module, docType, slots) -> {
            List<StampedSignature> all = slots.stream()
                    .map(s -> new StampedSignature(s.anchorPlaceholder(), png, 900, 300))
                    .toList();
            Map<String, String> names = signerMarkers(slots);

            DocumentGenerationService.Fitted fitted = service.fitSignatures(
                    height -> render(module, docType, names, all, height), unsigned(module, docType, names));

            assertLayoutKept(module + " doc " + docType,
                    service.convertDocxToPdfCached(unsigned(module, docType, names)), fitted.pdf(), names);
        });
    }

    @TestFactory
    @DisplayName("ลงนามแบบ incremental: PDF ตั้งต้นที่จองช่องลงนามไว้ครบ ไม่มีหน้าว่างงอกและบล็อกลงนามไม่แยกหน้า")
    List<DynamicTest> incrementalBaseKeepsItsPageCount() {
        return everySignableDocument((module, docType, slots) -> {
            Map<String, String> names = signerMarkers(slots);
            BasePdfBuilder.Result base = new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
                @Override
                public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures)
                        throws IOException {
                    return docx(overrides, pictures, DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
                }

                @Override
                public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures,
                        long signatureHeightEmu) throws IOException {
                    Map<String, String> values = new LinkedHashMap<>(names);
                    values.putAll(overrides);
                    return render(module, docType, values, pictures.stream()
                            .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height()))
                            .toList(), signatureHeightEmu);
                }

                @Override
                public byte[] toPdf(byte[] docx) throws IOException {
                    return service.convertDocxToPdf(docx);
                }
            }, List.of(), slots.stream()
                    .map(s -> new BasePdfBuilder.SlotSpec(s.slotKey(), s.anchorPlaceholder(), List.of()))
                    .toList());

            assertThat(base.layout().signatures()).as("%s doc %d: every signature place found", module, docType)
                    .hasSizeGreaterThanOrEqualTo(slots.size());
            assertLayoutKept(module + " doc " + docType + " base",
                    service.convertDocxToPdfCached(unsigned(module, docType, names)), base.pdf(), names);
        });
    }

    @Test
    @DisplayName("หน้าว่างที่ LibreOffice สร้างจริงถูกตัดทิ้งตอนแปลง")
    void conversionDropsTheEmptyPageLibreOfficeLaysOut() throws IOException {
        byte[] docx;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("บันทึกข้อความ");
            doc.createParagraph().createRun().addBreak(BreakType.PAGE);
            doc.createParagraph();
            doc.write(out);
            docx = out.toByteArray();
        }

        byte[] pdf = service.convertDocxToPdf(docx);

        assertThat(pages(pdf)).isEqualTo(1);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("บันทึกข้อความ");
        }
    }
}
