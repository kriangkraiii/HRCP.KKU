package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.DocumentProgress.ApplicantBadge;
import com.ecom.academic.service.DocumentProgress.Stage;

/**
 * ป้ายของการ์ดเอกสารในหน้าผู้ยื่น — เดิมขึ้นสองป้ายคู่กัน ("ลงนามแล้ว" + "รอยื่นคำร้อง")
 * ป้ายเหลืองพื้นทึบดูเหมือนปุ่ม และบอกงานของทั้งคำร้องแทนสถานะของเอกสาร
 */
@DisplayName("ป้ายสถานะเอกสารฝั่งผู้ยื่น")
class DocumentProgressApplicantBadgeTest {

    @Test
    @DisplayName("ก่อนส่งคำร้อง ส่วนของผู้ยื่นจบแล้ว: พร้อมยื่น สีเขียว ไม่มีคำว่ารอ")
    void readyBeforeSubmit() {
        ApplicantBadge b = DocumentProgress.applicantBadge(Stage.AWAITING_FORWARD, false, true);

        assertThat(b.label()).isEqualTo("พร้อมยื่น");
        assertThat(b.badgeClass()).contains("success");
        assertThat(b.icon()).isNotEqualTo("fa-paper-plane");
    }

    @Test
    @DisplayName("หลังส่งคำร้อง ขั้นเดียวกันคือรอเจ้าหน้าที่ส่งต่อ")
    void awaitingForwardAfterSubmit() {
        assertThat(DocumentProgress.applicantBadge(Stage.AWAITING_FORWARD, true, true).label())
                .isEqualTo("รอเจ้าหน้าที่ส่งต่อ");
    }

    @Test
    @DisplayName("รอลงนาม: แยกว่ารอผู้ยื่นเองหรือรอผู้อื่น")
    void awaitingSignatureSaysWho() {
        assertThat(DocumentProgress.applicantBadge(Stage.AWAITING_SIGNATURE, false, false).label())
                .isEqualTo("รอท่านลงนาม");
        assertThat(DocumentProgress.applicantBadge(Stage.AWAITING_SIGNATURE, true, true).label())
                .isEqualTo("รอผู้อื่นลงนาม");
    }

    @Test
    @DisplayName("ทุกป้ายเป็นพื้นอ่อนมีขอบ ไม่ใช่พื้นทึบแบบปุ่ม")
    void everyBadgeIsSubtle() {
        for (Stage stage : Stage.values()) {
            for (boolean submitted : new boolean[] { false, true }) {
                for (boolean signed : new boolean[] { false, true }) {
                    assertThat(DocumentProgress.applicantBadge(stage, submitted, signed).badgeClass())
                            .as("%s submitted=%s signed=%s", stage, submitted, signed)
                            .contains("border")
                            .doesNotContain("bg-warning ")
                            .doesNotContain("bg-success ");
                }
            }
        }
    }
}
