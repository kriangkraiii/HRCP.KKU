package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ยื่นขอตำแหน่งด้วย "วิชาเดิม ปีการศึกษาเดิม" ซ้ำไม่ได้.
 *
 * <p>คู่ที่ตัดสินคือ <b>รหัสวิชา + ปีการศึกษา</b> ไม่ใช่รหัสวิชาอย่างเดียว เพราะ
 * อาจารย์คนหนึ่งสอนวิชาเดิมทุกปี และผลประเมินของคนละปีการศึกษาคือผลคนละชิ้น
 * การล็อกที่รหัสวิชาอย่างเดียวจะปิดทางอาจารย์ที่ขอประเมินวิชาเดิมปีถัดไป
 *
 * <p>กติกาเดียวกับผลงานวิจัย ({@code ScopusQueryService.STATUSES_THAT_FREE_A_PUBLICATION}):
 * คำร้องที่ยังเป็น {@code DRAFT} ยังไม่ได้ยื่นจึงยังไม่กินสิทธิ์ และคำร้องที่
 * {@code REJECTED} ต้องคืนวิชาให้ยื่นใหม่ได้ ไม่เช่นนั้นการถูกปฏิเสธครั้งเดียว
 * จะเผาผลประเมินใบนั้นทิ้งถาวร
 */
@DisplayName("กันยื่นซ้ำ: วิชาเดิม ปีการศึกษาเดิม ใช้ขอตำแหน่งได้ครั้งเดียว")
class EvaluationReuseLockTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionService;

    @Nested
    @DisplayName("วิชาที่ถูกใช้ไปแล้ว")
    class SpentCourses {

        @Test
        @DisplayName("ยื่นคำร้องด้วยผลประเมินใบหนึ่งแล้ว — ใบนั้นเลือกซ้ำไม่ได้")
        void submittedRequestConsumesItsEvaluation() {
            UserDtls applicant = data.applicant();
            AcademicRequest spent = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, spent);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .doesNotContain(spent.getId());
        }

        @Test
        @DisplayName("ผลประเมินคนละใบ แต่เป็นวิชาเดิมปีเดิม — เลือกไม่ได้เช่นกัน")
        void anotherEvaluationOfTheSameCourseAndYearIsAlsoSpent() {
            UserDtls applicant = data.applicant();
            AcademicRequest used = data.evaluationForCourse(applicant, "CP001101", "2568");
            AcademicRequest duplicate = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, used);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .as("กติกาผูกกับวิชา ไม่ใช่กับหมายเลขคำร้องประเมิน")
                    .extracting(AcademicRequest::getId)
                    .doesNotContain(duplicate.getId());
        }

        @Test
        @DisplayName("รหัสวิชาเขียนเว้นวรรคต่างกัน ก็คือวิชาเดียวกัน")
        void spacingInTheCourseCodeDoesNotDefeatTheRule() {
            UserDtls applicant = data.applicant();
            AcademicRequest used = data.evaluationForCourse(applicant, "CP 123 456", "2568");
            AcademicRequest sameCourse = data.evaluationForCourse(applicant, "cp123456", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, used);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .doesNotContain(sameCourse.getId());
        }

        @Test
        @DisplayName("ปีการศึกษาเลขไทยกับเลขอารบิก คือปีเดียวกัน")
        void thaiNumeralsInTheYearDoNotDefeatTheRule() {
            UserDtls applicant = data.applicant();
            AcademicRequest used = data.evaluationForCourse(applicant, "CP001101", "๒๕๖๘");
            AcademicRequest sameYear = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, used);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .doesNotContain(sameYear.getId());
        }
    }

    @Nested
    @DisplayName("สิ่งที่ต้องไม่ถูกล็อก")
    class WhatStaysAvailable {

        @Test
        @DisplayName("วิชาเดิม แต่คนละปีการศึกษา — ยังใช้ยื่นได้")
        void theSameCourseInALaterYearIsADifferentResult() {
            UserDtls applicant = data.applicant();
            AcademicRequest lastYear = data.evaluationForCourse(applicant, "CP001101", "2567");
            AcademicRequest thisYear = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, lastYear);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .as("อาจารย์สอนวิชาเดิมทุกปี ผลประเมินคนละปีคือคนละชิ้น")
                    .extracting(AcademicRequest::getId)
                    .containsExactly(thisYear.getId());
        }

        @Test
        @DisplayName("คำร้องที่ยังเป็นแบบร่าง — ยังไม่กินสิทธิ์")
        void aDraftHasNotSpentAnything() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.DRAFT, evaluation);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @DisplayName("คำร้องที่ไม่รับ (REJECTED) — คืนวิชาให้ยื่นใหม่ได้")
        void arefusalGivesTheCourseBack() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.REJECTED, evaluation);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .as("ถูกปฏิเสธครั้งเดียวต้องไม่เผาผลประเมินที่ยังใช้ได้ทิ้ง")
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @DisplayName("คำร้องของอาจารย์ท่านอื่น ไม่กินสิทธิ์วิชาของเรา")
        void anotherLecturersRequestSpendsNothingOfMine() {
            UserDtls somchai = data.applicant();
            UserDtls malee = data.otherApplicant();
            AcademicRequest hers = data.evaluationForCourse(malee, "CP001101", "2568");
            data.positionRequest(malee, PositionRequestStatus.DOCUMENT_RECEIVED, hers);
            AcademicRequest mine = data.evaluationForCourse(somchai, "CP001101", "2568");

            assertThat(positionService.getEligibleEvaluations(somchai.getId()))
                    .as("สองคนสอนวิชาเดียวกันได้ กติกาผูกกับเจ้าของผลประเมิน")
                    .extracting(AcademicRequest::getId)
                    .containsExactly(mine.getId());
        }

        @Test
        @DisplayName("ผลประเมินที่อ่านรหัสวิชาไม่ได้ ต้องไม่ล็อกกันเอง")
        void evaluationsWithoutACourseCodeDoNotCollide() {
            UserDtls applicant = data.applicant();
            AcademicRequest nameless = data.completedEvaluation(applicant,
                    com.ecom.academic.model.RequestStatus.COMPLETED, null);
            AcademicRequest alsoNameless = data.completedEvaluation(applicant,
                    com.ecom.academic.model.RequestStatus.COMPLETED, null);
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, nameless);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .as("คีย์ว่างสองใบต้องไม่ชนกันจนใบที่ยังไม่ได้ใช้หายไป")
                    .extracting(AcademicRequest::getId)
                    .containsExactly(alsoNameless.getId());
        }
    }

    @Nested
    @DisplayName("บอกเหตุผลได้ว่าใครใช้ไป")
    class ExplainingTheLock {

        @Test
        @DisplayName("รายการวิชาที่ใช้ไปแล้ว ระบุรหัสคำร้องที่ใช้")
        void spentCoursesNameTheRequestThatTookThem() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            var consumer = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, evaluation);

            assertThat(positionService.getSpentCourses(applicant.getId()))
                    .as("หน้าจอที่ซ่อนตัวเลือกเฉย ๆ ทำให้ผู้ยื่นเดาว่าทำไม")
                    .containsValue(consumer.getRequestCode());
        }
    }

    /**
     * A rule the screen merely honours is not a rule.
     *
     * <p>Hiding a spent course from the list stops the honest path and nothing
     * else: a tab left open before another request took the course, a bookmarked
     * form, or a hand-built POST all arrive with an evaluation id the list would
     * no longer offer. The two endpoints that can spend a course therefore check
     * for themselves — the same reasoning that put the publication rule in the
     * API and not only in the picker.
     */
    @Nested
    @DisplayName("บังคับที่ปลายทาง ไม่ใช่แค่ซ่อนในหน้าจอ")
    class EnforcedAtThePost {

        @Test
        @DisplayName("POST create-request ด้วยวิชาที่ใช้ไปแล้ว — ถูกปฏิเสธ ไม่สร้างแบบร่าง")
        void creatingARequestWithASpentCourseIsRefused() throws Exception {
            UserDtls applicant = data.applicant();
            AcademicRequest spent = data.evaluationForCourse(applicant, "CP001101", "2568");
            data.positionRequest(applicant, PositionRequestStatus.SENT_TO_HR, spent);

            mvc.perform(post("/user/position/create-request")
                    .param("evaluationId", String.valueOf(spent.getId()))
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .redirectedUrl("/user/position/dashboard?error=course_already_used"));

            assertThat(positionService.findDraftByApplicant(applicant.getId()))
                    .as("คำร้องที่ถูกปฏิเสธต้องไม่ทิ้งแบบร่างค้างไว้")
                    .isEmpty();
        }

        @Test
        @DisplayName("กดยื่นแบบร่างที่วิชาถูกกินไประหว่างทาง — ถูกปฏิเสธ ยังเป็นแบบร่าง")
        void submittingADraftWhoseCourseWasTakenMeanwhileIsRefused() throws Exception {
            UserDtls applicant = data.applicant();
            AcademicRequest first = data.evaluationForCourse(applicant, "CP001101", "2568");
            AcademicRequest second = data.evaluationForCourse(applicant, "CP001101", "2568");

            PositionRequest draft = data.positionRequest(applicant,
                    PositionRequestStatus.DRAFT, second);
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, first);

            mvc.perform(post("/user/position/request/" + draft.getId() + "/submit")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .redirectedUrl("/user/position/request/" + draft.getId()
                                    + "?error=course_already_used"));

            assertThat(positionService.findById(draft.getId()).orElseThrow().getCurrentStatus())
                    .as("แบบร่างที่ยื่นไม่ผ่านต้องไม่ถูกเลื่อนสถานะ")
                    .isEqualTo(PositionRequestStatus.DRAFT);
        }
    }

    /**
     * The screen has to explain itself.
     *
     * <p>Two evaluations that both say "คำร้อง KKU-ACAD-2569-0003, ยื่นเมื่อ…" are
     * indistinguishable — which is what the screen used to show, and it left the
     * applicant picking one at random. What tells them apart is the course, the
     * year, the result and how long it has left, so those are what the screen is
     * given. A choice already spent stays on the page, greyed, naming the request
     * that took it: silently dropping it would leave someone hunting for an
     * evaluation they know they have.
     */
    @Nested
    @DisplayName("หน้าจอเลือกผลประเมิน")
    class TheChoiceScreen {

        @Test
        @DisplayName("แสดงรายละเอียดของแต่ละผลประเมินให้แยกออกจากกันได้")
        void eachChoiceCarriesEnoughDetailToTellThemApart() throws Exception {
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
        void aspentChoiceStaysVisibleAndSaysWhoTookIt() {
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

            assertThat(html)
                    .as("ข้อมูลที่ไปไม่ถึง template ก็เท่ากับไม่มี")
                    .contains("CP001101")
                    .contains("2568");
        }
    }

    @Nested
    @DisplayName("ผู้ยื่นเห็นว่าคำร้องผูกอยู่กับผลประเมินใบไหน")
    class TheLinkIsVisible {

        @Test
        @DisplayName("หน้ารายละเอียดคำร้อง แสดงรายวิชาและผลประเมินที่ผูกไว้")
        void therequestPageShowsTheEvaluationItWasBuiltOn() throws Exception {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DRAFT, evaluation);

            String html = mvc.perform(get("/user/position/request/" + request.getId())
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .as("ผู้ยื่นต้องตรวจได้ว่าคำร้องนี้ยื่นด้วยผลประเมินใบไหน โดยไม่ต้องเปิดอีกหน้า")
                    .contains("CP001101")
                    .contains(evaluation.getRequestCode());
        }

        @Test
        @DisplayName("ถูกปฏิเสธเพราะวิชาซ้ำ — หน้าจอบอกเหตุผล ไม่ใช่ข้อความว่างเปล่า")
        void therefusalExplainsItself() throws Exception {
            UserDtls applicant = data.applicant();

            String html = mvc.perform(get("/user/position/dashboard")
                    .param("error", "course_already_used")
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .as("กล่องเตือนที่ไม่มีข้อความข้างในแย่กว่าไม่มีกล่องเลย")
                    .contains("ยื่นซ้ำ");
        }
    }
}
