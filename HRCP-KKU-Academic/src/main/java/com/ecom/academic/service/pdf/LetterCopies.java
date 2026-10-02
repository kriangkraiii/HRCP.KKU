package com.ecom.academic.service.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

/**
 * ฉบับเดียวจาก PDF ที่พิมพ์หลายฉบับต่อกัน (เอกสารที่ 5: หนังสือถึงกรรมการท่านละฉบับ)
 *
 * <p>ลายเซ็นดิจิทัลของไฟล์รวมครอบทั้งไฟล์ ตัดหน้าออกมาแล้วจึงไม่ติดมาด้วย ฉบับที่ตัดจึงต้องลงนามใหม่
 * ({@link #sign}) ด้วยกุญแจเดียวกับที่ลงนามไฟล์รวม — Foxit/Adobe จึงขึ้นใบรับรองในไฟล์ของแต่ละท่าน
 */
public final class LetterCopies {

    private LetterCopies() {
    }

    /**
     * หน้าของฉบับหนึ่ง กับตำแหน่งรูปลายเซ็นบนหน้านั้น ตามชื่อช่องลงนาม ({@code sig_dean})
     */
    public record Letter(byte[] pdf, Map<String, PDRectangle> signatureBoxes) {
    }

    /**
     * หน้าของฉบับที่ {@code letter} เมื่อทุกฉบับยาวเท่ากัน รูปลายเซ็นและค่าในช่องพิมพ์ลงเนื้อหน้า
     *
     * @return null เมื่อจำนวนหน้าแบ่งเท่า ๆ กันไม่ได้
     */
    public static Letter cut(byte[] pdf, int letter, int letters) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int total = doc.getNumberOfPages();
            if (letters < 1 || total % letters != 0 || letter < 1 || letter > letters) {
                return null;
            }
            int per = total / letters;
            int first = (letter - 1) * per;
            Map<String, PDRectangle> boxes = new LinkedHashMap<>();
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            if (form != null) {
                for (PDField f : form.getFieldTree()) {
                    String name = f.getFullyQualifiedName();
                    if (name == null || !name.startsWith("sig_")) {
                        continue;
                    }
                    int mirror = name.indexOf(BasePdfBuilder.MIRROR_SEPARATOR);
                    String slotField = mirror < 0 ? name : name.substring(0, mirror);
                    for (PDAnnotationWidget w : f.getWidgets()) {
                        int page = doc.getPages().indexOf(w.getPage());
                        if (page >= first && page < first + per && w.getRectangle() != null) {
                            boxes.putIfAbsent(slotField, w.getRectangle());
                        }
                    }
                }
                // ช่องฟอร์มของหน้าที่ตัดทิ้งจะได้ไม่ค้างอยู่ในไฟล์
                form.flatten();
                doc.getDocumentCatalog().setAcroForm(null);
            }
            doc.getDocumentCatalog().getCOSObject().removeItem(COSName.PERMS);
            for (int p = total - 1; p >= 0; p--) {
                if (p / per != letter - 1) {
                    doc.removePage(p);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new Letter(out.toByteArray(), boxes);
        }
    }

    /**
     * ลงนามฉบับที่ตัดออกมา ช่องลงนามวางทับรูปลายเซ็นของ {@code slotField} (คลิกที่รูปแล้วขึ้นใบรับรอง)
     * ไม่มีรูปในหน้านั้นก็เป็นลายเซ็นที่มองไม่เห็น ใบรับรองยังขึ้นในแผงลายเซ็น
     *
     * <p>รับรองเอกสาร (DocMDP P=1): หลังจากนี้แก้ไขอะไรก็ทำให้ลายเซ็นไม่ถูกต้อง
     */
    public static byte[] sign(Letter letter, String slotField, CmsSigner signer, String name, String reason,
            String location, Calendar signedAt) throws IOException {
        PDRectangle box = letter.signatureBoxes().get(slotField);
        byte[] withField = withSignatureField(letter.pdf(), box);
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(withField))) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            PDSignatureField sf = (PDSignatureField) form.getField(FIELD);
            PDAnnotationWidget widget = sf.getWidgets().get(0);

            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(COSName.getPDFName("ETSI.CAdES.detached"));
            sig.setName(name);
            sig.setReason(reason);
            sig.setLocation(location);
            sig.setSignDate(signedAt);

            COSDictionary catalog = doc.getDocumentCatalog().getCOSObject();
            COSArray refs = new COSArray();
            refs.add(MdpSupport.docMdp(1));
            COSDictionary perms = new COSDictionary();
            perms.setItem(COSName.DOCMDP, sig);
            catalog.setItem(COSName.PERMS, perms);
            catalog.setNeedToBeUpdated(true);
            sig.getCOSObject().setItem(COSName.getPDFName("Reference"), refs);
            sf.getCOSObject().setItem(COSName.V, sig);

            try (SignatureOptions options = new SignatureOptions()) {
                options.setPreferredSignatureSize(32 * 1024);
                doc.addSignature(sig, signer, options);
                // addSignature() makes a field without a visual template invisible: put the
                // box back over the printed picture, with nothing drawn on top of it.
                if (box != null) {
                    widget.setRectangle(box);
                    widget.setAppearance(emptyAppearance(doc, box));
                }
                widget.getCOSObject().setNeedToBeUpdated(true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.saveIncremental(out);
                return out.toByteArray();
            }
        }
    }

    private static final String FIELD = "sig_copy";

    private static byte[] withSignatureField(byte[] pdf, PDRectangle box) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDSignatureField sf = new PDSignatureField(form);
            sf.setPartialName(FIELD);
            PDPage page = doc.getPage(0);
            PDAnnotationWidget widget = sf.getWidgets().get(0);
            widget.setRectangle(box != null ? box : new PDRectangle());
            widget.setPage(page);
            widget.setPrinted(true);
            page.getAnnotations().add(widget);
            form.getFields().add(sf);
            form.setSignaturesExist(true);
            form.setAppendOnly(true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static PDAppearanceDictionary emptyAppearance(PDDocument doc, PDRectangle box) {
        PDAppearanceStream ap = new PDAppearanceStream(doc);
        ap.setBBox(new PDRectangle(box.getWidth(), box.getHeight()));
        PDAppearanceDictionary apd = new PDAppearanceDictionary();
        apd.setNormalAppearance(ap);
        return apd;
    }
}
