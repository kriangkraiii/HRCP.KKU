package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.RequestStatusHistory;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * The order of the teaching-evaluation flow, steps 1–15.
 *
 * <p>The document describes a strict sequence: สารบรรณรับเรื่อง → คณบดีพิจารณา →
 * แต่งตั้งอนุกรรมการ 3 คน → นัดประชุม → ประชุม → กรรมการประจำวิทยาลัยฯ รับรอง →
 * แจ้งผล. Nothing in the code enforces any of it, which is GAP-30; the tests
 * here separate the parts that work from the parts that only appear to.
 */
@DisplayName("เฟส 1: ลำดับสถานะการประเมินการสอน (ข้อ 1-15)")
class AcademicStatusFlowTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService service;

    /** ข้อ 3 — คำสั่งแต่งตั้งต้องระบุอนุกรรมการครบ 3 คน จึงจะถือว่าแต่งตั้งแล้ว */
    private static final String APPOINTMENT_ORDER = "{\"committee_1_name\":\"รศ.ดร. หนึ่ง\",\"committee_2_name\":\"รศ.ดร. สอง\",\"committee_3_name\":\"ผศ.ดร. สาม\"}";

    private RequestStatus statusOf(AcademicRequest request) {
        return service.findById(request.getId()).orElseThrow().getCurrentStatus();
    }

    private List<RequestStatus> historyOf(Long requestId) {
        return service.getStatusHistory(requestId).stream()
                .map(RequestStatusHistory::getNewStatus)
                .toList();
    }

    @Nested
    @DisplayName("เส้นทางหลักตามเอกสาร")
    class HappyPath {

        @Test
        @DisplayName("เดินครบข้อ 1-11: รับเรื่อง → แต่งตั้งอนุกรรมการ → นัดประชุม → ผ่าน → กรรมการวิทยาลัยฯ รับรอง → เสร็จสิ้น")
        void walksTheDocumentedSequence() {
            UserDtls applicant = data.applicant();
            UserDtls staff = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            // ข้อ 4 — คำสั่งแต่งตั้งคณะอนุกรรมการ (เอกสารที่ 3)
            service.autoUpdateStatusByDocument(request.getId(), 3, staff, APPOINTMENT_ORDER, false);
            assertThat(statusOf(request)).isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);

            // ข้อ 6 — นัดหมายวันประชุม (เอกสารที่ 4)
            service.autoUpdateStatusByDocument(request.getId(), 4, staff, null, false);
            assertThat(statusOf(request)).isEqualTo(RequestStatus.MEETING_SCHEDULED);

            // ข้อ 8 — ผลจากที่ประชุมคณะอนุกรรมการ (เอกสารที่ 6)
            service.autoUpdateStatusByDocument(request.getId(), 6, staff,
                    "{\"eval_result_level\":\"ดี\"}", false);
            assertThat(statusOf(request)).isEqualTo(RequestStatus.COMPLETED_PASS);

            // ข้อ 9-10 — คณะกรรมการประจำวิทยาลัยฯ รับรองผลประเมิน
            service.updateStatus(request.getId(), RequestStatus.COLLEGE_ENDORSED, staff,
                    "ที่ประชุมกรรมการประจำวิทยาลัยฯ รับรองผล", false);
            assertThat(statusOf(request)).isEqualTo(RequestStatus.COLLEGE_ENDORSED);

            // ข้อ 11 — แจ้งผลให้ผู้ขอกำหนดตำแหน่งทราบ (เอกสารที่ 8)
            service.autoUpdateStatusByDocument(request.getId(), 8, staff, null, false);
            assertThat(statusOf(request)).isEqualTo(RequestStatus.COMPLETED);

            assertThat(historyOf(request.getId()))
                    .as("ทุกก้าวต้องถูกบันทึกไว้ในไทม์ไลน์")
                    .containsExactly(RequestStatus.COMPLETED, RequestStatus.COLLEGE_ENDORSED,
                            RequestStatus.COMPLETED_PASS, RequestStatus.MEETING_SCHEDULED,
                            RequestStatus.SUB_COMMITTEE_APPOINTED);
        }

        @Test
        @DisplayName("ข้อ 11 ต้องรอข้อ 9-10 ก่อน — บันทึกเอกสารที่ 8 ก่อนกรรมการวิทยาลัยฯ รับรอง สถานะยังไม่ขยับ")
        void document8WaitsForTheCollegeEndorsement() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_PASS);

            service.autoUpdateStatusByDocument(request.getId(), 8, data.admin(), null, false);

            assertThat(statusOf(request))
                    .as("เอกสารยังถูกบันทึก แต่สถานะรอการรับรองตามข้อ 9-10")
                    .isEqualTo(RequestStatus.COMPLETED_PASS);
        }

        @Test
        @DisplayName("ผลประเมิน 'ไม่ผ่าน' ในเอกสารที่ 6 → COMPLETED_FAIL")
        void failingResultEndsTheProcess() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);

            service.autoUpdateStatusByDocument(request.getId(), 6, data.admin(),
                    "{\"eval_result_level\":\"ไม่ผ่าน\"}", false);

            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.COMPLETED_FAIL);
        }

        @Test
        @DisplayName("บันทึกเอกสารซ้ำไม่ดึงสถานะถอยหลัง")
        void resavingAnEarlierDocumentDoesNotRewind() {
            UserDtls applicant = data.applicant();
            UserDtls staff = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);

            service.autoUpdateStatusByDocument(request.getId(), 3, staff, null, false);

            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .as("แก้เอกสารที่ 3 ทีหลัง ต้องไม่ดึงสถานะกลับไปเป็นแต่งตั้งอนุกรรมการ")
                    .isEqualTo(RequestStatus.MEETING_SCHEDULED);
        }
    }

    @Nested
    @DisplayName("การยื่นซ้ำระหว่างมีคำร้องค้าง")
    class ActiveRequestRules {

        @Test
        @DisplayName("มีคำร้องอยู่ระหว่างดำเนินการ — ยื่นใหม่ไม่ได้")
        void inFlightRequestBlocksANewOne() {
            UserDtls applicant = data.applicant();
            data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);

            assertThat(service.hasActiveRequest(applicant.getId())).isTrue();
        }

        /**
         * The one case where asking again is the entire point. This was broken
         * once: the "finished" list named REJECTED and COMPLETED but not
         * COMPLETED_FAIL, so a professor told their teaching did not pass could
         * never ask to be evaluated again.
         */
        @Test
        @DisplayName("ประเมินไม่ผ่าน — ต้องยื่นขอประเมินใหม่ได้")
        void failedEvaluationAllowsANewRequest() {
            UserDtls applicant = data.applicant();
            data.evaluation(applicant, RequestStatus.COMPLETED_FAIL);

            assertThat(service.hasActiveRequest(applicant.getId())).isFalse();
        }

        @Test
        @DisplayName("ไม่รับคำร้อง / เสร็จสิ้น — ยื่นใหม่ได้")
        void rejectedOrCompletedAllowsANewRequest() {
            UserDtls applicant = data.applicant();
            data.evaluation(applicant, RequestStatus.REJECTED);
            data.evaluation(applicant, RequestStatus.COMPLETED);

            assertThat(service.hasActiveRequest(applicant.getId())).isFalse();
        }

        @Test
        @DisplayName("แบบร่างที่ยังไม่ส่ง ไม่นับเป็นคำร้องค้าง (ไม่งั้นจะเข้าฟอร์มตัวเองไม่ได้)")
        void draftDoesNotCountAsActive() {
            UserDtls applicant = data.applicant();
            data.evaluation(applicant, RequestStatus.DRAFT);

            assertThat(service.hasActiveRequest(applicant.getId())).isFalse();
        }

        @Test
        @DisplayName("คำร้องของอาจารย์ท่านอื่นไม่ทำให้เรายื่นไม่ได้")
        void anotherPersonsRequestDoesNotBlockUs() {
            data.evaluation(data.otherApplicant(), RequestStatus.MEETING_SCHEDULED);

            assertThat(service.hasActiveRequest(data.applicant().getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("การเดินสถานะต้องไม่ผูกกับการแจ้งเตือน (GAP-36)")
    class StatusAdvanceIsIndependentOfNotification {

        /**
         * These were one decision until this suite separated them. "แจ้งผู้ยื่น"
         * decides whether an e-mail goes out; it must not decide whether the step
         * counted. An officer who left it unticked used to get the document
         * saved and the request silently left where it was.
         */
        @Test
        @DisplayName("เจ้าหน้าที่ไม่ติ๊ก 'แจ้งผู้ยื่น' — สถานะยังต้องเดินหน้า")
        void statusAdvancesEvenWithoutNotifying() throws Exception {
            UserDtls applicant = data.applicant();
            UserDtls officer = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            expectAccepted(mvc.perform(post("/admin/academic/request/" + request.getId()
                    + "/document/3")
                    .param("order_no", "123/2569")
                    .param("order_date", "5 กันยายน 2569")
                    .param("committee_1_name", "รศ.ดร. หนึ่ง")
                    .param("committee_2_name", "รศ.ดร. สอง")
                    .param("committee_3_name", "ผศ.ดร. สาม")
                    // sendNotify deliberately absent — the officer did not tick it
                    .with(csrf())
                    .with(user(officer.getEmail()).roles("ADMIN"))),
                    "/admin/academic/request/" + request.getId());

            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .as("บันทึกคำสั่งแต่งตั้งแล้ว สถานะต้องเป็น 'แต่งตั้งอนุกรรมการ' เสมอ")
                    .isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);
        }

        @Test
        @DisplayName("ไม่ติ๊กแจ้งเตือน — ต้องไม่มีอีเมลออกไป")
        void notTickingMeansNoEmail() {
            UserDtls applicant = data.applicant();
            UserDtls officer = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            service.autoUpdateStatusByDocument(request.getId(), 3, officer, APPOINTMENT_ORDER, false);

            settle();
            assertThat(mail().to(TestDataFactory.APPLICANT_EMAIL))
                    .as("เช็คบ็อกซ์ยังต้องคุมการส่งอีเมลได้ตามเดิม")
                    .isEmpty();
        }

        @Test
        @DisplayName("ติ๊กแจ้งเตือน — ผู้ยื่นได้รับอีเมล")
        void tickingSendsTheEmail() {
            UserDtls applicant = data.applicant();
            UserDtls officer = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            service.autoUpdateStatusByDocument(request.getId(), 3, officer, APPOINTMENT_ORDER, true);

            awaitCondition("อีเมลถึงผู้ยื่น",
                    () -> !mail().to(TestDataFactory.APPLICANT_EMAIL).isEmpty());
        }
    }

    @Nested
    @DisplayName("ลำดับสถานะถูกบังคับตาม flow (GAP-30/31/32/33/34)")
    class TransitionRules {

        /**
         * GAP-30, fixed. {@code updateStatus} was a setter with an audit trail:
         * it took whatever it was handed, so a mistyped dropdown could send a
         * request from "รับคำร้อง" straight to "เสร็จสิ้น", skipping steps 3 to 10.
         */
        @Test
        @DisplayName("กระโดดข้ามขั้นตอนไม่ได้อีกแล้ว")
        void illegalJumpsAreRejected() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            assertThatThrownBy(() -> service.updateStatus(request.getId(),
                    RequestStatus.COMPLETED, data.admin(), "ข้ามทุกขั้นตอน", false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ไม่ตรงกับลำดับใน flow");

            assertThat(statusOf(request)).isEqualTo(RequestStatus.RECEIVED);
        }

        @Test
        @DisplayName("คำร้องที่จบไปแล้ว ดึงกลับมาไม่ได้")
        void terminalRequestsCannotBeRewound() {
            UserDtls applicant = data.applicant();
            for (RequestStatus closed : List.of(RequestStatus.COMPLETED,
                    RequestStatus.COMPLETED_FAIL, RequestStatus.REJECTED)) {
                AcademicRequest request = data.evaluation(applicant, closed);

                assertThatThrownBy(() -> service.updateStatus(request.getId(),
                        RequestStatus.DRAFT, data.admin(), "ถอยกลับ", false))
                        .as("%s เป็นสถานะปลายทาง", closed)
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        /**
         * GAP-31, fixed. Every other case in {@code autoUpdateStatusByDocument}
         * checked where the request stood; document 8 did not, so saving it on a
         * refused request turned that refusal into "เสร็จสิ้น" and mailed the
         * applicant to say so.
         */
        @Test
        @DisplayName("GAP-31: บันทึกเอกสารที่ 8 บนคำร้องที่ปิดไปแล้ว ไม่ปลุกคำร้องกลับมา")
        void closedRequestsStayClosed() {
            UserDtls applicant = data.applicant();
            for (RequestStatus closed : List.of(RequestStatus.REJECTED,
                    RequestStatus.COMPLETED_FAIL)) {
                AcademicRequest request = data.evaluation(applicant, closed);

                service.autoUpdateStatusByDocument(request.getId(), 8, data.admin(), null, false);

                assertThat(statusOf(request))
                        .as("คำร้องที่ %s ต้องอยู่อย่างนั้น", closed.getThaiLabel())
                        .isEqualTo(closed);
            }
        }

        @Test
        @DisplayName("ไม่รับคำร้องทำได้ทุกจุดที่ยังไม่ปิด")
        void rejectionIsAlwaysAvailableWhileOpen() {
            UserDtls applicant = data.applicant();
            for (RequestStatus open : List.of(RequestStatus.RECEIVED,
                    RequestStatus.SUB_COMMITTEE_APPOINTED, RequestStatus.MEETING_SCHEDULED,
                    RequestStatus.COMPLETED_PASS, RequestStatus.COLLEGE_ENDORSED)) {
                AcademicRequest request = data.evaluation(applicant, open);

                service.updateStatus(request.getId(), RequestStatus.REJECTED, data.admin(),
                        "ตีกลับ", false);

                assertThat(statusOf(request)).isEqualTo(RequestStatus.REJECTED);
            }
        }

        /**
         * GAP-32, fixed. The "has it got this far" checks compared
         * {@code enum.ordinal()}, which is the declaration order and not the
         * process order — reordering the enum would have changed the behaviour
         * silently. They now ask the state machine.
         */
        @Test
        @DisplayName("GAP-32: บันทึกเอกสารซ้ำไม่ดึงสถานะถอยหลัง")
        void resavingAnEarlierDocumentDoesNotRewind() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);

            service.autoUpdateStatusByDocument(request.getId(), 3, data.admin(), null, false);

            assertThat(statusOf(request)).isEqualTo(RequestStatus.MEETING_SCHEDULED);
        }

        /**
         * GAP-33, fixed. The bar showed three of the fifteen steps and omitted
         * the college board's endorsement entirely.
         */
        @Test
        @DisplayName("GAP-33: แถบความคืบหน้าครอบคลุมเส้นทางหลักครบ รวมขั้นรับรองของกรรมการวิทยาลัยฯ")
        void progressBarCoversTheMainPath() {
            assertThat(RequestStatus.getProgressSteps())
                    .containsExactly(RequestStatus.RECEIVED,
                            RequestStatus.SUB_COMMITTEE_APPOINTED,
                            RequestStatus.MEETING_SCHEDULED,
                            RequestStatus.COMPLETED_PASS,
                            RequestStatus.COLLEGE_ENDORSED,
                            RequestStatus.COMPLETED);

            assertThat(RequestStatus.COLLEGE_ENDORSED.progressIndex())
                    .as("ต้องอยู่ก่อนขั้นสุดท้าย")
                    .isEqualTo(RequestStatus.COMPLETED.progressIndex() - 1);
            assertThat(RequestStatus.DRAFT.progressIndex()).isEqualTo(-1);
        }

        /**
         * GAP-34, fixed. "แจ้งผล - แก้ไข" was neither terminal nor able to move
         * on: the applicant could not start a new request and had no way forward.
         * Steps 12-15 now close the loop.
         */
        @Test
        @DisplayName("GAP-34: วงจรแก้ไข (ข้อ 12-15) เดินครบรอบจนกลับมาเสร็จสิ้นได้")
        void theReviseLoopCompletes() {
            UserDtls applicant = data.applicant();
            UserDtls staff = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

            // ข้อ 13 — ผู้ยื่นส่งเอกสารที่แก้แล้วกลับมา
            service.updateStatus(request.getId(), RequestStatus.REVISION_SUBMITTED, applicant,
                    "ส่งเอกสารที่แก้ไขแล้ว", false);
            assertThat(statusOf(request)).isEqualTo(RequestStatus.REVISION_SUBMITTED);

            // ข้อ 14-15 — เจ้าหน้าที่ส่งเข้าที่ประชุมอนุกรรมการอีกรอบ
            service.updateStatus(request.getId(), RequestStatus.MEETING_SCHEDULED, staff,
                    "นัดประชุมพิจารณาเอกสารที่แก้ไข", false);

            // แล้ววนตามกรณีไม่มีแก้ไข: ข้อ 9 → 10 → 11
            service.autoUpdateStatusByDocument(request.getId(), 6, staff,
                    "{\"eval_result_level\":\"ดี\"}", false);
            service.updateStatus(request.getId(), RequestStatus.COLLEGE_ENDORSED, staff,
                    "กรรมการวิทยาลัยฯ รับรอง", false);
            service.autoUpdateStatusByDocument(request.getId(), 8, staff, null, false);

            assertThat(statusOf(request))
                    .as("เดิมค้างอยู่ที่ 'แจ้งผล - แก้ไข' ไปต่อไม่ได้และยื่นใหม่ก็ไม่ได้")
                    .isEqualTo(RequestStatus.COMPLETED);
        }

        /**
         * GAP-35. ข้อ 3 asks for "อนุกรรมการประเมินการสอน จำนวน 3 คน" and the
         * appointment order has a line for each. Saving it with a name missing
         * used to mark the request "แต่งตั้งอนุกรรมการ" when no such committee
         * had been appointed.
         */
        @Test
        @DisplayName("GAP-35: คำสั่งแต่งตั้งที่ยังไม่ครบ 3 คน ไม่ทำให้สถานะเป็น 'แต่งตั้งอนุกรรมการ'")
        void anIncompleteAppointmentOrderDoesNotAdvanceTheStatus() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            service.autoUpdateStatusByDocument(request.getId(), 3, data.admin(),
                    "{\"committee_1_name\":\"รศ.ดร. หนึ่ง\",\"committee_2_name\":\"\"}", false);

            assertThat(statusOf(request))
                    .as("ขาดชื่ออนุกรรมการ 2 คน — ยังถือว่ายังไม่ได้แต่งตั้ง")
                    .isEqualTo(RequestStatus.RECEIVED);
        }

        @Test
        @DisplayName("GAP-35: ครบ 3 คนแล้ว สถานะจึงเปลี่ยนเป็น 'แต่งตั้งอนุกรรมการ'")
        void aCompleteAppointmentOrderAdvancesTheStatus() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            service.autoUpdateStatusByDocument(request.getId(), 3, data.admin(),
                    "{\"committee_1_name\":\"รศ.ดร. หนึ่ง\",\"committee_2_name\":\"รศ.ดร. สอง\","
                            + "\"committee_3_name\":\"ผศ.ดร. สาม\"}",
                    false);

            assertThat(statusOf(request)).isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);
        }

        @Test
        @DisplayName("ทุกสถานะที่ยังไม่ปิด ต้องมีทางเดินต่ออย่างน้อยหนึ่งทาง — ไม่มีทางตัน")
        void noOpenStatusIsADeadEnd() {
            for (RequestStatus status : RequestStatus.values()) {
                if (status.isTerminal()) {
                    assertThat(status.allowedNext())
                            .as("%s เป็นสถานะปลายทาง ต้องไปไหนต่อไม่ได้", status)
                            .isEmpty();
                } else {
                    assertThat(status.allowedNext())
                            .as("%s ไม่ใช่สถานะปลายทาง แต่ไปต่อไม่ได้ = ผู้ยื่นติดอยู่ตรงนี้", status)
                            .isNotEmpty();
                }
            }
        }
    }
}
