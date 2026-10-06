package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * PDF.js is self-hosted under /vendor/pdfjs and, from 4.x on, ships only as an
 * ES module. Both places that use it load it on demand, so a broken upgrade
 * (wrong MIME type, a CSP block, a renamed file, an API change) shows up only
 * when a page actually renders a PDF. The e-sign viewer even swallows the error
 * and falls back to a native frame, so nothing else would notice.
 */
@DisplayName("PDF.js — โหลดเป็นโมดูลและวาด PDF ได้จริง")
class PdfJsBrowserTest extends PlaywrightTestBase {

    /** A real Thai regulation, so CMaps and embedded Thai fonts are exercised too. */
    private static final Path REGULATION_PDF = Path.of("src/main/resources/static/upload",
            "ข้อบังคับฯ-ต.วิชาการ-พนักงานมหาวิทยาลัย-พ.ศ.-2565.pdf");

    /** The signature editor is shown only to someone holding a valid certificate. */
    private void signInWithCertificate() {
        UserDtls applicant = data.applicant();
        data.digitalCertificateFor(applicant);
        signIn(applicant.getEmail(), TestDataFactory.PASSWORD);
    }

    @Test
    @DisplayName("อัปโหลดลายเซ็นเป็น PDF — วาดหน้าแรกแล้วได้ภาพลายเซ็น")
    void signatureFromPdfUpload() throws Exception {
        signInWithCertificate();
        page.navigate(baseUrl() + "/esign/my-signatures");
        page.locator("#sigPanelUpload").evaluate("el => el.classList.remove('d-none')");

        page.locator("#sigUpload").setInputFiles(
                new FilePayload("signature.pdf", "application/pdf", inkedPdf()));

        page.locator("#sigPreview").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
        assertThat(page.locator("#sigPreview").getAttribute("src")).startsWith("data:image/");
    }

    @Test
    @DisplayName("ตัวแสดงเอกสารหน้าลงนาม — วาดทุกหน้าลง canvas ไม่ตกไปใช้ frame สำรอง")
    void esignViewerRendersPages() throws Exception {
        // Same-origin URL, served by the browser itself: the test profile has no
        // upload directory, and the viewer only needs bytes with a PDF type.
        byte[] pdf = Files.readAllBytes(REGULATION_PDF);
        String pdfUrl = baseUrl() + "/__pdfjs-probe.pdf";
        page.route(pdfUrl, route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200).setContentType("application/pdf").setBodyBytes(pdf)));

        signInWithCertificate();
        page.navigate(baseUrl() + "/esign/my-signatures");
        page.addScriptTag(new com.microsoft.playwright.Page.AddScriptTagOptions()
                .setUrl(baseUrl() + "/js/esign_pdf_viewer.js"));
        page.evaluate("url => { const d = document.createElement('div'); d.id = 'pdfjsProbe';"
                + " document.body.appendChild(d); window.initEsignPdfViewer('pdfjsProbe', url); }",
                pdfUrl);

        Locator firstCanvas = page.locator("#pdfjsProbe .esign-page-container canvas").first();
        firstCanvas.waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        assertThat(page.locator("#pdfjsProbe iframe").count()).as("fell back to the native frame").isZero();
        Number width = (Number) page.evaluate(
                "() => document.querySelector('#pdfjsProbe .esign-page-container canvas').width");
        assertThat(width.intValue()).isPositive();
    }

    /** One page with a filled black box: enough ink for the trimmer to keep. */
    private static byte[] inkedPdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage pdfPage = new PDPage();
            doc.addPage(pdfPage);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                cs.addRect(100, 500, 300, 80);
                cs.fill();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
