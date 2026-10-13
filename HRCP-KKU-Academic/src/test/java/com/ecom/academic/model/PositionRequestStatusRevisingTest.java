package com.ecom.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ขั้นที่แสดงเป็น "ส่งแก้ไข" บนแถบความคืบหน้าคำร้องขอตำแหน่ง
 *
 * <p>ส่งกลับในขั้นที่ flow ให้เปลี่ยนเป็น "ส่งแก้ไข" ได้ สถานะเปลี่ยนตาม แต่ขั้นที่เปลี่ยนไม่ได้
 * (เช่น รับรองมติกลั่นกรองฯ แล้ว) สถานะคงเดิม แถบเคยยังขึ้นสีปกติทั้งที่งานรอผู้ยื่นอยู่
 */
@DisplayName("แถบความคืบหน้าคำร้องขอตำแหน่ง เมื่อมีเอกสารถูกส่งกลับ")
class PositionRequestStatusRevisingTest {

    private static int[] revising(PositionRequestStatus status, boolean sentBack) {
        return IntStream.range(0, PositionRequestStatus.getProgressSteps().length)
                .filter(i -> status.revisingAt(i, sentBack))
                .toArray();
    }

    @Test
    @DisplayName("สถานะส่งแก้ไข: ขั้นตรวจสอบเอกสารเป็นส่งแก้ไข")
    void revisionRequestedMarksVerification() {
        int verification = PositionRequestStatus.DOCUMENT_VERIFICATION.progressIndex();
        assertThat(revising(PositionRequestStatus.REVISION_REQUESTED, false)).containsExactly(verification);
        assertThat(revising(PositionRequestStatus.REVISION_REQUESTED, true)).containsExactly(verification);
    }

    @Test
    @DisplayName("ส่งกลับในขั้นที่สถานะไม่เปลี่ยนตาม: ขั้นปัจจุบันเป็นส่งแก้ไข")
    void sentBackWithoutStatusChangeMarksTheCurrentStep() {
        PositionRequestStatus status = PositionRequestStatus.SCREENING_APPROVED;
        assertThat(revising(status, true)).containsExactly(status.progressIndex());
        assertThat(revising(status, false)).isEmpty();
    }
}
