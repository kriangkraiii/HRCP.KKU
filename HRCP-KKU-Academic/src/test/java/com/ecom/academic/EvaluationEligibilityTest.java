package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * Which teaching evaluations let a professor start a position request.
 *
 * <p>This is step 11 of the flow — "แจ้งผลการประเมินผลการสอนให้ผู้ขอกำหนดตำแหน่ง
 * ทราบเพื่อดำเนินการยื่นขอกำหนดตำแหน่งทางวิชาการต่อไป" — and it is the single
 * hinge between the two phases. Get it wrong in one direction and a professor
 * who passed cannot proceed; wrong in the other and an expired result is
 * accepted.
 *
 * <p>Two independent definitions of "a usable result" exist in the code today
 * and they disagree. Both are pinned here: the tests that pass describe what the
 * system does now, and the {@code @Disabled} ones describe what the flow
 * document says it should do. See GAP-20 and GAP-21.
 */
@DisplayName("คุณสมบัติผู้ยื่น: ผลประเมินการสอนที่ใช้ขอตำแหน่งได้")
class EvaluationEligibilityTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private AcademicRequestService academicService;

    /** A Buddhist-era date, in the all-numeric form {@code isExpired} can read. */
    private static String numericThaiDate(LocalDate date) {
        return date.getDayOfMonth() + "/" + date.getMonthValue() + "/" + (date.getYear() + 543);
    }

    @Nested
    @DisplayName("สถานะของคำร้องประเมิน")
    class StatusRules {

        @Test
        @DisplayName("COMPLETED พร้อมเอกสารที่ 8 ที่ไม่มีวันหมดอายุ — ใช้ยื่นได้")
        void completedWithoutExpiryIsEligible() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation =
                    data.completedEvaluation(applicant, RequestStatus.COMPLETED, null);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @DisplayName("COMPLETED แต่ไม่มีเอกสารที่ 8 — ใช้ยื่นไม่ได้")
        void completedWithoutDocument8IsNotEligible() {
            UserDtls applicant = data.applicant();
            data.evaluation(applicant, RequestStatus.COMPLETED);

            assertThat(positionService.getEligibleEvaluations(applicant.getId())).isEmpty();
        }

        @Test
        @DisplayName("ยังไม่จบกระบวนการ (DRAFT / RECEIVED / นัดประชุม) — ใช้ยื่นไม่ได้")
        void unfinishedEvaluationsAreNotEligible() {
            UserDtls applicant = data.applicant();
            for (RequestStatus status : new RequestStatus[] { RequestStatus.DRAFT,
                    RequestStatus.RECEIVED, RequestStatus.SUB_COMMITTEE_APPOINTED,
                    RequestStatus.MEETING_SCHEDULED }) {
                data.completedEvaluation(applicant, status, null);
            }

            assertThat(positionService.getEligibleEvaluations(applicant.getId())).isEmpty();
        }

        @Test
        @DisplayName("ไม่ผ่าน / ไม่รับคำร้อง — ใช้ยื่นไม่ได้")
        void failedOrRejectedIsNotEligible() {
            UserDtls applicant = data.applicant();
            data.completedEvaluation(applicant, RequestStatus.COMPLETED_FAIL, null);
            data.completedEvaluation(applicant, RequestStatus.REJECTED, null);

            assertThat(positionService.getEligibleEvaluations(applicant.getId())).isEmpty();
        }

        @Test
        @DisplayName("ผลประเมินของอาจารย์ท่านอื่น ไม่ถูกเสนอให้เลือกเด็ดขาด")
        void otherApplicantsEvaluationIsNeverOffered() {
            UserDtls somchai = data.applicant();
            UserDtls malee = data.otherApplicant();
            data.completedEvaluation(malee, RequestStatus.COMPLETED, null);

            assertThat(positionService.getEligibleEvaluations(somchai.getId())).isEmpty();
        }

        /**
         * GAP-20. The dashboard reads {@code getLatestEvaluationExpiry}, which
         * accepts COMPLETED_PASS, while the "start a position request" screen
         * reads {@code getEligibleEvaluations}, which does not. A professor
         * whose request stops at COMPLETED_PASS therefore sees a countdown
         * telling them their result is valid, and a form telling them they have
         * no result to use.
         *
         * <p>The assertion below records today's behaviour so the contradiction
         * is visible in the suite rather than only in the report; the intended
         * behaviour is the disabled test that follows.
         */
        @Test
        @DisplayName("GAP-20: COMPLETED_PASS ยังใช้ยื่นไม่ได้ (พฤติกรรมปัจจุบัน)")
        void completedPassIsNotEligibleToday() {
            UserDtls applicant = data.applicant();
            data.completedEvaluation(applicant, RequestStatus.COMPLETED_PASS, null);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .as("ถ้าเทสข้อนี้เริ่ม fail แปลว่า GAP-20 ถูกแก้แล้ว — ให้เปิดเทสถัดไปแทน")
                    .isEmpty();

            assertThat(academicService.getLatestEvaluationExpiry(applicant.getId()))
                    .as("แต่แดชบอร์ดกลับบอกว่าผลประเมินยังใช้ได้ — นี่คือความขัดแย้ง")
                    .isNotNull();
        }

        @Test
        @Disabled("GAP-20: ต้องรวมนิยาม 'ผลประเมินที่ใช้ได้' ให้เหลือที่เดียวก่อน")
        @DisplayName("GAP-20 (spec): COMPLETED_PASS ควรใช้ยื่นขอตำแหน่งได้")
        void completedPassShouldBeEligible() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation =
                    data.completedEvaluation(applicant, RequestStatus.COMPLETED_PASS, null);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }
    }

    @Nested
    @DisplayName("วันหมดอายุของผลประเมิน")
    class ExpiryRules {

        @Test
        @DisplayName("วันหมดอายุในอนาคต (รูปแบบตัวเลข) — ยังใช้ยื่นได้")
        void futureExpiryIsEligible() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.completedEvaluation(applicant, RequestStatus.COMPLETED,
                    numericThaiDate(LocalDate.now().plusYears(2)));

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @DisplayName("วันหมดอายุที่ผ่านมาแล้ว (รูปแบบตัวเลข) — ใช้ยื่นไม่ได้")
        void pastExpiryIsNotEligible() {
            UserDtls applicant = data.applicant();
            data.completedEvaluation(applicant, RequestStatus.COMPLETED,
                    numericThaiDate(LocalDate.now().minusYears(1)));

            assertThat(positionService.getEligibleEvaluations(applicant.getId())).isEmpty();
        }

        /**
         * GAP-21. {@code isExpired} strips every non-digit and then expects three
         * numbers. A date written with a Thai month name leaves only two, so the
         * method falls through to its {@code return false} — "assume not
         * expired". Documents in this system routinely carry Thai month names;
         * {@code AcademicRequestService.parseThaiDate} exists specifically to
         * read them.
         *
         * <p>The result is that expiry is, in practice, almost never enforced.
         */
        @Test
        @DisplayName("GAP-21: วันหมดอายุที่เขียนด้วยชื่อเดือนไทย ถูกมองว่ายังไม่หมดอายุเสมอ")
        void thaiMonthNameExpiryIsIgnoredToday() {
            UserDtls applicant = data.applicant();
            // 1 มกราคม 2560 = 1 January 2017, long past.
            AcademicRequest evaluation = data.completedEvaluation(applicant,
                    RequestStatus.COMPLETED, "1 มกราคม 2560");

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .as("""
                            ผลประเมินที่หมดอายุตั้งแต่ปี 2560 ยังถูกเสนอให้ใช้ยื่นได้
                            เพราะ isExpired() อ่านชื่อเดือนไทยไม่ออกแล้วคืนค่า 'ยังไม่หมดอายุ'
                            ถ้าเทสข้อนี้เริ่ม fail แปลว่า GAP-21 ถูกแก้แล้ว""")
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @Disabled("GAP-21: ต้องให้ isExpired ใช้ตัวอ่านวันที่ไทยตัวเดียวกับ parseThaiDate ก่อน")
        @DisplayName("GAP-21 (spec): วันหมดอายุที่เขียนด้วยชื่อเดือนไทยและผ่านมาแล้ว ต้องใช้ยื่นไม่ได้")
        void thaiMonthNameExpiryShouldBeHonoured() {
            UserDtls applicant = data.applicant();
            data.completedEvaluation(applicant, RequestStatus.COMPLETED, "1 มกราคม 2560");

            assertThat(positionService.getEligibleEvaluations(applicant.getId())).isEmpty();
        }

        @Test
        @DisplayName("แดชบอร์ดคำนวณวันหมดอายุจากวันยื่น + 3 ปี เมื่อเอกสารที่ 8 ไม่มีวันที่")
        void expiryFallsBackToSubmissionDatePlusThreeYears() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation =
                    data.completedEvaluation(applicant, RequestStatus.COMPLETED, null);

            assertThat(academicService.getLatestEvaluationExpiry(applicant.getId()))
                    .isEqualTo(evaluation.getSubmissionDate().plusYears(3));
        }

        @Test
        @DisplayName("ไม่มีผลประเมินที่จบแล้ว — ไม่มีวันหมดอายุให้แสดง")
        void noCompletedEvaluationMeansNoExpiry() {
            UserDtls applicant = data.applicant();
            data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);

            assertThat(academicService.getLatestEvaluationExpiry(applicant.getId())).isNull();
        }
    }
}
