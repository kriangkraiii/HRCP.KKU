package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * แอดมินดูเอกสารของผู้ยื่นได้อย่างเดียว จะแก้ต้องส่งกลับให้ผู้ยื่นแก้เอง
 *
 * <p>เดิมทั้งสองเฟสรับค่าที่ส่งมาทั้งก้อนแล้วเขียนทับ {@code json_data} โดยไม่ดูว่าใครส่ง สิ่งเดียว
 * ที่กันไว้คือ {@code <input type="hidden">} ที่เบราว์เซอร์ส่งค่ากลับมาเอง — เทสต์ชุดนี้จึงยิง POST
 * ตรงโดยไม่มีช่องซ่อนพวกนั้นเลย ซึ่งเป็นสิ่งที่วิธีเดิมกันไม่ได้
 */
@DisplayName("แอดมินแก้เอกสารของผู้ยื่นไม่ได้")
class AdminCannotEditApplicantDocumentTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

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

    @Nested
    @DisplayName("เฟส 1 — การประเมินการสอน")
    class Phase1 {

        @Test
        @DisplayName("แก้ชื่อผู้ยื่นในเอกสารที่ 1 ไม่ได้ ค่าเดิมยังอยู่")
        void cannotRewriteTheApplicantsOwnAnswers() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1,
                    "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\",\"course_name\":\"วิชาของผู้ยื่น\"}");

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("applicant_name", "แอดมินแอบแก้")
                    .param("course_name", "วิชาที่แอดมินแต่ง"))
                    .andExpect(status().is3xxRedirection());

            Map<String, String> stored = academicService.getLatestDocumentData(request.getId(), 1);
            assertThat(stored)
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้")
                    .containsEntry("course_name", "วิชาของผู้ยื่น");
        }

        @Test
        @DisplayName("กรอกเลขที่หนังสือได้ เพราะเป็นช่องของเจ้าหน้าที่")
        void mayStillWriteTheMemoNumber() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "อว 660301.26.8/123"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                    .containsEntry("memo_no", "อว 660301.26.8/123")
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }

        @Test
        @DisplayName("คอลัมน์ 'เจ้าหน้าที่' ของเอกสารที่ 2 ยังติ๊กได้ แต่ติ๊กของผู้ยื่นแตะไม่ได้")
        void mayFillTheOfficerColumnWithoutTouchingTheApplicants() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 2, "{\"chk_app_1\":\"✓\",\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/2")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("chk_off_1", "✓")
                    .param("text_1", "ครบถ้วน")
                    .param("chk_app_1", "")
                    .param("applicant_name", "แอดมินแอบแก้"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 2))
                    .containsEntry("chk_off_1", "✓")
                    .containsEntry("text_1", "ครบถ้วน")
                    .containsEntry("chk_app_1", "✓")
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }

        @Test
        @DisplayName("เอกสารของเจ้าหน้าที่ (3–9) ยังแก้ได้เต็มฉบับเหมือนเดิม")
        void officerDocumentsAreUnaffected() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 4, "{\"order_no\":\"เดิม\"}");

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/4")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("order_no", "ใหม่"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 4))
                    .containsEntry("order_no", "ใหม่");
        }
    }

    @Nested
    @DisplayName("เฟส 2 — การขอกำหนดตำแหน่ง")
    class Phase2 {

        @Test
        @DisplayName("แก้เอกสารที่ 1 ของผู้ยื่นไม่ได้เลย ไม่มีช่องของแอดมินในฉบับนั้น")
        void documentOneIsFullyReadOnly() throws Exception {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");

            mvc.perform(post("/admin/position/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("applicant_name", "แอดมินแอบแก้"))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.getLatestDocumentData(request.getId(), 1))
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }

        @Test
        @DisplayName("เอกสารที่ 3 กรอกชื่อคณบดีได้ แต่แก้คำรับรองของผู้ยื่นไม่ได้")
        void documentThreeSplitsDeanFieldsFromTheApplicants() throws Exception {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 3,
                    "{\"certification_date\":\"๑๐ มีนาคม ๒๕๖๙\",\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");

            mvc.perform(post("/admin/position/request/" + request.getId() + "/document/3")
                    .with(as(officer)).with(csrf())
                    .param("dean_name", "คณบดีตัวจริง")
                    .param("certification_date", "วันที่แอดมินแก้")
                    .param("applicant_name", "แอดมินแอบแก้"))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.getLatestDocumentData(request.getId(), 3))
                    .containsEntry("dean_name", "คณบดีตัวจริง")
                    .containsEntry("certification_date", "๑๐ มีนาคม ๒๕๖๙")
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }

        @Test
        @DisplayName("เอกสารที่ 7 ยังเป็นของแอดมินเต็มฉบับ")
        void documentSevenRemainsTheOfficersOwn() throws Exception {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 7, "{\"hr_officer_name\":\"เดิม\"}");

            mvc.perform(post("/admin/position/request/" + request.getId() + "/document/7")
                    .with(as(officer)).with(csrf())
                    .param("hr_officer_name", "เจ้าหน้าที่ใหม่"))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.getLatestDocumentData(request.getId(), 7))
                    .containsEntry("hr_officer_name", "เจ้าหน้าที่ใหม่");
        }
    }

    @Nested
    @DisplayName("ผู้ยื่นก็แตะช่องของเจ้าหน้าที่ไม่ได้เช่นกัน")
    class TheApplicantIsHeldToTheSameRule {

        @Test
        @DisplayName("ผู้ยื่นแก้เลขที่หนังสือของเฟส 1 ไม่ได้")
        void cannotForgeTheMemoNumber() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            data.academicDocument(request, 1, "{\"memo_no\":\"อว 1/2569\"}");

            mvc.perform(post("/user/academic/request/" + request.getId() + "/document-1")
                    .with(as(applicant)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "ผู้ยื่นแต่งเอง")
                    .param("applicant_name", "ชื่อจริงของผู้ยื่น"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                    .containsEntry("memo_no", "อว 1/2569")
                    .containsEntry("applicant_name", "ชื่อจริงของผู้ยื่น");
        }

        @Test
        @DisplayName("ผู้ยื่นลบหมายเหตุของเจ้าหน้าที่ด้วยการส่งค่าว่างไม่ได้")
        void cannotEraseTheOfficersNotes() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            data.academicDocument(request, 2, "{\"text_1\":\"เจ้าหน้าที่บันทึกไว้\"}");

            mvc.perform(post("/user/academic/request/" + request.getId() + "/document-2")
                    .with(as(applicant)).with(csrf())
                    .param("action", "draft")
                    .param("text_1", "")
                    .param("chk_app_1", "✓"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 2))
                    .containsEntry("text_1", "เจ้าหน้าที่บันทึกไว้")
                    .containsEntry("chk_app_1", "✓");
        }
    }
}
