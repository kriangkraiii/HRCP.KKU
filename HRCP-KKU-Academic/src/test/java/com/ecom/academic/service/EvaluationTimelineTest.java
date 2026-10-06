package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.RequestStatusHistory;

@DisplayName("กำหนดเวลาการประเมินการสอน — ประกาศ มข. 1669/2569 ข้อ 10.3")
class EvaluationTimelineTest {

    private final List<RequestStatusHistory> history = new ArrayList<>();
    private RequestStatus last = RequestStatus.RECEIVED;

    private void at(LocalDate day, RequestStatus status) {
        RequestStatusHistory h = new RequestStatusHistory();
        h.setOldStatus(last);
        h.setNewStatus(status);
        h.setChangedAt(day.atTime(10, 0));
        history.add(0, h); // ใหม่สุดก่อน เหมือน repository
        last = status;
    }

    @Test
    @DisplayName("แต่งตั้งกรรมการวันจันทร์ — ต้องรายงานผลภายใน 45 วันทำการ, รายงานทันถือว่าดำเนินการแล้ว")
    void committeeHasFortyFiveWorkingDays() {
        LocalDate appointed = LocalDate.of(2026, 10, 5);
        at(appointed, RequestStatus.SUB_COMMITTEE_APPOINTED);
        at(appointed.plusDays(20), RequestStatus.MEETING_SCHEDULED);
        at(appointed.plusDays(30), RequestStatus.COMPLETED_PASS);

        EvaluationTimeline.Deadline d = EvaluationTimeline.of(history).get(0);

        assertThat(d.due()).isEqualTo(appointed.plusWeeks(9));
        assertThat(d.met()).isTrue();
        assertThat(d.overdue(LocalDate.of(2027, 1, 1))).isFalse();
    }

    @Test
    @DisplayName("ยังไม่รายงานผลเมื่อพ้นกำหนด — เกินกำหนด")
    void unreportedPastTheDueDateIsOverdue() {
        LocalDate appointed = LocalDate.of(2026, 10, 5);
        at(appointed, RequestStatus.SUB_COMMITTEE_APPOINTED);

        EvaluationTimeline.Deadline d = EvaluationTimeline.of(history).get(0);

        assertThat(d.overdue(appointed.plusWeeks(9))).isFalse();
        assertThat(d.overdue(appointed.plusWeeks(9).plusDays(1))).isTrue();
    }

    @Test
    @DisplayName("ไม่ผ่าน — ขอทบทวนได้ภายใน 30 วันทำการ และขอได้ครั้งเดียว")
    void appealWindowIsThirtyWorkingDays() {
        LocalDate failed = LocalDate.of(2026, 10, 5);
        at(failed.minusDays(10), RequestStatus.MEETING_SCHEDULED);
        at(failed, RequestStatus.COMPLETED_FAIL);

        assertThat(EvaluationTimeline.appealDeadline(history)).isEqualTo(failed.plusWeeks(6));
        assertThat(EvaluationTimeline.alreadyAppealed(history)).isFalse();

        at(failed.plusDays(3), RequestStatus.APPEAL_SUBMITTED);
        assertThat(EvaluationTimeline.alreadyAppealed(history)).isTrue();
    }

    @Test
    @DisplayName("ผลไม่ผ่าน — นับ 7 วันทำการแจ้งผลจากวันรับรอง และนับ 30 วันทำการขอทบทวนจากวันแจ้งผล")
    void aFailIsAnnouncedWithinSevenWorkingDaysOfEndorsement() {
        LocalDate endorsed = LocalDate.of(2026, 10, 5);
        at(endorsed.minusDays(10), RequestStatus.MEETING_SCHEDULED);
        at(endorsed.minusDays(5), RequestStatus.SUBCOMMITTEE_FAIL);
        at(endorsed, RequestStatus.COLLEGE_ENDORSED_FAIL);

        EvaluationTimeline.Deadline notify = EvaluationTimeline.of(history).stream()
                .filter(d -> d.label().startsWith("แจ้งผล")).findFirst().orElseThrow();
        assertThat(notify.due()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(notify.met()).isFalse();
        assertThat(EvaluationTimeline.appealDeadline(history))
                .as("ยังไม่แจ้งผลอย่างเป็นทางการ จึงยังไม่เริ่มนับกำหนดขอทบทวน").isNull();

        at(endorsed.plusDays(2), RequestStatus.COMPLETED_FAIL);
        notify = EvaluationTimeline.of(history).stream()
                .filter(d -> d.label().startsWith("แจ้งผล")).findFirst().orElseThrow();
        assertThat(notify.met()).isTrue();
        assertThat(EvaluationTimeline.appealDeadline(history)).isEqualTo(endorsed.plusDays(2).plusWeeks(6));
    }

    @Test
    @DisplayName("ยังไม่เริ่มขั้นไหน — ไม่มีกำหนดเวลาให้แสดง")
    void nothingStartedNothingShown() {
        at(LocalDate.of(2026, 10, 5), RequestStatus.RECEIVED);
        assertThat(EvaluationTimeline.of(history)).isEmpty();
        assertThat(EvaluationTimeline.appealDeadline(history)).isNull();
    }

    @Test
    @DisplayName("คำนำหน้าชื่อบอกตำแหน่งทางวิชาการ — ดูเฉพาะต้นชื่อ")
    void rankComesFromTheNamePrefix() {
        assertThat(AcademicRequestService.rankFromNamePrefix("รศ.ดร.สมชาย ใจดี"))
                .isEqualTo(com.ecom.academic.model.AcademicRank.ASSOCIATE_PROFESSOR);
        assertThat(AcademicRequestService.rankFromNamePrefix("ศ.ดร.สมหญิง"))
                .isEqualTo(com.ecom.academic.model.AcademicRank.PROFESSOR);
        assertThat(AcademicRequestService.rankFromNamePrefix("ผู้ช่วยศาสตราจารย์ ดร.ก"))
                .isEqualTo(com.ecom.academic.model.AcademicRank.ASSISTANT_PROFESSOR);
        assertThat(AcademicRequestService.rankFromNamePrefix("ดร.ผศวัฒน์ ทดสอบ")).isNull();
    }
}
