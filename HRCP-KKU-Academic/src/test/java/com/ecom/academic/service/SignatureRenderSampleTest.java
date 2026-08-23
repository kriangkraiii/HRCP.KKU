package com.ecom.academic.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes signed sample documents to disk for a human to look at.
 *
 * <p>Not part of the normal build — the assertions in {@code SignatureStampingTest}
 * prove the file is structurally correct, but only eyes can confirm the signature
 * sits on the line, at a sensible size, without pushing the layout around. Run it
 * deliberately:
 *
 * <pre>
 *   ./mvnw test -Dtest=SignatureRenderSampleTest -Design.samples=/tmp/esign-samples
 * </pre>
 */
@EnabledIfSystemProperty(named = "esign.samples", matches = ".+")
class SignatureRenderSampleTest {

    private final DocumentGenerationService service = new DocumentGenerationService();
    private final ObjectMapper mapper = new ObjectMapper();

    /** A curved mark that reads as a signature, so the samples look realistic. */
    private static byte[] handwrittenPng() throws IOException {
        BufferedImage image = new BufferedImage(420, 130, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x11, 0x11, 0x11));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Float(20, 95, 120, 5, 210, 80));
        g.draw(new QuadCurve2D.Float(210, 80, 280, 120, 330, 40));
        g.draw(new QuadCurve2D.Float(120, 105, 250, 118, 400, 70));
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void writeSignedSamples() throws IOException {
        Path outDir = Path.of(System.getProperty("esign.samples"));
        Files.createDirectories(outDir);

        byte[] png = handwrittenPng();
        SignatureAnchorRegistry registry = new SignatureAnchorRegistry();

        // Realistic values so the signature sits next to a real printed name
        // rather than an empty pair of brackets.
        String json = mapper.writeValueAsString(Map.of(
                "title", "ผู้ช่วยศาสตราจารย์ ",
                "applicant_name", "เกรียงไกร ประเสริฐ",
                "dean_name", "สมชาย ใจดี",
                "department_head_name", "สุดา วงศ์ทอง",
                "department_head", "สุดา วงศ์ทอง",
                "hr_officer_name", "จิราภรณ์ หอมอ่อน",
                "committee_president_name", "วิชัย มั่นคง",
                "committee_1_name", "วิชัย มั่นคง",
                "dean_position", "คณบดีวิทยาลัยการคอมพิวเตอร์"));

        boolean pdfAvailable = service.isPdfConversionAvailable();
        System.out.println("SAMPLES: writing to " + outDir + " (pdf=" + pdfAvailable + ")");

        for (SignatureModule module : registry.modules()) {
            for (int docType : registry.signableDocumentTypes(module)) {
                List<StampedSignature> signatures = registry.slotsFor(module, docType).stream()
                        .map(s -> new StampedSignature(s.anchorPlaceholder(), png, 420, 130))
                        .toList();

                byte[] docx = module == SignatureModule.ACADEMIC
                        ? service.generateSignedDocx(docType, json, signatures)
                        : service.generateSignedP2Docx(docType, json, signatures);

                String base = (module == SignatureModule.ACADEMIC ? "doc_" : "p2doc_") + docType;
                Files.write(outDir.resolve(base + "_signed.docx"), docx);

                if (pdfAvailable) {
                    byte[] pdf = service.convertDocxToPdf(docx);
                    if (pdf != null && pdf.length > 0) {
                        Files.write(outDir.resolve(base + "_signed.pdf"), pdf);
                        System.out.println("SAMPLES: " + base + " -> pdf " + pdf.length + " bytes");
                    } else {
                        System.out.println("SAMPLES: " + base + " -> PDF CONVERSION RETURNED NOTHING");
                    }
                }
            }
        }
    }
}
