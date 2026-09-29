package com.ecom.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.ecom.support.TestCertificates;


/**
 * SPIKE S4/S5: the incremental signing chain Phase 2 depends on.
 *
 * <p>base (fields pre-created) → applicant signs + certifies (DocMDP P=2, FieldMDP on
 * own fields) → office fills memo_no → head signs → office fills date → office locks.
 * Plus two tampered variants that must be detected. Output lands in target/spike for
 * Acrobat. Run with {@code -Dspike=true}.
 */
@EnabledIfSystemProperty(named = "spike", matches = "true")
class PadesChainSpikeTest {

    private static final Path OUT = Path.of("target", "spike");
    private static final String FONT = "fonts/th-sarabun/THSarabunNew.ttf";
    private static final COSName F1 = COSName.getPDFName("F1");
    private static final float SIZE = 16f;

    private ThaiGlyphRun thai() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(FONT)) {
            return new ThaiGlyphRun(in);
        }
    }

    // ------------------------------------------------------------------ base

    private record FieldSpec(String name, float x, float y, float w, float h, boolean signature) {
    }

    private static final List<FieldSpec> FIELDS = List.of(
            new FieldSpec("memo_no", 110, 760, 220, 22, false),
            new FieldSpec("date", 380, 760, 170, 22, false),
            new FieldSpec("sig_applicant", 70, 520, 170, 55, true),
            new FieldSpec("date_applicant", 70, 492, 170, 22, false),
            new FieldSpec("sig_head", 330, 520, 170, 55, true),
            new FieldSpec("date_head", 330, 492, 170, 22, false),
            new FieldSpec("comment_head", 70, 420, 460, 22, false),
            new FieldSpec("lock", 0, 0, 0, 0, true));

    private byte[] base() throws Exception {
        ThaiGlyphRun thai = thai();
        try (PDDocument doc = new PDDocument(); InputStream ttf = getClass().getClassLoader().getResourceAsStream(FONT)) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = PDType0Font.load(doc, ttf, false); // whole font: CID = GID

            PDResources pageRes = new PDResources();
            pageRes.put(F1, font);
            page.setResources(pageRes);
            String body = thai.operators("F1", 20, "บันทึกข้อความ", 240, 800)
                    + thai.operators("F1", SIZE, "ที่", 70, 765)
                    + thai.operators("F1", SIZE, "วันที่", 340, 765)
                    + thai.operators("F1", SIZE, "เรื่อง ขอรับการประเมินผลการสอน ผู้ใหญ่ นี้ ปิ่น ญี่ปุ่น น้ำ ฤๅษี ๑๒๓", 70, 720)
                    + thai.operators("F1", SIZE, "(ผู้ยื่นคำร้อง)", 110, 470)
                    + thai.operators("F1", SIZE, "(หัวหน้าสาขา)", 375, 470)
                    + thai.operators("F1", SIZE, "ความเห็น", 70, 445);
            PDStream contents = new PDStream(doc);
            try (var os = contents.createOutputStream(COSName.FLATE_DECODE)) {
                os.write(body.getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(contents);

            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources dr = new PDResources();
            dr.put(F1, font);
            form.setDefaultResources(dr);
            form.setDefaultAppearance("/F1 16 Tf 0 g");
            form.setNeedAppearances(false);

            for (FieldSpec f : FIELDS) {
                PDField field;
                if (f.signature()) {
                    field = new PDSignatureField(form);
                } else {
                    PDTextField t = new PDTextField(form);
                    t.setDefaultAppearance("/F1 16 Tf 0 g");
                    t.setReadOnly(true);
                    field = t;
                }
                field.setPartialName(f.name());
                PDAnnotationWidget w = field.getWidgets().get(0);
                w.setRectangle(new PDRectangle(f.x(), f.y(), f.w(), f.h()));
                w.setPage(page);
                w.setPrinted(true);
                page.getAnnotations().add(w);
                form.getFields().add(field);
            }
            // Each signer's lock is part of the base: creating it at signing time is an
            // annotation change, which DocMDP P=2 does not allow.
            form.getField("sig_applicant").getCOSObject().setItem(COSName.getPDFName("Lock"),
                    lockDict("Include", List.of("sig_applicant", "date_applicant"), null));
            form.getField("sig_head").getCOSObject().setItem(COSName.getPDFName("Lock"),
                    lockDict("Include", List.of("sig_head", "date_head", "comment_head"), null));
            // The final lock: whatever signs this field freezes the whole document.
            COSDictionary lock = new COSDictionary();
            lock.setItem(COSName.TYPE, COSName.getPDFName("SigFieldLock"));
            lock.setItem(COSName.getPDFName("Action"), COSName.getPDFName("All"));
            lock.setInt(COSName.P, 1);
            form.getField("lock").getCOSObject().setItem(COSName.getPDFName("Lock"), lock);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ fills

    private void fillField(PDDocument doc, PDAcroForm form, String name, String value) throws Exception {
        ThaiGlyphRun thai = thai();
        PDField field = form.getField(name);
        PDAnnotationWidget w = field.getWidgets().get(0);
        PDRectangle r = w.getRectangle();

        PDAppearanceStream ap = new PDAppearanceStream(doc);
        ap.setBBox(new PDRectangle(r.getWidth(), r.getHeight()));
        PDResources res = new PDResources();
        // Same font object as /DR — a later revision must never embed a new subset.
        COSDictionary drFonts = form.getDefaultResources().getCOSObject().getCOSDictionary(COSName.FONT);
        COSDictionary fonts = new COSDictionary();
        fonts.setItem(F1, drFonts.getItem(F1));
        res.getCOSObject().setItem(COSName.FONT, fonts);
        ap.setResources(res);
        String ops = "/Tx BMC\n" + thai.operators("F1", SIZE, value, 2, 6) + "EMC\n";
        try (var os = ap.getContentStream().createOutputStream(COSName.FLATE_DECODE)) {
            os.write(ops.getBytes(StandardCharsets.US_ASCII));
        }
        PDAppearanceDictionary apd = new PDAppearanceDictionary();
        apd.setNormalAppearance(ap);
        w.setAppearance(apd);
        field.getCOSObject().setItem(COSName.V, new COSString(value));

        field.getCOSObject().setNeedToBeUpdated(true);
        w.getCOSObject().setNeedToBeUpdated(true);
    }

    private byte[] fill(byte[] pdf, Map<String, String> values) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            for (var e : values.entrySet()) {
                fillField(doc, form, e.getKey(), e.getValue());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.saveIncremental(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ signing

    private static COSDictionary fieldMdp(String action, List<String> fields, COSDictionary catalog) {
        COSDictionary params = new COSDictionary();
        params.setItem(COSName.TYPE, COSName.getPDFName("TransformParams"));
        params.setItem(COSName.getPDFName("Action"), COSName.getPDFName(action));
        if (fields != null) {
            COSArray arr = new COSArray();
            fields.forEach(f -> arr.add(new COSString(f)));
            params.setItem(COSName.FIELDS, arr);
        }
        params.setName(COSName.V, "1.2");
        COSDictionary ref = new COSDictionary();
        ref.setItem(COSName.TYPE, COSName.getPDFName("SigRef"));
        ref.setItem(COSName.getPDFName("TransformMethod"), COSName.getPDFName("FieldMDP"));
        ref.setItem(COSName.getPDFName("TransformParams"), params);
        ref.setItem(COSName.getPDFName("Data"), catalog);
        return ref;
    }

    private static COSDictionary docMdp(int p) {
        COSDictionary params = new COSDictionary();
        params.setItem(COSName.TYPE, COSName.getPDFName("TransformParams"));
        params.setInt(COSName.P, p);
        params.setName(COSName.V, "1.2");
        COSDictionary ref = new COSDictionary();
        ref.setItem(COSName.TYPE, COSName.getPDFName("SigRef"));
        ref.setItem(COSName.getPDFName("TransformMethod"), COSName.DOCMDP);
        ref.setItem(COSName.getPDFName("TransformParams"), params);
        return ref;
    }

    private static COSDictionary lockDict(String action, List<String> fields, Integer p) {
        COSDictionary lock = new COSDictionary();
        lock.setItem(COSName.TYPE, COSName.getPDFName("SigFieldLock"));
        lock.setItem(COSName.getPDFName("Action"), COSName.getPDFName(action));
        if (fields != null) {
            COSArray arr = new COSArray();
            fields.forEach(f -> arr.add(new COSString(f)));
            lock.setItem(COSName.FIELDS, arr);
        }
        if (p != null) {
            lock.setInt(COSName.P, p);
        }
        return lock;
    }

    private PDAppearanceDictionary signatureAppearance(PDDocument doc, PDRectangle r) throws Exception {
        BufferedImage img = new BufferedImage(340, 110, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(20, 40, 140));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int[] xs = { 20, 70, 110, 150, 190, 230, 280, 320 };
        int[] ys = { 80, 30, 90, 25, 85, 35, 70, 40 };
        g.drawPolyline(xs, ys, xs.length);
        g.dispose();

        PDImageXObject image = LosslessFactory.createFromImage(doc, img);
        PDAppearanceStream ap = new PDAppearanceStream(doc);
        ap.setBBox(new PDRectangle(r.getWidth(), r.getHeight()));
        PDResources res = new PDResources();
        COSName im = res.add(image);
        ap.setResources(res);
        String ops = "q " + r.getWidth() + " 0 0 " + r.getHeight() + " 0 0 cm /" + im.getName() + " Do Q\n";
        try (var os = ap.getContentStream().createOutputStream(COSName.FLATE_DECODE)) {
            os.write(ops.getBytes(StandardCharsets.US_ASCII));
        }
        PDAppearanceDictionary apd = new PDAppearanceDictionary();
        apd.setNormalAppearance(ap);
        return apd;
    }

    /**
     * One signer's revision: their own fields are filled, then their existing signature
     * field is signed, locking {@code locked} (FieldMDP Include) or everything.
     */
    private byte[] sign(byte[] pdf, SpikeCms signer, String sigField, Map<String, String> ownFields,
            List<String> locked, boolean certify, boolean lockAll, boolean visible) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            for (var e : ownFields.entrySet()) {
                fillField(doc, form, e.getKey(), e.getValue());
            }
            PDSignatureField sf = (PDSignatureField) form.getField(sigField);
            PDAnnotationWidget w = sf.getWidgets().get(0);
            PDRectangle rect = w.getRectangle();

            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(COSName.getPDFName("ETSI.CAdES.detached"));
            sig.setName(signer.cert.getSubjectX500Principal().getName());
            sig.setReason("ลงนามในช่อง " + sigField);
            sig.setLocation("มหาวิทยาลัยขอนแก่น");
            sig.setSignDate(Calendar.getInstance(TimeZone.getTimeZone("Asia/Bangkok")));

            COSDictionary catalog = doc.getDocumentCatalog().getCOSObject();
            COSArray refs = new COSArray();
            if (certify) {
                refs.add(docMdp(2));
                COSDictionary perms = new COSDictionary();
                perms.setItem(COSName.DOCMDP, sig);
                catalog.setItem(COSName.PERMS, perms);
                catalog.setNeedToBeUpdated(true);
            }
            if (lockAll) {
                refs.add(fieldMdp("All", null, catalog));
            } else {
                refs.add(fieldMdp("Include", locked, catalog));
            }
            sig.getCOSObject().setItem(COSName.getPDFName("Reference"), refs);
            sf.getCOSObject().setItem(COSName.V, sig);

            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(32 * 1024);
            doc.addSignature(sig, signer, options);

            // addSignature() blanks the widget of a field it finds without a visual
            // template; put back the pre-created rectangle and draw our own appearance.
            if (visible) {
                w.setRectangle(rect);
                w.setAppearance(signatureAppearance(doc, rect));
            }
            w.getCOSObject().setNeedToBeUpdated(true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.saveIncremental(out);
            options.close();
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ checks

    /** DSS in a child JVM with BouncyCastle 1.80 (see {@link DssRunner}). */
    private static String dss(Path pdf, List<Path> trusted) throws Exception {
        String bc80 = System.getProperty("user.home") + "/.m2/repository/org/bouncycastle/";
        List<String> cp = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (!e.contains("/org/bouncycastle/")) {
                cp.add(e);
            }
        }
        for (String a : List.of("bcprov", "bcpkix", "bcutil")) {
            cp.add(bc80 + a + "-jdk18on/1.80/" + a + "-jdk18on-1.80.jar");
        }
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", String.join(java.io.File.pathSeparator, cp), DssRunner.class.getName(), pdf.toString()));
        trusted.forEach(t -> cmd.add(t.toString()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        StringBuilder sb = new StringBuilder();
        for (String l : out.split("\n")) {
            if (l.startsWith("  ") || l.contains("Exception")) {
                sb.append(l).append('\n');
            }
        }
        return sb.toString();
    }

    private static String pdfsig(Path file) {
        try {
            Process p = new ProcessBuilder("/opt/homebrew/bin/pdfsig", "-nocert", file.toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            StringBuilder sb = new StringBuilder();
            for (String l : out.split("\n")) {
                if (l.startsWith("Signature #") || l.contains("Signature Validation") || l.contains("Total document signed")
                        || l.contains("Signature Field Name")) {
                    sb.append("  ").append(l.trim()).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "  pdfsig failed: " + e;
        }
    }

    private static void assertPrefix(byte[] earlier, byte[] later) {
        assertThat(later.length).isGreaterThan(earlier.length);
        assertThat(Arrays.equals(earlier, 0, earlier.length, later, 0, earlier.length))
                .as("a revision must be a byte-exact prefix of the next").isTrue();
    }

    @Test
    void incrementalChain() throws Exception {
        Files.createDirectories(OUT);
        SpikeCms applicant = new SpikeCms(TestCertificates.validP12("ผู้ยื่น ทดสอบ"), TestCertificates.PIN);
        SpikeCms head = new SpikeCms(TestCertificates.validP12("หัวหน้า ทดสอบ"), TestCertificates.PIN);
        SpikeCms office = new SpikeCms(TestCertificates.validP12("เจ้าหน้าที่ ทดสอบ"), TestCertificates.PIN);
        List<Path> trusted = new ArrayList<>();
        for (SpikeCms c : List.of(applicant, head, office)) {
            Path der = OUT.resolve("trust-" + trusted.size() + ".cer");
            Files.write(der, c.cert.getEncoded());
            trusted.add(der);
        }

        Map<String, byte[]> revs = new LinkedHashMap<>();
        byte[] r0 = base();
        byte[] r1 = sign(r0, applicant, "sig_applicant", Map.of("date_applicant", "29 กันยายน 2569"),
                List.of("sig_applicant", "date_applicant"), true, false, true);
        byte[] r2 = fill(r1, Map.of("memo_no", "อว 660301.26.8/1234"));
        revs.put("r2b-office-corrects-memo", fill(r2, Map.of("memo_no", "อว 660301.26.8/1235")));
        byte[] r3 = sign(r2, head, "sig_head",
                Map.of("date_head", "30 กันยายน 2569", "comment_head", "เห็นควรอนุมัติ ผู้ใหญ่ นี้ ปิ่น น้ำ"),
                List.of("sig_head", "date_head", "comment_head"), false, false, true);
        byte[] r4 = fill(r3, Map.of("date", "1 ตุลาคม 2569"));
        byte[] r5 = sign(r4, office, "lock", Map.of(), null, false, true, false);
        revs.put("r0-base", r0);
        revs.put("r1-applicant-certify", r1);
        revs.put("r2-office-memo", r2);
        revs.put("r3-head", r3);
        revs.put("r4-office-date", r4);
        revs.put("r5-locked", r5);
        // Tampering that must be caught.
        revs.put("x1-edit-applicant-date-after-sign", fill(r3, Map.of("date_applicant", "1 มกราคม 2500")));
        revs.put("x2-edit-memo-after-lock", fill(r5, Map.of("memo_no", "อว 999/9999")));

        byte[][] chain = { r0, r1, r2, r3, r4, r5 };
        for (int i = 1; i < chain.length; i++) {
            assertPrefix(chain[i - 1], chain[i]);
        }

        StringBuilder report = new StringBuilder();
        for (var e : revs.entrySet()) {
            Path f = OUT.resolve(e.getKey() + ".pdf");
            Files.write(f, e.getValue());
            report.append("== ").append(e.getKey()).append(" (").append(e.getValue().length).append(" bytes)\n");
            report.append(" pdfsig:\n").append(pdfsig(f));
            if (!e.getKey().startsWith("r0")) {
                report.append(" DSS:\n");
                report.append(dss(f, trusted));
            }
        }
        // Rendering for eyes: the final page.
        try (PDDocument doc = Loader.loadPDF(r5)) {
            javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png",
                    OUT.resolve("r5-locked.png").toFile());
        }
        Files.writeString(OUT.resolve("report.txt"), report.toString());
        System.out.println(report);
    }

    /** S6: eight signers in turn on a ten-page document; each sign() must stay fast. */
    @Test
    void signingStaysFast() throws Exception {
        Files.createDirectories(OUT);
        ThaiGlyphRun thai = thai();
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); InputStream ttf = getClass().getClassLoader().getResourceAsStream(FONT)) {
            PDType0Font font = PDType0Font.load(doc, ttf, false);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources dr = new PDResources();
            dr.put(F1, font);
            form.setDefaultResources(dr);
            for (int p = 0; p < 10; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDResources res = new PDResources();
                res.put(F1, font);
                page.setResources(res);
                StringBuilder body = new StringBuilder();
                for (int l = 0; l < 30; l++) {
                    body.append(thai.operators("F1", 16, "ข้อความทดสอบหน้าที่ " + (p + 1) + " บรรทัด " + l + " ผู้ใหญ่ น้ำ ปิ่น", 60, 780 - l * 24));
                }
                PDStream c = new PDStream(doc);
                try (var os = c.createOutputStream(COSName.FLATE_DECODE)) {
                    os.write(body.toString().getBytes(StandardCharsets.US_ASCII));
                }
                page.setContents(c);
            }
            PDPage last = doc.getPage(9);
            for (int i = 0; i < 8; i++) {
                PDSignatureField sf = new PDSignatureField(form);
                sf.setPartialName("sig_" + i);
                PDAnnotationWidget w = sf.getWidgets().get(0);
                w.setRectangle(new PDRectangle(60 + (i % 4) * 130, 100 + (i / 4) * 80, 120, 50));
                w.setPage(last);
                w.setPrinted(true);
                last.getAnnotations().add(w);
                form.getFields().add(sf);
                sf.getCOSObject().setItem(COSName.getPDFName("Lock"), lockDict("Include", List.of("sig_" + i), null));
                PDTextField t = new PDTextField(form);
                t.setPartialName("date_" + i);
                t.setReadOnly(true);
                PDAnnotationWidget tw = t.getWidgets().get(0);
                tw.setRectangle(new PDRectangle(60 + (i % 4) * 130, 80 + (i / 4) * 80, 120, 20));
                tw.setPage(last);
                last.getAnnotations().add(tw);
                form.getFields().add(t);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdf = out.toByteArray();
        }
        StringBuilder r = new StringBuilder("S6 sign() ms per signer on 10 pages:");
        for (int i = 0; i < 8; i++) {
            SpikeCms signer = new SpikeCms(TestCertificates.validP12("ผู้ลงนาม " + i), TestCertificates.PIN);
            long t0 = System.nanoTime();
            pdf = sign(pdf, signer, "sig_" + i, Map.of("date_" + i, "29 กันยายน 2569"),
                    List.of("sig_" + i, "date_" + i), i == 0, false, true);
            r.append(' ').append((System.nanoTime() - t0) / 1_000_000);
        }
        r.append(" | final size ").append(pdf.length / 1024).append(" KB\n");
        Files.write(OUT.resolve("s6-eight-signers.pdf"), pdf);
        Files.writeString(OUT.resolve("s6-report.txt"), r.toString());
        System.out.println(r);
    }
}
