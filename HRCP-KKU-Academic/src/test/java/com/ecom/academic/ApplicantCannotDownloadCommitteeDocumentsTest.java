package com.ecom.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่มีรายชื่อกรรมการ ผู้ยื่นต้องโหลดไม่ได้
 *
 * <p>เอกสารที่ 4 (คำสั่งแต่งตั้งคณะอนุกรรมการ), 5 (ขอเชิญเป็นกรรมการ), 7 และ 8 มีรายชื่อ
 * กรรมการอยู่ ซึ่งเป็นความลับทางราชการ ผู้ยื่นเห็นได้เฉพาะเอกสารของตัวเอง (1, 2)
 * กับหนังสือแจ้งผล (9) ตาม {@code APPLICANT_VISIBLE_DOC_TYPES}
 *
 * <p>ด่านนั้นอยู่ใน {@code downloadDocument} แต่ {@code downloadDocumentByType} มีทางลัด:
 * ถ้าไม่มีแถวเอกสาร มันจะ render จากซองลายเซ็นส่งกลับไปเลยโดยไม่ตรวจอะไรทั้งสิ้น
 * ทั้งประเภทเอกสารและความเป็นเจ้าของคำร้อง
 */
@DisplayName("ผู้ยื่นโหลดเอกสารที่มีรายชื่อกรรมการไม่ได้")
class ApplicantCannotDownloadCommitteeDocumentsTest extends AbstractFlowTest {

    private static final int[] COMMITTEE_DOCS = { 4, 5, 7, 8 };

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls intruder;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        intruder = data.otherApplicant();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /** ซองลายเซ็นโดยไม่มีแถวเอกสาร — เงื่อนไขที่เปิดทางลัดใน downloadDocumentByType */
    private void envelopeOnly(Long requestId, int docType) {
        String frozen = "{\"committee_1_name\":\"กรรมการลับ\"}";
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(SignatureModule.ACADEMIC);
        envelope.setRequestId(requestId);
        envelope.setDocumentType(docType);
        envelope.setStatus(SignatureRequestStatus.COMPLETED);
        envelope.setFrozenJson(frozen);
        envelope.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        envelope.setVerificationCode("VC" + System.nanoTime());
        envelope.setCreatedAt(LocalDateTime.now());
        envelopes.save(envelope);
    }

    @Test
    @DisplayName("มีแถวเอกสารอยู่ — ต้องได้ 403")
    void refusedWhenTheDocumentRowExists() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
        for (int type : COMMITTEE_DOCS) {
            data.academicDocument(request, type, "{\"committee_1_name\":\"กรรมการลับ\"}");
        }

        for (int type : COMMITTEE_DOCS) {
            mvc.perform(get("/user/academic/request/" + request.getId() + "/document/" + type
                    + "/download").with(as(applicant)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("มีแต่ซองลายเซ็น ไม่มีแถวเอกสาร — ก็ต้องได้ 403 (ทางลัดที่เคยรั่ว)")
    void refusedWhenOnlyAnEnvelopeExists() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
        for (int type : COMMITTEE_DOCS) {
            envelopeOnly(request.getId(), type);
        }

        for (int type : COMMITTEE_DOCS) {
            mvc.perform(get("/user/academic/request/" + request.getId() + "/document/" + type
                    + "/download").with(as(applicant)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("คำร้องของคนอื่น แม้เป็นเอกสารที่ผู้ยื่นเห็นได้ ก็ต้องได้ 403")
    void refusedForSomeoneElsesRequest() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
        envelopeOnly(request.getId(), 9);

        mvc.perform(get("/user/academic/request/" + request.getId() + "/document/9/download")
                .with(as(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("เอกสารของตัวเองยังโหลดได้ตามปกติ")
    void ownDocumentsStillWork() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
        data.academicDocument(request, 1, "{\"applicant_name\":\"สมชาย ใจดีวิชาการ\"}");

        mvc.perform(get("/user/academic/request/" + request.getId() + "/document/1/download")
                .with(as(applicant)))
                .andExpect(status().isOk());
    }
}
