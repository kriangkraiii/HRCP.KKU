package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * เจ้าหน้าที่ส่งเอกสารกลับให้แก้ แต่สถานะคำร้องยังเป็น "รับคำร้อง" ตามเดิม
 *
 * <p>ผู้ยื่นเห็นแต่การ์ดบนแดชบอร์ด ถ้าการ์ดไม่บอก ผู้ยื่นก็ไม่รู้ว่ามีงานรออยู่
 */
@DisplayName("ผู้ยื่นเห็นบนแดชบอร์ดว่าถูกส่งเอกสารกลับให้แก้")
class SentBackIsVisibleToApplicantTest extends AbstractFlowTest {

    private static final String REASON = "กรุณาแก้ชื่อรายวิชาให้ตรงกับมคอ.3";

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
    }

    private String dashboard(String url) throws Exception {
        return mvc.perform(get(url).with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("คำร้องประเมินการสอน: การ์ดบอกว่าต้องแก้เอกสารไหน พร้อมเหตุผลและลิงก์")
    void academicCardShowsTheSentBackDocument() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        data.academicDocument(request, 1, "{}");

        assertThat(dashboard("/user/academic/dashboard")).doesNotContain("ต้องแก้ไขเอกสาร");

        academicService.openDocumentForRevision(request.getId(), 1, REASON);

        String html = dashboard("/user/academic/dashboard");
        assertThat(html)
                .contains("ต้องแก้ไขเอกสาร")
                .contains("เจ้าหน้าที่ส่งเอกสารกลับมาให้ท่านแก้ไข")
                .contains(REASON)
                .contains("/user/academic/request/" + request.getId() + "/document/1");
    }

    @Test
    @DisplayName("คำร้องขอตำแหน่ง: การ์ดทั้งสองแดชบอร์ดบอกว่าต้องแก้เอกสาร")
    void positionCardsShowTheSentBackDocument() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, 2, "{}");

        positionService.openDocumentForRevision(request.getId(), 2, REASON);

        String link = "/user/position/request/" + request.getId() + "/document/2";
        assertThat(dashboard("/user/position/dashboard"))
                .contains("ต้องแก้ไขเอกสาร").contains(REASON).contains(link);
        assertThat(dashboard("/user/academic/dashboard"))
                .contains("ต้องแก้ไขเอกสาร").contains(REASON).contains(link);
    }

    @Test
    @DisplayName("เอกสารที่ 6 ฉบับงานวิจัยถูกส่งกลับ: แถบเลขเอกสารและหัวการ์ดต้องขึ้นว่าต้องแก้ เหมือนฉบับอื่น")
    void workCopySentBackIsFlaggedOnTheCard() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null,
                "ผู้ช่วยศาสตราจารย์");
        data.positionDocument(request, 1, "{\"asst_research_working_1\":\"งานวิจัยเรื่องแรก\"}");
        data.positionDocument(request, 901, "{\"title_name\":\"งานวิจัยเรื่องแรก\"}");

        positionService.openDocumentForRevision(request.getId(), 901, REASON);

        org.jsoup.nodes.Document page = org.jsoup.Jsoup.parse(dashboard("/user/position/request/" + request.getId()));
        assertThat(page.select(".step-indicator.revise")).as("แถบเลขเอกสารด้านบน").hasSize(1);
        org.jsoup.select.Elements card = page.select(".card-academic:has(.card-header:contains(ลักษณะการมีส่วนร่วมในผลงาน))");
        assertThat(card.select(".card-header .badge:contains(ต้องแก้ไข)")).as("หัวการ์ดเอกสารที่ 6").isNotEmpty();
        assertThat(card.select("a.btn-warning[href=/user/position/request/" + request.getId() + "/document/901]"))
                .as("ปุ่มแก้ไขของฉบับที่ส่งกลับ").hasSize(1);
    }
}
