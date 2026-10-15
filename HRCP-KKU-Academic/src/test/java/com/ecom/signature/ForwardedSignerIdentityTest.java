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
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.pdf.IncrementalSigningService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;

/**
 * แบบ ก.พ.ว. มข. ๐๓ ส่งเวียนให้ผู้ยื่นก่อน แล้วแอดมินส่งต่อให้หัวหน้าสาขาทีหลัง — ตอนสร้างไฟล์ตั้งต้นยังไม่รู้ว่า
 * หัวหน้าสาขาคือใคร ชื่อและตำแหน่งใต้ลายเซ็นต้องเป็นของคนที่ลงนามจริง ไม่ใช่หัวหน้าสาขาที่ระบบเดาจากการตั้งค่า
 * (ซอง 74: ขึ้น "รศ.ดร.ชานนท์ เดชสุภา" ทั้งที่ส่งให้บัญชีทดสอบ)
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=POSITION:1",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("ผู้ลงนามที่ส่งต่อทีหลัง — ชื่อและตำแหน่งใต้ลายเซ็นเป็นของผู้ลงนามจริง")
class ForwardedSignerIdentityTest extends AbstractFlowTest {

    private static final int KPW03 = 1;
    private static final String HEAD_POSITION = "หัวหน้าสาขาวิชาวิทยาการคอมพิวเตอร์";

    @Autowired
    private SignedPdfRevisionService revisions;

    @Autowired
    private com.ecom.academic.repository.SignatureRequestRepository envelopes;

    @BeforeEach
    void needsLibreOffice() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
    }

    private SignatureStep stepFor(SignatureRequest envelope, String slotKey) {
        return signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> slotKey.equals(s.getSlotKey())).findFirst().orElseThrow();
    }

    private Result sign(UserDtls signer, SignatureStep step) {
        var question = signatureWorkflow.signerChoiceFor(step.getId());
        return signatureWorkflow.sign(step.getId(), signer, data.signatureFor(signer).getId(), true,
                ActorContext.none(), TestCertificates.PIN, question != null ? question.options().get(0) : null);
    }

    /** The font size a field's value is drawn at, from its appearance stream ("/F 16 Tf"). */
    private static String drawnSize(byte[] pdf, String field) throws java.io.IOException {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var widget = doc.getDocumentCatalog().getAcroForm().getField(field).getWidgets().get(0);
            try (var in = widget.getAppearance().getNormalAppearance().getAppearanceStream().getCOSObject()
                    .createInputStream()) {
                var m = java.util.regex.Pattern.compile("([0-9.]+) Tf")
                        .matcher(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1));
                assertThat(m.find()).as("%s ไม่มีข้อความ", field).isTrue();
                return m.group(1);
            }
        }
    }

    /** The size the base gave a field — the template's own, from its /DA. */
    private static String templateSize(byte[] pdf, String field) throws java.io.IOException {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String da = doc.getDocumentCatalog().getAcroForm().getField(field).getCOSObject()
                    .getString(org.apache.pdfbox.cos.COSName.DA);
            var m = java.util.regex.Pattern.compile("([0-9.]+)\\s+Tf").matcher(da);
            assertThat(m.find()).as("%s ไม่มี /DA", field).isTrue();
            return String.format(java.util.Locale.ROOT, "%.3f", Float.parseFloat(m.group(1)));
        }
    }

    @Test
    @DisplayName("ไฟล์ตั้งต้นเว้นช่องชื่อและตำแหน่งหัวหน้าสาขาไว้ หัวหน้าสาขาที่ลงนามเติมของตัวเอง")
    void forwardedHeadPrintsOwnNameAndPosition() throws Exception {
        UserDtls applicant = data.applicant();
        data.digitalCertificateFor(applicant);
        UserDtls head = data.user("head-forwarded@example.invalid", "หัวหน้าสาขา", "ส่งต่อทีหลัง", "ROLE_USER");
        head.setPositionTitle(HEAD_POSITION);
        head.setTitle("รองศาสตราจารย์ ดร.");
        head = data.saveUser(head);
        data.digitalCertificateFor(head);

        var request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        SignatureRequest envelope = circulate(SignatureModule.POSITION, request.getId(), KPW03,
                "{\"applicant_name\":\"" + applicant.getName() + "\"}", applicant,
                List.of(new SignerAssignment("applicant", applicant.getId())));

        var base = IncrementalSigningService.values(revisions.latest(envelope.getId()));
        assertThat(base).as("ยังไม่มีหัวหน้าสาขา — ต้องเป็นช่องว่างให้เติม ไม่ใช่ชื่อที่เดาไว้")
                .containsEntry("department_head_name", "")
                .containsEntry("head_position_line", "");

        Result applicantSigned = sign(applicant, stepFor(envelope, "applicant"));
        assertThat(applicantSigned.ok()).as(applicantSigned.error()).isTrue();

        Result forwarded = signatureWorkflow.forwardToNextSigners(envelope.getId(),
                List.of(new SignerAssignment("head", head.getId())), null, data.admin(), ActorContext.none());
        assertThat(forwarded.ok()).as(forwarded.error()).isTrue();

        Result headSigned = sign(head, stepFor(envelope, "head"));
        assertThat(headSigned.ok()).as(headSigned.error()).isTrue();

        byte[] pdf = revisions.latest(envelopes.findById(envelope.getId()).orElseThrow().getId());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "incremental"));
        java.nio.file.Files.write(java.nio.file.Path.of("target", "incremental", "kpw03-forwarded-head.pdf"), pdf);
        var signed = IncrementalSigningService.values(pdf);
        assertThat(signed.get("department_head_name")).contains("รองศาสตราจารย์ ดร.หัวหน้าสาขา ส่งต่อทีหลัง");
        assertThat(signed).containsEntry("head_position_line", HEAD_POSITION);
        for (String field : List.of("department_head_name", "head_position_line")) {
            assertThat(drawnSize(pdf, field)).as("%s ต้องไม่ถูกย่อให้เล็กกว่าขนาดตัวอักษรของแบบฟอร์ม", field)
                    .isEqualTo(templateSize(pdf, field));
        }
    }
}
