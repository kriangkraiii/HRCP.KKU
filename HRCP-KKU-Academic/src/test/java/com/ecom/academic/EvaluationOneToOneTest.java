package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผลประเมินการสอน 1 ฉบับ ใช้ยื่นขอตำแหน่งได้ 1 คำร้อง.
 *
 * <p>Flow ข้อ 30 หัวข้อ 9: คำร้องขอตำแหน่งแนบ "เอกสารประเมินการสอน จำนวน 1 ชุด" และข้อ 11
 * แจ้งผลประเมินให้ผู้ขอ "เพื่อดำเนินการยื่นขอกำหนดตำแหน่งต่อไป" ผลประเมินหนึ่งฉบับจึงเป็นของ
 * คำร้องหนึ่งฉบับ ไม่มีสถานะไหนคืนสิทธิ์ เพราะ Flow ไม่มีการปฏิเสธ มีแต่ตีกลับให้แก้ แบบร่างที่ถูกลบ
 * คือแถวที่หายไปจริง ผลประเมินจึงว่างลงเอง
 *
 * <p>กุญแจคือตัวผลประเมิน ไม่ใช่วิชา ใช้วิชาเดิมยื่นตำแหน่งใหม่ได้ ถ้าประเมินใหม่พร้อมเนื้อหาใหม่
 */
@DisplayName("ผลประเมินการสอน 1 ฉบับ ↔ คำร้องขอตำแหน่ง 1 ฉบับ")
class EvaluationOneToOneTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionService;

    @Nested
    @DisplayName("ผลประเมินที่ผูกกับคำร้องแล้ว ใช้ซ้ำไม่ได้")
    class OneEvaluationOneRequest {

        @Test
        @DisplayName("ยื่นคำร้องไปแล้ว — ผลประเมินใบนั้นเลือกซ้ำไม่ได้")
        void aSubmittedRequestHoldsItsEvaluation() {
            UserDtls applicant = data.applicant();
            AcademicRequest used = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, used);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .doesNotContain(used.getId());
        }

        @Test
        @DisplayName("แบบร่างก็ถือผลประเมินไว้ — สองคำร้องผูกผลประเมินเดียวกันไม่ได้ในทุกสถานะ")
        void aDraftAlsoHoldsItsEvaluation() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DRAFT, evaluation);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .doesNotContain(evaluation.getId());
        }

        @Test
        @DisplayName("ยกเลิกแบบร่าง — ผลประเมินกลับมาใช้ได้")
        void cancellingTheDraftGivesTheEvaluationBack() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, evaluation);

            positionService.deleteDraftRequest(draft.getId(), applicant.getId());

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @DisplayName("ฐานข้อมูลเองก็ไม่ยอมให้ผูกผลประเมินเดียวกันกับสองคำร้อง")
        void theDatabaseRefusesASecondLink() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.SENT_TO_HR, evaluation);

            assertThatThrownBy(() ->
                    data.positionRequest(applicant, PositionRequestStatus.DRAFT, evaluation))
                    .as("สองแท็บกดพร้อมกันผ่านการตรวจของ service ได้ทั้งคู่ ต้องมีอะไรกันที่ชั้นล่างสุด")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("สิ่งที่ต้องไม่ถูกล็อก")
    class WhatStaysAvailable {

        @Test
        @DisplayName("ผลประเมินอีกฉบับของวิชาเดิม ปีเดิม — ยังใช้ยื่นได้")
        void anotherEvaluationOfTheSameCourseIsStillUsable() {
            UserDtls applicant = data.applicant();
            AcademicRequest used = data.evaluationForCourse(applicant, "CP001101", "2568");
            AcademicRequest fresh = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.SENT_TO_HR, used);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .as("ใช้วิชาเดิมยื่นตำแหน่งใหม่ได้ ถ้าประเมินใหม่พร้อมเนื้อหาใหม่")
                    .extracting(AcademicRequest::getId)
                    .containsExactly(fresh.getId());
        }

        @Test
        @DisplayName("คำร้องของอาจารย์ท่านอื่น ไม่กระทบผลประเมินของเรา")
        void anotherLecturersRequestSpendsNothingOfMine() {
            UserDtls somchai = data.applicant();
            UserDtls malee = data.otherApplicant();
            AcademicRequest hers = data.evaluationForCourse(malee, "CP001101", "2568");
            data.positionRequest(malee, PositionRequestStatus.DOCUMENT_RECEIVED, hers);
            AcademicRequest mine = data.evaluationForCourse(somchai, "CP001101", "2568");

            assertThat(positionService.getEligibleEvaluations(somchai.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(mine.getId());
        }
    }

    @Test
    @DisplayName("รายการผลประเมินที่ใช้ไปแล้ว ระบุรหัสคำร้องที่ใช้")
    void spentEvaluationsNameTheRequestThatTookThem() {
        UserDtls applicant = data.applicant();
        AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
        PositionRequest consumer = data.positionRequest(applicant,
                PositionRequestStatus.DOCUMENT_RECEIVED, evaluation);

        assertThat(positionService.getSpentEvaluations(applicant.getId()))
                .containsEntry(evaluation.getId(), consumer.getRequestCode());
    }

    /**
     * หน้าจอที่ซ่อนตัวเลือกกันได้แค่ทางปกติ แท็บที่เปิดค้างไว้หรือ POST ที่ประกอบเองมาถึงพร้อม id
     * ที่หน้าจอไม่เสนอให้แล้ว ปลายทางจึงต้องตรวจเอง
     */
    @Test
    @DisplayName("POST create-request ด้วยผลประเมินที่ใช้ไปแล้ว — ถูกปฏิเสธ ไม่สร้างแบบร่าง")
    void creatingARequestWithASpentEvaluationIsRefused() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest spent = data.evaluationForCourse(applicant, "CP001101", "2568");
        data.positionRequest(applicant, PositionRequestStatus.SENT_TO_HR, spent);

        mvc.perform(post("/user/position/create-request")
                .param("evaluationId", String.valueOf(spent.getId()))
                .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/position/dashboard?error=evaluation_already_used"));

        assertThat(positionService.findDraftByApplicant(applicant.getId())).isEmpty();
    }

    @Nested
    @DisplayName("หน้าจอเลือกผลประเมิน")
    class TheChoiceScreen {

        @Test
        @DisplayName("แสดงรายละเอียดของแต่ละผลประเมินให้แยกออกจากกันได้")
        void eachChoiceCarriesEnoughDetailToTellThemApart() {
            UserDtls applicant = data.applicant();
            data.evaluationForCourse(applicant, "CP001101", "2568");

            var choices = positionService.getEvaluationChoices(applicant.getId());

            assertThat(choices).hasSize(1);
            assertThat(choices.get(0).summary().courseCode()).isEqualTo("CP001101");
            assertThat(choices.get(0).summary().academicYear()).isEqualTo("2568");
            assertThat(choices.get(0).summary().resultLevel()).isEqualTo("ชำนาญ");
            assertThat(choices.get(0).spent()).isFalse();
        }

        @Test
        @DisplayName("ตัวเลือกที่ใช้ไปแล้ว ยังอยู่บนหน้าจอ พร้อมบอกว่าคำร้องไหนใช้")
        void aSpentChoiceStaysVisibleAndSaysWhoTookIt() {
            UserDtls applicant = data.applicant();
            AcademicRequest spent = data.evaluationForCourse(applicant, "CP001101", "2568");
            PositionRequest consumer = data.positionRequest(applicant,
                    PositionRequestStatus.SENT_TO_HR, spent);

            var choices = positionService.getEvaluationChoices(applicant.getId());

            assertThat(choices).hasSize(1);
            assertThat(choices.get(0).spent()).isTrue();
            assertThat(choices.get(0).spentBy()).isEqualTo(consumer.getRequestCode());
        }

        @Test
        @DisplayName("หน้าเลือกผลประเมิน แสดงรหัสวิชาให้เห็นจริงบนหน้าเว็บ")
        void thePageActuallyRendersTheCourse() throws Exception {
            UserDtls applicant = data.applicant();
            data.evaluationForCourse(applicant, "CP001101", "2568");

            String html = mvc.perform(get("/user/position/new-request")
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("CP001101").contains("2568");
        }
    }

    @Nested
    @DisplayName("ผู้ยื่นเห็นว่าคำร้องผูกอยู่กับผลประเมินใบไหน")
    class TheLinkIsVisible {

        @Test
        @DisplayName("หน้ารายละเอียดคำร้อง แสดงรายวิชาและผลประเมินที่ผูกไว้")
        void theRequestPageShowsTheEvaluationItWasBuiltOn() throws Exception {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DRAFT, evaluation);

            String html = mvc.perform(get("/user/position/request/" + request.getId())
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("CP001101").contains(evaluation.getRequestCode());
        }

        @Test
        @DisplayName("ถูกปฏิเสธเพราะผลประเมินถูกใช้แล้ว — หน้าจอบอกเหตุผล ไม่ใช่ข้อความว่างเปล่า")
        void theRefusalExplainsItself() throws Exception {
            UserDtls applicant = data.applicant();

            String html = mvc.perform(get("/user/position/dashboard")
                    .param("error", "evaluation_already_used")
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("ผลประเมินการสอนฉบับนี้ถูกใช้ยื่นขอตำแหน่งไปแล้ว");
        }
    }
}
