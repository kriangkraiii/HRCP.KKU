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
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
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

    /**
     * A late value to reserve. {@code reserve} is its length in non-breaking spaces;
     * a {@code tick} (✓ in a pair of brackets) gets a short fixed reserve instead.
     */
    public record TextSpec(String field, int reserve, boolean tick, int lines) {
        public TextSpec(String field, int reserve) {
            this(field, reserve, false, 1);
        }

        public TextSpec(String field, int reserve, boolean tick) {
            this(field, reserve, tick, 1);
        }
    }

    /** Length of a tick's reserve. Tick fields are told apart by order, not length. */
    static final int TICK_RUN = 5;

    public record SlotSpec(String slotKey, String anchorPlaceholder, List<String> ownFields) {
    }

    /**
     * Where one field ended up. Coordinates in PDF points, origin bottom-left. A
     * multi-line field is one box over all its lines, {@code pitch} apart.
     */
    public record Box(String field, int page, float x, float y, float width, float height, float fontSize,
            int lines, float pitch) {
        public Box(String field, int page, float x, float y, float width, float height, float fontSize) {
            this(field, page, x, y, width, height, fontSize, 1, 0);
        }
    }

    /** Custom keys on a multi-line field: how many lines, and how far apart. */
    static final COSName LINES = COSName.getPDFName("HRCPLines");
    static final COSName PITCH = COSName.getPDFName("HRCPPitch");

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
        Map<String, Integer> wanted = new LinkedHashMap<>();
        Map<String, Integer> lines = new LinkedHashMap<>();
        for (TextSpec t : texts) {
            wanted.put(t.field(), t.tick() ? TICK_RUN : Math.max(MIN_RUN, t.reserve()));
            lines.put(t.field(), t.tick() ? 1 : Math.max(1, t.lines()));
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        wanted.keySet().forEach(f -> overrides.put(f, String.format(TOKEN, f)));

        List<SlotPicture> pictures = new ArrayList<>();
        Map<Integer, SlotSpec> byWidth = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            int w = SLOT_PNG_WIDTH + i;
            pictures.add(new SlotPicture(slots.get(i).anchorPlaceholder(), clearPng(w, SLOT_PNG_HEIGHT), w, SLOT_PNG_HEIGHT));
            byWidth.put(w, slots.get(i));
        }

        byte[] template = renderer.docx(overrides, pictures);
        // A reserve longer than its line (a narrow table cell) is split by the layout
        // and cannot be found; shrink those and render again. Each field ends up with
        // the widest reserve that stays on one line.
        for (int attempt = 0;; attempt++) {
            // Distinct lengths first; once a field has had to shrink, equal lengths
            // are allowed and told apart by their order in the document.
            Map<String, Integer> lengths = attempt == 0 ? distinct(wanted) : new LinkedHashMap<>(wanted);
            Map<String, Integer> present = new LinkedHashMap<>();
            List<String> order = new ArrayList<>();
            byte[] docx = reserve(template, lengths, lines, present, order);
            // A late field this template does not print has nothing to reserve.
            lengths.keySet().retainAll(present.keySet());
            wanted.keySet().retainAll(present.keySet());
            byte[] rendered = renderer.toPdf(docx);

            List<Box> found = new ArrayList<>();
            List<String> missing = locate(rendered, lengths, order, found);
            if (missing.stream().anyMatch(f -> lengths.get(f) == TICK_RUN)) {
                throw new BaseBuildException("Tick places for " + missing + " not found in the rendered page");
            }
            if (!missing.isEmpty()) {
                boolean shrunk = false;
                for (String f : missing) {
                    int smaller = Math.max(MIN_RUN, (int) (wanted.get(f) * 0.7));
                    shrunk |= smaller < wanted.get(f);
                    wanted.put(f, smaller);
                }
                if (!shrunk || attempt >= 4) {
                    throw new BaseBuildException("Reserved area for " + missing + " not found in the rendered page");
                }
                continue;
            }
            return finish(rendered, mergeLines(found, lines), byWidth, slots);
        }
    }

    /**
     * A multi-line field was found as one box per line, in reading order: join each
     * occurrence's lines into one box.
     */
    private static List<Box> mergeLines(List<Box> found, Map<String, Integer> lines) throws BaseBuildException {
        List<Box> out = new ArrayList<>();
        Map<String, List<Box>> pending = new LinkedHashMap<>();
        for (Box b : found) {
            int k = lines.getOrDefault(b.field(), 1);
            if (k <= 1) {
                out.add(b);
                continue;
            }
            List<Box> group = pending.computeIfAbsent(b.field(), f -> new ArrayList<>());
            group.add(b);
            if (group.size() == k) {
                Box top = group.get(0), bottom = group.get(k - 1);
                if (top.page() != bottom.page()) {
                    throw new BaseBuildException("Lines of " + b.field() + " run across a page break");
                }
                float x = (float) group.stream().mapToDouble(Box::x).min().orElse(top.x());
                float right = (float) group.stream().mapToDouble(g -> g.x() + g.width()).max().orElse(top.x());
                float pitch = (top.y() - bottom.y()) / (k - 1);
                out.add(new Box(b.field(), top.page(), x, bottom.y(), right - x, top.y() + top.height() - bottom.y(),
                        top.fontSize(), k, pitch));
                pending.remove(b.field());
            }
        }
        if (!pending.isEmpty()) {
            throw new BaseBuildException("Not every line of " + pending.keySet() + " was found");
        }
        return out;
    }

    /** Run lengths at least three apart, so no field's run can pass for another's. */
    private static Map<String, Integer> distinct(Map<String, Integer> wanted) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var w : wanted.entrySet()) {
            int n = w.getValue();
            if (n != TICK_RUN) {
                while (tooClose(out, n)) {
                    n++;
                }
            }
            out.put(w.getKey(), n);
        }
        return out;
    }

    private static boolean tooClose(Map<String, Integer> taken, int n) {
        for (int o : taken.values()) {
            if (o != TICK_RUN && Math.abs(o - n) < 3) {
                return true;
            }
        }
        return false;
    }

    private Result finish(byte[] rendered, List<Box> found, Map<Integer, SlotSpec> byWidth,
            List<SlotSpec> slots) throws IOException, BaseBuildException {
        List<Box> textBoxes = new ArrayList<>(found);
        List<Box> sigBoxes = locateSlots(rendered, byWidth);
        byte[] pdf = installForm(rendered, textBoxes, sigBoxes, slots);
        return new Result(pdf, new Layout(textBoxes, sigBoxes));
    }

    // ------------------------------------------------------------------ DOCX

    /** Swaps each token for its NBSP run, in the token's own run formatting. */
    /**
     * @param order filled with each token occurrence's field, in document order
     */
    static byte[] reserve(byte[] docx, Map<String, Integer> lengths, Map<String, Integer> lines,
            Map<String, Integer> seen, List<String> order) throws IOException {
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
                    if (name.equals("word/document.xml")) {
                        // Runs of equal length are matched to fields by reading order.
                        java.util.TreeMap<Integer, String> at = new java.util.TreeMap<>();
                        for (var l : lengths.entrySet()) {
                            String token = String.format(TOKEN, l.getKey());
                            for (int i = xml.indexOf(token); i >= 0; i = xml.indexOf(token, i + 1)) {
                                at.put(i, l.getKey());
                            }
                        }
                        // A multi-line reserve is one run per line.
                        at.values().forEach(f -> {
                            for (int n = lines.getOrDefault(f, 1); n > 0; n--) {
                                order.add(f);
                            }
                        });
                    }
                    for (var l : lengths.entrySet()) {
                        String token = String.format(TOKEN, l.getKey());
                        int at;
                        while ((at = xml.indexOf(token)) >= 0) {
                            // Word trims a text element's edge spaces unless told not to.
                            int tStart = xml.lastIndexOf("<w:t", at);
                            int tEnd = xml.indexOf('>', tStart);
                            String open = xml.substring(tStart, tEnd + 1);
                            String fixed = open.contains("xml:space") ? open : "<w:t xml:space=\"preserve\">";
                            String run = String.valueOf(NBSP).repeat(l.getValue());
                            String reserved = run;
                            for (int n = lines.getOrDefault(l.getKey(), 1); n > 1; n--) {
                                reserved += "</w:t><w:br/><w:t xml:space=\"preserve\">" + run;
                            }
                            xml = xml.substring(0, tStart) + fixed + xml.substring(tEnd + 1, at)
                                    + reserved
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

    /**
     * Finds each reserved run in reading order and gives it to the next field, in
     * document order, of the same length. A template NBSP may touch the front of a
     * text reserve, so a run one or two longer also matches (ours is its tail);
     * tick reserves must match exactly.
     *
     * @return the fields left without a place
     */
    private static List<String> locate(byte[] pdf, Map<String, Integer> lengths, List<String> order, List<Box> boxes)
            throws IOException {
        Map<Integer, java.util.ArrayDeque<String>> queues = new LinkedHashMap<>();
        for (String f : order) {
            Integer n = lengths.get(f);
            if (n != null) {
                queues.computeIfAbsent(n, k -> new java.util.ArrayDeque<>()).add(f);
            }
        }
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
                    for (int extra = 0; extra <= 2; extra++) {
                        int n = run - extra;
                        if (n == TICK_RUN && extra > 0) {
                            continue;
                        }
                        var queue = queues.get(n);
                        if (queue != null && !queue.isEmpty()) {
                            TextPosition first = all.get(i + extra), last = all.get(k - 1);
                            float size = first.getFontSizeInPt();
                            float width = last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj();
                            float baseline = pageHeight - first.getYDirAdj();
                            float height = size * 1.3f;
                            boxes.add(new Box(queue.poll(), p, first.getXDirAdj(),
                                    baseline - height * PdfIncrementService.BASELINE_RATIO, width, height, size));
                            break;
                        }
                    }
                    i = k - 1;
                }
            }
        }
        List<String> missing = new ArrayList<>();
        queues.values().forEach(missing::addAll);
        return missing.stream().distinct().toList();
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

            // Push buttons, not text fields: viewers redraw a text field's value themselves
            // (Preview/PDFKit with its own font, clipped to the box), losing TH Sarabun and
            // the Thai shaping. A push button has no value to redraw, so every viewer shows
            // the appearance we draw. The value itself is kept in the field's /TU.
            Map<String, PDPushButton> textFields = new LinkedHashMap<>();
            for (Box b : texts) {
                PDPushButton t = textFields.get(b.field());
                PDAnnotationWidget w;
                if (t == null) {
                    t = new PDPushButton(form);
                    t.setPartialName(b.field());
                    // Kept for the size the value is drawn at (the template's own).
                    t.getCOSObject().setString(COSName.DA,
                            String.format(Locale.ROOT, "/%s %.1f Tf 0 g", PdfIncrementService.FONT, b.fontSize()));
                    // Filled by the server only.
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
                if (b.lines() > 1) {
                    t.getCOSObject().setInt(LINES, b.lines());
                    t.getCOSObject().setFloat(PITCH, b.pitch());
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
