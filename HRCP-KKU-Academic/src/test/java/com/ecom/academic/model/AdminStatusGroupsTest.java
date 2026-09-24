package com.ecom.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * แถบสถานะหน้าแอดมินแสดงเฉพาะสถานะที่อยู่ในกลุ่ม — เพิ่มสถานะใหม่ใน enum แล้วลืมใส่กลุ่ม
 * ชิปจะหายไปเงียบ ๆ และคำร้องในสถานะนั้นก็หาไม่เจอจากแถบกรอง
 */
@DisplayName("กลุ่มสถานะบนแถบกรองหน้าแอดมิน")
class AdminStatusGroupsTest {

    @Test
    @DisplayName("ประเมินผลการสอน: ทุกสถานะที่ไม่ใช่แบบร่างอยู่ในกลุ่มเดียวพอดี")
    void evaluationStatusesAreGroupedExactlyOnce() {
        List<RequestStatus> grouped = new ArrayList<>();
        grouped.addAll(RequestStatus.awaitingAdmin());
        grouped.addAll(RequestStatus.awaitingApplicant());
        grouped.addAll(RequestStatus.closed());

        assertThat(grouped).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(RequestStatus.values()).filter(s -> !s.isDraft()).toList());
        assertThat(RequestStatus.closed()).allMatch(RequestStatus::isTerminal);
    }

    @Test
    @DisplayName("ขอตำแหน่งทางวิชาการ: ทุกสถานะที่ไม่ใช่แบบร่างอยู่ในกลุ่มเดียวพอดี")
    void positionStatusesAreGroupedExactlyOnce() {
        List<PositionRequestStatus> grouped = new ArrayList<>();
        grouped.addAll(PositionRequestStatus.awaitingAdmin());
        grouped.addAll(PositionRequestStatus.awaitingApplicant());
        grouped.addAll(PositionRequestStatus.closed());

        assertThat(grouped).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(PositionRequestStatus.values()).filter(s -> !s.isDraft()).toList());
        assertThat(PositionRequestStatus.closed()).allMatch(PositionRequestStatus::isTerminal);
    }
}
