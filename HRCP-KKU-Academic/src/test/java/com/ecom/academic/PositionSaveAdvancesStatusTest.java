package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * สถานะงานกับการแจ้งเตือนเป็นคนละเรื่องกัน
 *
 * <p>แถบความคืบหน้าที่ผู้ยื่นเห็นคือ source of truth ของกระบวนการ มันต้องบอกว่างานเดินไป
 * ถึงไหนแล้วจริง ๆ ส่วนอีเมลเป็นเพียงช่องทางสื่อสาร การผูกสองอย่างนี้เข้าด้วยกันทำให้
 * เจ้าหน้าที่ที่เลือก "ไม่รบกวนผู้ยื่น" กลายเป็นทำให้แถบค้าง ผู้ยื่นเปิดมาดูแล้วเข้าใจว่า
 * ยังไม่มีใครเริ่มทำ ทั้งที่เอกสารเสร็จไปแล้ว
 *
 * <p>เฟส 1 แยกสองอย่างนี้ถูกอยู่แล้ว เทสต์ชุดนี้ล็อกให้เฟส 2 เดินกติกาเดียวกัน
 */
@DisplayName("บันทึกเอกสารเฟส 2 เลื่อนสถานะเสมอ")
class PositionSaveAdvancesStatusTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private PositionRequest requestAwaitingVerification() {
        return data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
    }

    private PositionRequestStatus statusOf(PositionRequest request) {
        return positionService.findById(request.getId()).orElseThrow().getCurrentStatus();
    }

    private void saveDocumentSeven(PositionRequest request, boolean sendNotify) throws Exception {
        mvc.perform(post("/admin/position/request/" + request.getId() + "/document/7")
                .with(as(officer)).with(csrf())
                .param("sendNotify", String.valueOf(sendNotify))
                .param("applicant_name", "สมชาย ใจดีวิชาการ"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("ไม่ติ๊กแจ้งเตือน สถานะก็ยังต้องเดิน")
    void statusAdvancesEvenWithoutNotifying() throws Exception {
        PositionRequest request = requestAwaitingVerification();

        saveDocumentSeven(request, false);

        assertThat(statusOf(request))
                .as("งานเสร็จแล้วจริง แถบความคืบหน้าต้องบอกตามนั้น ไม่ว่าจะส่งอีเมลหรือไม่")
                .isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
    }

    @Test
    @DisplayName("ติ๊กแจ้งเตือน สถานะเดินเหมือนกัน")
    void statusAdvancesWhenNotifying() throws Exception {
        PositionRequest request = requestAwaitingVerification();

        saveDocumentSeven(request, true);

        assertThat(statusOf(request)).isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
    }

    @Test
    @DisplayName("เอกสารถูกบันทึกจริงทั้งสองกรณี")
    void theDocumentIsStillSaved() throws Exception {
        PositionRequest request = requestAwaitingVerification();

        saveDocumentSeven(request, false);

        assertThat(positionService.getDocumentsByType(request.getId(), 7)).isNotEmpty();
    }
}
