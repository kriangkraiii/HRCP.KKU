package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.SignedPdfRevision;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionDocTypes;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;

/**
 * เอกสารที่ 9 เฟส 2 แบบใส่ทับ (ฉบับแยกตามผลงาน 901 ใช้กติกาของเอกสารที่ 9): ผู้ขอที่เป็นผู้ประพันธ์อันดับแรกและบรรณกิจเอง ลงนามครั้งเดียว ได้ลายมือชื่อดิจิทัล
 * ในไฟล์ครบทุกช่องของตัวเอง — ช่องละหนึ่ง revision ด้วยใบรับรองเดียวกัน
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=POSITION:9",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("เอกสารที่ 9 — ผู้ขอลงนามทุกช่องของตัวเองลงไฟล์ในครั้งเดียว")
class ApplicantHeldSlotsSigningTest extends AbstractFlowTest {

    @Autowired
    private SignedPdfRevisionService revisions;

    @Autowired
    private com.ecom.academic.service.pdf.IncrementalSigningService incrementalSigning;

    @Autowired
    private com.ecom.academic.repository.SignatureRequestRepository envelopes;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private static byte[] samplePng() throws java.io.IOException {
        var image = new java.awt.image.BufferedImage(120, 48, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.drawLine(5, 40, 115, 8);
        g.dispose();
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @BeforeEach
    void needsLibreOffice() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
    }

    @Test
    @DisplayName("ลงนามครั้งเดียว: ไฟล์มีลายมือชื่อของผู้ขอ ผู้ประพันธ์อันดับแรก และบรรณกิจ")
    void oneSigningPutsEveryOwnSlotIntoThePdf() {
        UserDtls applicant = data.applicant();
        UserSignature signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant);
        var request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        String json = "{\"applicant_name\":\"" + applicant.getName() + "\","
                + "\"title_name\":\"ผลงานทดสอบ\",\"role_des1\":\"ริเริ่ม\","
                + "\"chk_ firstauthor\":\"☑\",\"chk_corresp\":\"☑\","
                + "\"firstauthor_name\":\"" + applicant.getName() + "\","
                + "\"firstauthor_name__signer\":\"" + applicant.getId() + "\","
                + "\"corres_name\":\"" + applicant.getName() + "\","
                + "\"corres_name__signer\":\"" + applicant.getId() + "\"}";
        SignatureRequest envelope = circulate(SignatureModule.POSITION, request.getId(),
                PositionDocTypes.copyType(1), json, applicant,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())));
        SignatureStep applicantStep = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId())
                .get(0);

        // ตัวอย่างก่อนลงนาม: ลายเซ็นต้องขึ้นทุกช่องที่จะลงนามพร้อมกัน ไม่ใช่แค่ช่องผู้ขอ
        try {
            // ในคำขอจริงมี session ตลอดคำขอ (open-in-view) — เทสต์จำลองด้วยธุรกรรม
            byte[] png = samplePng();
            byte[] preview = new org.springframework.transaction.support.TransactionTemplate(txManager).execute(tx -> {
                try {
                    return incrementalSigning.preview(envelopes.findByIdWithSteps(envelope.getId()).orElseThrow(),
                            signatureSteps.findById(applicantStep.getId()).orElseThrow(), png, null, null);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            try (var doc = org.apache.pdfbox.Loader.loadPDF(preview)) {
                var form = doc.getDocumentCatalog().getAcroForm();
                for (String field : List.of("sig_applicant", "sig_first_author", "sig_corresponding_author")) {
                    var widget = form.getField(field).getWidgets().get(0);
                    var stream = widget.getAppearance().getNormalAppearance().getAppearanceStream();
                    assertThat(stream.getResources().getXObjectNames()).as("ตัวอย่าง %s ต้องมีภาพลายเซ็น", field)
                            .isNotEmpty();
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        var result = signatureWorkflow.sign(applicantStep.getId(), applicant, signature.getId(), true,
                SignatureWorkflowService.ActorContext.none(), TestCertificates.PIN);

        assertThat(result.ok()).as(result.error()).isTrue();
        assertThat(signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()))
                .extracting(SignatureStep::getStatus)
                .containsOnly(SignatureStepStatus.SIGNED);
        assertThat(revisions.chain(envelope.getId())).extracting(SignedPdfRevision::getKind)
                .containsExactly(SignedPdfRevision.Kind.BASE, SignedPdfRevision.Kind.SIGN,
                        SignedPdfRevision.Kind.SIGN, SignedPdfRevision.Kind.SIGN);
        assertThat(result.request().getStatus()).isEqualTo(SignatureRequestStatus.COMPLETED);

        // ลายเซ็นต้องมองเห็นในทุกบล็อกของผู้ขอ — ช่องลายเซ็นของแต่ละบทบาทมีภาพอยู่บนหน้า ไม่ใช่แค่ลายมือชื่อในไฟล์
        byte[] pdf = revisions.latest(envelope.getId());
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var form = doc.getDocumentCatalog().getAcroForm();
            for (String field : List.of("sig_applicant", "sig_first_author", "sig_corresponding_author")) {
                var sig = (org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField) form.getField(field);
                assertThat(sig).as(field).isNotNull();
                assertThat(sig.getValue()).as("%s ต้องลงนามแล้ว", field).isNotNull();
                var widget = sig.getWidgets().get(0);
                assertThat(widget.getRectangle().getWidth() * widget.getRectangle().getHeight())
                        .as("%s ต้องมีพื้นที่บนหน้า", field).isGreaterThan(0f);
                assertThat(widget.getAppearance()).as("%s ต้องมีภาพลายเซ็น", field).isNotNull();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
