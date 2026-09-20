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
 * การบันทึกเอกสารกับการยืนยันว่าเอกสารเสร็จ เป็นคนละเรื่องกัน
 *
 * <p>กล่องยืนยันของเฟส 2 เสนอสองทางเลือกมาตลอด — "ไม่แจ้งเตือน (บันทึกอย่างเดียว)" กับ
 * "แจ้งเตือนผู้ยื่นและอัปเดตสถานะ" และเขียนกำกับไว้ด้วยว่า <em>"หากเอกสารยังไม่เรียบร้อย
 * สามารถเลือก 'ไม่แจ้งเตือน' เพื่อบันทึกไฟล์โดยยังไม่อัปเดตสถานะได้"</em>
 *
 * <p>แต่เซิร์ฟเวอร์เรียก {@code autoUpdateStatusByDocument} ทุกครั้งไม่ว่าเลือกอะไร
 * ค่าที่ส่งมาคุมแค่ว่าจะส่งอีเมลไหม เจ้าหน้าที่ที่กรอกเอกสารที่ 7 ไปครึ่งเดียวแล้วกด
 * "บันทึกอย่างเดียว" จึงดันคำร้องข้ามขั้นไปโดยไม่ตั้งใจ และถอยกลับไม่ได้เพราะลำดับสถานะ
 * ถูกคุมไว้
 */
@DisplayName("บันทึกเอกสารเฟส 2 ไม่เลื่อนสถานะเอง")
class PositionSaveDoesNotAdvanceStatusTest extends AbstractFlowTest {

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

    @Test
    @DisplayName("บันทึกอย่างเดียว — สถานะต้องไม่ขยับ")
    void savingWithoutConfirmingLeavesTheStatusAlone() throws Exception {
        PositionRequest request = requestAwaitingVerification();

        mvc.perform(post("/admin/position/request/" + request.getId() + "/document/7")
                .with(as(officer)).with(csrf())
                .param("sendNotify", "false")
                .param("applicant_name", "กรอกไปครึ่งเดียว"))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(request))
                .as("เจ้าหน้าที่ยังไม่ได้ยืนยันว่าเอกสารเสร็จ")
                .isEqualTo(PositionRequestStatus.DOCUMENT_RECEIVED);
    }

    @Test
    @DisplayName("ยืนยันว่าเสร็จ — สถานะเลื่อนตามปกติ")
    void confirmingAdvancesTheStatus() throws Exception {
        PositionRequest request = requestAwaitingVerification();

        mvc.perform(post("/admin/position/request/" + request.getId() + "/document/7")
                .with(as(officer)).with(csrf())
                .param("sendNotify", "true")
                .param("applicant_name", "กรอกครบแล้ว"))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(request))
                .as("เจ้าหน้าที่กดยืนยันแล้ว")
                .isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
    }

    @Test
    @DisplayName("บันทึกอย่างเดียวก็ยังต้องบันทึกเอกสารจริง")
    void theDocumentIsStillSaved() throws Exception {
        PositionRequest request = requestAwaitingVerification();

        mvc.perform(post("/admin/position/request/" + request.getId() + "/document/7")
                .with(as(officer)).with(csrf())
                .param("sendNotify", "false")
                .param("applicant_name", "กรอกไปครึ่งเดียว"))
                .andExpect(status().is3xxRedirection());

        assertThat(positionService.getDocumentsByType(request.getId(), 7))
                .as("ไม่เลื่อนสถานะ ไม่ได้แปลว่าไม่บันทึก")
                .isNotEmpty();
    }
}
