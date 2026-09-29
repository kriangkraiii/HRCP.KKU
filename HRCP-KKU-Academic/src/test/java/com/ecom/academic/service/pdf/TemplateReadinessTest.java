package com.ecom.academic.service.pdf;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentFieldOwnership;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Which documents can be switched to incremental signing
 * ({@code app.esign.incremental-docs}).
 *
 * <p>For every template with signature places: build the base with sample values
 * and check that every reserved area and signature place is found, that the page
 * count matches the old rendering (reserves must not push anything onto another
 * page), and that typical late values fit their reserve. Writes
 * {@code target/incremental/template-readiness.txt}. Run with {@code -Dtemplates=true}.
 */
@EnabledIfSystemProperty(named = "templates", matches = "true")
class TemplateReadinessTest {

    private static final DocumentGenerationService GEN = new DocumentGenerationService();
    private static final ThaiText THAI = ThaiText.sarabun();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}");

    /** Typical long values of late fields, for the fit check. */
    private static String typical(String field) {
        if (field.contains("memo_no")) {
            return "อว 660301.26.8/12345";
        }
        if (field.contains("date")) {
            return "30 พฤศจิกายน 2569";
        }
        if (field.startsWith("chk")) {
            return PdfIncrementService.TICK;
        }
        if (field.contains("position")) {
            return "คณบดีวิทยาลัยการคอมพิวเตอร์";
        }
        if (field.contains("name")) {
            return "รองศาสตราจารย์ ดร.สมชาย ใจดี";
        }
        return "เห็นควรตามเสนอ";
    }

    private static Set<String> placeholders(SignatureModule module, int type) throws IOException {
        String path = "templates/docx/" + (module == SignatureModule.ACADEMIC ? "doc_" : "Phase2/p2doc_") + type + ".docx";
        Set<String> keys = new LinkedHashSet<>();
        try (InputStream in = TemplateReadinessTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ZipInputStream zin = new ZipInputStream(in);
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().startsWith("word/") && e.getName().endsWith(".xml")) {
                    String text = new String(zin.readAllBytes(), StandardCharsets.UTF_8).replaceAll("<[^>]+>", "");
                    Matcher m = PLACEHOLDER.matcher(text);
                    while (m.find()) {
                        keys.add(m.group(1));
                    }
                }
            }
        }
        return keys;
    }

    private static byte[] png(boolean visible) throws IOException {
        BufferedImage img = new BufferedImage(340, 110, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (visible) {
            g.setColor(new Color(20, 40, 140));
            g.setStroke(new BasicStroke(6));
            g.drawPolyline(new int[] { 20, 80, 130, 190, 250, 320 }, new int[] { 80, 25, 90, 20, 85, 40 }, 6);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] docx(SignatureModule module, int type, Map<String, String> values, List<StampedSignature> sigs)
            throws IOException {
        String json = new ObjectMapper().writeValueAsString(values);
        return module == SignatureModule.ACADEMIC ? GEN.generateSignedDocx(type, json, sigs)
                : GEN.generateSignedP2Docx(type, json, sigs);
    }

    private static int pages(byte[] pdf) throws IOException {
        try (var doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private static final PdfIncrementService PDF = new PdfIncrementService(THAI);

    /** Would {@code v} fit the box at some allowed size, wrapping if the box has lines? */
    private static boolean fits(String v, BasePdfBuilder.Box b) {
        for (float s = b.fontSize(); s >= Math.min(PdfIncrementService.MIN_TEXT_SIZE, b.fontSize()); s -= 0.5f) {
            if (b.lines() > 1) {
                List<String> lines = PDF.wrap(v, s, b.width() - 1);
                if (lines != null && lines.size() <= b.lines()) {
                    return true;
                }
            } else if (THAI.width(v, s) <= b.width() - 1) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyTemplate() throws Exception {
        assumeTrue(GEN.isPdfConversionAvailable(), "LibreOffice is not installed");
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "%-12s %-6s %-6s %s%n",
                "document", "ready", "pages", "details"));
        List<String> ready = new ArrayList<>();
        for (SignatureModule module : SignatureModule.values()) {
            for (int type = 1; type <= 12; type++) {
                List<SignatureSlot> slots = SignatureAnchorRegistry.slotsOf(module, type);
                Set<String> keys = placeholders(module, type);
                if (slots == null || slots.isEmpty() || keys == null) {
                    continue;
                }
                String doc = module + ":" + type;
                // Every placeholder gets a sample; late and signer fields get realistic values.
                Map<String, String> filled = new LinkedHashMap<>();
                for (String k : keys) {
                    filled.put(k, "ตัวอย่าง");
                }
                List<BasePdfBuilder.TextSpec> texts = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();
                for (String f : DocumentFieldOwnership.lateFields(module, type)) {
                    if (keys.contains(f) && seen.add(f)) {
                        texts.add(new BasePdfBuilder.TextSpec(f, IncrementalSigningService.reserveFor(f),
                                IncrementalSigningService.isTickField(f), IncrementalSigningService.linesFor(f)));
                    }
                }
                List<BasePdfBuilder.SlotSpec> slotSpecs = new ArrayList<>();
                for (SignatureSlot s : slots) {
                    List<String> own = IncrementalSigningService.ownFields(s);
                    for (String f : own) {
                        if (keys.contains(f) && seen.add(f)) {
                            texts.add(new BasePdfBuilder.TextSpec(f, IncrementalSigningService.reserveFor(f),
                                    IncrementalSigningService.tickFields(s).contains(f)));
                        }
                    }
                    slotSpecs.add(new BasePdfBuilder.SlotSpec(s.slotKey(), s.anchorPlaceholder(), own));
                }
                texts.forEach(t -> filled.put(t.field(), typical(t.field())));

                String details;
                boolean ok;
                int legacyPages = -1, basePages = -1;
                try {
                    List<StampedSignature> real = new ArrayList<>();
                    for (SignatureSlot s : slots) {
                        real.add(new StampedSignature(s.anchorPlaceholder(), png(true), 340, 110));
                    }
                    legacyPages = pages(GEN.convertDocxToPdf(docx(module, type, filled, real)));

                    final SignatureModule m = module;
                    final int t = type;
                    BasePdfBuilder.Result base = new BasePdfBuilder().build(new BasePdfBuilder.Renderer() {
                        @Override
                        public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures)
                                throws IOException {
                            Map<String, String> v = new LinkedHashMap<>(filled);
                            v.putAll(overrides);
                            return TemplateReadinessTest.docx(m, t, v, pictures.stream()
                                    .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height()))
                                    .toList());
                        }

                        @Override
                        public byte[] toPdf(byte[] docx) throws IOException {
                            return GEN.convertDocxToPdf(docx);
                        }
                    }, texts, slotSpecs);
                    basePages = pages(base.pdf());

                    List<String> problems = new ArrayList<>();
                    if (basePages != legacyPages) {
                        problems.add("page count " + legacyPages + " -> " + basePages);
                    }
                    for (BasePdfBuilder.Box b : base.layout().texts()) {
                        String v = typical(b.field());
                        boolean fits = PdfIncrementService.TICK.equals(v) || fits(v, b);
                        if (!fits) {
                            problems.add(b.field() + " too narrow (" + Math.round(b.width()) + "pt)");
                        }
                    }
                    ok = problems.isEmpty();
                    details = (ok ? "" : String.join("; ", problems) + " | ")
                            + base.layout().texts().size() + " fields, " + base.layout().signatures().size() + " signature places";
                    Files.createDirectories(Path.of("target", "incremental", "bases"));
                    Files.write(Path.of("target", "incremental", "bases", module + "-" + type + ".pdf"), base.pdf());
                } catch (BasePdfBuilder.BaseBuildException e) {
                    ok = false;
                    details = "cannot build: " + e.getMessage();
                } catch (Exception e) {
                    ok = false;
                    details = "error: " + e;
                }
                if (ok) {
                    ready.add(doc);
                }
                report.append(String.format(Locale.ROOT, "%-12s %-6s %-6s %s%n", doc, ok ? "yes" : "no",
                        legacyPages + "/" + basePages, details));
            }
        }
        report.append("\napp.esign.incremental-docs=").append(String.join(",", ready)).append('\n');
        Files.createDirectories(Path.of("target", "incremental"));
        Files.writeString(Path.of("target", "incremental", "template-readiness.txt"), report.toString());
        System.out.println(report);
    }
}
