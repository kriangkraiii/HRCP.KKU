package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

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
import com.ecom.academic.service.PositionRequestService.StatusDates;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * การขอทบทวนผลทั้งสองเฟส — เฟส 1 ตามประกาศ มข. 1669/2569 ข้อ 10.3, เฟส 2 ตามข้อบังคับ มข. พ.ศ. 2569 ข้อ 35
 * และวันที่สภามหาวิทยาลัยรับเรื่องตามประกาศ มข. 1670/2569 ข้อ 6
 */
@DisplayName("ขอทบทวนผลและวันรับเรื่อง (เกณฑ์ พ.ศ. 2569)")
class AppealFlowTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Nested
    @DisplayName("เฟส 1: ขอทบทวนผลการประเมินการสอนที่ไม่ผ่าน")
    class TeachingEvaluationAppeal {

        private AcademicRequest failed(UserDtls applicant) {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
            for (RequestStatus step : new RequestStatus[] { RequestStatus.SUBCOMMITTEE_FAIL,
                    RequestStatus.COLLEGE_ENDORSED_FAIL, RequestStatus.COMPLETED_FAIL }) {
                academicService.updateStatus(request.getId(), step, data.admin(), null, false);
            }
            return academicService.findById(request.getId()).orElseThrow();
        }

        private RequestStatus statusOf(AcademicRequest request) {
            return academicService.findById(request.getId()).orElseThrow().getCurrentStatus();
        }

        @Test
        @DisplayName("ผู้ยื่นขอทบทวนพร้อมเหตุผล — สถานะเป็นขอทบทวน และขอซ้ำไม่ได้")
        void theApplicantMayAppealOnce() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = failed(applicant);

            assertThat(academicService.appealProblem(request, LocalDate.now())).isNull();
            academicService.submitAppeal(request.getId(), applicant, "กรรมการไม่ได้พิจารณาสื่อการสอนออนไลน์");
            assertThat(statusOf(request)).isEqualTo(RequestStatus.APPEAL_SUBMITTED);

            // หัวหน้าส่วนงานยืนผลเดิมต้องมีเหตุผล
            assertThatThrownBy(() -> academicService.updateStatus(request.getId(), RequestStatus.COMPLETED_FAIL,
                    data.admin(), " ", false)).hasMessageContaining("ยืนผล");
            academicService.updateStatus(request.getId(), RequestStatus.COMPLETED_FAIL, data.admin(),
                    "หลักฐานไม่เปลี่ยนผล", false);

            AcademicRequest reloaded = academicService.findById(request.getId()).orElseThrow();
            assertThat(academicService.appealProblem(reloaded, LocalDate.now())).contains("ครั้งเดียว");
            assertThatThrownBy(() -> academicService.submitAppeal(request.getId(), applicant, "ขออีกครั้ง"))
                    .hasMessageContaining("ครั้งเดียว");
        }

        @Test
        @DisplayName("ไม่มีเหตุผล หรือไม่ใช่เจ้าของคำร้อง — ขอทบทวนไม่ได้")
        void reasonAndOwnershipAreRequired() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = failed(applicant);

            assertThatThrownBy(() -> academicService.submitAppeal(request.getId(), applicant, "  "))
                    .hasMessageContaining("เหตุผล");
            assertThatThrownBy(() -> academicService.submitAppeal(request.getId(), data.admin(), "แทนผู้ยื่น"))
                    .hasMessageContaining("ของท่านเอง");
            assertThat(statusOf(request)).isEqualTo(RequestStatus.COMPLETED_FAIL);
        }

        @Test
        @DisplayName("พ้น 30 วันทำการ — ขอทบทวนไม่ได้")
        void theWindowCloses() {
            AcademicRequest request = failed(data.applicant());
            assertThat(academicService.appealProblem(request, LocalDate.now().plusWeeks(7)))
                    .contains("พ้นกำหนด");
        }

        @Test
        @DisplayName("รับทบทวนต้องนัดประชุมรอบใหม่พร้อมวันประชุม")
        void acceptingTheAppealNeedsANewMeeting() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = failed(applicant);
            academicService.submitAppeal(request.getId(), applicant, "ขอให้พิจารณาใหม่");

            assertThatThrownBy(() -> academicService.updateStatus(request.getId(), RequestStatus.MEETING_SCHEDULED,
                    data.admin(), null, false)).hasMessageContaining("วันประชุม");
            academicService.setMeetingDate(request.getId(), java.time.LocalDateTime.now().plusDays(7),
                    "ห้องประชุม", data.admin());
            assertThat(statusOf(request)).isEqualTo(RequestStatus.MEETING_SCHEDULED);
        }
    }

    @Nested
    @DisplayName("เฟส 2: วันรับเรื่อง มติสภามหาวิทยาลัย และการขอทบทวน")
    class CouncilAppeal {

        private final LocalDate today = LocalDate.now();

        private PositionRequest sentToHr() {
            PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.COLLEGE_COMMITTEE, null);
            positionService.updateStatus(request.getId(), PositionRequestStatus.COLLEGE_APPROVED, data.admin(), null,
                    new StatusDates(today.minusDays(1), null, null, null));
            positionService.updateStatus(request.getId(), PositionRequestStatus.SENT_TO_HR, data.admin(), null);
            return request;
        }

        private PositionRequestStatus statusOf(PositionRequest request) {
            return positionService.findById(request.getId()).orElseThrow().getCurrentStatus();
        }

        @Test
        @DisplayName("รับรองมติวิทยาลัยฯ ต้องระบุวันมติ และบันทึกวันไว้ใช้คำนวณวันรับเรื่อง")
        void collegeApprovalRecordsTheResolutionDate() {
            PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.COLLEGE_COMMITTEE, null);

            assertThatThrownBy(() -> positionService.updateStatus(request.getId(), PositionRequestStatus.COLLEGE_APPROVED,
                    data.admin(), null, StatusDates.NONE)).hasMessageContaining("วันที่คณะกรรมการประจำวิทยาลัยฯ มีมติ");
            assertThatThrownBy(() -> positionService.updateStatus(request.getId(), PositionRequestStatus.COLLEGE_APPROVED,
                    data.admin(), null, new StatusDates(today.plusDays(1), null, null, null)))
                    .hasMessageContaining("อนาคต");

            positionService.updateStatus(request.getId(), PositionRequestStatus.COLLEGE_APPROVED, data.admin(), null,
                    new StatusDates(today.minusDays(3), today.minusDays(1), null, null));
            PositionRequest saved = positionService.findById(request.getId()).orElseThrow();
            assertThat(com.ecom.academic.service.CouncilTimeline.receiptDate(saved)).isEqualTo(today.minusDays(1));
        }

        @Test
        @DisplayName("สภาไม่อนุมัติ — ขอทบทวนได้ 2 ครั้ง ครั้งที่ 3 ไม่ได้")
        void twoAppealsAtMost() {
            PositionRequest request = sentToHr();
            StatusDates rejected = new StatusDates(null, null, today.minusDays(2), today.minusDays(1));

            for (int round = 1; round <= 2; round++) {
                positionService.updateStatus(request.getId(), PositionRequestStatus.COUNCIL_REJECTED, data.admin(),
                        null, rejected);
                positionService.updateStatus(request.getId(), PositionRequestStatus.APPEAL_SUBMITTED, data.admin(),
                        "เหตุผลทางวิชาการรอบ " + round, StatusDates.appeal(today, today));
                assertThat(statusOf(request)).isEqualTo(PositionRequestStatus.APPEAL_SUBMITTED);
            }
            positionService.updateStatus(request.getId(), PositionRequestStatus.COUNCIL_REJECTED, data.admin(), null,
                    rejected);
            assertThatThrownBy(() -> positionService.updateStatus(request.getId(),
                    PositionRequestStatus.APPEAL_SUBMITTED, data.admin(), "รอบที่ 3", StatusDates.appeal(today, today)))
                    .hasMessageContaining("ครบ 2 ครั้ง");
        }

        @Test
        @DisplayName("พ้น 90 วันนับจากวันรับทราบมติ หรือไม่มีเหตุผล — ขอทบทวนไม่ได้")
        void theNinetyDayWindowAndAReasonAreRequired() {
            PositionRequest request = sentToHr();
            positionService.updateStatus(request.getId(), PositionRequestStatus.COUNCIL_REJECTED, data.admin(), null,
                    new StatusDates(null, null, today.minusDays(120), today.minusDays(91)));
            assertThatThrownBy(() -> positionService.updateStatus(request.getId(),
                    PositionRequestStatus.APPEAL_SUBMITTED, data.admin(), "เหตุผล", StatusDates.appeal(today, today)))
                    .hasMessageContaining("พ้นกำหนด");

            PositionRequest other = sentToHr();
            positionService.updateStatus(other.getId(), PositionRequestStatus.COUNCIL_REJECTED, data.admin(), null,
                    new StatusDates(null, null, today.minusDays(2), today.minusDays(1)));
            assertThatThrownBy(() -> positionService.updateStatus(other.getId(),
                    PositionRequestStatus.APPEAL_SUBMITTED, data.admin(), "", StatusDates.appeal(today, today)))
                    .hasMessageContaining("เหตุผล");
        }

        @Test
        @DisplayName("90 วันนับถึงวันที่ส่วนงานรับเรื่องขอทบทวน ไม่ใช่วันที่เจ้าหน้าที่บันทึก (ข้อ 35)")
        void theWindowIsJudgedOnTheDayTheFacultyReceivedTheAppeal() {
            PositionRequest request = sentToHr();
            positionService.updateStatus(request.getId(), PositionRequestStatus.COUNCIL_REJECTED, data.admin(), null,
                    new StatusDates(null, null, today.minusDays(110), today.minusDays(100)));

            positionService.updateStatus(request.getId(), PositionRequestStatus.APPEAL_SUBMITTED, data.admin(),
                    "เหตุผลทางวิชาการ", StatusDates.appeal(today.minusDays(20), today.minusDays(5)));

            PositionRequest saved = positionService.findById(request.getId()).orElseThrow();
            assertThat(saved.getCurrentStatus()).isEqualTo(PositionRequestStatus.APPEAL_SUBMITTED);
            assertThat(saved.getAppealReceivedDate()).isEqualTo(today.minusDays(20));
            assertThat(saved.getAppealEndorsedDate()).isEqualTo(today.minusDays(5));
        }

        @Test
        @DisplayName("ขอทบทวนต้องผ่านความเห็นชอบของคณะกรรมการประจำส่วนงานก่อนเสนอมหาวิทยาลัย (ข้อ 35)")
        void theFacultyBoardMustApproveTheAppealFirst() {
            PositionRequest request = sentToHr();
            positionService.updateStatus(request.getId(), PositionRequestStatus.COUNCIL_REJECTED, data.admin(), null,
                    new StatusDates(null, null, today.minusDays(10), today.minusDays(9)));

            assertThatThrownBy(() -> positionService.updateStatus(request.getId(),
                    PositionRequestStatus.APPEAL_SUBMITTED, data.admin(), "เหตุผล", StatusDates.appeal(today, null)))
                    .hasMessageContaining("คณะกรรมการประจำส่วนงาน");
            assertThatThrownBy(() -> positionService.updateStatus(request.getId(),
                    PositionRequestStatus.APPEAL_SUBMITTED, data.admin(), "เหตุผล", StatusDates.appeal(null, today)))
                    .hasMessageContaining("รับเรื่องขอทบทวน");
            assertThatThrownBy(() -> positionService.updateStatus(request.getId(),
                    PositionRequestStatus.APPEAL_SUBMITTED, data.admin(), "เหตุผล",
                    StatusDates.appeal(today.minusDays(1), today.minusDays(3))))
                    .hasMessageContaining("ก่อนวันที่รับเรื่อง");
            assertThat(statusOf(request)).isEqualTo(PositionRequestStatus.COUNCIL_REJECTED);
        }

        @Test
        @DisplayName("ผู้ยื่นที่เป็นข้าราชการ — ระบบรู้ เพราะใช้ข้อบังคับฉบับ พ.ศ. 2565 ไม่ใช่ฉบับพนักงาน พ.ศ. 2569")
        void aCivilServantApplicantIsRecognised() {
            PositionRequest civilServant = data.positionRequest(data.applicant(), PositionRequestStatus.DRAFT, null);
            data.positionDocument(civilServant, 2, "{\"status\":\"ข้าราชการ\"}");
            PositionRequest employee = data.positionRequest(data.applicant(), PositionRequestStatus.DRAFT, null);
            data.positionDocument(employee, 2, "{\"status\":\"พนักงานมหาวิทยาลัย\"}");

            assertThat(positionService.isCivilServant(civilServant)).isTrue();
            assertThat(positionService.isCivilServant(employee)).isFalse();
            assertThat(positionService.isCivilServant(data.positionRequest(data.applicant(),
                    PositionRequestStatus.DRAFT, null))).as("ไม่มีข้อมูล ถือเป็นพนักงาน").isFalse();
        }

        @Test
        @DisplayName("สภาไม่อนุมัติต้องระบุวันรับทราบมติ — ใช้นับ 90 วัน")
        void rejectionNeedsTheAcknowledgementDate() {
            PositionRequest request = sentToHr();
            assertThatThrownBy(() -> positionService.updateStatus(request.getId(),
                    PositionRequestStatus.COUNCIL_REJECTED, data.admin(), null,
                    new StatusDates(null, null, today.minusDays(2), null)))
                    .hasMessageContaining("รับทราบมติ");
            positionService.updateStatus(request.getId(), PositionRequestStatus.COUNCIL_APPROVED, data.admin(), null,
                    new StatusDates(null, null, today.minusDays(2), null));
            assertThat(statusOf(request)).isEqualTo(PositionRequestStatus.COUNCIL_APPROVED);
            assertThat(statusOf(request).isTerminal()).isTrue();
        }
    }
}
