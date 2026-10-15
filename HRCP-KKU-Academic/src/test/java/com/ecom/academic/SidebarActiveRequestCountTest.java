package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ตัวเลขคำร้องที่กำลังดำเนินการบนเมนูของแอดมินและผู้ยื่น — นับเฉพาะที่ยื่นแล้วและยังไม่จบ
 * แบบร่างกับคำร้องที่จบแล้วไม่นับ
 */
@DisplayName("ตัวเลขคำร้องที่กำลังดำเนินการบนเมนู")
class SidebarActiveRequestCountTest extends AbstractFlowTest {

    private String sidebarAsAdmin() throws Exception {
        UserDtls admin = data.admin();
        return mvc.perform(get("/admin/academic/dashboard")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("นับคำร้องที่ยื่นแล้วและยังไม่จบ แยกตามประเภท")
    void countsOnlySubmittedUnfinishedRequests() throws Exception {
        UserDtls applicant = data.applicant();
        data.evaluation(applicant, RequestStatus.DRAFT);
        data.evaluation(applicant, RequestStatus.RECEIVED);
        data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
        // คำร้องขอตำแหน่งหนึ่งคำร้องผูกผลประเมินได้หนึ่งฉบับ
        for (PositionRequestStatus st : new PositionRequestStatus[] { PositionRequestStatus.DRAFT,
                PositionRequestStatus.DOCUMENT_RECEIVED, PositionRequestStatus.SENT_TO_HR }) {
            AcademicRequest done = data.evaluation(applicant, RequestStatus.COMPLETED);
            data.positionRequest(applicant, st, done);
        }

        String html = sidebarAsAdmin();

        assertThat(html).containsPattern("id=\"nav-eval\"[\\s\\S]*?sidebar-count-badge[^>]*>2</span>");
        assertThat(html).containsPattern("id=\"nav-position\"(?:(?!</a>)[\\s\\S])*?sidebar-count-badge[^>]*>1</span>");
    }

    @Test
    @DisplayName("ผู้ยื่น — นับเฉพาะคำร้องของตัวเองที่ยังไม่จบ")
    void applicantSeesOwnUnfinishedRequestsOnly() throws Exception {
        UserDtls applicant = data.applicant();
        data.evaluation(applicant, RequestStatus.DRAFT);
        data.evaluation(applicant, RequestStatus.RECEIVED);
        AcademicRequest done = data.evaluation(applicant, RequestStatus.COMPLETED);
        data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, done);
        data.evaluation(data.otherApplicant(), RequestStatus.RECEIVED);

        String html = mvc.perform(get("/user/academic/dashboard")
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).containsPattern("id=\"nav-new\"(?:(?!</a>)[\\s\\S])*?sidebar-count-badge[^>]*>1</span>");
        assertThat(html).containsPattern("id=\"nav-position\"(?:(?!</a>)[\\s\\S])*?sidebar-count-badge[^>]*>1</span>");
    }

    @Test
    @DisplayName("ไม่มีคำร้องที่กำลังดำเนินการ: ไม่แสดงป้าย")
    void noBadgeWhenNothingIsActive() throws Exception {
        assertThat(sidebarAsAdmin()).doesNotContain("sidebar-count-badge ms-auto\"");
    }
}
