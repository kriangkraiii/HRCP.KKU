package com.ecom.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;

/**
 * SPIKE S1/S2: can we find, in LibreOffice's own layout, where late values and
 * signatures will land — without them being there?
 *
 * <p>Three renders of the real doc_1 template: FINAL (values and signature in, as the
 * system renders today), BASE (values blank, signature transparent — what would be
 * signed first) and PROBE (a marker in each value). The marker's first glyph gives
 * the start and baseline; our own glyph run drawn there on BASE is compared with FINAL.
 */
@EnabledIfSystemProperty(named = "spike", matches = "true")
class LayoutSpikeTest {

    private static final Path OUT = Path.of("target", "spike");
    private static final String MEMO = "อว 660301.26.8/1234";
    private static final String DATE = "1 ตุลาคม 2569";

    private static String json(String memo, String date) {
        return "{\"applicant_name\":\"นายสมชาย ใจดี\",\"title\":\"ขอรับการประเมินผลการสอน\","
                + "\"current_position\":\"อาจารย์\",\"requested_rank\":\"ผู้ช่วยศาสตราจารย์\","
                + "\"employee_type\":\"พนักงานมหาวิทยาลัย\",\"course_code\":\"CP353001\","
                + "\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"2569\","
                + "\"memo_no\":\"" + memo + "\",\"date\":\"" + date + "\"}";
    }

    private static byte[] png(boolean visible) throws IOException {
        BufferedImage img = new BufferedImage(340, 110, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (visible) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(20, 40, 140));
            g.setStroke(new BasicStroke(6));
            g.drawPolyline(new int[] { 20, 80, 130, 190, 250, 320 }, new int[] { 80, 25, 90, 20, 85, 40 }, 6);
        } else {
            g.setColor(new Color(255, 255, 255, 1)); // invisible, but not an all-zero image LibreOffice might drop
            g.fillRect(0, 0, 1, 1);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private record Glyphs(List<TextPosition> all, float pageHeight, int pages) {
        /** First glyph of {@code text} as [x, baselineFromBottom, fontSize, page]. */
        float[] find(String text) {
            StringBuilder sb = new StringBuilder();
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                String u = all.get(i).getUnicode();
                for (int k = 0; k < u.length(); k++) {
                    if (!Character.isWhitespace(u.charAt(k))) { // spaces are not glyphs here
                        sb.append(u.charAt(k));
                        idx.add(i);
                    }
                }
            }
            int at = sb.indexOf(text.replaceAll("\\s", ""));
            if (at < 0) {
                return null;
            }
            TextPosition t = all.get(idx.get(at));
            return new float[] { t.getXDirAdj(), pageHeight - t.getYDirAdj(), t.getFontSizeInPt() };
        }
    }

    private static Glyphs glyphs(byte[] pdf) throws IOException {
        List<TextPosition> all = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    all.add(text);
                }
            };
            s.setSortByPosition(false);
            s.getText(doc);
            return new Glyphs(all, doc.getPage(0).getMediaBox().getHeight(), doc.getNumberOfPages());
        }
    }

    /** Where each image XObject is painted: page index and user-space rectangle. */
    private static List<String> images(byte[] pdf) throws IOException {
        List<String> found = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int pageNo = 0;
            for (PDPage page : doc.getPages()) {
                final int p = pageNo++;
                PDFStreamEngine engine = new PDFStreamEngine() {
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
                        if ("Do".equals(operator.getName())) {
                            PDXObject x = getResources().getXObject((COSName) operands.get(0));
                            if (x instanceof PDImageXObject img) {
                                Matrix m = getGraphicsState().getCurrentTransformationMatrix();
                                found.add(String.format(Locale.ROOT, "p%d %dx%d at (%.2f, %.2f) size %.2f x %.2f",
                                        p, img.getWidth(), img.getHeight(), m.getTranslateX(), m.getTranslateY(),
                                        m.getScalingFactorX(), m.getScalingFactorY()));
                                return;
                            }
                        }
                        super.processOperator(operator, operands);
                    }
                };
                engine.processPage(page);
            }
        }
        return found;
    }

    private byte[] overlay(byte[] base, List<float[]> at, List<String> values) throws Exception {
        try (PDDocument doc = Loader.loadPDF(base);
                InputStream ttf = getClass().getClassLoader().getResourceAsStream("fonts/th-sarabun/THSarabunNew.ttf");
                InputStream ttf2 = getClass().getClassLoader().getResourceAsStream("fonts/th-sarabun/THSarabunNew.ttf")) {
            PDType0Font font = PDType0Font.load(doc, ttf, false);
            ThaiGlyphRun run = new ThaiGlyphRun(ttf2);
            PDPage page = doc.getPage(0);
            PDResources res = page.getResources();
            COSName f = res.add(font);
            StringBuilder ops = new StringBuilder("q 0 g\n");
            for (int i = 0; i < at.size(); i++) {
                ops.append(run.operators(f.getName(), at.get(i)[2], values.get(i), at.get(i)[0], at.get(i)[1]));
            }
            ops.append("Q\n");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                cs.appendRawCommands(ops.toString());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Fraction of differing pixels inside a crop (PDF points, origin bottom-left). */
    private static double diff(byte[] a, byte[] b, Rectangle2D.Float r, String name) throws IOException {
        float dpi = 200, s = dpi / 72f;
        try (PDDocument da = Loader.loadPDF(a); PDDocument db = Loader.loadPDF(b)) {
            float h = da.getPage(0).getMediaBox().getHeight();
            BufferedImage ia = new PDFRenderer(da).renderImageWithDPI(0, dpi);
            BufferedImage ib = new PDFRenderer(db).renderImageWithDPI(0, dpi);
            int x0 = Math.round(r.x * s), y0 = Math.round((h - r.y - r.height) * s);
            int w = Math.round(r.width * s), hh = Math.round(r.height * s);
            BufferedImage side = new BufferedImage(w, hh * 2 + 4, BufferedImage.TYPE_INT_RGB);
            int differ = 0, ink = 0;
            for (int y = 0; y < hh; y++) {
                for (int x = 0; x < w; x++) {
                    int pa = gray(ia.getRGB(x0 + x, y0 + y)), pb = gray(ib.getRGB(x0 + x, y0 + y));
                    if (pa < 128 || pb < 128) {
                        ink++;
                        if (Math.abs(pa - pb) > 96) {
                            differ++;
                        }
                    }
                    side.setRGB(x, y, ia.getRGB(x0 + x, y0 + y));
                    side.setRGB(x, y + hh + 4, ib.getRGB(x0 + x, y0 + y));
                }
            }
            ImageIO.write(side, "png", OUT.resolve(name + ".png").toFile());
            return ink == 0 ? 0 : (double) differ / ink;
        }
    }

    /** Pixels that differ between two page renders, ignoring the masked areas (PDF points). */
    private static int outsideDiff(byte[] a, byte[] b, List<Rectangle2D.Float> masks) throws IOException {
        float dpi = 100, s = dpi / 72f;
        try (PDDocument da = Loader.loadPDF(a); PDDocument db = Loader.loadPDF(b)) {
            float h = da.getPage(0).getMediaBox().getHeight();
            int total = 0;
            for (int p = 0; p < Math.min(da.getNumberOfPages(), db.getNumberOfPages()); p++) {
                BufferedImage ia = new PDFRenderer(da).renderImageWithDPI(p, dpi);
                BufferedImage ib = new PDFRenderer(db).renderImageWithDPI(p, dpi);
                for (int y = 0; y < Math.min(ia.getHeight(), ib.getHeight()); y++) {
                    for (int x = 0; x < Math.min(ia.getWidth(), ib.getWidth()); x++) {
                        float px = x / s, py = h - y / s;
                        final int page = p;
                        if (page == 0 && masks.stream().anyMatch(m -> m.contains(px, py))) {
                            continue;
                        }
                        if (Math.abs(gray(ia.getRGB(x, y)) - gray(ib.getRGB(x, y))) > 64) {
                            total++;
                        }
                    }
                }
            }
            return total;
        }
    }

    private static int gray(int rgb) {
        return (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
    }

    /**
     * Wraps each {@code @@OVL_name@@} token in the DOCX in a bookmark {@code ovl_name}
     * around {@code reserve} non-breaking spaces, keeping the token run's formatting.
     */
    private static byte[] bookmark(byte[] docx, java.util.Map<String, Integer> reserve) throws IOException {
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docx));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(out);
        java.util.zip.ZipEntry e;
        int id = 9100;
        while ((e = zin.getNextEntry()) != null) {
            byte[] data = zin.readAllBytes();
            if (e.getName().equals("word/document.xml")) {
                String xml = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                for (var r : reserve.entrySet()) {
                    String token = "@@OVL_" + r.getKey() + "@@";
                    int at = xml.indexOf(token);
                    if (at < 0) {
                        throw new IllegalStateException("token not in document: " + token);
                    }
                    int runStart = xml.lastIndexOf("<w:r>", at);
                    int runStart2 = xml.lastIndexOf("<w:r ", at);
                    runStart = Math.max(runStart, runStart2);
                    int rprS = xml.indexOf("<w:rPr>", runStart);
                    String rpr = rprS >= 0 && rprS < at ? xml.substring(rprS, xml.indexOf("</w:rPr>", rprS) + 8) : "";
                    String inject = "</w:t></w:r><w:bookmarkStart w:id=\"" + id + "\" w:name=\"ovl" + r.getKey().replace("_", "")
                            + "\"/><w:r>" + rpr + "<w:t xml:space=\"preserve\">"
                            + "\u00A0".repeat(r.getValue())
                            + "</w:t></w:r><w:bookmarkEnd w:id=\"" + id + "\"/><w:r>" + rpr + "<w:t xml:space=\"preserve\">";
                    // Splitting the text element must not let Word trim the space before the token.
                    int tStart = xml.lastIndexOf("<w:t", at);
                    int tEnd = xml.indexOf('>', tStart);
                    String open = xml.substring(tStart, tEnd + 1);
                    String fixed = open.contains("xml:space") ? open : "<w:t xml:space=\"preserve\">";
                    xml = xml.substring(0, tStart) + fixed + xml.substring(tEnd + 1, at) + inject
                            + xml.substring(at + token.length());
                    id++;
                }
                data = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            zout.putNextEntry(new java.util.zip.ZipEntry(e.getName()));
            zout.write(data);
            zout.closeEntry();
        }
        zout.close();
        return out.toByteArray();
    }

    private static byte[] sofficeWithDestinations(byte[] docx) throws Exception {
        Path dir = Files.createTempDirectory("spike-lo");
        Path in = dir.resolve("doc.docx");
        Files.write(in, docx);
        Process p = new ProcessBuilder("/Applications/LibreOffice.app/Contents/MacOS/soffice", "--headless",
                "-env:UserInstallation=file://" + dir.resolve("profile"),
                "--convert-to", "pdf:writer_pdf_Export:{\"ExportBookmarksToPDFDestination\":{\"type\":\"boolean\",\"value\":\"true\"}}",
                "--outdir", dir.toString(), in.toString()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        p.waitFor();
        return Files.readAllBytes(dir.resolve("doc.pdf"));
    }

    /** Named destinations: name -> [pageIndex, left, top]. */
    private static java.util.Map<String, float[]> destinations(byte[] pdf) throws IOException {
        java.util.Map<String, float[]> m = new java.util.TreeMap<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            var names = doc.getDocumentCatalog().getNames();
            java.util.Map<String, org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination> all = new java.util.HashMap<>();
            if (names != null && names.getDests() != null) {
                collect(names.getDests(), all);
            }
            var dests = doc.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.DESTS);
            if (dests != null) {
                for (COSName k : dests.keySet()) {
                    var d = org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination.create(dests.getDictionaryObject(k));
                    if (d instanceof org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination pd) {
                        all.put(k.getName(), pd);
                    }
                }
            }
            for (var e : all.entrySet()) {
                if (e.getValue() instanceof org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination x) {
                    m.put(e.getKey(), new float[] { x.retrievePageNumber(), x.getLeft(), x.getTop() });
                } else {
                    m.put(e.getKey(), new float[] { e.getValue().retrievePageNumber(), Float.NaN, Float.NaN });
                }
            }
        }
        return m;
    }

    private static void collect(org.apache.pdfbox.pdmodel.common.PDNameTreeNode<org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination> node,
            java.util.Map<String, org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination> into) throws IOException {
        if (node.getNames() != null) {
            into.putAll(node.getNames());
        }
        if (node.getKids() != null) {
            for (var k : node.getKids()) {
                collect(k, into);
            }
        }
    }

    @Test
    void bookmarksGivePositionsWithoutTouchingLayout() throws Exception {
        Files.createDirectories(OUT);
        DocumentGenerationService gen = new DocumentGenerationService();
        StampedSignature sigBlank = new StampedSignature("applicant_name", png(false), 340, 110);
        java.util.Map<String, Integer> reserve = new java.util.LinkedHashMap<>();
        reserve.put("memo_no", 20);
        reserve.put("date", 20);
        byte[] docx = bookmark(gen.generateSignedDocx(1, json("@@OVL_memo_no@@", "@@OVL_date@@"), List.of(sigBlank)), reserve);
        long t0 = System.currentTimeMillis();
        byte[] base = sofficeWithDestinations(docx);
        long t1 = System.currentTimeMillis();
        Files.write(OUT.resolve("doc1-base-bookmarked.pdf"), base);
        var dests = destinations(base);
        StringBuilder r = new StringBuilder("bookmark render ms " + (t1 - t0) + "\n");
        dests.forEach((k, v) -> r.append(String.format(Locale.ROOT, "dest %s page=%d left=%.2f top=%.2f%n", k, (int) v[0], v[1], v[2])));

        // Where would LibreOffice put each value? Same bookmarked layout, value typed before the reserve.
        java.util.Map<String, String> values = java.util.Map.of("memo_no", MEMO, "date", DATE);
        ThaiGlyphRun run;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fonts/th-sarabun/THSarabunNew.ttf")) {
            run = new ThaiGlyphRun(in);
        }
        for (String field : reserve.keySet()) {
            String memo = field.equals("memo_no") ? MEMO + "@@OVL_memo_no@@" : "@@OVL_memo_no@@";
            String date = field.equals("date") ? DATE + "@@OVL_date@@" : "@@OVL_date@@";
            java.util.Map<String, Integer> rr = new java.util.LinkedHashMap<>(reserve);
            // keep total width equal: the value replaces part of the reserve is not exact, so shorten generously
            byte[] ref = sofficeWithDestinations(bookmark(gen.generateSignedDocx(1, json(memo, date), List.of(sigBlank)), rr));
            float[] d = dests.get("ovl" + field.replace("_", ""));
            // baseline = top - ascent(size 16): calibrate from the probe marker
            byte[] probe = sofficeWithDestinations(bookmark(gen.generateSignedDocx(1,
                    json(field.equals("memo_no") ? "QQ@@OVL_memo_no@@" : "@@OVL_memo_no@@",
                         field.equals("date") ? "QQ@@OVL_date@@" : "@@OVL_date@@"), List.of(sigBlank)), rr));
            float[] q = glyphs(probe).find("QQ");
            r.append(String.format(Locale.ROOT, "%s: dest left=%.2f top=%.2f | glyph x=%.2f baseline=%.2f size=%.1f | top-baseline=%.2f%n",
                    field, d[1], d[2], q[0], q[1], q[2], d[2] - q[1]));
            byte[] over = overlay(base, List.of(new float[] { d[1], q[1], q[2] }), List.of(values.get(field)));
            Rectangle2D.Float box = new Rectangle2D.Float(d[1] - 1, q[1] - 7, run.width(values.get(field), q[2]) + 2, 24);
            r.append(String.format(Locale.ROOT, "   overlay vs LibreOffice ink diff %.3f%n",
                    diff(over, ref, box, "bm-" + field)));
        }
        Files.writeString(OUT.resolve("bookmark-report.txt"), r.toString());
        System.out.println(r);
    }

    @Test
    void lateValuesAndSignaturesCanBeOverlaid() throws Exception {
        Files.createDirectories(OUT);
        DocumentGenerationService gen = new DocumentGenerationService();
        assertThat(gen.isPdfConversionAvailable()).as("LibreOffice").isTrue();

        // The base reserves a fixed width for memo_no with non-breaking spaces: without
        // it the tab stops after memo_no move the "วันที่" label with the memo's length.
        String reserve = "\u00A0".repeat(48);
        StampedSignature sigBlank = new StampedSignature("applicant_name", png(false), 340, 110);
        long t0 = System.currentTimeMillis();
        byte[] base = gen.convertDocxToPdf(gen.generateSignedDocx(1, json(reserve, ""), List.of(sigBlank)));
        long t1 = System.currentTimeMillis();
        byte[] probe = gen.convertDocxToPdf(gen.generateSignedDocx(1, json("QQMEMO" + reserve, "QQDATE"), List.of(sigBlank)));
        // LibreOffice's own rendering of each value, in the same reserved layout.
        byte[] refMemo = gen.convertDocxToPdf(gen.generateSignedDocx(1, json(MEMO + reserve, ""), List.of(sigBlank)));
        byte[] refDate = gen.convertDocxToPdf(gen.generateSignedDocx(1, json(reserve, DATE), List.of(sigBlank)));
        byte[] fin = gen.convertDocxToPdf(gen.generateSignedDocx(1, json(MEMO, DATE),
                List.of(new StampedSignature("applicant_name", png(true), 340, 110))));
        Files.write(OUT.resolve("doc1-base.pdf"), base);
        Files.write(OUT.resolve("doc1-probe.pdf"), probe);
        Files.write(OUT.resolve("doc1-final-today.pdf"), fin);

        Glyphs gb = glyphs(base), gp = glyphs(probe), gdate = glyphs(refDate);
        StringBuilder r = new StringBuilder();
        r.append("render ms (one LibreOffice conversion): ").append(t1 - t0).append('\n');
        r.append("pages base/probe/refMemo/refDate: ").append(gb.pages()).append('/').append(gp.pages()).append('/')
                .append(glyphs(refMemo).pages()).append('/').append(gdate.pages()).append('\n');

        float[] pm = gp.find("QQMEMO"), pd = gp.find("QQDATE");
        r.append(String.format(Locale.ROOT, "memo probe x=%.2f baseline=%.2f size=%.1f%n", pm[0], pm[1], pm[2]));
        r.append(String.format(Locale.ROOT, "date probe x=%.2f baseline=%.2f size=%.1f%n", pd[0], pd[1], pd[2]));
        r.append("images base : ").append(images(base)).append('\n');
        r.append("images today: ").append(images(fin)).append('\n');

        ThaiGlyphRun run;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fonts/th-sarabun/THSarabunNew.ttf")) {
            run = new ThaiGlyphRun(in);
        }
        Rectangle2D.Float memoBox = new Rectangle2D.Float(pm[0] - 1, pm[1] - 7, run.width(MEMO, pm[2]) + 2, 24);
        Rectangle2D.Float dateBox = new Rectangle2D.Float(pd[0] - 1, pd[1] - 7, run.width(DATE, pd[2]) + 2, 24);

        // S1: filling a reserved field changes nothing outside it.
        r.append(String.format(Locale.ROOT, "S1 pixels differing outside the memo box (base vs refMemo): %d%n",
                outsideDiff(base, refMemo, List.of(memoBox))));
        r.append(String.format(Locale.ROOT, "S1 pixels differing outside the date box (base vs refDate): %d%n",
                outsideDiff(base, refDate, List.of(dateBox))));

        byte[] over = overlay(base, List.of(pm, pd), List.of(MEMO, DATE));
        Files.write(OUT.resolve("doc1-overlay.pdf"), over);
        double dm = diff(over, refMemo, memoBox, "doc1-memo-overlay-vs-libreoffice");
        double dd = diff(over, refDate, dateBox, "doc1-date-overlay-vs-libreoffice");
        r.append(String.format(Locale.ROOT, "S2 differing ink pixels: memo %.3f date %.3f%n", dm, dd));
        Files.writeString(OUT.resolve("layout-report.txt"), r.toString());
        System.out.println(r);
    }

    /**
     * Reserved areas are runs of non-breaking spaces of a length unique to each field.
     * Returns run length -> [x, baseline, size, width]; a length seen twice is an error.
     */
    private static java.util.Map<Integer, float[]> markers(byte[] pdf) throws IOException {
        Glyphs g = glyphs(pdf);
        java.util.Map<Integer, float[]> m = new java.util.TreeMap<>();
        List<TextPosition> all = g.all();
        for (int i = 0; i < all.size(); i++) {
            if (!all.get(i).getUnicode().equals("\u00A0")) {
                continue;
            }
            int k = i;
            while (k < all.size() && all.get(k).getUnicode().equals("\u00A0")
                    && Math.abs(all.get(k).getYDirAdj() - all.get(i).getYDirAdj()) < 0.1
                    && (k == i || all.get(k).getXDirAdj() - (all.get(k - 1).getXDirAdj() + all.get(k - 1).getWidthDirAdj()) < 0.2)) {
                k++;
            }
            TextPosition first = all.get(i), last = all.get(k - 1);
            if (k - i >= 8) {
                float[] prev = m.put(k - i, new float[] { first.getXDirAdj(), g.pageHeight() - first.getYDirAdj(),
                        first.getFontSizeInPt(), last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj() });
                if (prev != null) {
                    throw new IllegalStateException("two reserved runs of length " + (k - i));
                }
            }
            i = k - 1;
        }
        return m;
    }

    @Test
    void zeroWidthMarkersGiveExactPositions() throws Exception {
        Files.createDirectories(OUT);
        DocumentGenerationService gen = new DocumentGenerationService();
        StampedSignature sigBlank = new StampedSignature("applicant_name", png(false), 340, 110);
        java.util.Map<String, Integer> reserve = new java.util.LinkedHashMap<>();
        reserve.put("memo_no", 32);
        reserve.put("date", 25);
        long t0 = System.currentTimeMillis();
        byte[] base = gen.convertDocxToPdf(bookmark(
                gen.generateSignedDocx(1, json("@@OVL_memo_no@@", "@@OVL_date@@"), List.of(sigBlank)), reserve));
        long t1 = System.currentTimeMillis();
        Files.write(OUT.resolve("doc1-base-markers.pdf"), base);
        var at = markers(base);
        if (at.size() < 2) {
            throw new IllegalStateException("markers found: " + at.keySet());
        }
        StringBuilder r = new StringBuilder("base render ms " + (t1 - t0) + "\n");
        at.forEach((k, v) -> r.append(String.format(Locale.ROOT, "field %d: x=%.2f baseline=%.2f size=%.1f reserved width=%.2f%n",
                k, v[0], v[1], v[2], v[3])));

        ThaiGlyphRun run;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fonts/th-sarabun/THSarabunNew.ttf")) {
            run = new ThaiGlyphRun(in);
        }
        // LibreOffice's own rendering of each value at the same place.
        byte[] refMemo = gen.convertDocxToPdf(gen.generateSignedDocx(1, json(MEMO, "\u00A0".repeat(25)), List.of(sigBlank)));
        byte[] refDate = gen.convertDocxToPdf(gen.generateSignedDocx(1,
                json("\u00A0".repeat(32), DATE), List.of(sigBlank)));
        float[] m = at.get(32), d = at.get(25);
        byte[] over = overlay(base, List.of(new float[] { m[0], m[1], m[2] }, new float[] { d[0], d[1], d[2] }),
                List.of(MEMO, DATE));
        Files.write(OUT.resolve("doc1-overlay-markers.pdf"), over);
        r.append(String.format(Locale.ROOT, "memo width %.2f of %.2f reserved; date width %.2f of %.2f%n",
                run.width(MEMO, m[2]), m[3], run.width(DATE, d[2]), d[3]));
        r.append(String.format(Locale.ROOT, "S2 overlay vs LibreOffice ink diff: memo %.3f date %.3f%n",
                diff(over, refMemo, new Rectangle2D.Float(m[0] - 1, m[1] - 7, run.width(MEMO, m[2]) + 2, 24), "mk-memo"),
                diff(over, refDate, new Rectangle2D.Float(d[0] - 1, d[1] - 7, run.width(DATE, d[2]) + 2, 24), "mk-date")));
        r.append("images base: ").append(images(base)).append('\n');
        r.append(String.format(Locale.ROOT, "S1 pixels differing outside the field (base vs LibreOffice filled): memo %d date %d%n",
                outsideDiff(base, refMemo, List.of(new Rectangle2D.Float(m[0] - 1, m[1] - 7, m[3] + 2, 24))),
                outsideDiff(base, refDate, List.of(new Rectangle2D.Float(d[0] - 1, d[1] - 7, d[3] + 2, 24)))));
        Files.writeString(OUT.resolve("marker-report.txt"), r.toString());
        System.out.println(r);
    }
}
