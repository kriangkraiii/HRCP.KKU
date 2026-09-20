package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * "สถานะงาน" กับ "การแจ้งเตือน" ต้องแยกขาดจากกัน
 *
 * <p>สถานะคือ source of truth ที่สะท้อนว่ากระบวนการเดินไปถึงไหน ส่วนอีเมลเป็นเพียงช่องทาง
 * สื่อสาร การเอาสองอย่างมาผูกเป็นเงื่อนไขเดียวกันทำให้ข้อมูลไม่ตรงความจริง — เจ้าหน้าที่ที่
 * ทำงานเสร็จแล้วแต่ไม่อยากรบกวนผู้ยื่น กลายเป็นทำให้แถบความคืบหน้าค้างอยู่ที่เดิม
 *
 * <p>เทสต์ชุดนี้ล็อกทั้งสองทิศ: ไม่ติ๊กต้องไม่มีอีเมล <em>และ</em> สถานะต้องเดิน
 * ถ้าใครเผลอผูกกลับเข้าด้วยกันอีก ข้อใดข้อหนึ่งจะพัง
 */
@DisplayName("สถานะงานกับการแจ้งเตือนแยกจากกัน")
class StatusAndNotificationAreSeparateTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

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

    @Nested
    @DisplayName("เฟส 1 — การประเมินการสอน")
    class Phase1 {

        /** เอกสารที่ 5 เลื่อนสถานะเป็น "นัดหมายคณะอนุกรรมการ" */
        private AcademicRequest saveDocumentFive(boolean sendNotify) throws Exception {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);

            var post = post("/admin/academic/request/" + request.getId() + "/document/5")
                    .with(as(officer)).with(csrf())
                    .param("memo_no", "อว 660301.26.4/ว.1");
            if (sendNotify) {
                post = post.param("sendNotify", "true");
            }
            mvc.perform(post).andExpect(status().is3xxRedirection());
            return request;
        }

        private RequestStatus statusOf(AcademicRequest request) {
            return academicService.findById(request.getId()).orElseThrow().getCurrentStatus();
        }

        @Test
        @DisplayName("ไม่ติ๊ก — สถานะเดิน แต่ไม่มีอีเมลถึงผู้ยื่น")
        void withoutNotifying() throws Exception {
            AcademicRequest request = saveDocumentFive(false);

            assertThat(statusOf(request)).isEqualTo(RequestStatus.MEETING_SCHEDULED);
            settle();
            assertThat(mail().to(applicant.getEmail()))
                    .as("เจ้าหน้าที่เลือกไม่รบกวนผู้ยื่น")
                    .isEmpty();
        }

        @Test
        @DisplayName("ติ๊ก — สถานะเดิน และมีอีเมลถึงผู้ยื่น")
        void whenNotifying() throws Exception {
            AcademicRequest request = saveDocumentFive(true);

            assertThat(statusOf(request)).isEqualTo(RequestStatus.MEETING_SCHEDULED);
            awaitCondition("อีเมลถึงผู้ยื่น",
                    () -> !mail().to(applicant.getEmail()).isEmpty());
        }

        @Test
        @DisplayName("ประวัติสถานะถูกบันทึกไม่ว่าจะแจ้งเตือนหรือไม่")
        void statusHistoryIsWrittenEitherWay() throws Exception {
            AcademicRequest request = saveDocumentFive(false);

            assertThat(academicService.getStatusHistory(request.getId()))
                    .as("หลักฐานว่างานเดินจริง ต้องมีแม้ไม่ได้ส่งอีเมล")
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("เฟส 2 — การขอกำหนดตำแหน่ง")
    class Phase2 {

        private PositionRequest saveDocumentSeven(boolean sendNotify) throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);

            var post = post("/admin/position/request/" + request.getId() + "/document/7")
                    .with(as(officer)).with(csrf())
                    .param("applicant_name", "สมชาย ใจดีวิชาการ");
            if (sendNotify) {
                post = post.param("sendNotify", "true");
            }
            mvc.perform(post).andExpect(status().is3xxRedirection());
            return request;
        }

        private PositionRequestStatus statusOf(PositionRequest request) {
            return positionService.findById(request.getId()).orElseThrow().getCurrentStatus();
        }

        @Test
        @DisplayName("ไม่ติ๊ก — สถานะเดิน แต่ไม่มีอีเมลถึงผู้ยื่น")
        void withoutNotifying() throws Exception {
            PositionRequest request = saveDocumentSeven(false);

            assertThat(statusOf(request)).isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
            settle();
            assertThat(mail().to(applicant.getEmail())).isEmpty();
        }

        @Test
        @DisplayName("ติ๊ก — สถานะเดิน และมีอีเมลถึงผู้ยื่น")
        void whenNotifying() throws Exception {
            PositionRequest request = saveDocumentSeven(true);

            assertThat(statusOf(request)).isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
            awaitCondition("อีเมลถึงผู้ยื่น",
                    () -> !mail().to(applicant.getEmail()).isEmpty());
        }
    }
}
