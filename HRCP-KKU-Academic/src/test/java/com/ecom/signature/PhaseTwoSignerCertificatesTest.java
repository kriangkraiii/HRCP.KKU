package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.DocumentFieldOwnership;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;

/**
 * เฟส 2 ทุกเอกสารที่ลงนามได้: ลงนามครบแล้ว ไฟล์ PDF ต้องมีลายมือชื่อดิจิทัลของผู้ลงนามทุกคน — คนละใบรับรอง
 * ตรวจได้ทุกลายมือชื่อ ใช้รายการเอกสารแบบใส่ทับตามค่าตั้งต้นของระบบ (ไม่ตั้ง app.esign.incremental-docs)
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("เฟส 2 — ไฟล์ที่ลงนามครบมีใบรับรองของผู้ลงนามทุกคน")
class PhaseTwoSignerCertificatesTest extends AbstractFlowTest {

    @Autowired
    private SignedPdfRevisionService revisions;

    @Autowired
    private SignatureRequestRepository envelopes;

    @BeforeEach
    void needsLibreOffice() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
    }

    @ParameterizedTest(name = "เอกสารที่ {0}")
    @ValueSource(ints = { 1, 2, 3, 4, 6, 7, 8, 901 })
    void everySignersCertificateIsInTheSignedFile(int documentType) throws Exception {
        UserDtls admin = data.admin();
        UserDtls applicant = data.applicant();
        var request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        List<SignatureSlot> slots = SignatureAnchorRegistry.slotsOf(SignatureModule.POSITION, documentType);
        var nameFields = SignatureAnchorRegistry.signerNameFields(SignatureModule.POSITION, documentType);

        // ผู้ลงนามแต่ละช่องเป็นคนละคน มี Digital ID ของตัวเอง — ชื่อในใบรับรองคือชื่อบัญชี
        Map<String, UserDtls> signers = new LinkedHashMap<>();
        Map<String, String> json = new LinkedHashMap<>();
        List<SignatureWorkflowService.SignerAssignment> assignments = new ArrayList<>();
        for (SignatureSlot slot : slots) {
            // ชื่อภาษาอังกฤษ: ใบรับรองทดสอบใช้ชื่อบัญชีเป็น CN
            UserDtls signer = "applicant".equals(slot.slotKey()) ? applicant
                    : data.user("cert-" + documentType + "-" + slot.slotKey() + "@kku.ac.th",
                            "Signer" + slot.slotKey().replace("_", ""), "Doc" + documentType, "ROLE_USER");
            data.digitalCertificateFor(signer);
            signers.put(slot.slotKey(), signer);
            if (nameFields.contains(slot.anchorPlaceholder())) {
                json.put(slot.anchorPlaceholder(), SignerNameResolver.printedName(signer));
                json.put(slot.anchorPlaceholder() + DocumentFieldOwnership.SIGNER_ID_SUFFIX, String.valueOf(signer.getId()));
            } else {
                assignments.add(new SignatureWorkflowService.SignerAssignment(slot.slotKey(), signer.getId()));
            }
        }
        json.put("applicant_name", SignerNameResolver.printedName(applicant));
        UserDtls initiator = signers.containsKey("applicant") ? applicant : admin;

        SignatureRequest envelope = circulate(SignatureModule.POSITION, request.getId(), documentType,
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(json), initiator, assignments);
        assertThat(envelope.isIncremental()).as("เอกสารนี้ต้องลงนามลงไฟล์ PDF").isTrue();

        officerFilledIn(SignatureModule.POSITION, request.getId(), documentType);
        if (slots.stream().anyMatch(s -> !"applicant".equals(s.slotKey()))) {
            signatureWorkflow.startCirculation(envelope.getId(), admin, SignatureWorkflowService.ActorContext.none());
        }
        for (SignatureStep step : signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId())) {
            UserDtls signer = signers.get(step.getSlotKey());
            var question = signatureWorkflow.signerChoiceFor(step.getId());
            var result = signatureWorkflow.sign(step.getId(), signer, data.signatureFor(signer).getId(), true,
                    SignatureWorkflowService.ActorContext.none(), TestCertificates.PIN,
                    question != null ? question.options().get(0) : null);
            assertThat(result.error()).as("ลงนามช่อง %s ไม่สำเร็จ", step.getSlotKey()).isNull();
        }
        assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.COMPLETED);

        byte[] pdf = revisions.latest(envelope.getId());
        List<String> certificateNames = new ArrayList<>();
        try (var doc = Loader.loadPDF(pdf)) {
            for (PDSignature signature : doc.getSignatureDictionaries()) {
                // ช่อง /Contents เผื่อที่ไว้ด้วยศูนย์ต่อท้าย — อ่านเฉพาะ CMS ตัวจริง
                byte[] der;
                try (var in = new ASN1InputStream(signature.getContents(pdf))) {
                    der = in.readObject().getEncoded();
                }
                CMSSignedData cms = new CMSSignedData(
                        new CMSProcessableByteArray(signature.getSignedContent(pdf)), der);
                SignerInformation info = cms.getSignerInfos().getSigners().iterator().next();
                X509CertificateHolder cert = (X509CertificateHolder) cms.getCertificates()
                        .getMatches(info.getSID()).iterator().next();
                assertThat(info.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert)))
                        .as("ลายมือชื่อของ %s ตรวจไม่ผ่าน", cert.getSubject()).isTrue();
                certificateNames.add(IETFUtils.valueToString(
                        cert.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue()));
            }
        }
        assertThat(certificateNames).as("ใบรับรองในไฟล์ของเอกสารที่ %d", documentType)
                .containsExactlyInAnyOrderElementsOf(signers.values().stream().map(UserDtls::getName).toList());
    }
}
