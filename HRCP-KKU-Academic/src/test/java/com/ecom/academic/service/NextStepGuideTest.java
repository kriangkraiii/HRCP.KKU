package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.DocumentProgress.Stage;
import com.ecom.academic.service.NextStepGuide.CouncilDates;
import com.ecom.academic.service.NextStepGuide.Item;
import com.ecom.academic.service.NextStepGuide.State;
import com.ecom.academic.service.NextStepGuide.Step;

@DisplayName("กล่องขั้นต่อไปของเจ้าหน้าที่ (เช็กลิสต์)")
class NextStepGuideTest {

    private static Map<Integer, String> rows(int... types) {
        Map<Integer, String> rows = new LinkedHashMap<>();
        for (int t : types) {
            rows.put(t, "ชื่อ" + t);
        }
        return rows;
    }

    @Test
    @DisplayName("เฟส 2 รับคำร้อง: ที่เสร็จแล้วติ๊ก ข้อแรกที่ทำได้ไฮไลท์ ที่ยังไม่ถึงจาง — เลขเอกสารตามที่แสดง")
    void positionReceivedIsAChecklist() {
        Step step = NextStepGuide.position(PositionRequestStatus.DOCUMENT_RECEIVED, rows(2, 3, 4, 6, 7, 8),
                Map.of(2, Stage.DONE, 3, Stage.DONE, 4, Stage.AWAITING_FORWARD, 6, Stage.AWAITING_OFFICE,
                        7, Stage.NOT_STARTED, 8, Stage.NOT_STARTED),
                List.of(), CouncilDates.NONE);

        assertThat(step.items()).extracting(Item::text, Item::state).containsExactly(
                tuple("ตรวจแล้วส่งต่อลงนาม — เอกสารที่ 3 ชื่อ3", State.DONE),
                tuple("ตรวจแล้วส่งต่อลงนาม — เอกสารที่ 4 ชื่อ4", State.CURRENT),
                tuple("ตรวจแล้วส่งต่อลงนาม — เอกสารที่ 5 ชื่อ6", State.DONE),
                tuple("ออกเลขที่หนังสือและวันที่ — เอกสารที่ 4 ชื่อ4", State.UPCOMING),
                tuple("ออกเลขที่หนังสือและวันที่ — เอกสารที่ 5 ชื่อ6", State.TODO),
                tuple("กรอกแล้วส่งลงนาม — เอกสารที่ 7 ชื่อ7", State.TODO),
                tuple("ลงนามครบ → สถานะเปลี่ยนเป็น “ตรวจสอบความถูกต้อง/ครบถ้วน” เอง", State.UPCOMING));
        assertThat(step.getDoneCount()).isEqualTo(2);
        assertThat(step.getTotal()).isEqualTo(7);
    }

    @Test
    @DisplayName("เฟส 2 ตรวจสอบเอกสาร: เอกสารที่ 8 ส่งแล้วรอลงนาม — ไม่มีอะไรให้ทำ ไม่มีข้อไฮไลท์")
    void positionVerificationWaitsForTheSummary() {
        Step step = NextStepGuide.position(PositionRequestStatus.DOCUMENT_VERIFICATION, rows(4, 7, 8),
                Map.of(4, Stage.DONE, 7, Stage.DONE, 8, Stage.AWAITING_SIGNATURE), List.of(), CouncilDates.NONE);

        // เอกสารของผู้ยื่นที่จบแล้วไม่ขึ้นในขั้นนี้ — ขั้นหลังรับคำร้องเห็นเฉพาะที่ยังค้าง
        assertThat(step.items()).extracting(Item::text, Item::state).containsExactly(
                tuple("กรอกแล้วส่งลงนาม — เอกสารที่ 8 ชื่อ8", State.DONE),
                tuple("ลงนามครบ → สถานะเปลี่ยนเป็น “เสนอวาระกลั่นกรองฯ” เอง", State.WAITING));
        assertThat(step.items()).noneMatch(i -> i.state() == State.CURRENT);
    }

    @Test
    @DisplayName("เฟส 2 ส่งแก้ไข: เอกสารที่รอผู้ยื่นขึ้นเฉพาะรายการที่ส่งกลับ")
    void positionRevision() {
        Step step = NextStepGuide.position(PositionRequestStatus.REVISION_REQUESTED, rows(1, 4),
                Map.of(1, Stage.AWAITING_SIGNATURE, 4, Stage.AWAITING_FORWARD), List.of(1), CouncilDates.NONE);

        assertThat(step.items()).extracting(Item::text, Item::state).containsExactly(
                tuple("รอผู้ยื่นแก้ไขและลงนามใหม่ — เอกสารที่ 1 ชื่อ1", State.WAITING));
    }

    @Test
    @DisplayName("เฟส 2 สภาไม่อนุมัติแต่ขอทบทวนไม่ได้แล้ว — ไม่มีงาน บอกเหตุผล")
    void positionRejectedPastAppeal() {
        Step step = NextStepGuide.position(PositionRequestStatus.COUNCIL_REJECTED, rows(1), Map.of(1, Stage.DONE),
                List.of(), new CouncilDates(null, null, "ขอทบทวนครบ 2 ครั้งแล้ว"));

        assertThat(step.items()).isEmpty();
        assertThat(step.hint()).isEqualTo("ขอทบทวนครบ 2 ครั้งแล้ว");
    }

    @Test
    @DisplayName("ไม่มีขั้นต่อไป: แบบร่าง และจบแล้ว ทั้งสองเฟส")
    void nothingToDo() {
        assertThat(NextStepGuide.position(PositionRequestStatus.DRAFT, rows(), Map.of(), List.of(), null)).isNull();
        assertThat(NextStepGuide.position(PositionRequestStatus.COUNCIL_APPROVED, rows(), Map.of(), List.of(), null))
                .isNull();
        assertThat(NextStepGuide.academic(RequestStatus.DRAFT, rows(), Map.of(), null)).isNull();
        assertThat(NextStepGuide.academic(RequestStatus.COMPLETED, rows(), Map.of(), null)).isNull();
    }

    @Test
    @DisplayName("เฟส 1 รับคำร้อง: ขอรายชื่อเสร็จแล้ว ไฮไลท์คำสั่งแต่งตั้ง")
    void academicReceived() {
        Step step = NextStepGuide.academic(RequestStatus.RECEIVED, rows(3, 4),
                Map.of(3, Stage.DONE, 4, Stage.NOT_STARTED), null);

        assertThat(step.items()).extracting(Item::text, Item::state).containsExactly(
                tuple("กรอกแล้วส่งลงนาม — เอกสารที่ 3 ชื่อ3", State.DONE),
                tuple("ลงนามครบ", State.DONE),
                tuple("กรอกแล้วส่งลงนาม — เอกสารที่ 4 ชื่อ4", State.CURRENT),
                tuple("ลงนามครบ → สถานะเปลี่ยนเป็น “แต่งตั้งอนุกรรมการ” เอง", State.UPCOMING));
    }

    @Test
    @DisplayName("เฟส 1 นัดประชุมแล้ว: รอกรรมการลงนามแบบประเมิน — ข้อที่ทำได้คือส่งข้อเสนอแนะเมื่อให้แก้")
    void academicMeeting() {
        Step step = NextStepGuide.academic(RequestStatus.MEETING_SCHEDULED, rows(6, 7),
                Map.of(6, Stage.NOT_STARTED, 7, Stage.AWAITING_SIGNATURE), null);

        assertThat(step.items()).extracting(Item::documentType, Item::state).containsExactly(
                tuple(7, State.DONE), tuple(null, State.WAITING), tuple(6, State.CURRENT));
    }
}
