package com.ecom.academic.model;

import static com.ecom.academic.model.ProgressStepState.CURRENT;
import static com.ecom.academic.model.ProgressStepState.DONE;
import static com.ecom.academic.model.ProgressStepState.FINAL;
import static com.ecom.academic.model.ProgressStepState.PENDING;
import static com.ecom.academic.model.ProgressStepState.REJECTED;
import static com.ecom.academic.model.ProgressStepState.REVISE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ชื่อสถานะคือเหตุการณ์ที่เกิดไปแล้ว — "นัดหมายคณะอนุกรรมการ" แปลว่านัดแล้ว
 * ขั้นของสถานะปัจจุบันจึงต้องเป็นสีเขียว ส่วนขั้นถัดไปคือขั้นที่กำลังดำเนินการ
 *
 * <p>เดิมขั้นของสถานะปัจจุบันเป็นสีน้ำเงิน (กำลังดำเนินการ) ผู้ทดสอบที่นัดประชุมไปแล้ว
 * เห็นขั้น "นัดหมายคณะอนุกรรมการ" ยังไม่เสร็จ
 */
@DisplayName("แถบความคืบหน้าคำร้องประเมินผลการสอน")
class RequestStatusProgressTest {

    private static ProgressStepState[] bar(RequestStatus status) {
        return IntStream.range(0, RequestStatus.getProgressSteps().length)
                .mapToObj(status::stepStateAt)
                .toArray(ProgressStepState[]::new);
    }

    @Test
    @DisplayName("นัดหมายแล้ว: ขั้นนัดหมายเป็นเขียว ขั้นแจ้งผลกำลังดำเนินการ")
    void meetingScheduledMarksTheMeetingDone() {
        assertThat(bar(RequestStatus.MEETING_SCHEDULED))
                .containsExactly(DONE, DONE, DONE, CURRENT, PENDING, PENDING);
        assertThat(RequestStatus.MEETING_SCHEDULED.focusIndex()).isEqualTo(3);
        assertThat(RequestStatus.MEETING_SCHEDULED.getStepHint()).isEqualTo("รอผลการประชุม");
    }

    @Test
    @DisplayName("ส่งเอกสารแก้ไขแล้ว: ขั้นนัดหมายยังเป็นเขียว ไม่ถอยกลับไปเป็นน้ำเงิน")
    void revisionSubmittedKeepsTheMeetingDone() {
        assertThat(bar(RequestStatus.REVISION_SUBMITTED))
                .containsExactly(DONE, DONE, DONE, REVISE, PENDING, PENDING);
        assertThat(RequestStatus.REVISION_SUBMITTED.getStepHint()).contains("รอพิจารณารอบใหม่");
    }

    @Test
    @DisplayName("ผลให้แก้ไข / ไม่ผ่าน แสดงที่ขั้นแจ้งผล")
    void outcomesShowOnTheResultStep() {
        assertThat(bar(RequestStatus.COMPLETED_REVISE))
                .containsExactly(DONE, DONE, DONE, REVISE, PENDING, PENDING);
        assertThat(bar(RequestStatus.COMPLETED_FAIL))
                .containsExactly(DONE, DONE, DONE, REJECTED, PENDING, PENDING);
        // ไม่ผ่านที่รอรับรอง / รับรองแล้วรอแจ้งผล — ยังเป็นผลไม่ผ่านที่ขั้นแจ้งผลเหมือนกัน
        assertThat(bar(RequestStatus.SUBCOMMITTEE_FAIL))
                .containsExactly(DONE, DONE, DONE, REJECTED, PENDING, PENDING);
        assertThat(bar(RequestStatus.COLLEGE_ENDORSED_FAIL))
                .containsExactly(DONE, DONE, DONE, REJECTED, PENDING, PENDING);
    }

    @Test
    @DisplayName("รับคำร้องแล้ว: ขั้นแต่งตั้งอนุกรรมการกำลังดำเนินการ")
    void receivedMovesFocusToTheNextStep() {
        assertThat(bar(RequestStatus.RECEIVED))
                .containsExactly(DONE, CURRENT, PENDING, PENDING, PENDING, PENDING);
        assertThat(RequestStatus.RECEIVED.getStepHint()).isEqualTo("กำลังดำเนินการ");
    }

    @Test
    @DisplayName("เสร็จสิ้น: ทุกขั้นเขียว ขั้นสุดท้ายเป็นเขียวเข้ม ไม่มีขั้นที่กำลังดำเนินการ")
    void completedHasNoFocus() {
        assertThat(bar(RequestStatus.COMPLETED))
                .containsExactly(DONE, DONE, DONE, DONE, DONE, FINAL);
        assertThat(RequestStatus.COMPLETED.focusIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("แบบร่าง: ยังไม่เริ่ม ทุกขั้นเป็นเทา")
    void draftHasNothingReached() {
        assertThat(bar(RequestStatus.DRAFT)).containsOnly(PENDING);
        assertThat(RequestStatus.DRAFT.focusIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("ทุกสถานะที่ยังไม่ปิดมีขั้นที่ต้องทำต่อ และมีคำอธิบายใต้ขั้นนั้น")
    void everyOpenStatusHasAFocusWithAHint() {
        for (RequestStatus status : RequestStatus.values()) {
            if (status.isDraft() || status == RequestStatus.COMPLETED) {
                continue;
            }
            assertThat(status.focusIndex()).as(status.name()).isBetween(0, 5);
            assertThat(status.getStepHint()).as(status.name()).isNotBlank();
        }
    }
}
