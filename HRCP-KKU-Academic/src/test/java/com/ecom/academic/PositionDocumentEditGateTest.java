package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ประตูแก้ไขเอกสารของผู้ยื่นในเฟส 2
 *
 * <p>ก่อนหน้านี้เฟส 2 ไม่มีประตูนี้เลย — ทั้ง GET และ POST ฝั่งผู้ยื่นเช็กแค่ความเป็นเจ้าของคำร้อง
 * ผู้ยื่นจึงแก้เอกสารที่ลงนามครบไปแล้วได้ ทำให้เนื้อหาไม่ตรงกับแฮชที่ซองลายเซ็นเก็บไว้ และลายเซ็น
 * ทุกใบในซองนั้นเป็นโมฆะย้อนหลังโดยไม่มีใครรู้
 *
 * <p>เรื่องนี้กลายเป็นเรื่องใหญ่เมื่อแอดมินแก้เอกสารของผู้ยื่นไม่ได้อีกต่อไป เพราะ "ส่งกลับให้ผู้ยื่นแก้"
 * เป็นทางเดียวที่เหลือ มันจึงต้องเปิดสิทธิ์ให้ได้จริง ไม่ใช่แค่ยกเลิกซองลายเซ็นแล้วจบ
 */
@DisplayName("ประตูแก้ไขเอกสารของผู้ยื่น (เฟส 2)")
class PositionDocumentEditGateTest extends AbstractFlowTest {

    private static final int APPLICANT_DOC = 3;
    private static final int OFFICER_DOC = 7;

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private PositionRequest requestWith(PositionRequestStatus status) {
        PositionRequest request = data.positionRequest(applicant, status, null);
        data.positionDocument(request, APPLICANT_DOC, "{\"applicant_name\":\"เดิม\"}");
        return request;
    }

    private void applicantSaves(PositionRequest request, int type, String name) throws Exception {
        mvc.perform(post("/user/position/request/" + request.getId() + "/document/" + type)
                .with(user(applicant.getEmail()).roles("USER")).with(csrf())
                .param("action", "draft")
                .param("applicant_name", name))
                .andExpect(status().is3xxRedirection());
    }

    @Nested
    @DisplayName("ก่อนยื่นคำร้อง")
    class WhileStillADraft {

        @Test
        @DisplayName("แก้เอกสารของตัวเองได้ตามปกติ")
        void theApplicantMayEditFreely() {
            assertThat(positionService.canApplicantEditDocument(
                    requestWith(PositionRequestStatus.DRAFT), APPLICANT_DOC)).isTrue();
        }
    }

    @Nested
    @DisplayName("หลังยื่นคำร้องแล้ว")
    class OnceSubmitted {

        @Test
        @DisplayName("แก้ไม่ได้จนกว่าจะถูกส่งกลับมา")
        void theDocumentIsOutOfTheApplicantsHands() {
            assertThat(positionService.canApplicantEditDocument(
                    requestWith(PositionRequestStatus.DOCUMENT_RECEIVED), APPLICANT_DOC)).isFalse();
        }

        @Test
        @DisplayName("POST ที่ยิงตรงก็ถูกปฏิเสธ ไม่ใช่แค่ซ่อนปุ่ม")
        void aDirectPostIsRefusedToo() throws Exception {
            PositionRequest request = requestWith(PositionRequestStatus.DOCUMENT_RECEIVED);

            applicantSaves(request, APPLICANT_DOC, "ผู้ยื่นแอบแก้หลังยื่นแล้ว");

            assertThat(positionService.getLatestDocumentData(request.getId(), APPLICANT_DOC))
                    .containsEntry("applicant_name", "เดิม");
        }

        @Test
        @DisplayName("สถานะปิดแล้วก็ยังแก้ไม่ได้")
        void aFinishedRequestStaysClosed() {
            assertThat(positionService.canApplicantEditDocument(
                    requestWith(PositionRequestStatus.SENT_TO_HR), APPLICANT_DOC)).isFalse();
        }
    }

    @Nested
    @DisplayName("เมื่อแอดมินส่งกลับให้แก้")
    class WhenSentBack {

        @Test
        @DisplayName("ปุ่มส่งกลับคืนสิทธิ์แก้ให้ผู้ยื่นจริง ไม่ใช่แค่ยกเลิกซองลายเซ็น")
        void theApplicantActuallyRegainsTheRightToEdit() throws Exception {
            PositionRequest request = requestWith(PositionRequestStatus.DOCUMENT_RECEIVED);
            assertThat(positionService.canApplicantEditDocument(request, APPLICANT_DOC)).isFalse();

            mvc.perform(post("/admin/position/request/" + request.getId()
                    + "/document/" + APPLICANT_DOC + "/request-resign")
                    .with(user(officer.getEmail()).roles("ADMIN")).with(csrf())
                    .param("reason", "ชื่อรายวิชาไม่ตรงกับที่ลงทะเบียน"))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.canApplicantEditDocument(
                    positionService.findById(request.getId()).orElseThrow(), APPLICANT_DOC)).isTrue();
            assertThat(positionService.getRevisionNote(request.getId(), APPLICANT_DOC))
                    .isEqualTo("ชื่อรายวิชาไม่ตรงกับที่ลงทะเบียน");
        }

        @Test
        @DisplayName("แก้แล้วบันทึกได้จริง")
        void andCanSaveTheCorrection() throws Exception {
            PositionRequest request = requestWith(PositionRequestStatus.DOCUMENT_RECEIVED);
            positionService.openDocumentForRevision(request.getId(), APPLICANT_DOC, "แก้ชื่อด้วย");

            applicantSaves(request, APPLICANT_DOC, "ชื่อที่แก้แล้ว");

            assertThat(positionService.getLatestDocumentData(request.getId(), APPLICANT_DOC))
                    .containsEntry("applicant_name", "ชื่อที่แก้แล้ว");
        }

        @Test
        @DisplayName("ส่งกลับเอกสารที่ผู้ยื่นยังไม่เคยกรอก ก็ยังเปิดสิทธิ์ให้ได้")
        void worksEvenForADocumentTheApplicantNeverFilledIn() {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);

            positionService.openDocumentForRevision(request.getId(), APPLICANT_DOC, "กรุณากรอกด้วย");

            assertThat(positionService.canApplicantEditDocument(request, APPLICANT_DOC)).isTrue();
        }
    }

    @Nested
    @DisplayName("เอกสารของเจ้าหน้าที่")
    class OfficerDocuments {

        @Test
        @DisplayName("ผู้ยื่นแก้เอกสารที่ 7 ไม่ได้แม้คำร้องยังเป็นแบบร่าง")
        void areNeverTheApplicantsToEdit() {
            assertThat(positionService.canApplicantEditDocument(
                    requestWith(PositionRequestStatus.DRAFT), OFFICER_DOC)).isFalse();
        }
    }
}
