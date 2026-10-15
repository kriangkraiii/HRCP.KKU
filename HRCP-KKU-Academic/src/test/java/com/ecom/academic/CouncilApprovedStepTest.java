package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ขั้นสุดท้ายของแถบความคืบหน้า (สภามหาวิทยาลัยอนุมัติ) เมื่อถึงแล้ว — จบแล้ว จึงเป็นสีเขียว ไม่ใช่สีน้ำเงินกระพริบ
 * ของขั้นที่กำลังดำเนินการ และเขียนว่า "อนุมัติแล้ว"
 */
@DisplayName("แถบความคืบหน้าผู้ยื่น: สภามหาวิทยาลัยอนุมัติแล้ว")
class CouncilApprovedStepTest extends AbstractFlowTest {

    private Element lastStep(UserDtls applicant) throws Exception {
        String html = mvc.perform(get("/user/position/dashboard").with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var steps = Jsoup.parse(html).select(".progress-tracker .progress-step");
        assertThat(steps).isNotEmpty();
        return steps.last();
    }

    @Test
    @DisplayName("อนุมัติแล้ว: ขั้นสุดท้ายเสร็จ (สีเขียว) และมีคำว่า แล้ว")
    void approvedIsDone() throws Exception {
        UserDtls applicant = data.applicant();
        data.positionRequest(applicant, PositionRequestStatus.COUNCIL_APPROVED, null, "ผู้ช่วยศาสตราจารย์");

        Element last = lastStep(applicant);
        assertThat(last.classNames()).contains("completed", "completed-final").doesNotContain("active");
        assertThat(last.selectFirst(".step-label").text()).isEqualTo("สภามหาวิทยาลัยอนุมัติแล้ว");
    }

    @Test
    @DisplayName("ยังไม่ถึง: ขั้นสุดท้ายเขียนว่า สภามหาวิทยาลัยอนุมัติ (ยังไม่เกิด จึงไม่มีคำว่า แล้ว)")
    void notYetReached() throws Exception {
        UserDtls applicant = data.applicant();
        data.positionRequest(applicant, PositionRequestStatus.SENT_TO_HR, null, "ผู้ช่วยศาสตราจารย์");

        Element last = lastStep(applicant);
        assertThat(last.classNames()).doesNotContain("completed", "active");
        assertThat(last.selectFirst(".step-label").text()).isEqualTo("สภามหาวิทยาลัยอนุมัติ");
    }
}
