package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
 * <p>There used to be two definitions of "a usable result" in the code and they
 * disagreed — one accepted only {@code COMPLETED}, the other also
 * {@code COMPLETED_PASS} — so the two screens a professor sees contradicted each
 * other (GAP-20). And the expiry check could not read a Thai month name, so it
 * answered "not expired" to every date written the way the documents actually
 * write them (GAP-21). Both are now one rule,
 * {@code AcademicRequestService.findUsableEvaluations}, and these tests hold it
 * there.
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
        void aFailedEvaluationIsNotEligible() {
            UserDtls applicant = data.applicant();
            data.completedEvaluation(applicant, RequestStatus.COMPLETED_FAIL, null);

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
         * GAP-20, fixed. The dashboard's expiry countdown and the "start a
         * position request" screen used to answer this question separately and
         * disagree: a professor whose request stopped at COMPLETED_PASS was
         * shown a valid result on one screen and none on the next. Both now ask
         * {@code AcademicRequestService.findUsableEvaluations}.
         */
        @Test
        @DisplayName("COMPLETED_PASS ใช้ยื่นขอตำแหน่งได้ (ข้อ 11 — แจ้งผลแล้วให้ยื่นต่อได้)")
        void completedPassIsEligible() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation =
                    data.completedEvaluation(applicant, RequestStatus.COMPLETED_PASS, null);

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @DisplayName("แดชบอร์ดกับหน้ายื่นคำร้องต้องตอบตรงกันเสมอ")
        void theTwoScreensAgree() {
            UserDtls applicant = data.applicant();
            data.completedEvaluation(applicant, RequestStatus.COMPLETED_PASS, null);

            boolean dashboardSaysValid =
                    academicService.getLatestEvaluationExpiry(applicant.getId()) != null;
            boolean formOffersIt =
                    !positionService.getEligibleEvaluations(applicant.getId()).isEmpty();

            assertThat(formOffersIt)
                    .as("ถ้าสองหน้านี้ไม่ตรงกัน ผู้ยื่นจะเจอทางตันโดยไม่มีคำอธิบาย")
                    .isEqualTo(dashboardSaysValid);
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
         * GAP-21, fixed. The old reader stripped every non-digit and then
         * expected three numbers, so a date written with a Thai month name left
         * it with two and it fell through to "assume not expired". Expiry was in
         * practice never enforced. There is now one date reader,
         * {@code AcademicRequestService.parseThaiDate}, and it takes both forms.
         */
        @Test
        @DisplayName("วันหมดอายุที่เขียนด้วยชื่อเดือนไทยและผ่านมาแล้ว — ใช้ยื่นไม่ได้")
        void thaiMonthNameExpiryIsHonoured() {
            UserDtls applicant = data.applicant();
            // 1 มกราคม 2560 = 1 January 2017, long past.
            data.completedEvaluation(applicant, RequestStatus.COMPLETED, "1 มกราคม 2560");

            assertThat(positionService.getEligibleEvaluations(applicant.getId())).isEmpty();
        }

        @Test
        @DisplayName("วันหมดอายุชื่อเดือนไทยที่ยังไม่ถึง — ยังใช้ได้")
        void futureThaiMonthNameExpiryIsStillValid() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation = data.completedEvaluation(applicant,
                    RequestStatus.COMPLETED, "1 มกราคม 2599");

            assertThat(positionService.getEligibleEvaluations(applicant.getId()))
                    .extracting(AcademicRequest::getId)
                    .containsExactly(evaluation.getId());
        }

        @Test
        @DisplayName("ตัวอ่านวันที่รับได้ทั้งชื่อเดือนไทย ตัวเลข เลขไทย และปี พ.ศ.")
        void theDateReaderTakesEveryFormTheDocumentsUse() {
            LocalDateTime expected = LocalDateTime.of(2026, 2, 17, 0, 0);

            assertThat(AcademicRequestService.parseThaiDate("17 กุมภาพันธ์ 2569")).isEqualTo(expected);
            assertThat(AcademicRequestService.parseThaiDate("17/2/2569")).isEqualTo(expected);
            assertThat(AcademicRequestService.parseThaiDate("17-2-2569")).isEqualTo(expected);
            assertThat(AcademicRequestService.parseThaiDate("๑๗ กุมภาพันธ์ ๒๕๖๙")).isEqualTo(expected);
            assertThat(AcademicRequestService.parseThaiDate("17 กุมภาพันธ์ 2026")).isEqualTo(expected);

            assertThat(AcademicRequestService.parseThaiDate("ไม่ใช่วันที่")).isNull();
            assertThat(AcademicRequestService.parseThaiDate("")).isNull();
            assertThat(AcademicRequestService.parseThaiDate(null)).isNull();
        }

        @Test
        @DisplayName("แดชบอร์ดคำนวณวันหมดอายุจากวันยื่น + 3 ปี เมื่อเอกสารที่ 8 ไม่มีวันที่")
        void expiryFallsBackToSubmissionDatePlusThreeYears() {
            UserDtls applicant = data.applicant();
            AcademicRequest evaluation =
                    data.completedEvaluation(applicant, RequestStatus.COMPLETED, null);

            assertThat(academicService.getLatestEvaluationExpiry(applicant.getId()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                    .isEqualTo(evaluation.getSubmissionDate().plusYears(3).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        }

        /**
         * GAP-22, fixed. This method used to ignore its argument entirely and
         * ask the repository for the requests of applicant {@code null}, which
         * is nobody — so the reminder job it backs could never have found
         * anything to warn about.
         */
        @Test
        @DisplayName("ค้นหาผลประเมินที่ใกล้หมดอายุ — คืนเฉพาะรายการที่หมดอายุก่อนวันที่ระบุ")
        void expiringSoonHonoursTheCutoff() {
            UserDtls applicant = data.applicant();

            AcademicRequest lapsingSoon = data.evaluation(applicant, RequestStatus.COMPLETED);
            lapsingSoon.setEvaluationExpiryDate(LocalDateTime.now().plusDays(20));
            data.saveEvaluation(lapsingSoon);

            AcademicRequest lapsingLater = data.evaluation(applicant, RequestStatus.COMPLETED);
            lapsingLater.setEvaluationExpiryDate(LocalDateTime.now().plusYears(2));
            data.saveEvaluation(lapsingLater);

            AcademicRequest noExpiryYet = data.evaluation(applicant, RequestStatus.COMPLETED);

            assertThat(academicService.findRequestsExpiringSoon(LocalDateTime.now().plusDays(30)))
                    .extracting(AcademicRequest::getId)
                    .as("ต้องได้เฉพาะรายการที่หมดอายุภายใน 30 วัน")
                    .containsExactly(lapsingSoon.getId())
                    .doesNotContain(lapsingLater.getId(), noExpiryYet.getId());
        }

        @Test
        @DisplayName("ค้นหาผลประเมินใกล้หมดอายุ — ไม่นับคำร้องที่ยังไม่จบกระบวนการ")
        void expiringSoonIgnoresUnfinishedRequests() {
            UserDtls applicant = data.applicant();
            AcademicRequest inFlight = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
            inFlight.setEvaluationExpiryDate(LocalDateTime.now().plusDays(5));
            data.saveEvaluation(inFlight);

            assertThat(academicService.findRequestsExpiringSoon(LocalDateTime.now().plusDays(30)))
                    .isEmpty();
        }

        @Test
        @DisplayName("ส่งวันที่เป็น null — ไม่ล้ม คืนรายการว่าง")
        void expiringSoonHandlesNull() {
            assertThat(academicService.findRequestsExpiringSoon(null)).isEmpty();
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
