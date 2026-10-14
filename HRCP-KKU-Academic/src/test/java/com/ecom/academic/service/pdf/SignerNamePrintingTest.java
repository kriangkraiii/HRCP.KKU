package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;

@DisplayName("ชื่อในวงเล็บใต้ลายเซ็น — ผู้ลงนามเติมเองตอนเซ็นถ้ายังว่าง")
class SignerNamePrintingTest {

    private static final SignatureSlot HR = new SignatureSlot("hr", "นักทรัพยากรบุคคล", "hr_staff_name", "HR", 2);

    private static SignatureStep signedBy(String name) {
        SignatureStep step = new SignatureStep();
        step.setSignerNameSnapshot(name);
        return step;
    }

    @Test
    @DisplayName("เอกสารที่ 2: ช่องชื่อนักทรัพยากรบุคคลว่าง — ใส่ชื่อผู้ตรวจที่ลงนาม")
    void blankNameIsFilledWithTheSigner() {
        Map<String, String> own = new LinkedHashMap<>();

        IncrementalSigningService.putNameIfBlank(own, HR, signedBy("สมหญิง สายตรวจการ"),
                Set.of("sig_hr", "hr_staff_name"), Map.of("hr_staff_name", ""));

        assertThat(own).containsEntry("hr_staff_name", "สมหญิง สายตรวจการ");
    }

    @Test
    @DisplayName("ชื่อที่เจ้าหน้าที่กรอกไว้แล้ว หรือไฟล์ไม่ได้เผื่อช่องชื่อ — ไม่แตะ")
    void filledOrMissingFieldIsLeftAlone() {
        Map<String, String> own = new LinkedHashMap<>();

        IncrementalSigningService.putNameIfBlank(own, HR, signedBy("สมหญิง สายตรวจการ"),
                Set.of("sig_hr", "hr_staff_name"), Map.of("hr_staff_name", "นายสมชาย กรอกเอง"));
        IncrementalSigningService.putNameIfBlank(own, HR, signedBy("สมหญิง สายตรวจการ"),
                Set.of("sig_hr"), Map.of());

        assertThat(own).isEmpty();
    }

    @Test
    @DisplayName("BasePdfBuilder: ช่องชื่อที่มีวงเล็บครอบใน Word จะถูกตัดวงเล็บ static ออก เพื่อไม่ให้ค้างบนพื้นหลัง PDF")
    void reserveStripsStaticParenthesesFromNameField() throws Exception {
        String docxXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p><w:r><w:t>(@@OVL_hr_staff_name@@)</w:t></w:r></w:p></w:body></w:document>";

        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(bout)) {
            zout.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zout.write(docxXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zout.closeEntry();
        }

        Map<String, Integer> lengths = Map.of("hr_staff_name", 45);
        Map<String, Integer> lines = Map.of("hr_staff_name", 1);
        Map<String, Integer> seen = new LinkedHashMap<>();
        java.util.List<String> order = new java.util.ArrayList<>();
        java.util.Set<String> inline = new java.util.HashSet<>();

        byte[] reservedDocx = BasePdfBuilder.reserve(bout.toByteArray(), lengths, lines, seen, order, inline);

        String resultXml = "";
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(reservedDocx))) {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    resultXml = new String(zin.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }

        assertThat(resultXml).doesNotContain("( ");
        assertThat(resultXml).doesNotContain(" )");
        assertThat(resultXml).doesNotContain("(");
        assertThat(resultXml).doesNotContain(")");
        assertThat(seen).containsEntry("hr_staff_name", 1);
    }

    @Test
    @DisplayName("ตรวจ real doc_2.docx: ตัดวงเล็บ static รอบ hr_staff_name ออกจริงใน Word และ PDF ฉบับฐาน")
    void testDoc2RealReserve() throws Exception {
        com.ecom.academic.service.DocumentGenerationService gen = new com.ecom.academic.service.DocumentGenerationService();
        byte[] docx = gen.generateSignedDocx(2, "{\"hr_staff_name\":\"@@OVL_hr_staff_name@@\"}",
                java.util.List.of(), com.ecom.academic.service.DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
        Map<String, Integer> lengths = Map.of("hr_staff_name", 45);
        Map<String, Integer> lines = Map.of("hr_staff_name", 1);
        Map<String, Integer> seen = new LinkedHashMap<>();
        java.util.List<String> order = new java.util.ArrayList<>();
        java.util.Set<String> inline = new java.util.HashSet<>();
        byte[] reserved = BasePdfBuilder.reserve(docx, lengths, lines, seen, order, inline);

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(reserved))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    String xml = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(xml).as("no static parentheses in reserved doc_2").doesNotContain("( ");
                    assertThat(xml).as("no static parentheses in reserved doc_2").doesNotContain(" )");
                }
            }
        }

        if (gen.isPdfConversionAvailable()) {
            byte[] pdf = gen.convertDocxToPdf(reserved);
            try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                String text = stripper.getText(pdfDoc);
                // ตรวจสอบว่าในบรรทัดใต้ลายเซ็นของนักทรัพยากรบุคคล ไม่มีวงเล็บเปิดปิดค้างบนพื้นหลัง PDF
                for (String line : text.split("\\r?\\n")) {
                    if (line.contains("นักทรัพยากรบุคคล")) {
                        assertThat(line).doesNotContain("( )");
                        assertThat(line).doesNotContain("(   )");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("ตรวจ real doc_6.docx: ตัดวงเล็บรอบชื่อคณบดี ไม่ใช่วงเล็บเปิดของชื่อผู้ขอที่อยู่ย่อหน้าก่อน")
    void testDoc6StripsOnlyTheDeanParentheses() throws Exception {
        com.ecom.academic.service.DocumentGenerationService gen = new com.ecom.academic.service.DocumentGenerationService();
        byte[] docx = gen.generateSignedP2Docx(6,
                "{\"applicant_title\":\"อาจารย์\",\"applicant_name\":\"ผู้ยื่นคำขอ ทดสอบ\","
                        + "\"dean_signature_name\":\"@@OVL_dean_signature_name@@\"}",
                java.util.List.of(), com.ecom.academic.service.DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
        Map<String, Integer> lengths = Map.of("dean_signature_name", 45);
        Map<String, Integer> lines = Map.of("dean_signature_name", 1);
        Map<String, Integer> seen = new LinkedHashMap<>();
        byte[] reserved = BasePdfBuilder.reserve(docx, lengths, lines, seen,
                new java.util.ArrayList<>(), new java.util.HashSet<>());

        java.util.List<String> paragraphs = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(reserved))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    String xml = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    for (String p : xml.split("</w:p>")) {
                        paragraphs.add(p.replaceAll("<w:tab/>", " ").replaceAll("<[^>]+>", "").trim());
                    }
                }
            }
        }

        assertThat(seen).containsEntry("dean_signature_name", 1);
        assertThat(paragraphs).anyMatch(p -> p.equals("(อาจารย์ผู้ยื่นคำขอ ทดสอบ)"));
        String dean = paragraphs.stream().filter(p -> p.contains(" ".repeat(45))).findFirst().orElseThrow();
        assertThat(dean).doesNotContain("(").doesNotContain(")");
    }

    @Test
    @DisplayName("PdfIncrementService: ฟิลด์ที่เป็น centred (เช่นชื่อใต้ลายเซ็น) จะเติมวงเล็บแนบชิดชื่อ")
    void fillFieldWrapsCentredNameWithParentheses() throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);

            org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm form = new org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);

            org.apache.pdfbox.pdmodel.PDResources res = new org.apache.pdfbox.pdmodel.PDResources();
            try (java.io.InputStream ttf = ThaiText.class.getClassLoader().getResourceAsStream(ThaiText.FONT_RESOURCE)) {
                org.apache.pdfbox.pdmodel.font.PDType0Font font = org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc, ttf);
                res.put(org.apache.pdfbox.cos.COSName.getPDFName(PdfIncrementService.FONT), font);
            }
            form.setDefaultResources(res);

            org.apache.pdfbox.pdmodel.interactive.form.PDPushButton btn = new org.apache.pdfbox.pdmodel.interactive.form.PDPushButton(form);
            btn.setPartialName("hr_staff_name");
            form.getFields().add(btn);

            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget widget = btn.getWidgets().get(0);
            widget.setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(50, 50, 200, 30));
            widget.setPage(page);
            page.getAnnotations().add(widget);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            byte[] basePdf = out.toByteArray();

            PdfIncrementService pdfService = new PdfIncrementService(ThaiText.sarabun());
            byte[] previewPdf = pdfService.preview(basePdf, "sig_hr", Map.of("hr_staff_name", "นางสาวเจ้าหน้าที่HR ทดสอบ"),
                    Set.of("hr_staff_name"), null);

            try (org.apache.pdfbox.pdmodel.PDDocument previewDoc = org.apache.pdfbox.Loader.loadPDF(previewPdf)) {
                var previewForm = previewDoc.getDocumentCatalog().getAcroForm(null);
                String val = PdfIncrementService.valueOf(previewForm.getField("hr_staff_name"));
                assertThat(val).isEqualTo("(นางสาวเจ้าหน้าที่HR ทดสอบ)");
            }
        }
    }
}

