package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ตำแหน่งที่ขอต้องสูงกว่าตำแหน่งที่ถืออยู่ และคำร้องขอตำแหน่งขอได้เฉพาะตำแหน่งที่ผลประเมินการสอนระบุไว้
 *
 * <p>บังคับทั้งสองเฟส: ผู้ที่เป็น ผศ. อยู่แล้ว ไม่ควรเสียเวลาไปขอประเมินการสอนเพื่อ ผศ. ตั้งแต่แรก
 * ส่วน ศ. ไม่ต้องใช้ผลประเมินการสอนเลย
 */
@DisplayName("กติกาตำแหน่งที่ขอ")
class PositionRankRuleTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private AcademicRequestService academicService;

    private UserDtls applicantHolding(String position) {
        UserDtls applicant = data.applicant();
        applicant.setAcademicPosition(position);
        return data.saveUser(applicant);
    }

    @Nested
    @DisplayName("เฟส 1: ขอรับการประเมินการสอน")
    class TeachingEvaluation {

        @Test
        @DisplayName("ผศ. ส่งเอกสารที่ 1 ขอประเมินเพื่อ ผศ. — ไม่บันทึก และบอกเหตุผล")
        void anAssistantProfessorCannotAskToBeEvaluatedForAssistantProfessor() throws Exception {
            UserDtls applicant = applicantHolding("ผู้ช่วยศาสตราจารย์");
            AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);

            mvc.perform(post("/user/academic/request/" + draft.getId() + "/document-1")
                    .param("current_position", "ผู้ช่วยศาสตราจารย์")
                    .param("chk1", "✓")
                    .param("course_code", "CP001101")
                    .param("course_name", "วิชาทดสอบ")
                    .param("academic_year", "2568")
                    .param("action", "submit")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/user/academic/request/" + draft.getId() + "/document-1"))
                    .andExpect(flash().attributeExists("error"));

            assertThat(academicService.getDocumentsByType(draft.getId(), 1)).isEmpty();
        }

        @Test
        @DisplayName("ช่องตำแหน่งปัจจุบันว่าง — ใช้ตำแหน่งจากโปรไฟล์ตัดสิน")
        void anEmptyCurrentPositionFallsBackToTheProfile() throws Exception {
            UserDtls applicant = applicantHolding("ผู้ช่วยศาสตราจารย์");
            AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);

            mvc.perform(post("/user/academic/request/" + draft.getId() + "/document-1")
                    .param("current_position", "")
                    .param("chk1", "✓")
                    .param("action", "submit")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(redirectedUrl("/user/academic/request/" + draft.getId() + "/document-1"))
                    .andExpect(flash().attributeExists("error"));

            assertThat(academicService.getDocumentsByType(draft.getId(), 1)).isEmpty();
        }

        @Test
        @DisplayName("ยื่นคำร้องประเมินที่เอกสารที่ 1 ขอตำแหน่งเดิม — ไม่ส่ง ยังเป็นแบบร่าง")
        void submittingAnEvaluationForTheRankAlreadyHeldIsRefused() throws Exception {
            UserDtls applicant = applicantHolding("อาจารย์");
            AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);
            data.academicDocument(draft, 1,
                    "{\"current_position\":\"ผู้ช่วยศาสตราจารย์\",\"chk1\":\"✓\"}");

            mvc.perform(post("/user/academic/request/" + draft.getId() + "/submit")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(redirectedUrl("/user/academic/request/" + draft.getId() + "/document-1"))
                    .andExpect(flash().attributeExists("error"));

            assertThat(academicService.findById(draft.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.DRAFT);
        }

        @Test
        @DisplayName("ฟอร์มเอกสารที่ 1 ปิดตัวเลือกตำแหน่งที่ถืออยู่แล้ว")
        void theFormDisablesTheRankAlreadyHeld() throws Exception {
            UserDtls applicant = applicantHolding("ผู้ช่วยศาสตราจารย์");
            AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);

            String html = mvc.perform(get("/user/academic/request/" + draft.getId() + "/document-1")
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(inputTag(html, "targetPos1")).contains("disabled");
            assertThat(inputTag(html, "targetPos2")).doesNotContain("disabled");
        }
    }

    @Nested
    @DisplayName("เฟส 2: สร้างคำร้องขอตำแหน่ง")
    class CreatingAPositionRequest {

        @Test
        @DisplayName("ตำแหน่งที่ขอดึงมาจากผลประเมินการสอน")
        void theTargetComesFromTheEvaluation() throws Exception {
            UserDtls applicant = applicantHolding("อาจารย์");
            AcademicRequest evaluation = data.evaluationFor(applicant,
                    AcademicRank.ASSOCIATE_PROFESSOR, "อาจารย์");

            mvc.perform(post("/user/position/create-request")
                    .param("evaluationId", String.valueOf(evaluation.getId()))
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.findDraftByApplicant(applicant.getId()))
                    .get()
                    .extracting(PositionRequest::getTargetPosition)
                    .isEqualTo("รองศาสตราจารย์");
        }

        @Test
        @DisplayName("ผลประเมินขอ ผศ. แต่เอกสารที่ 1 ระบุว่าเป็น ผศ. อยู่แล้ว — ไม่สร้างคำร้อง")
        void anEvaluationForTheRankAlreadyHeldCannotStartARequest() throws Exception {
            UserDtls applicant = applicantHolding("อาจารย์");
            AcademicRequest evaluation = data.evaluationFor(applicant,
                    AcademicRank.ASSISTANT_PROFESSOR, "ผู้ช่วยศาสตราจารย์");

            mvc.perform(post("/user/position/create-request")
                    .param("evaluationId", String.valueOf(evaluation.getId()))
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(redirectedUrl("/user/position/dashboard?error=rank_not_higher"));

            assertThat(positionService.findDraftByApplicant(applicant.getId())).isEmpty();
        }

        @Test
        @DisplayName("รศ. ขอ ศ. — สร้างคำร้องได้โดยไม่ต้องมีผลประเมินการสอน")
        void anAssociateProfessorCanApplyForProfessorWithoutAnEvaluation() throws Exception {
            UserDtls applicant = applicantHolding("รองศาสตราจารย์");

            mvc.perform(post("/user/position/create-professor-request")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection());

            PositionRequest draft = positionService.findDraftByApplicant(applicant.getId()).orElseThrow();
            assertThat(draft.getTargetPosition()).isEqualTo("ศาสตราจารย์");
            assertThat(draft.getLinkedEvaluation()).isNull();
        }

        @Test
        @DisplayName("ศ. ขอ ศ. — ไม่สร้างคำร้อง")
        void aProfessorCannotApplyForProfessor() throws Exception {
            UserDtls applicant = applicantHolding("ศาสตราจารย์");

            mvc.perform(post("/user/position/create-professor-request")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(redirectedUrl("/user/position/dashboard?error=rank_not_higher"));

            assertThat(positionService.findDraftByApplicant(applicant.getId())).isEmpty();
        }

        @Test
        @DisplayName("หน้าเริ่มคำร้อง มีทางขอ ศ. ให้คนที่ยังไม่เป็น ศ. เท่านั้น")
        void theProfessorRouteIsOfferedOnlyBelowProfessor() throws Exception {
            UserDtls associate = applicantHolding("รองศาสตราจารย์");
            String offered = mvc.perform(get("/user/position/new-request")
                    .with(user(associate.getEmail()).roles("USER")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(offered).contains("/user/position/create-professor-request");

            UserDtls professor = applicantHolding("ศาสตราจารย์");
            String notOffered = mvc.perform(get("/user/position/new-request")
                    .with(user(professor.getEmail()).roles("USER")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(notOffered).doesNotContain("/user/position/create-professor-request");
        }
    }

    @Nested
    @DisplayName("เฟส 2: ยื่นคำร้องขอตำแหน่ง")
    class SubmittingAPositionRequest {

        private void expectRefusal(UserDtls applicant, PositionRequest draft, String error) throws Exception {
            mvc.perform(post("/user/position/request/" + draft.getId() + "/submit")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(redirectedUrl("/user/position/request/" + draft.getId() + "?error=" + error));

            assertThat(positionService.findById(draft.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(PositionRequestStatus.DRAFT);
        }

        @Test
        @DisplayName("ตำแหน่งที่ขอไม่ตรงกับที่ผลประเมินระบุ — ไม่ส่ง")
        void aTargetThatDiffersFromTheEvaluationIsRefused() throws Exception {
            UserDtls applicant = applicantHolding("อาจารย์");
            AcademicRequest evaluation = data.evaluationFor(applicant,
                    AcademicRank.ASSOCIATE_PROFESSOR, "อาจารย์");
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                    evaluation, "ผู้ช่วยศาสตราจารย์");

            expectRefusal(applicant, draft, "position_mismatch");
        }

        @Test
        @DisplayName("ขอ ผศ. โดยไม่มีผลประเมินการสอน — ไม่ส่ง")
        void anAssistantProfessorRequestWithoutAnEvaluationIsRefused() throws Exception {
            UserDtls applicant = applicantHolding("อาจารย์");
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                    null, "ผู้ช่วยศาสตราจารย์");

            expectRefusal(applicant, draft, "evaluation_required");
        }

        @Test
        @DisplayName("ขอตำแหน่งที่ไม่สูงกว่าตำแหน่งปัจจุบัน — ไม่ส่ง")
        void aTargetNotAboveTheCurrentRankIsRefused() throws Exception {
            UserDtls applicant = applicantHolding("ศาสตราจารย์");
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                    null, "ศาสตราจารย์");

            expectRefusal(applicant, draft, "rank_not_higher");
        }
    }

    @Nested
    @DisplayName("ตำแหน่งที่ขอในเอกสารถูกตรึงไว้")
    class TheTargetIsPinned {

        @Test
        @DisplayName("บันทึกเอกสารที่ 1 ด้วยตำแหน่งอื่น — ระบบเก็บตำแหน่งตามคำร้อง")
        void savingDocumentOneWithAnotherTargetKeepsTheRequestTarget() throws Exception {
            UserDtls applicant = applicantHolding("อาจารย์");
            AcademicRequest evaluation = data.evaluationFor(applicant,
                    AcademicRank.ASSOCIATE_PROFESSOR, "อาจารย์");
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                    evaluation, "รองศาสตราจารย์");

            mvc.perform(post("/user/position/request/" + draft.getId() + "/document/1")
                    .param("target_position", "ผู้ช่วยศาสตราจารย์")
                    .param("action", "draft")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.getLatestDocumentData(draft.getId(), 1))
                    .containsEntry("target_position", "รองศาสตราจารย์");
        }

        @Test
        @DisplayName("เอกสารที่ 2 ระบุตำแหน่งอื่น — ไม่เขียนทับตำแหน่งของคำร้อง")
        void documentTwoDoesNotOverwriteTheRequestTarget() {
            UserDtls applicant = applicantHolding("อาจารย์");
            AcademicRequest evaluation = data.evaluationFor(applicant,
                    AcademicRank.ASSOCIATE_PROFESSOR, "อาจารย์");
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                    evaluation, "รองศาสตราจารย์");

            positionService.saveDraft(draft, 2, "{\"request_position\":\"ผู้ช่วยศาสตราจารย์\"}",
                    "เอกสารที่ 2", "ADMIN");

            assertThat(positionService.findById(draft.getId()).orElseThrow().getTargetPosition())
                    .isEqualTo("รองศาสตราจารย์");
        }
    }

    /** The whole {@code <input ...>} tag carrying this id, so its attributes can be checked. */
    private static String inputTag(String html, String id) {
        Matcher m = Pattern.compile("<input[^>]*id=\"" + id + "\"[^>]*>").matcher(html);
        assertThat(m.find()).as("ไม่พบ input id=" + id).isTrue();
        return m.group();
    }
}
