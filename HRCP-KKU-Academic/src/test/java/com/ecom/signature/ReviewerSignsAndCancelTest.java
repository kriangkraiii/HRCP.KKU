package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่ 2 เฟส 1: ผู้ยื่นลงนามมาก่อนแล้ว เจ้าหน้าที่ผู้ตรวจ (นักทรัพยากรบุคคล) ลงนามเองต่อจากนั้น
 * และ "ยกเลิกการเวียนลงนาม" หลังส่งต่อ ถอนเฉพาะส่วนที่ส่งต่อไป ไม่ใช่ลบลายเซ็นของผู้ยื่นทิ้ง
 */
@DisplayName("เอกสารที่ 2: ผู้ตรวจลงนามเอง และยกเลิกการเวียนไม่ลบลายเซ็นผู้ยื่น")
class ReviewerSignsAndCancelTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls officer;
    private UserDtls otherStaff;
    private AcademicRequest request;
    private UserSignature officerSignature;
    private String doc2;

    @BeforeEach
    void applicantHasSigned() {
        applicant = data.applicant();
        officer = data.admin();
        otherStaff = data.user("other.hr@" + com.ecom.support.TestDataFactory.DOMAIN, "สมหญิง", "สายตรวจการ", "ROLE_ADMIN");
        UserSignature applicantSignature = data.signatureFor(applicant);
        officerSignature = data.signatureFor(officer);
        request = data.evaluation(applicant, RequestStatus.RECEIVED);
        StringBuilder json = new StringBuilder("{\"applicant_name\":\"").append(applicant.getName()).append('"');
        for (int i = 1; i <= 5; i++) {
            json.append(",\"chk_app_").append(i).append("\":\"✓\",\"chk_off_").append(i).append("\":\"✓\"");
        }
        doc2 = json.append('}').toString();
        academicService.saveDraft(request, 2, doc2, "เอกสารที่ 2", null);
        SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 2, doc2, applicant,
                List.of(new SignerAssignment("applicant", applicant.getId())));
        SignatureStep step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
        assertThat(signatureWorkflow.sign(step.getId(), applicant, applicantSignature.getId(), true,
                ActorContext.none()).ok()).isTrue();
    }

    private SignatureRequest envelope() {
        return envelopes.findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(
                SignatureModule.ACADEMIC, request.getId(), 2).get(0);
    }

    private SignatureWorkflowService.Result forwardTo(UserDtls hr) {
        return signatureWorkflow.forwardToNextSigners(envelope().getId(),
                List.of(new SignerAssignment("hr", hr.getId())), null, officer, ActorContext.none());
    }

    private List<SignatureStep> steps() {
        return signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope().getId());
    }

    @Test
    @DisplayName("ส่งช่องนักทรัพยากรบุคคลให้คนอื่น — ปฏิเสธ ต้องเป็นผู้ตรวจเอง")
    void theReviewerSlotCannotGoToSomeoneElse() {
        var result = forwardTo(otherStaff);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("เจ้าหน้าที่ผู้ตรวจสอบเอกสารเอง");
        assertThat(steps()).extracting(SignatureStep::getSlotKey).containsExactly("applicant");
    }

    @Test
    @DisplayName("ผู้ตรวจลงนามเอง — ถึงคิวทันที ลงนามแล้วซองครบ ผู้ยื่นไม่ต้องลงนามซ้ำ")
    void theReviewerSignsStraightAway() {
        var forwarded = forwardTo(officer);
        assertThat(forwarded.ok()).as(forwarded.error()).isTrue();
        SignatureStep hr = steps().get(1);
        assertThat(hr.getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        assertThat(hr.getSigner().getId()).isEqualTo(officer.getId());

        var signed = signatureWorkflow.sign(hr.getId(), officer, officerSignature.getId(), true, ActorContext.none(), null,
                com.ecom.academic.service.SignatureAnchorRegistry.APPROVED);
        assertThat(signed.ok()).as(signed.error()).isTrue();
        assertThat(envelope().getStatus()).isEqualTo(SignatureRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("ยกเลิกหลังส่งต่อ — ถอนเฉพาะขั้นที่ส่งต่อ ลายเซ็นผู้ยื่นยังอยู่ ส่งต่อใหม่แล้วลงนามครบได้")
    void cancellingAfterForwardingKeepsTheApplicantSignature() {
        assertThat(forwardTo(officer).ok()).isTrue();

        var cancelled = signatureWorkflow.cancel(envelope().getId(), officer, "กรอกหมายเหตุผิด", ActorContext.none());

        assertThat(cancelled.ok()).isTrue();
        SignatureRequest envelope = envelope();
        assertThat(envelope.getStatus()).as("กลับเป็น 'ผู้ยื่นลงนามแล้ว รอเจ้าหน้าที่ตรวจ'")
                .isEqualTo(SignatureRequestStatus.COMPLETED);
        assertThat(envelope.isCirculationStarted()).isFalse();
        assertThat(steps()).extracting(SignatureStep::getSlotKey, SignatureStep::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("applicant", SignatureStepStatus.SIGNED));

        // ส่งต่อใหม่ได้ และขั้นที่ถูกถอนไม่ค้างขวางไม่ให้ซองลงนามครบ
        assertThat(forwardTo(officer).ok()).isTrue();
        SignatureStep hr = steps().get(1);
        assertThat(signatureWorkflow.sign(hr.getId(), officer, officerSignature.getId(), true, ActorContext.none(), null,
                com.ecom.academic.service.SignatureAnchorRegistry.APPROVED).ok())
                .isTrue();
        assertThat(envelope().getStatus()).isEqualTo(SignatureRequestStatus.COMPLETED);
        assertThat(steps()).allMatch(s -> s.getStatus() == SignatureStepStatus.SIGNED);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(UserDtls who) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private String page(String url, UserDtls who) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).with(as(who)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("แผงของผู้ตรวจ: ไม่มีตัวเลือกผู้ลงนาม ปุ่ม 'ยืนยันความถูกต้องและลงนาม' แล้วไปหน้าลงนามเลย")
    void thePanelOffersToSignRatherThanForward() throws Exception {
        String html = page("/admin/academic/request/" + request.getId() + "/document/2", officer);
        assertThat(html).contains("data-reviewer-signs").contains("ยืนยันความถูกต้องและลงนาม")
                .doesNotContain("ยืนยันความถูกต้องและส่งเวียนลงนามต่อ");

        String redirect = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/esign/envelope/" + envelope().getId() + "/forward")
                        .param("slotKeys", "hr").param("signerUserIds", String.valueOf(officer.getId()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(as(officer)))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(redirect).isEqualTo("/esign/sign/" + steps().get(1).getId());
    }

    @Test
    @DisplayName("เจ้าหน้าที่คนอื่นเปิดขั้นของผู้ตรวจ: ไม่มีปุ่มลงนามตอนนี้ หน้าลงนามดูได้อย่างเดียว และไม่นับเป็นการเปิดดูของผู้ลงนาม")
    void someoneElsesStepIsViewOnly() throws Exception {
        assertThat(forwardTo(officer).ok()).isTrue();
        SignatureStep hr = steps().get(1);

        assertThat(page("/admin/academic/request/" + request.getId() + "/document/2", otherStaff))
                .doesNotContain("/esign/sign/" + hr.getId() + "\"");
        assertThat(page("/admin/academic/request/" + request.getId() + "/document/2", officer))
                .contains("/esign/sign/" + hr.getId() + "\"");

        String signPage = page("/esign/sign/" + hr.getId(), otherStaff);
        assertThat(signPage).contains("data-view-only").contains("ลงนามแทนไม่ได้");
        assertThat(signatureSteps.findById(hr.getId()).orElseThrow().getViewedAt())
                .as("การเปิดของคนอื่นไม่ใช่หลักฐานว่าผู้ลงนามเปิดดูแล้ว").isNull();

        page("/esign/sign/" + hr.getId(), officer);
        assertThat(signatureSteps.findById(hr.getId()).orElseThrow().getViewedAt()).isNotNull();
    }

    @Test
    @DisplayName("ผู้ยื่นยังไม่ลงนาม — ยกเลิกทั้งรอบตามเดิม")
    void cancellingBeforeTheApplicantSignsCancelsTheRound() {
        AcademicRequest other = data.evaluation(applicant, RequestStatus.RECEIVED);
        academicService.saveDraft(other, 2, doc2, "เอกสารที่ 2", null);
        SignatureRequest fresh = circulate(SignatureModule.ACADEMIC, other.getId(), 2, doc2, applicant,
                List.of(new SignerAssignment("applicant", applicant.getId())));

        assertThat(signatureWorkflow.cancel(fresh.getId(), officer, null, ActorContext.none()).ok()).isTrue();
        assertThat(envelopes.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.CANCELLED);
    }
}
