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
 * Signing must never change how many pages a document has: no empty page after
 * the last line, no line pushed onto a page of its own.
 */
@DisplayName("ลงนามแล้วจำนวนหน้าต้องเท่าเดิม — ไม่มีหน้าว่างงอก")
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

    private byte[] unsigned(SignatureModule module, int docType) throws IOException {
        return render(module, docType, Map.of(), List.of(), DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
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
    @DisplayName("ลงนามแบบเดิม (ฝังรูปใน DOCX): เซ็นครบทุกช่องแล้วจำนวนหน้าเท่ากับตอนยังไม่เซ็น")
    List<DynamicTest> stampedDocumentKeepsItsPageCount() throws IOException {
        byte[] png = signaturePng();
        return everySignableDocument((module, docType, slots) -> {
            List<StampedSignature> all = slots.stream()
                    .map(s -> new StampedSignature(s.anchorPlaceholder(), png, 900, 300))
                    .toList();

            DocumentGenerationService.Fitted fitted = service.fitSignatures(
                    height -> render(module, docType, Map.of(), all, height), unsigned(module, docType));

            assertThat(pages(fitted.pdf())).as("%s doc %d: pages signed vs unsigned", module, docType)
                    .isEqualTo(pages(service.convertDocxToPdfCached(unsigned(module, docType))));
        });
    }

    @TestFactory
    @DisplayName("ลงนามแบบ incremental: PDF ตั้งต้นที่จองช่องลงนามไว้ครบ จำนวนหน้าเท่ากับตอนยังไม่เซ็น")
    List<DynamicTest> incrementalBaseKeepsItsPageCount() {
        return everySignableDocument((module, docType, slots) -> {
            BasePdfBuilder.Result base = new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
                @Override
                public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures)
                        throws IOException {
                    return docx(overrides, pictures, DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
                }

                @Override
                public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures,
                        long signatureHeightEmu) throws IOException {
                    return render(module, docType, overrides, pictures.stream()
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
            assertThat(pages(base.pdf())).as("%s doc %d: base pages vs unsigned", module, docType)
                    .isEqualTo(pages(service.convertDocxToPdfCached(unsigned(module, docType))));
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
