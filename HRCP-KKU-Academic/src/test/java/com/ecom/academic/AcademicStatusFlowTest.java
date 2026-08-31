package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.Disabled;
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

    private List<RequestStatus> historyOf(Long requestId) {
        return service.getStatusHistory(requestId).stream()
                .map(RequestStatusHistory::getNewStatus)
                .toList();
    }

    @Nested
    @DisplayName("เส้นทางหลักตามเอกสาร")
    class HappyPath {

        @Test
        @DisplayName("เดินครบข้อ 1-11: รับเรื่อง → แต่งตั้งอนุกรรมการ → นัดประชุม → ผ่าน → เสร็จสิ้น")
        void walksTheDocumentedSequence() {
            UserDtls applicant = data.applicant();
            UserDtls staff = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            // ข้อ 4 — คำสั่งแต่งตั้งคณะอนุกรรมการ (เอกสารที่ 3)
            service.autoUpdateStatusByDocument(request.getId(), 3, staff, null, false);
            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);

            // ข้อ 6 — นัดหมายวันประชุม (เอกสารที่ 4)
            service.autoUpdateStatusByDocument(request.getId(), 4, staff, null, false);
            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.MEETING_SCHEDULED);

            // ข้อ 8 — ผลจากที่ประชุมคณะอนุกรรมการ (เอกสารที่ 6)
            service.autoUpdateStatusByDocument(request.getId(), 6, staff,
                    "{\"eval_result_level\":\"ดี\"}", false);
            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.COMPLETED_PASS);

            // ข้อ 11 — แจ้งผลให้ผู้ขอกำหนดตำแหน่งทราบ (เอกสารที่ 8)
            service.autoUpdateStatusByDocument(request.getId(), 8, staff, null, false);
            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.COMPLETED);

            assertThat(historyOf(request.getId()))
                    .as("ทุกก้าวต้องถูกบันทึกไว้ในไทม์ไลน์")
                    .containsExactly(RequestStatus.COMPLETED, RequestStatus.COMPLETED_PASS,
                            RequestStatus.MEETING_SCHEDULED, RequestStatus.SUB_COMMITTEE_APPOINTED);
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

            service.autoUpdateStatusByDocument(request.getId(), 3, officer, null, false);

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

            service.autoUpdateStatusByDocument(request.getId(), 3, officer, null, true);

            awaitCondition("อีเมลถึงผู้ยื่น",
                    () -> !mail().to(TestDataFactory.APPLICANT_EMAIL).isEmpty());
        }
    }

    @Nested
    @DisplayName("ช่องว่างที่พบ — ลำดับสถานะไม่ถูกบังคับ")
    class TransitionGaps {

        /**
         * GAP-31. Every other {@code case} in
         * {@code autoUpdateStatusByDocument} checks where the request already is
         * before moving it. Document 8 does not, so saving it on a request that
         * was refused turns that refusal into "เสร็จสิ้น" — and mails the
         * applicant to say so.
         */
        @Test
        @DisplayName("GAP-31: บันทึกเอกสารที่ 8 บนคำร้องที่ถูกปฏิเสธ ปลุกคำร้องกลับมาเป็น 'เสร็จสิ้น'")
        void document8ResurrectsARejectedRequest() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.REJECTED);

            service.autoUpdateStatusByDocument(request.getId(), 8, data.admin(), null, false);

            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .as("""
                            คำร้องที่ 'ไม่รับคำร้อง' กลายเป็น 'เสร็จสิ้น' เพราะ case 8 ไม่มีเงื่อนไขคุม
                            ถ้าเทสข้อนี้เริ่ม fail แปลว่า GAP-31 ถูกแก้แล้ว""")
                    .isEqualTo(RequestStatus.COMPLETED);
        }

        @Test
        @Disabled("GAP-31: ต้องใส่เงื่อนไขให้ case 8 เหมือน case 3/4/6 ก่อน")
        @DisplayName("GAP-31 (spec): คำร้องที่จบไปแล้วต้องไม่ถูกดึงกลับเข้ากระบวนการ")
        void closedRequestsShouldStayClosed() {
            UserDtls applicant = data.applicant();
            for (RequestStatus closed : List.of(RequestStatus.REJECTED,
                    RequestStatus.COMPLETED_FAIL)) {
                AcademicRequest request = data.evaluation(applicant, closed);

                service.autoUpdateStatusByDocument(request.getId(), 8, data.admin(), null, false);

                assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                        .isEqualTo(closed);
            }
        }

        /**
         * GAP-30. {@code updateStatus} is a setter with an audit trail, not a
         * transition. Nothing stops an accidental jump from "รับคำร้อง" straight
         * to "เสร็จสิ้น", skipping the subcommittee, the meeting and the
         * college board — steps 3 through 10 of the flow.
         */
        @Test
        @DisplayName("GAP-30: กระโดดข้ามขั้นตอนได้ทั้งหมด ไม่มีอะไรห้าม")
        void anyJumpIsCurrentlyAllowed() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            service.updateStatus(request.getId(), RequestStatus.COMPLETED, data.admin(),
                    "ข้ามทุกขั้นตอน", false);

            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .as("ถ้าเทสข้อนี้เริ่ม fail แปลว่า GAP-30 ถูกแก้แล้ว")
                    .isEqualTo(RequestStatus.COMPLETED);
        }

        @Test
        @DisplayName("GAP-30: ดึงคำร้องที่จบแล้วกลับมาเป็นแบบร่างได้")
        void terminalRequestsCanBeRewound() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED);

            service.updateStatus(request.getId(), RequestStatus.DRAFT, data.admin(), "ถอยกลับ", false);

            assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.DRAFT);
        }

        @Test
        @Disabled("GAP-30: ต้องมีตารางการเปลี่ยนสถานะที่อนุญาต ตาม flow 33 ข้อก่อน")
        @DisplayName("GAP-30 (spec): การเปลี่ยนสถานะที่ผิดลำดับต้องถูกปฏิเสธ")
        void illegalTransitionsShouldBeRejected() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> service.updateStatus(request.getId(),
                            RequestStatus.COMPLETED, data.admin(), "ข้ามขั้นตอน", false))
                    .as("RECEIVED → COMPLETED ข้ามข้อ 3-10 ทั้งหมด")
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * GAP-33 and GAP-34. The progress bar the applicant watches shows three
         * of the fifteen steps, and the "กรณีมีแก้ไข" branch (ข้อ 12–15) has no
         * way back into the process once entered.
         */
        @Test
        @DisplayName("GAP-33: แถบความคืบหน้าแสดงแค่ 3 ขั้น จากกระบวนการจริง 15 ขั้น")
        void progressBarUnderstatesTheProcess() {
            assertThat(RequestStatus.getProgressSteps())
                    .as("ขาดขั้นรับรองผลโดยคณะกรรมการประจำวิทยาลัยฯ (ข้อ 10) และขั้นแจ้งผล (ข้อ 11)")
                    .containsExactly(RequestStatus.RECEIVED,
                            RequestStatus.SUB_COMMITTEE_APPOINTED,
                            RequestStatus.MEETING_SCHEDULED);
        }

        @Test
        @DisplayName("GAP-34: 'แจ้งผล - แก้ไข' ค้างอยู่กับที่ — เดินต่อไม่ได้ ยื่นใหม่ก็ไม่ได้")
        void reviseBranchIsADeadEnd() {
            UserDtls applicant = data.applicant();
            data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

            assertThat(RequestStatus.COMPLETED_REVISE.isTerminal())
                    .as("ไม่ใช่สถานะปลายทาง จึงยื่นคำร้องใหม่ไม่ได้")
                    .isFalse();
            assertThat(service.hasActiveRequest(applicant.getId()))
                    .as("และยังนับเป็นคำร้องค้าง — ผู้ยื่นจึงติดอยู่ตรงนี้")
                    .isTrue();
        }
    }
}
