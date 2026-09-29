package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PdfMode;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignedPdfRevision;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.pdf.BasePdfBuilder;
import com.ecom.academic.service.pdf.CmsSigner;
import com.ecom.academic.service.pdf.IncrementalSigningService;
import com.ecom.academic.service.pdf.PdfIncrementService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;

/**
 * ซองลงนามแบบใส่ทับ: ไฟล์ตั้งต้นถูกสร้างและเก็บเป็น revision 0 ในฐานข้อมูล แต่ละขั้นต่อท้าย
 * เป็น revision ใหม่ และไฟล์ที่ประกอบกลับจากฐานข้อมูลคือไฟล์เดียวกับที่เซ็นจริง
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:1" })
@DisplayName("ซองลงนามแบบใส่ทับ — revision chain ในฐานข้อมูล")
class IncrementalEnvelopeTest extends AbstractFlowTest {

    @Autowired
    private IncrementalSigningService incremental;

    @Autowired
    private SignedPdfRevisionService revisions;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    /** As the workflow calls it: inside a transaction, on the managed envelope. */
    private <T> T inTx(Long envelopeId, ThrowingFunction<T> work) {
        return new org.springframework.transaction.support.TransactionTemplate(txManager).execute(status -> {
            try {
                return work.apply(envelopes.findById(envelopeId).orElseThrow());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingFunction<T> {
        T apply(SignatureRequest envelope) throws Exception;
    }

    @BeforeEach
    void needsLibreOffice() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
    }

    private SignatureRequest envelopeFor(UserDtls applicant) {
        AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);
        return circulate(SignatureModule.ACADEMIC, draft.getId(), 1,
                "{\"applicant_name\":\"" + applicant.getName() + "\",\"title\":\"ขอรับการประเมินผลการสอน\","
                        + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"2569\"}",
                applicant, List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())));
    }

    private SignatureStep applicantStep(SignatureRequest envelope) {
        return signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
    }

    private static CmsSigner p12(String name) throws Exception {
        return CmsSigner.open(TestCertificates.validP12(name), TestCertificates.PIN.toCharArray());
    }

    @Test
    @DisplayName("เริ่ม → ผู้ยื่นเซ็น → ใส่เลขที่/วันที่ → ปิดเอกสาร: 4 revision และลายเซ็นทั้งสองถูกต้อง")
    void fullChainIsStoredAndReassembled() throws Exception {
        UserDtls applicant = data.applicant();
        UserDtls officer = data.admin();
        Long id = envelopeFor(applicant).getId();

        assertThat(this.<Boolean>inTx(id, e -> incremental.start(e))).as("เอกสารที่ 1 ต้องเตรียมไฟล์ตั้งต้นได้").isTrue();
        SignatureRequest envelope = envelopes.findById(id).orElseThrow();
        assertThat(envelope.getPdfMode()).isEqualTo(PdfMode.INCREMENTAL);
        assertThat(envelope.getFieldLayoutJson()).contains("memo_no").contains("sig_applicant");

        CmsSigner applicantKey = p12(applicant.getName());
        inTx(id, e -> {
            SignatureStep step = applicantStep(e);
            step.setSignedAt(java.time.LocalDateTime.now());
            return incremental.sign(e, step, applicantKey, null, applicant.getName());
        });

        Map<String, String> office = new LinkedHashMap<>();
        office.put("memo_no", "อว 660301.26.8/1234");
        office.put("date", "1 ตุลาคม 2569");
        office.put("not_in_this_document", "ignored");
        assertThat(this.<Integer>inTx(id, e -> incremental.fill(e, office, officer))).isEqualTo(2);
        assertThat(this.<Integer>inTx(id, e -> incremental.fill(e, office, officer))).as("ค่าเดิมซ้ำไม่ต้องต่อ revision").isEqualTo(-1);

        CmsSigner officerKey = p12("เจ้าหน้าที่ ทดสอบ");
        inTx(id, e -> incremental.lock(e, officerKey, "เจ้าหน้าที่ ทดสอบ", officer));
        envelope = envelopes.findById(id).orElseThrow();
        assertThat(envelope.getPdfLockedAt()).isNotNull();
        assertThat(envelope.getCurrentRevisionNo()).isEqualTo(3);

        assertThat(revisions.chain(envelope.getId())).extracting(SignedPdfRevision::getKind)
                .containsExactly(SignedPdfRevision.Kind.BASE, SignedPdfRevision.Kind.SIGN,
                        SignedPdfRevision.Kind.FILL, SignedPdfRevision.Kind.LOCK);
        byte[] pdf = revisions.latest(envelope.getId());
        assertThat(PdfIncrementService.verify(pdf)).extracting(PdfIncrementService.SignatureCheck::field)
                .containsExactly("sig_applicant", BasePdfBuilder.LOCK_FIELD);
        assertThat(PdfIncrementService.verify(pdf)).allMatch(PdfIncrementService.SignatureCheck::valid);
        assertThat(IncrementalSigningService.values(pdf)).containsEntry("memo_no", "อว 660301.26.8/1234");

        assertThatThrownBy(() -> inTx(id, e -> incremental.fill(e, Map.of("memo_no", "แก้หลังปิด"), officer)))
                .as("ปิดเอกสารแล้วใส่ทับไม่ได้อีก").isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("เอกสารที่ไม่ได้เปิดใช้ — คงเป็นแบบเดิม")
    void otherDocumentsStayLegacy() {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);
        Long id = circulate(SignatureModule.ACADEMIC, draft.getId(), 2, "{}", applicant,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId()))).getId();

        assertThat(this.<Boolean>inTx(id, e -> incremental.start(e))).isFalse();
        SignatureRequest envelope = envelopes.findById(id).orElseThrow();
        assertThat(envelope.getPdfMode()).isEqualTo(PdfMode.LEGACY);
        assertThat(revisions.chain(envelope.getId())).isEmpty();
    }
}
