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
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * หมายเหตุของเจ้าหน้าที่ในเอกสารที่ 2 ที่ยาวเกินช่องในไฟล์ลงนาม ต้องถูกกั้นตอนเจ้าหน้าที่ส่งเวียน
 * ไม่ใช่ไปล้มตอนผู้ยื่นลงนาม (ซอง 71 วันที่ 30 ก.ย. 2569)
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:2",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("หมายเหตุของเจ้าหน้าที่ยาวเกินช่อง — กั้นตอนส่งเวียน พร้อมบอกว่าช่องไหน")
class StaffRemarkFitTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls officer;
    private AcademicRequest request;

    @BeforeEach
    void setUp() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        applicant = data.applicant();
        officer = data.admin();
        request = data.evaluation(applicant, RequestStatus.RECEIVED);
    }

    private String doc2(String remark1) {
        StringBuilder json = new StringBuilder("{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"")
                .append(applicant.getName()).append('"');
        for (int i = 1; i <= 5; i++) {
            json.append(",\"chk_app_").append(i).append("\":\"✓\",\"chk_off_").append(i).append("\":\"")
                    .append(i == 1 ? "" : "✓").append('"');
        }
        return json.append(",\"text_1\":\"").append(remark1).append("\"}").toString();
    }

    private SignatureWorkflowService.Result sendAsOfficer(String json) {
        academicService.saveDraft(request, 2, json, "เอกสารที่ 2", null);
        return signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2, "เอกสารที่ 2", json,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId()),
                        new SignatureWorkflowService.SignerAssignment("hr", officer.getId())),
                null, officer, SignatureWorkflowService.ActorContext.none());
    }

    @Test
    @DisplayName("หมายเหตุยาวเกิน — ไม่ส่ง บอกเจ้าหน้าที่ว่าเป็นหมายเหตุข้อ 1 และไม่มีซองค้าง")
    void tooLongIsRefusedWhenSending() {
        var result = sendAsOfficer(doc2("ไม่พบเอกสารแผนการสอนฉบับสมบูรณ์ในแฟ้มที่ส่งมาทั้งหมดเลยสักชุด"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("หมายเหตุข้อ 1").contains("ยาวเกินช่อง");
        assertThat(envelopes.findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(
                SignatureModule.ACADEMIC, request.getId(), 2)).isEmpty();
    }

    @Test
    @DisplayName("ทางปกติ: ผู้ยื่นลงนามแล้ว เจ้าหน้าที่กรอกหมายเหตุยาวเกินแล้วกดส่งเวียนต่อ — กั้นไว้ ย่อแล้วส่งได้")
    void tooLongIsRefusedWhenForwarding() {
        String json = doc2("");
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

        var hr = List.of(new SignatureWorkflowService.SignerAssignment("hr", officer.getId()));
        academicService.saveDraft(request, 2, doc2("ไม่พบเอกสารแผนการสอนฉบับสมบูรณ์ในแฟ้มที่ส่งมาทั้งหมดเลยสักชุด"),
                "เอกสารที่ 2", null);
        var refused = signatureWorkflow.forwardToNextSigners(id, hr, null, officer,
                SignatureWorkflowService.ActorContext.none());
        assertThat(refused.ok()).isFalse();
        assertThat(refused.error()).contains("หมายเหตุข้อ 1");

        academicService.saveDraft(request, 2, doc2("ไม่เห็นเอกสาร"), "เอกสารที่ 2", null);
        var forwarded = signatureWorkflow.forwardToNextSigners(id, hr, null, officer,
                SignatureWorkflowService.ActorContext.none());
        assertThat(forwarded.ok()).as(forwarded.error()).isTrue();
    }

    @Test
    @DisplayName("เทมเพลตใหม่: \"ไม่เห็นเอกสาร\" ที่เคยล้ม ใส่ได้แล้ว และช่องหมายเหตุกว้างกว่าเดิม (36pt)")
    void theWiderColumnTakesTheRemarkThatUsedToFail() throws Exception {
        var result = sendAsOfficer(doc2("ไม่เห็นเอกสาร"));

        assertThat(result.ok()).as(result.error()).isTrue();
        SignatureRequest envelope = envelopes.findById(result.request().getId()).orElseThrow();
        var texts = new ObjectMapper().readTree(envelope.getFieldLayoutJson()).path("texts");
        for (var box : texts) {
            if (box.path("field").asText().startsWith("text_")) {
                assertThat(box.path("width").asDouble()).as(box.path("field").asText()).isGreaterThan(60);
            }
        }
    }
}
