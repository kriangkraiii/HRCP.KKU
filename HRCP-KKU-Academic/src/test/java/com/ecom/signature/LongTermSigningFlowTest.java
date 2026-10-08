package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSSignedData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.SignedPdfRevision;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionDocTypes;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.pdf.PdfIncrementService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;
import com.ecom.support.TestPki;

/**
 * เส้นทางลงนามจริงเมื่อตั้งบริการตราประทับเวลาไว้: ทุกลายมือชื่อมีตราประทับเวลา และไฟล์มีข้อมูลสถานะใบรับรอง
 * ติดต่อบริการไม่ได้ — ไม่ลงนาม ไม่มีอะไรถูกบันทึก และบอกผู้ลงนามให้ลองใหม่
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=POSITION:9",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.esign.tsa.required=true", "app.esign.ltv.enabled=true" })
@DisplayName("ลงนามแบบตรวจสอบได้ระยะยาว — ผ่านเส้นทางลงนามจริง")
class LongTermSigningFlowTest extends AbstractFlowTest {

    private static final TestPki PKI;

    static {
        try {
            PKI = new TestPki();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void tsa(DynamicPropertyRegistry registry) {
        registry.add("app.esign.tsa.url", PKI::tsaUrl);
    }

    @AfterAll
    static void stopPki() {
        PKI.close();
    }

    @Autowired
    private SignedPdfRevisionService revisions;

    @BeforeEach
    void needsLibreOffice() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        PKI.tsaDown.set(false);
    }

    private SignatureStep applicantStep(SignatureRequest envelope) {
        return signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
    }

    private SignatureRequest circulateAsAuthor(UserDtls applicant) {
        var request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        String json = "{\"applicant_name\":\"" + applicant.getName() + "\","
                + "\"title_name\":\"ผลงานทดสอบ\",\"role_des1\":\"ริเริ่ม\","
                + "\"chk_ firstauthor\":\"☑\",\"chk_corresp\":\"☑\","
                + "\"firstauthor_name\":\"" + applicant.getName() + "\","
                + "\"firstauthor_name__signer\":\"" + applicant.getId() + "\","
                + "\"corres_name\":\"" + applicant.getName() + "\","
                + "\"corres_name__signer\":\"" + applicant.getId() + "\"}";
        return circulate(SignatureModule.POSITION, request.getId(), PositionDocTypes.copyType(1), json, applicant,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())));
    }

    @Test
    @DisplayName("ลงนามครั้งเดียวสามช่อง: ทุกลายมือชื่อมีตราประทับเวลา ไฟล์มี DSS และลายมือชื่อทั้งหมดยังถูกต้อง")
    void everySignatureIsTimestampedAndValidationDataIsEmbedded() throws Exception {
        UserDtls applicant = data.applicant();
        UserSignature signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant);
        SignatureRequest envelope = circulateAsAuthor(applicant);

        var result = signatureWorkflow.sign(applicantStep(envelope).getId(), applicant, signature.getId(), true,
                SignatureWorkflowService.ActorContext.none(), TestCertificates.PIN);

        assertThat(result.ok()).as(result.error()).isTrue();
        assertThat(revisions.chain(envelope.getId())).extracting(SignedPdfRevision::getKind)
                .contains(SignedPdfRevision.Kind.LTV)
                .filteredOn(k -> k == SignedPdfRevision.Kind.SIGN).hasSize(3);
        byte[] pdf = revisions.latest(envelope.getId());
        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.getPDFName("DSS")))
                    .isNotNull();
            for (var sig : doc.getSignatureDictionaries()) {
                try (ASN1InputStream in = new ASN1InputStream(sig.getContents())) {
                    var signer = new CMSSignedData(in.readObject().getEncoded()).getSignerInfos().getSigners()
                            .iterator().next();
                    assertThat(signer.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken))
                            .as("ตราประทับเวลาของ %s", sig.getName()).isNotNull();
                }
            }
        }
        assertThat(PdfIncrementService.verify(pdf)).hasSize(3).allMatch(PdfIncrementService.SignatureCheck::valid);
    }

    @Test
    @DisplayName("ติดต่อบริการตราประทับเวลาไม่ได้ — ไม่ลงนาม ไม่บันทึกอะไร และบอกให้ลองใหม่")
    void unreachableAuthorityRefusesTheSignature() {
        UserDtls applicant = data.applicant();
        UserSignature signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant);
        SignatureRequest envelope = circulateAsAuthor(applicant);
        PKI.tsaDown.set(true);

        var result = signatureWorkflow.sign(applicantStep(envelope).getId(), applicant, signature.getId(), true,
                SignatureWorkflowService.ActorContext.none(), TestCertificates.PIN);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("บริการประทับเวลา");
        assertThat(signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()))
                .extracting(SignatureStep::getStatus).doesNotContain(SignatureStepStatus.SIGNED);
        assertThat(revisions.chain(envelope.getId())).extracting(SignedPdfRevision::getKind)
                .containsExactly(SignedPdfRevision.Kind.BASE);
    }
}
