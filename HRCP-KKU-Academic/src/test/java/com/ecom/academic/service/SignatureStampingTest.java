package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Stamps a signature into every signable template and checks the result is a
 * well-formed DOCX that actually contains the picture.
 *
 * <p>Runs against the real {@code .docx} files rather than a fixture: the whole
 * feature rests on assumptions about how these particular templates are built —
 * where the anchors sit, which namespaces are declared, whether a dotted rule
 * exists — and a fixture would stop testing exactly the thing that can break.
 */
@DisplayName("การฝังลายเซ็นลงเอกสาร DOCX")
class SignatureStampingTest {

    private final DocumentGenerationService service = new DocumentGenerationService();
    private final SignatureAnchorRegistry registry = new SignatureAnchorRegistry();
    private final ObjectMapper mapper = new ObjectMapper();

    private static byte[] samplePng() throws IOException {
        BufferedImage image = new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.drawLine(10, 80, 290, 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** Unzips a DOCX, returning entry name to bytes. Throws if it is not a valid zip. */
    private static Map<String, byte[]> unzip(byte[] docx) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entries.put(e.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }

    private static String text(Map<String, byte[]> entries, String name) {
        byte[] data = entries.get(name);
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }

    private byte[] render(SignatureModule module, int docType, List<StampedSignature> signatures) throws IOException {
        String json = mapper.writeValueAsString(Map.of("dummy", "x"));
        return module == SignatureModule.ACADEMIC
                ? service.generateSignedDocx(docType, json, signatures)
                : service.generateSignedP2Docx(docType, json, signatures);
    }

    /**
     * The core sweep: one dynamic test per signable document, each stamping every
     * slot that document declares.
     */
    @TestFactory
    @DisplayName("ทุกฉบับที่ลงนามได้ ต้องฝังรูปครบทุกจุดและไฟล์ยังเปิดได้")
    List<DynamicTest> stampsEverySignableDocument() throws IOException {
        byte[] png = samplePng();
        List<DynamicTest> tests = new ArrayList<>();

        for (SignatureModule module : registry.modules()) {
            for (int docType : registry.signableDocumentTypes(module)) {
                List<SignatureSlot> slots = registry.slotsFor(module, docType);

                tests.add(DynamicTest.dynamicTest(module + " doc " + docType
                        + " (" + slots.size() + " จุดลงนาม)", () -> {

                    List<StampedSignature> signatures = slots.stream()
                            .map(s -> new StampedSignature(s.anchorPlaceholder(), png, 300, 100))
                            .toList();

                    byte[] docx = render(module, docType, signatures);
                    Map<String, byte[]> entries = unzip(docx);

                    // 1. Every image part is present and byte-identical.
                    for (int i = 1; i <= slots.size(); i++) {
                        assertThat(entries).containsKey("word/media/hrcpsig" + i + ".png");
                        assertThat(entries.get("word/media/hrcpsig" + i + ".png")).isEqualTo(png);
                    }

                    // 2. Each image is declared as a relationship, or Word reports
                    //    the file as corrupt.
                    String rels = text(entries, "word/_rels/document.xml.rels");
                    for (int i = 1; i <= slots.size(); i++) {
                        assertThat(rels).contains("Id=\"rIdHrcpSig" + i + "\"");
                        assertThat(rels).contains("Target=\"media/hrcpsig" + i + ".png\"");
                    }

                    // 3. The png content type is declared.
                    assertThat(text(entries, "[Content_Types].xml")).contains("Extension=\"png\"");

                    // 4. The body references each image exactly once.
                    String document = text(entries, "word/document.xml");
                    assertThat(document).contains("<w:drawing>");
                    for (int i = 1; i <= slots.size(); i++) {
                        assertThat(countOf(document, "r:embed=\"rIdHrcpSig" + i + "\""))
                                .as("document.xml must embed signature %d exactly once", i)
                                .isEqualTo(1);
                    }

                    // 5. Paragraph tags stay balanced — the stamper rewrites raw
                    //    XML, so an unbalanced edit is the likeliest way to break
                    //    a template.
                    assertThat(countOfParagraphOpens(document))
                            .as("<w:p> and </w:p> must remain balanced")
                            .isEqualTo(countOf(document, "</w:p>"));

                    // 6. Nothing left unstamped: a missing anchor is logged, not
                    //    thrown, so this is what would catch it.
                    assertThat(countOf(document, "<w:drawing>"))
                            .as("expected one drawing per slot, plus any the template already had")
                            .isGreaterThanOrEqualTo(slots.size());
                }));
            }
        }
        return tests;
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int at = 0;
        while ((at = haystack.indexOf(needle, at)) != -1) {
            count++;
            at += needle.length();
        }
        return count;
    }

    /** Counts paragraph opens, excluding {@code <w:pPr>} and self-closing tags. */
    private static int countOfParagraphOpens(String xml) {
        int count = 0;
        int at = 0;
        while ((at = xml.indexOf("<w:p", at)) != -1) {
            int after = at + 4;
            if (after < xml.length()) {
                char c = xml.charAt(after);
                if (c == '>' || Character.isWhitespace(c)) {
                    int tagEnd = xml.indexOf('>', at);
                    if (tagEnd != -1 && xml.charAt(tagEnd - 1) != '/') {
                        count++;
                    }
                }
            }
            at += 4;
        }
        return count;
    }

    @Test
    @DisplayName("ไม่มีลายเซ็น ต้องได้ไฟล์เหมือนเดิมทุกไบต์ (ไม่กระทบเอกสารเดิม)")
    void withoutSignaturesOutputIsUnchanged() throws IOException {
        String json = mapper.writeValueAsString(Map.of("dummy", "x"));

        for (SignatureModule module : registry.modules()) {
            for (int docType : registry.signableDocumentTypes(module)) {
                byte[] plain = module == SignatureModule.ACADEMIC
                        ? service.generatePreviewDocx(docType, json)
                        : service.generateP2PreviewDocx(docType, json);
                byte[] viaSignedPath = render(module, docType, List.of());

                Map<String, byte[]> actual = unzip(viaSignedPath);
                Map<String, byte[]> expected = unzip(plain);
                assertThat(actual.keySet())
                        .as("%s doc %d entry keys must match", module, docType)
                        .isEqualTo(expected.keySet());
                for (String entryName : expected.keySet()) {
                    assertThat(actual.get(entryName))
                            .as("%s doc %d entry %s must match", module, docType, entryName)
                            .isEqualTo(expected.get(entryName));
                }
            }
        }
    }

    @Test
    @DisplayName("ลายเซ็นของผู้ขอต้องไปอยู่ใต้บรรทัด 'ลงชื่อ' ไม่ใช่กลางเนื้อความ")
    void anchorsOnTheSignatureBlockNotTheProse() throws IOException {
        // doc_0 mentions {{applicant_name}} twice: once inside the sentence
        // "ข้าพเจ้า ..." and once under the signature rule. Landing on the first
        // would stamp a signature into the middle of a paragraph of prose.
        byte[] docx = render(SignatureModule.ACADEMIC, 0,
                List.of(new StampedSignature("applicant_name", samplePng(), 300, 100)));

        String document = text(unzip(docx), "word/document.xml");

        // Locate the signature by its own relationship, not by the first
        // <w:drawing> in the file: doc_0 already carries a logo image whose
        // drawing appears earlier in the body.
        int signatureAt = document.indexOf("r:embed=\"rIdHrcpSig1\"");
        int signLabelAt = document.indexOf("ลงชื่อ");

        assertThat(signatureAt).isPositive();
        assertThat(signLabelAt).isPositive();
        // The picture replaces the dotted rule on the "ลงชื่อ" line, so it must
        // land after the label and close to it — not back in the prose, where
        // {{applicant_name}} also appears.
        assertThat(signatureAt).isGreaterThan(signLabelAt);
        assertThat(signatureAt - signLabelAt).isLessThan(3000);
    }

    @Test
    @DisplayName("บรรทัดจุดไข่ปลาถูกแทนที่ด้วยลายเซ็น")
    void dotLeaderIsReplaced() throws IOException {
        byte[] before = render(SignatureModule.POSITION, 5, List.of());
        byte[] after = render(SignatureModule.POSITION, 5,
                List.of(new StampedSignature("department_head_name", samplePng(), 300, 100)));

        String plain = text(unzip(before), "word/document.xml");
        String stamped = text(unzip(after), "word/document.xml");

        assertThat(countOf(plain, "...........")).isGreaterThan(countOf(stamped, "..........."));
    }

    @Test
    @DisplayName("เอกสารที่ไม่มีจุดลงนาม ไม่ถูกนับว่าลงนามได้")
    void unsignableDocumentsAreExcluded() {
        // Phase 1 doc 5 is only a suggestions textbox; Phase 2 has no doc 0 file.
        assertThat(registry.isSignable(SignatureModule.ACADEMIC, 5)).isFalse();
        assertThat(registry.isSignable(SignatureModule.POSITION, 0)).isFalse();

        assertThat(registry.signableDocumentTypes(SignatureModule.ACADEMIC))
                .containsExactly(0, 1, 2, 3, 4, 6, 7, 8);
        assertThat(registry.signableDocumentTypes(SignatureModule.POSITION))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    @DisplayName("รวมทั้งระบบต้องลงนามได้ 17 ฉบับ")
    void seventeenSignableDocumentsInTotal() {
        int total = registry.modules().stream()
                .mapToInt(m -> registry.signableDocumentTypes(m).size())
                .sum();
        assertThat(total).isEqualTo(17);
    }
}
