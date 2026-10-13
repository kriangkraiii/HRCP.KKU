package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
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
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * สถานะ "ส่งแก้ไขเอกสาร" ของคำร้องประเมินผลการสอน
 *
 * <p>เดิมการส่งเอกสารกลับไม่เปลี่ยนสถานะ ทั้งแอดมินและผู้ยื่นจึงเห็น "รับคำร้อง · กำลังดำเนินการ"
 * ทั้งที่งานหยุดรอผู้ยื่นแก้อยู่ ตอนนี้ส่งกลับแล้วเป็น "ส่งแก้ไขเอกสาร" และแก้ครบแล้วกลับไปขั้นเดิมเอง
 */
@DisplayName("สถานะส่งแก้ไขเอกสาร (เฟส 1)")
class DocumentRevisionStatusTest extends AbstractFlowTest {

    private static final String DOC = "{\"course_name\":\"x\"}";

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private AcademicRequest sendBack(RequestStatus from) {
        AcademicRequest request = data.evaluation(applicant, from);
        data.academicDocument(request, 1, DOC);
        academicService.openDocumentForRevision(request.getId(), 1, "แก้ชื่อรายวิชา");
        academicService.markRevisionRequested(request.getId(), 1, officer, "แก้ชื่อรายวิชา");
        return request;
    }

    private AcademicRequest reload(AcademicRequest request) {
        return academicService.findById(request.getId()).orElseThrow();
    }

    private void applicantResigns(AcademicRequest request) {
        var created = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "เอกสารที่ 1", DOC, List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        assertThat(created.error()).isNull();
        SignatureStep step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(created.request().getId())
                .stream().filter(s -> "applicant".equals(s.getSlotKey())).findFirst().orElseThrow();
        var signed = signatureWorkflow.sign(step.getId(), applicant, data.signatureFor(applicant).getId(), true,
                ActorContext.none(), null, null);
        assertThat(signed.error()).isNull();
    }

    @Test
    @DisplayName("ส่งกลับ: สถานะเป็นส่งแก้ไขเอกสาร จำขั้นเดิมไว้ แถบความคืบหน้ายังยืนที่ขั้นเดิม")
    void sendingBackChangesTheStatus() {
        AcademicRequest request = reload(sendBack(RequestStatus.RECEIVED));

        assertThat(request.getCurrentStatus()).isEqualTo(RequestStatus.DOCUMENT_REVISION);
        assertThat(request.getStatusBeforeRevision()).isEqualTo(RequestStatus.RECEIVED);
        assertThat(request.getProgressStatus()).isEqualTo(RequestStatus.RECEIVED);
        assertThat(academicService.getStatusHistory(request.getId()).get(0).getNote())
                .contains("เอกสารที่ 1").contains("แก้ชื่อรายวิชา");
    }

    @Test
    @DisplayName("ส่งกลับหลังมีผลประเมินแล้ว: สถานะคงเดิม (ผลผ่านต้องไม่หายระหว่างแก้)")
    void afterAResultTheStatusStays() {
        AcademicRequest request = reload(sendBack(RequestStatus.COLLEGE_ENDORSED));

        assertThat(request.getCurrentStatus()).isEqualTo(RequestStatus.COLLEGE_ENDORSED);
    }

    @Test
    @DisplayName("ผู้ยื่นลงนามใหม่ครบ: กลับไปขั้นเดิมเอง")
    void resigningReturnsToTheSameStep() {
        AcademicRequest request = sendBack(RequestStatus.SUB_COMMITTEE_APPOINTED);

        applicantResigns(request);

        awaitCondition("คำร้องกลับไปขั้นแต่งตั้งอนุกรรมการ",
                () -> reload(request).getCurrentStatus() == RequestStatus.SUB_COMMITTEE_APPOINTED);
        assertThat(reload(request).getStatusBeforeRevision()).isNull();
    }

    @Test
    @DisplayName("หนังสือเชิญลงนามครบระหว่างรอผู้ยื่นแก้: กลับขั้นเดิมแล้วเลื่อนเป็นนัดหมายต่อเอง")
    void staffDocumentSignedDuringRevisionAdvancesAfterReturn() {
        AcademicRequest request = sendBack(RequestStatus.SUB_COMMITTEE_APPOINTED);
        String frozen = "{\"memo_no\":\"อว 660301.26.4/ว.1\"}";
        SignatureRequest invitation = new SignatureRequest();
        invitation.setModule(SignatureModule.ACADEMIC);
        invitation.setRequestId(request.getId());
        invitation.setDocumentType(5);
        invitation.setStatus(SignatureRequestStatus.COMPLETED);
        invitation.setFrozenJson(frozen);
        invitation.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        invitation.setVerificationCode("VC" + System.nanoTime());
        invitation.setCreatedAt(LocalDateTime.now());
        invitation.setCompletedAt(LocalDateTime.now());
        invitation.setInitiatedBy(officer);
        envelopes.save(invitation);

        applicantResigns(request);

        awaitCondition("หนังสือเชิญที่ลงนามครบระหว่างแก้ไขเลื่อนสถานะต่อ",
                () -> reload(request).getCurrentStatus() == RequestStatus.MEETING_SCHEDULED);
    }

    @Test
    @DisplayName("อัปเดตสถานะเอง: เข้าส่งแก้ไขเอกสารไม่ได้ และออกได้เฉพาะกลับขั้นเดิม")
    void manualUpdatesAreGuarded() {
        AcademicRequest fresh = data.evaluation(applicant, RequestStatus.RECEIVED);
        assertThatThrownBy(() -> academicService.updateStatus(fresh.getId(), RequestStatus.DOCUMENT_REVISION,
                officer, null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ส่งกลับแก้ไข");

        AcademicRequest revising = sendBack(RequestStatus.RECEIVED);
        assertThatThrownBy(() -> academicService.updateStatus(revising.getId(),
                RequestStatus.SUB_COMMITTEE_APPOINTED, officer, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("รับคำร้อง");
    }
}
