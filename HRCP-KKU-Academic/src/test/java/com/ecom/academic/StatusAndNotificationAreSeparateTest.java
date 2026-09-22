package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * สถานะเดินตามงานจริง และการแจ้งเตือนเดินตามสถานะ
 *
 * <p>สองอย่างนี้เคยถูกผูกเป็นเงื่อนไขเดียวกัน — เฟส 2 เลื่อนสถานะเฉพาะตอนเจ้าหน้าที่ติ๊ก
 * "แจ้งผู้ยื่น" คนที่เลือกไม่รบกวนผู้ยื่นจึงทำให้แถบความคืบหน้าค้างอยู่ที่เดิม ทั้งที่งานเดินไปแล้ว
 * ตอนนี้ไม่มีตัวแปรนั้นอีกแล้ว: สถานะเปลี่ยนเมื่อหนังสือลงนามครบ และเมื่อเปลี่ยนจริงผู้ยื่น
 * ได้รับแจ้งเสมอ
 *
 * <p>เทสต์ชุดนี้ล็อกสามอย่างที่ต้องเกิดพร้อมกันเสมอ: สถานะเปลี่ยน · มี status history เป็น
 * หลักฐาน · มีอีเมลถึงผู้ยื่น และล็อกอีกด้านว่าไม่มีใครสั่งให้มันเกิดก่อนเวลาได้ด้วยการยิง
 * พารามิเตอร์เก่าเข้ามา
 */
@DisplayName("สถานะเดินตามงานจริง และการแจ้งเตือนเดินตามสถานะ")
class StatusAndNotificationAreSeparateTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private UserDtls officer;
    private UserDtls dean;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
        dean = data.user("dean@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    @Nested
    @DisplayName("เฟส 1 — การประเมินการสอน")
    class Phase1 {

        @Test
        @DisplayName("ลงนามครบ: สถานะเปลี่ยน มีประวัติ และมีอีเมล ครบทั้งสามอย่าง")
        void allThreeHappenTogether() {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);
            data.academicDocument(request, 5, "{\"memo_no\":\"อว 660301.26.4/ว.1\"}");

            signEveryStep(circulate(SignatureModule.ACADEMIC, request.getId(), 5,
                    "{\"memo_no\":\"อว 660301.26.4/ว.1\"}", officer,
                    List.of(new SignerAssignment("dean", dean.getId()))), officer);

            assertThat(academicService.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.MEETING_SCHEDULED);
            assertThat(academicService.getStatusHistory(request.getId()))
                    .as("หลักฐานว่างานเดินจริง")
                    .isNotEmpty();
            awaitCondition("อีเมลถึงผู้ยื่น", () -> !mail().to(applicant.getEmail()).isEmpty());
        }

        @Test
        @DisplayName("ยิง sendNotify เข้ามาเองตอนบันทึก ก็สั่งให้สถานะเดินก่อนเวลาไม่ได้")
        void theOldParameterCannotForceAnything() throws Exception {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/5")
                    .with(as(officer)).with(csrf())
                    .param("memo_no", "อว 660301.26.4/ว.1")
                    .param("sendNotify", "true"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);
            assertThat(academicService.getLatestDocumentData(request.getId(), 5))
                    .as("พารามิเตอร์ที่เลิกใช้แล้วต้องไม่หลุดลงเนื้อเอกสาร")
                    .doesNotContainKey("sendNotify");
            settle();
            assertThat(mail().to(applicant.getEmail())).isEmpty();
        }
    }

    @Nested
    @DisplayName("เฟส 2 — การขอกำหนดตำแหน่ง")
    class Phase2 {

        @Test
        @DisplayName("ลงนามครบ: สถานะเปลี่ยนและมีประวัติ")
        void signingMovesTheStatus() {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 7, "{\"applicant_name\":\"สมชาย ใจดีวิชาการ\"}");

            signEveryStep(circulate(SignatureModule.POSITION, request.getId(), 7,
                    "{\"applicant_name\":\"สมชาย ใจดีวิชาการ\"}", officer,
                    List.of(new SignerAssignment("hr", officer.getId()),
                            new SignerAssignment("dean", dean.getId()))), officer);

            assertThat(positionService.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
            assertThat(positionService.getStatusHistory(request.getId())).isNotEmpty();
        }

        @Test
        @DisplayName("บันทึกเฉย ๆ ไม่เลื่อนสถานะ แม้ยิง sendNotify เข้ามา")
        void savingAloneChangesNothing() throws Exception {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);

            mvc.perform(post("/admin/position/request/" + request.getId() + "/document/7")
                    .with(as(officer)).with(csrf())
                    .param("applicant_name", "สมชาย ใจดีวิชาการ")
                    .param("sendNotify", "true"))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .isEqualTo(PositionRequestStatus.DOCUMENT_RECEIVED);
            settle();
            assertThat(mail().to(applicant.getEmail())).isEmpty();
        }
    }
}
