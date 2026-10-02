package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignedDocumentRenderer;
import com.ecom.academic.service.pdf.IncrementalSigningService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่ 2: ผู้ยื่นลงนามแล้ว เจ้าหน้าที่ติ๊กช่องของตัวเองและเขียนหมายเหตุ — ตัวอย่างเอกสารต้องเห็นทันที
 * เดิมค่าเหล่านี้เข้าไฟล์ลงนามตอนเจ้าหน้าที่ลงนามเท่านั้น ระหว่างนั้นตัวอย่างไม่แสดงอะไรเลย (คำร้อง 24)
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:2",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("เอกสารที่ 2 — ช่องเจ้าหน้าที่ที่ติ๊กหลังผู้ยื่นลงนาม ขึ้นในตัวอย่างทันที")
class OfficerTicksPreviewTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignedDocumentRenderer renderer;

    @Autowired
    private SignedPdfRevisionService revisions;

    private UserDtls applicant;
    private AcademicRequest request;

    @BeforeEach
    void setUp() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        applicant = data.applicant();
        request = data.evaluation(applicant, RequestStatus.RECEIVED);
    }

    private String doc2(boolean officerTicked, String remark1) {
        StringBuilder json = new StringBuilder("{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"")
                .append(applicant.getName()).append('"');
        for (int i = 1; i <= 5; i++) {
            json.append(",\"chk_app_").append(i).append("\":\"✓\",\"chk_off_").append(i).append("\":\"")
                    .append(officerTicked ? "✓" : "").append('"');
        }
        return json.append(",\"text_1\":\"").append(remark1).append("\"}").toString();
    }

    @Test
    @DisplayName("ติ๊กแล้วตัวอย่าง PDF แสดงเครื่องหมายและหมายเหตุ โดยไม่เขียนลงไฟล์ลงนามจนกว่าจะลงนาม")
    void ticksShowInThePreviewBeforeTheOfficerSigns() throws Exception {
        String json = doc2(false, "");
        academicService.saveDraft(request, 2, json, "เอกสารที่ 2", null);
        var sent = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2, "เอกสารที่ 2", json,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())),
                null, applicant, SignatureWorkflowService.ActorContext.none());
        assertThat(sent.ok()).as(sent.error()).isTrue();
        Long id = sent.request().getId();
        var signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant);
        var step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(id).get(0);
        assertThat(signatureWorkflow.sign(step.getId(), applicant, signature.getId(), true,
                SignatureWorkflowService.ActorContext.none(), com.ecom.support.TestCertificates.PIN).ok()).isTrue();

        // เจ้าหน้าที่ติ๊กครบและเขียนหมายเหตุ (บันทึกร่างอัตโนมัติ)
        academicService.saveDraft(request, 2, doc2(true, "ไม่เห็นเอกสาร"), "เอกสารที่ 2", null);
        SignatureRequest envelope = envelopes.findById(id).orElseThrow();
        int chainBefore = revisions.chain(id).size();

        byte[] preview = renderer.renderForDownload(envelope, "pdf");

        assertThat(IncrementalSigningService.values(preview))
                .containsEntry("chk_off_1", "✓")
                .containsEntry("chk_off_5", "✓")
                .containsEntry("text_1", "ไม่เห็นเอกสาร");
        assertThat(revisions.chain(id)).as("ตัวอย่างไม่สร้างฉบับแก้ไขใหม่ในไฟล์ลงนาม").hasSize(chainBefore);
        assertThat(IncrementalSigningService.values(revisions.latest(id)).getOrDefault("chk_off_1", ""))
                .as("ไฟล์ลงนามจริงยังไม่มีค่า — เขียนตอนเจ้าหน้าที่ลงนาม").isBlank();
    }
}
