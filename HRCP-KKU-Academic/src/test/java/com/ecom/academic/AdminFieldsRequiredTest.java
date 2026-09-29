package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ช่องของเจ้าหน้าที่ต้องกรอกครบก่อนบันทึก/ส่งต่อ
 *
 * <p>สองจังหวะที่เจ้าหน้าที่กรอกช่องของตัวเอง: ตรวจเอกสารของผู้ยื่นแล้วส่งต่อให้ผู้ลงนามคนถัดไป
 * และออกเลขที่หนังสือ/วันที่หลังลงนามครบ ทั้งสองจังหวะต้องกรอกครบก่อนถึงจะไปต่อได้
 */
@DisplayName("ช่องของเจ้าหน้าที่ต้องกรอกครบ")
class AdminFieldsRequiredTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private SignatureStep stepFor(SignatureRequest envelope, String slotKey) {
        return signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> slotKey.equals(s.getSlotKey()))
                .findFirst().orElseThrow();
    }

    private void applicantSigns(SignatureRequest envelope) {
        var result = signatureWorkflow.sign(stepFor(envelope, "applicant").getId(), applicant,
                data.signatureFor(applicant).getId(), true, ActorContext.none(), null, null);
        assertThat(result.error()).isNull();
    }

    /** เอกสารที่ 2 เฟส 1: ผู้ยื่นเซ็นแล้ว รอเจ้าหน้าที่ตรวจแล้วปล่อยให้นักทรัพยากรบุคคลลงนาม */
    private SignatureRequest documentTwoSignedByApplicant(AcademicRequest request, UserDtls hr) {
        String doc2 = "{\"chk_app_1\":\"✓\",\"chk_off_1\":\"\",\"chk_off_2\":\"\",\"chk_off_3\":\"\","
                + "\"chk_off_4\":\"\",\"chk_off_5\":\"\"}";
        data.academicDocument(request, 2, doc2);
        SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 2, doc2, applicant,
                List.of(new SignerAssignment("applicant", applicant.getId()),
                        new SignerAssignment("hr", hr.getId())));
        applicantSigns(envelope);
        return envelope;
    }

    @Test
    @DisplayName("เอกสารที่ 2: เจ้าหน้าที่ยังไม่ได้ตรวจ — ปล่อยให้ลงนามต่อไม่ได้")
    void documentTwoCannotBeReleasedUnchecked() {
        UserDtls hr = data.user("hr-required@example.invalid", "เจ้าหน้าที่", "บุคคล", "ROLE_ADMIN");
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        SignatureRequest envelope = documentTwoSignedByApplicant(request, hr);

        Result released = signatureWorkflow.startCirculation(envelope.getId(), officer, ActorContext.none());

        assertThat(released.ok()).isFalse();
        assertThat(released.error()).contains("ช่องของเจ้าหน้าที่ยังกรอกไม่ครบ").contains("5 ช่อง");
        assertThat(stepFor(envelope, "hr").getStatus()).isEqualTo(SignatureStepStatus.WAITING);
    }

    @Test
    @DisplayName("เอกสารที่ 2: แต่ละแถวติ๊ก หรือไม่ติ๊กแต่เขียนหมายเหตุ — ปล่อยได้")
    void aRowWithANoteInsteadOfATickIsAccepted() {
        UserDtls hr = data.user("hr-note@example.invalid", "เจ้าหน้าที่", "บุคคล", "ROLE_ADMIN");
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        SignatureRequest envelope = documentTwoSignedByApplicant(request, hr);

        academicService.saveOfficeFieldsAcrossCopies(request, 2, Map.of(
                "chk_off_1", "✓", "chk_off_2", "✓", "chk_off_4", "✓", "chk_off_5", "✓",
                "text_3", "ไม่มีสื่อการสอนแนบมา"), "เอกสารที่ 2", true);
        assertThat(signatureWorkflow.startCirculation(envelope.getId(), officer, ActorContext.none()).ok())
                .as("ข้อ 3 ไม่ติ๊กแต่มีหมายเหตุ")
                .isTrue();
    }

    @Test
    @DisplayName("เอกสารที่ 2: ข้อที่ไม่ติ๊กและไม่มีหมายเหตุ — ยังปล่อยไม่ได้")
    void aRowWithNeitherTickNorNoteIsRefused() {
        UserDtls hr = data.user("hr-gap@example.invalid", "เจ้าหน้าที่", "บุคคล", "ROLE_ADMIN");
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        SignatureRequest envelope = documentTwoSignedByApplicant(request, hr);

        academicService.saveOfficeFieldsAcrossCopies(request, 2, Map.of(
                "chk_off_1", "✓", "chk_off_2", "✓", "chk_off_4", "✓", "chk_off_5", "✓"),
                "เอกสารที่ 2", true);
        Result released = signatureWorkflow.startCirculation(envelope.getId(), officer, ActorContext.none());
        assertThat(released.ok()).isFalse();
        assertThat(released.error()).contains("1 ช่อง");
    }

    @Test
    @DisplayName("เฟส 2 เอกสารที่ 3: ยังไม่กรอกตำแหน่งคณบดี — ส่งต่อไม่ได้")
    void positionDocumentThreeCannotBeForwardedIncomplete() {
        UserDtls dean = data.user("dean-required@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        String doc3 = "{\"applicant_name\":\"สมชาย ใจดี\"}";
        data.positionDocument(request, 3, doc3);
        SignatureRequest envelope = circulate(SignatureModule.POSITION, request.getId(), 3, doc3, applicant,
                List.of(new SignerAssignment("applicant", applicant.getId())));
        applicantSigns(envelope);
        positionService.saveOfficeFieldsAcrossCopies(request, 3,
                Map.of("verify_date", "3 ตุลาคม 2569"), "เอกสารที่ 3", true);

        Result forwarded = signatureWorkflow.forwardToNextSigners(envelope.getId(),
                List.of(new SignerAssignment("dean", dean.getId())), null, officer, ActorContext.none());
        assertThat(forwarded.ok()).isFalse();
        assertThat(forwarded.error()).contains("ช่องของเจ้าหน้าที่");

        positionService.saveOfficeFieldsAcrossCopies(request, 3,
                Map.of("dean_position", "คณบดี"), "เอกสารที่ 3", true);
        assertThat(signatureWorkflow.forwardToNextSigners(envelope.getId(),
                List.of(new SignerAssignment("dean", dean.getId())), null, officer, ActorContext.none()).ok())
                .isTrue();
    }

    /** ซองที่ลงนามครบทุกช่องแล้ว โดยไม่ต้องเดินเวียนลงนามจริง */
    private void fullySigned(SignatureModule module, Long requestId, int docType) {
        String frozen = "{}";
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(module);
        envelope.setRequestId(requestId);
        envelope.setDocumentType(docType);
        envelope.setStatus(SignatureRequestStatus.COMPLETED);
        envelope.setFrozenJson(frozen);
        envelope.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        envelope.setVerificationCode("VC" + System.nanoTime());
        envelope.setCreatedAt(LocalDateTime.now());
        envelope = envelopes.save(envelope);
        int order = 1;
        for (var slot : com.ecom.academic.service.SignatureAnchorRegistry.slotsOf(module, docType)) {
            SignatureStep step = new SignatureStep();
            step.setSignatureRequest(envelope);
            step.setStepOrder(order++);
            step.setSlotKey(slot.slotKey());
            step.setAnchorPlaceholder("{{sig_" + slot.slotKey() + "}}");
            step.setStatus(SignatureStepStatus.SIGNED);
            signatureSteps.save(step);
        }
    }

    @Test
    @DisplayName("ออกเลข เฟส 1: เลขที่หนังสือมีแต่รหัสหน่วยงาน — กดบันทึกไม่ได้ ค่าเดิมไม่เปลี่ยน")
    void officeSaveNeedsARealNumber() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        data.academicDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่น\",\"memo_no\":\"\",\"date\":\"\"}");
        fullySigned(SignatureModule.ACADEMIC, request.getId(), 1);

        mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                .with(as(officer)).with(csrf())
                .param("action", "submit")
                .param("memo_no", "อว 660301.26.8/")
                .param("date", "29 กันยายน 2569"))
                .andExpect(redirectedUrl("/admin/academic/request/" + request.getId()
                        + "/document/1?error=office_incomplete"));

        assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                .containsEntry("memo_no", "")
                .containsEntry("date", "");
    }

    @Test
    @DisplayName("ออกเลข เฟส 2: ไม่ได้ลงวันที่ — กดบันทึกไม่ได้")
    void positionOfficeSaveNeedsTheDate() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.SCREENING_COMMITTEE, null);
        data.positionDocument(request, 8, "{\"date\":\"\"}");
        fullySigned(SignatureModule.POSITION, request.getId(), 8);

        mvc.perform(post("/admin/position/request/" + request.getId() + "/document/8")
                .with(as(officer)).with(csrf())
                .param("date", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/position/request/" + request.getId()
                        + "/document/8?error=office_incomplete"));
    }
}
