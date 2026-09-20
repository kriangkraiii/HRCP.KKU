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
 * ช่อง "ส่งอีเมลแจ้งผู้ยื่น" ขึ้นเฉพาะเอกสารที่ทำให้คำร้องเดินไปขั้นถัดไป
 *
 * <p>ของเดิมเป็นกล่องยืนยันที่เด้งขึ้นทุกครั้ง บังคับให้เจ้าหน้าที่เลือกซ้ำ ๆ และรายชื่อ
 * เอกสารที่เด้งถูกคัดลอกไว้ใน JavaScript จนเพี้ยนจากของจริง ตอนนี้หน้าเว็บอ่านจาก
 * {@code STATUS_ADVANCING_DOCUMENTS} ตัวเดียวกับที่เซิร์ฟเวอร์ใช้
 */
@DisplayName("ช่องส่งอีเมลแจ้งผู้ยื่น")
class NotifyCheckboxRenderTest extends AbstractFlowTest {

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
    @DisplayName("เอกสารที่เลื่อนสถานะ — มีช่อง ติ๊กมาให้แล้ว")
    void shownAndTickedOnStatusAdvancingDocuments() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        for (int type : AcademicRequestService.STATUS_ADVANCING_DOCUMENTS) {
            String html = formFor(request, type);
            assertThat(html).as("เอกสารที่ %d ต้องมีช่องแจ้งเตือน", type)
                    .contains("name=\"sendNotify\"");
            assertThat(html).as("เอกสารที่ %d ต้องติ๊กมาให้แล้ว", type)
                    .containsPattern("name=\"sendNotify\"[^>]*checked");
        }
    }

    @Test
    @DisplayName("เอกสารที่ไม่เลื่อนสถานะ — ไม่มีช่องนี้")
    void hiddenOnEverythingElse() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        for (int type = 1; type <= 9; type++) {
            if (AcademicRequestService.STATUS_ADVANCING_DOCUMENTS.contains(type)) {
                continue;
            }
            assertThat(formFor(request, type))
                    .as("เอกสารที่ %d ไม่เลื่อนสถานะ จึงไม่มีอีเมลจะส่ง", type)
                    .doesNotContain("name=\"sendNotify\"");
        }
    }

    @Test
    @DisplayName("กล่องยืนยันแบบเด้งหายไปแล้ว")
    void theOldModalIsGone() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        assertThat(formFor(request, 5))
                .doesNotContain("notifyConfirmModal")
                .doesNotContain("ยังไม่อัปเดตสถานะได้");
    }
}
