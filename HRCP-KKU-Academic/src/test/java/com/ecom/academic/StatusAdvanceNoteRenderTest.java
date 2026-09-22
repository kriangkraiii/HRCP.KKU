package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * หน้าเอกสารบอกว่าสถานะจะเปลี่ยนเมื่อไร และต้องไม่มีช่องติ๊กแจ้งเตือนหลงเหลืออยู่
 *
 * <p>ช่องติ๊ก "ส่งอีเมลแจ้งผู้ยื่น" ถูกถอดออกเมื่อสถานะย้ายไปเลื่อนตอนซองลายเซ็นปิด —
 * คนที่กดบันทึกวันนี้ไม่มีทางรู้ว่าอีกกี่วันกว่าจะลงนามครบ การให้ตัดสินใจเรื่องอีเมลไว้
 * ล่วงหน้าข้ามจังหวะแบบนั้นจึงไม่มีความหมาย ผู้ยื่นได้รับแจ้งเสมอเมื่อสถานะเปลี่ยนจริง
 *
 * <p>ที่ต้องเฝ้าคือ<em>ซาก</em>ของมัน: ถ้า {@code name="sendNotify"} ยังโผล่ในหน้าไหน
 * เจ้าหน้าที่จะเห็นช่องที่ติ๊กแล้วไม่มีผลอะไรเลย ซึ่งแย่กว่าไม่มีช่อง
 */
@DisplayName("ข้อความบอกจังหวะเปลี่ยนสถานะบนหน้าเอกสาร")
class StatusAdvanceNoteRenderTest extends AbstractFlowTest {

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

    private String formFor(AcademicRequest request, int type) throws Exception {
        return mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/" + type)
                .with(as(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("เอกสารที่เลื่อนสถานะ — บอกว่าจะเปลี่ยนเมื่อลงนามครบ")
    void statusAdvancingDocumentsExplainWhen() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        for (int type : AcademicRequestService.STATUS_ADVANCING_DOCUMENTS) {
            assertThat(formFor(request, type))
                    .as("เอกสารที่ %d ต้องบอกว่าสถานะเปลี่ยนตอนลงนามครบ", type)
                    .contains("เอกสารฉบับนี้ทำให้คำร้องเดินไปขั้นถัดไป")
                    .contains("สถานะจะเปลี่ยนเมื่อลงนามครบทุกขั้นแล้วเท่านั้น");
        }
    }

    @Test
    @DisplayName("เอกสารที่ไม่เลื่อนสถานะ — ไม่มีข้อความนี้")
    void everythingElseStaysQuiet() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        for (int type = 1; type <= 9; type++) {
            if (AcademicRequestService.STATUS_ADVANCING_DOCUMENTS.contains(type)) {
                continue;
            }
            assertThat(formFor(request, type))
                    .as("เอกสารที่ %d ไม่ได้เลื่อนสถานะ จึงไม่มีอะไรจะบอก", type)
                    .doesNotContain("เอกสารฉบับนี้ทำให้คำร้องเดินไปขั้นถัดไป");
        }
    }

    @Test
    @DisplayName("ไม่มีช่องติ๊กแจ้งเตือนและกล่องยืนยันแบบเด้งหลงเหลือ")
    void noNotifyControlSurvives() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        for (int type = 1; type <= 9; type++) {
            assertThat(formFor(request, type))
                    .as("เอกสารที่ %d ยังมีซากช่องแจ้งเตือนที่ไม่มีผลอะไรแล้ว", type)
                    .doesNotContain("name=\"sendNotify\"")
                    .doesNotContain("notifyConfirmModal")
                    .doesNotContain("ยังไม่อัปเดตสถานะได้");
        }
    }
}
