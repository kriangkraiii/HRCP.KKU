package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * ส่งเอกสารกลับให้แก้แล้ว สถานะที่แสดงต้องบอกว่างานรอผู้ยื่นอยู่
 *
 * <p>การส่งกลับไม่เปลี่ยนสถานะคำร้อง เดิมทั้งหน้าแอดมินและผู้ยื่นจึงยังขึ้น "รับคำร้อง"
 * และขั้น "แต่งตั้งอนุกรรมการ · กำลังดำเนินการ" สีน้ำเงิน เหมือนเรื่องเดินต่อตามปกติ
 */
@DisplayName("สถานะที่แสดงหลังส่งเอกสารกลับให้แก้")
class SentBackStatusShownTest extends AbstractFlowTest {

    private static final String AWAITING = "รอผู้ยื่นแก้ไขเอกสาร";

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private UserDtls officer;
    private AcademicRequest request;

    @BeforeEach
    void sendBack() {
        applicant = data.applicant();
        officer = data.admin();
        request = data.evaluation(applicant, RequestStatus.RECEIVED);
        data.academicDocument(request, 1, "{}");
        academicService.openDocumentForRevision(request.getId(), 1, "แก้ชื่อรายวิชา");
    }

    private Document page(String url, UserDtls who, String role) throws Exception {
        return Jsoup.parse(mvc.perform(get(url).with(user(who.getEmail()).roles(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("แดชบอร์ดผู้ยื่น: ป้ายต้องแก้ไขแทนป้ายรับคำร้อง ขั้นที่ค้างเป็นสีส้ม")
    void applicantDashboard() throws Exception {
        Document html = page("/user/academic/dashboard", applicant, "USER");
        assertThat(html.select(".request-card-status .status-RECEIVED")).isEmpty();
        assertThat(html.select(".request-card-status .status-needs-revision").text()).contains("ต้องแก้ไขเอกสาร");
        assertThat(html.select(".timeline-v2-step.t-active")).isEmpty();
        assertThat(html.select(".timeline-v2-step.t-revise .timeline-v2-hint").text()).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("หน้ารายละเอียดผู้ยื่น: ป้ายและแถบความคืบหน้าบอกว่าต้องแก้")
    void applicantDetail() throws Exception {
        Document html = page("/user/academic/request/" + request.getId(), applicant, "USER");
        assertThat(html.select(".page-header .status-RECEIVED")).isEmpty();
        assertThat(html.select(".page-header .status-needs-revision")).hasSize(1);
        assertThat(html.select(".timeline-v2-step.t-revise .timeline-v2-hint").text()).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("หน้าแอดมิน: สถานะและแถบความคืบหน้าบอกว่ารอผู้ยื่นแก้")
    void adminDetailAndList() throws Exception {
        Document detail = page("/admin/academic/request/" + request.getId(), officer, "ADMIN");
        assertThat(detail.select(".detail-hero-status").text()).isEqualTo(AWAITING);
        assertThat(detail.select(".detail-progress-step.dp-active")).isEmpty();
        assertThat(detail.select(".detail-progress-step.dp-revise .detail-progress-step-hint").text())
                .isEqualTo(AWAITING);

        assertThat(detail.select("#nextStep h5").text()).contains("รอผู้ยื่นแก้ไขเอกสารที่ส่งกลับ");

        Document list = page("/admin/academic/requests", officer, "ADMIN");
        assertThat(list.select("#req-" + request.getId() + " .at-status").text()).contains(AWAITING);
    }

    /** หลังรับรองมติกลั่นกรองฯ flow ไม่ให้เปลี่ยนเป็น "ส่งแก้ไข" — สถานะคงเดิม แต่ที่แสดงต้องบอกว่ารอผู้ยื่น */
    @Test
    @DisplayName("เฟส 2 ส่งกลับในขั้นที่สถานะไม่เปลี่ยนตาม: หน้าแอดมิน แดชบอร์ด และขั้นต่อไปบอกว่ารอผู้ยื่นแก้")
    void positionSentBackWithoutStatusChange() throws Exception {
        PositionRequest pos = data.positionRequest(applicant, PositionRequestStatus.SCREENING_APPROVED, null);
        data.positionDocument(pos, 2, "{}");
        positionService.openDocumentForRevision(pos.getId(), 2, "แก้สาขาวิชา");

        Document detail = page("/admin/position/request/" + pos.getId(), officer, "ADMIN");
        assertThat(detail.select(".detail-hero-status").text()).isEqualTo(AWAITING);
        assertThat(detail.select(".detail-progress-step.dp-active")).isEmpty();
        assertThat(detail.select(".detail-progress-step.dp-revise .detail-progress-step-label").text())
                .isEqualTo(AWAITING);
        assertThat(detail.select("#nextStep h5").text()).contains("รอผู้ยื่นแก้ไขเอกสารที่ส่งกลับ");

        Document dashboard = page("/user/academic/dashboard", applicant, "USER");
        assertThat(dashboard.select(".timeline-v2-step.t-revise .timeline-v2-label").text()).contains(AWAITING);

        Document list = page("/admin/academic/requests?type=position", officer, "ADMIN");
        assertThat(list.select("#pos-req-" + pos.getId() + " .at-status").text()).contains(AWAITING);
    }
}
