package com.ecom.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * รหัส {@code ?error=} ที่ controller ส่งกลับมา ต้องมีข้อความบนหน้าปลายทาง
 *
 * <p>Thymeleaf ประเมินนิพจน์ในกล่องข้อความเฉพาะตอนมีพารามิเตอร์นั้น หน้าที่ render ปกติจึงไม่จับรหัสที่ลืมเขียน
 * — เดิมกล่องสีแดงขึ้นมาว่าง ๆ (เกินขนาดไฟล์แนบ) หรือขึ้นข้อความที่ผิดเรื่อง (ยังไม่มีงานวิจัย → "ส่งคำร้องผิดพลาด")
 */
@DisplayName("ข้อความของรหัสข้อผิดพลาดใน URL แสดงบนหน้าปลายทาง")
class RedirectMessagesRenderTest extends AbstractFlowTest {

    private String page(String url, UserDtls who) throws Exception {
        return mvc.perform(get(url).with(user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("แอดมิน: ไฟล์แนบเกิน 75 MB — ทั้งคำร้องประเมินการสอนและคำร้องขอตำแหน่ง")
    void totalSizeExceededIsExplained() throws Exception {
        UserDtls admin = data.admin();
        AcademicRequest evaluation = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        PositionRequest position = data.positionRequest(data.applicant(), PositionRequestStatus.DOCUMENT_RECEIVED, null);

        assertThat(page("/admin/academic/request/" + evaluation.getId() + "?error=total_size_exceeded", admin))
                .contains("เกิน 75 MB");
        assertThat(page("/admin/position/request/" + position.getId() + "?error=total_size_exceeded", admin))
                .contains("เกิน 75 MB");
    }

    @Test
    @DisplayName("ผู้ยื่น: ยังไม่มีงานวิจัย ไม่ใช่ข้อผิดพลาดในการส่งคำร้อง")
    void noResearchWorksIsNotASubmissionError() throws Exception {
        UserDtls applicant = data.applicant();
        PositionRequest position = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);

        String html = page("/user/position/request/" + position.getId() + "?error=no_research_works", applicant);

        assertThat(html).contains("ยังไม่มีงานวิจัยในเอกสารที่ 1")
                .doesNotContain("เกิดข้อผิดพลาดในการส่งคำร้อง");
    }

    @Test
    @DisplayName("แอดมิน: ผลประเมินการสอนข้างคำร้องขอตำแหน่งคือฉบับที่ผูกไว้ ไม่ใช่ฉบับล่าสุด และปุ่มดาวน์โหลดเปิดได้จริง")
    void positionPageShowsTheLinkedEvaluation() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest linked = data.completedEvaluation(applicant, RequestStatus.COMPLETED, "1 ตุลาคม 2571");
        AcademicRequest newer = data.completedEvaluation(applicant, RequestStatus.COMPLETED, "9 กันยายน 2572");
        assertThat(newer.getId()).isGreaterThan(linked.getId());
        PositionRequest position = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, linked);

        String html = page("/admin/position/request/" + position.getId(), data.admin());

        assertThat(html).contains("1 ตุลาคม 2571").doesNotContain("9 กันยายน 2572");
        assertThat(html)
                .as("ลิงก์เดิม /admin/academic/document/download ไม่มีหน้าปลายทาง (404)")
                .doesNotContain("/admin/academic/document/download")
                .contains("/admin/academic/request/" + linked.getId() + "/document/9/download");
    }

    @Test
    @DisplayName("คำร้องที่ไม่มีอยู่ (ลิงก์เก่าของแบบร่างที่ลบแล้ว) ตอบ 404 ไม่ใช่ 500")
    void missingRequestIsNotFoundNotAServerError() throws Exception {
        UserDtls applicant = data.applicant();
        UserDtls admin = data.admin();

        mvc.perform(get("/user/academic/request/987654").with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/user/position/request/987654").with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/academic/request/987654").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/position/request/987654").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ผู้ยื่น: กดส่งคำร้องซ้ำ บอกว่าส่งไปแล้ว")
    void doubleSubmitIsExplained() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest evaluation = data.evaluation(applicant, RequestStatus.RECEIVED);

        assertThat(page("/user/academic/request/" + evaluation.getId() + "?error=already_submitted", applicant))
                .contains("คำร้องนี้ส่งไปแล้ว");
    }
}
