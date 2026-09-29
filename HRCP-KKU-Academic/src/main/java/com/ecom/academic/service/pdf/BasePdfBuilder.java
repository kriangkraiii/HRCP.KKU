package com.ecom.academic.service.pdf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

/**
 * Builds revision 0 of a signed document: the rendered page with every place a
 * value or signature will later go already reserved and turned into a form field.
 *
 * <p><b>Why everything is decided here.</b> After the first signature certifies
 * the document, only filling existing fields and signing existing signature fields
 * leave earlier signatures intact; creating a field, or even a field's /Lock, is a
 * change the certification forbids.
 *
 * <p><b>Reserving space.</b> Each late text value is replaced in the DOCX by a run
 * of non-breaking spaces. That keeps the layout fixed whatever is typed later (tab
 * stops after a value no longer move with its length), bounds how much can be
 * typed, and — because LibreOffice keeps NBSPs in the text layer with their exact
 * positions — tells us where the field is. Each field's run has its own length so
 * runs identify their field. Signature slots get a transparent picture of the same
 * geometry a signature would have, told apart by pixel width.
 *
 * <p>The DOCX must be converted by the caller's {@code converter}: the app's
 * LibreOffice profile carries the Thai fonts, and a bare profile lays out differently.
 */
public final class BasePdfBuilder {

    /** Name of the invisible signature field that closes the document. */
    public static final String LOCK_FIELD = "doc_lock";
    private static final String TOKEN = "@@OVL_%s@@";
    private static final char NBSP = ' ';
    private static final int SLOT_PNG_WIDTH = 500, SLOT_PNG_HEIGHT = 180;
    private static final int MIN_RUN = 12;

    /** What the builder needs from the document pipeline. */
    public interface Renderer {
        /** DOCX of the envelope with the given values and slot pictures (anchor → png, width, height). */
        byte[] docx(Map<String, String> overrides, List<SlotPicture> pictures) throws IOException;

        byte[] toPdf(byte[] docx) throws IOException;
    }

    public record SlotPicture(String anchorPlaceholder, byte[] png, int width, int height) {
    }

    /** A late text value to reserve. {@code reserve} is its length in non-breaking spaces. */
    public record TextSpec(String field, int reserve) {
    }

    public record SlotSpec(String slotKey, String anchorPlaceholder, List<String> ownFields) {
    }

    /** Where one field ended up. Coordinates in PDF points, origin bottom-left. */
    public record Box(String field, int page, float x, float y, float width, float height, float fontSize) {
    }

    public record Layout(List<Box> texts, List<Box> signatures) {
    }

    public record Result(byte[] pdf, Layout layout) {
    }

    /** The document could not be prepared this way; the envelope should use the old flow. */
    public static final class BaseBuildException extends Exception {
        public BaseBuildException(String message) {
            super(message);
        }
    }

    public Result build(Renderer renderer, List<TextSpec> texts, List<SlotSpec> slots)
            throws IOException, BaseBuildException {
        // Distinct run lengths, three apart so a template NBSP touching a run cannot
        // make it look like another field's.
        Map<String, Integer> lengths = new LinkedHashMap<>();
        int bump = 0;
        for (TextSpec t : texts) {
            lengths.put(t.field(), Math.max(MIN_RUN, t.reserve()) + bump);
            bump += 3;
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        lengths.keySet().forEach(f -> overrides.put(f, String.format(TOKEN, f)));

        List<SlotPicture> pictures = new ArrayList<>();
        Map<Integer, SlotSpec> byWidth = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            int w = SLOT_PNG_WIDTH + i;
            pictures.add(new SlotPicture(slots.get(i).anchorPlaceholder(), clearPng(w, SLOT_PNG_HEIGHT), w, SLOT_PNG_HEIGHT));
            byWidth.put(w, slots.get(i));
        }

        Map<String, Integer> present = new LinkedHashMap<>();
        byte[] docx = reserve(renderer.docx(overrides, pictures), lengths, present);
        // A late field this template does not print has nothing to reserve.
        lengths.keySet().retainAll(present.keySet());
        byte[] rendered = renderer.toPdf(docx);

        List<Box> textBoxes = locateTexts(rendered, lengths);
        List<Box> sigBoxes = locateSlots(rendered, byWidth);
        byte[] pdf = installForm(rendered, textBoxes, sigBoxes, slots);
        return new Result(pdf, new Layout(textBoxes, sigBoxes));
    }

    // ------------------------------------------------------------------ DOCX

    /** Swaps each token for its NBSP run, in the token's own run formatting. */
    static byte[] reserve(byte[] docx, Map<String, Integer> lengths, Map<String, Integer> seen) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx));
                ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();
                String name = e.getName();
                if (name.startsWith("word/") && name.endsWith(".xml")
                        && (name.equals("word/document.xml") || name.startsWith("word/header") || name.startsWith("word/footer"))) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    for (var l : lengths.entrySet()) {
                        String token = String.format(TOKEN, l.getKey());
                        int at;
                        while ((at = xml.indexOf(token)) >= 0) {
                            // Word trims a text element's edge spaces unless told not to.
                            int tStart = xml.lastIndexOf("<w:t", at);
                            int tEnd = xml.indexOf('>', tStart);
                            String open = xml.substring(tStart, tEnd + 1);
                            String fixed = open.contains("xml:space") ? open : "<w:t xml:space=\"preserve\">";
                            xml = xml.substring(0, tStart) + fixed + xml.substring(tEnd + 1, at)
                                    + String.valueOf(NBSP).repeat(l.getValue())
                                    + xml.substring(at + token.length());
                            seen.merge(l.getKey(), 1, Integer::sum);
                        }
                    }
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                zout.putNextEntry(new ZipEntry(name));
                zout.write(data);
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] clearPng(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // One nearly-invisible pixel: a fully empty image is one a converter may drop.
        g.setColor(new Color(255, 255, 255, 1));
        g.fillRect(0, 0, 1, 1);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ locating

    private static List<Box> locateTexts(byte[] pdf, Map<String, Integer> lengths) throws IOException, BaseBuildException {
        Map<Integer, String> byLength = new LinkedHashMap<>();
        lengths.forEach((f, n) -> byLength.put(n, f));
        List<Box> boxes = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                float pageHeight = doc.getPage(p).getMediaBox().getHeight();
                List<TextPosition> all = new ArrayList<>();
                PDFTextStripper s = new PDFTextStripper() {
                    @Override
                    protected void processTextPosition(TextPosition text) {
                        all.add(text);
                    }
                };
                s.setStartPage(p + 1);
                s.setEndPage(p + 1);
                s.getText(doc);
                for (int i = 0; i < all.size(); i++) {
                    if (!isNbsp(all.get(i))) {
                        continue;
                    }
                    int k = i + 1;
                    while (k < all.size() && isNbsp(all.get(k)) && sameLine(all.get(i), all.get(k))
                            && touching(all.get(k - 1), all.get(k))) {
                        k++;
                    }
                    int run = k - i;
                    // A template NBSP may touch the front of ours: take ours from the end.
                    for (int extra = 0; extra <= 2; extra++) {
                        String field = byLength.get(run - extra);
                        if (field != null) {
                            TextPosition first = all.get(i + extra), last = all.get(k - 1);
                            float size = first.getFontSizeInPt();
                            float width = last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj();
                            float baseline = pageHeight - first.getYDirAdj();
                            float height = size * 1.3f;
                            boxes.add(new Box(field, p, first.getXDirAdj(),
                                    baseline - height * PdfIncrementService.BASELINE_RATIO, width, height, size));
                            break;
                        }
                    }
                    i = k - 1;
                }
            }
        }
        for (String f : lengths.keySet()) {
            if (boxes.stream().noneMatch(b -> b.field().equals(f))) {
                throw new BaseBuildException("Reserved area for " + f + " not found in the rendered page");
            }
        }
        return boxes;
    }

    private static boolean isNbsp(TextPosition t) {
        return t.getUnicode().length() == 1 && t.getUnicode().charAt(0) == NBSP;
    }

    private static boolean sameLine(TextPosition a, TextPosition b) {
        return Math.abs(a.getYDirAdj() - b.getYDirAdj()) < 0.1;
    }

    private static boolean touching(TextPosition prev, TextPosition next) {
        return next.getXDirAdj() - (prev.getXDirAdj() + prev.getWidthDirAdj()) < 0.2;
    }

    private static List<Box> locateSlots(byte[] pdf, Map<Integer, SlotSpec> byWidth) throws IOException, BaseBuildException {
        List<Box> boxes = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int pageNo = 0;
            for (PDPage page : doc.getPages()) {
                final int p = pageNo++;
                new PDFStreamEngine() {
                    {
                        addOperator(new Concatenate(this));
                        addOperator(new DrawObject(this));
                        addOperator(new SetGraphicsStateParameters(this));
                        addOperator(new Save(this));
                        addOperator(new Restore(this));
                        addOperator(new SetMatrix(this));
                    }

                    @Override
                    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
                        if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName n) {
                            PDXObject x = getResources().getXObject(n);
                            if (x instanceof PDImageXObject img && img.getHeight() == SLOT_PNG_HEIGHT
                                    && byWidth.containsKey(img.getWidth())) {
                                Matrix m = getGraphicsState().getCurrentTransformationMatrix();
                                boxes.add(new Box("sig_" + byWidth.get(img.getWidth()).slotKey(), p,
                                        m.getTranslateX(), m.getTranslateY(), m.getScalingFactorX(), m.getScalingFactorY(), 0));
                                return;
                            }
                        }
                        super.processOperator(operator, operands);
                    }
                }.processPage(page);
            }
        }
        for (SlotSpec s : byWidth.values()) {
            if (boxes.stream().noneMatch(b -> b.field().equals("sig_" + s.slotKey()))) {
                throw new BaseBuildException("Signature place for slot " + s.slotKey() + " not found in the rendered page");
            }
        }
        return boxes;
    }

    // ------------------------------------------------------------------ form

    private static byte[] installForm(byte[] pdf, List<Box> texts, List<Box> sigs, List<SlotSpec> slots) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf);
                InputStream ttf = BasePdfBuilder.class.getClassLoader().getResourceAsStream(ThaiText.FONT_RESOURCE)) {
            // Whole font, not a subset: later revisions draw any glyph and must not embed anything new.
            PDType0Font font = PDType0Font.load(doc, ttf, false);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources dr = new PDResources();
            dr.put(COSName.getPDFName(PdfIncrementService.FONT), font);
            form.setDefaultResources(dr);
            form.setDefaultAppearance("/" + PdfIncrementService.FONT + " 16 Tf 0 g");
            form.setNeedAppearances(false);

            Map<String, PDTextField> textFields = new LinkedHashMap<>();
            for (Box b : texts) {
                PDTextField t = textFields.get(b.field());
                PDAnnotationWidget w;
                if (t == null) {
                    t = new PDTextField(form);
                    t.setPartialName(b.field());
                    t.setDefaultAppearance(String.format(Locale.ROOT, "/%s %.1f Tf 0 g", PdfIncrementService.FONT, b.fontSize()));
                    // Filled by the server only; a viewer regenerating the appearance would lose the Thai shaping.
                    t.setReadOnly(true);
                    textFields.put(b.field(), t);
                    form.getFields().add(t);
                    w = t.getWidgets().get(0);
                } else {
                    // The same value printed twice: one field, another widget.
                    w = new PDAnnotationWidget();
                    w.setParent(t);
                    List<PDAnnotationWidget> ws = new ArrayList<>(t.getWidgets());
                    ws.add(w);
                    t.setWidgets(ws);
                }
                place(doc, w, b);
            }
            for (Box b : sigs) {
                PDSignatureField sf = new PDSignatureField(form);
                sf.setPartialName(b.field());
                SlotSpec slot = slots.stream().filter(s -> b.field().equals("sig_" + s.slotKey())).findFirst().orElseThrow();
                List<String> locked = new ArrayList<>();
                locked.add(b.field());
                slot.ownFields().stream().filter(textFields::containsKey).forEach(locked::add);
                sf.getCOSObject().setItem(PdfIncrementService.lockKey(), MdpSupport.lock("Include", locked, null));
                place(doc, sf.getWidgets().get(0), b);
                form.getFields().add(sf);
            }
            PDSignatureField lock = new PDSignatureField(form);
            lock.setPartialName(LOCK_FIELD);
            lock.getCOSObject().setItem(PdfIncrementService.lockKey(), MdpSupport.lock("All", null, 1));
            place(doc, lock.getWidgets().get(0), new Box(LOCK_FIELD, 0, 0, 0, 0, 0, 0));
            form.getFields().add(lock);

            inlineIndirectNumbers(doc);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * LibreOffice writes some numbers of the tagged-PDF structure tree (marked-content
     * ids) as objects of their own. Validators such as EU DSS cannot compare such
     * objects between revisions and report every later revision as an "undefined
     * modification". Nothing is signed yet, so write them in place.
     */
    private static void inlineIndirectNumbers(PDDocument doc) {
        java.util.Set<org.apache.pdfbox.cos.COSBase> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<org.apache.pdfbox.cos.COSBase> todo = new java.util.ArrayDeque<>();
        todo.add(doc.getDocument().getTrailer());
        while (!todo.isEmpty()) {
            org.apache.pdfbox.cos.COSBase node = todo.pop();
            if (node instanceof org.apache.pdfbox.cos.COSObject o) {
                node = o.getObject();
            }
            if (node == null || !seen.add(node)) {
                continue;
            }
            if (node instanceof org.apache.pdfbox.cos.COSDictionary d) {
                for (COSName k : new ArrayList<>(d.keySet())) {
                    org.apache.pdfbox.cos.COSBase v = d.getItem(k);
                    if (v instanceof org.apache.pdfbox.cos.COSObject o && o.getObject() instanceof org.apache.pdfbox.cos.COSNumber n) {
                        d.setItem(k, n);
                    } else if (v != null) {
                        todo.push(v);
                    }
                }
            } else if (node instanceof org.apache.pdfbox.cos.COSArray a) {
                for (int i = 0; i < a.size(); i++) {
                    org.apache.pdfbox.cos.COSBase v = a.get(i);
                    if (v instanceof org.apache.pdfbox.cos.COSObject o && o.getObject() instanceof org.apache.pdfbox.cos.COSNumber n) {
                        a.set(i, n);
                    } else if (v != null) {
                        todo.push(v);
                    }
                }
            }
        }
    }

    private static void place(PDDocument doc, PDAnnotationWidget w, Box b) throws IOException {
        PDPage page = doc.getPage(b.page());
        w.setRectangle(new PDRectangle(b.x(), b.y(), b.width(), b.height()));
        w.setPage(page);
        w.setPrinted(true);
        page.getAnnotations().add(w);
    }

    /** Which boxes exist, for tests and diagnostics. */
    public static String describe(Layout layout) {
        StringBuilder sb = new StringBuilder();
        for (Box b : layout.texts()) {
            sb.append(String.format(Locale.ROOT, "text %s p%d (%.1f, %.1f) %.1f x %.1f @%.1fpt%n",
                    b.field(), b.page(), b.x(), b.y(), b.width(), b.height(), b.fontSize()));
        }
        for (Box b : layout.signatures()) {
            sb.append(String.format(Locale.ROOT, "sign %s p%d (%.1f, %.1f) %.1f x %.1f%n",
                    b.field(), b.page(), b.x(), b.y(), b.width(), b.height()));
        }
        return sb.toString();
    }

    static List<PDField> fields(PDAcroForm form) {
        List<PDField> out = new ArrayList<>();
        form.getFieldTree().forEach(out::add);
        return out;
    }
}
