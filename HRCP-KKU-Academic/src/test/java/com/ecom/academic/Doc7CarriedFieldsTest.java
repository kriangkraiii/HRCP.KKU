package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/** เอกสารที่ 7: ข้อมูลผู้เสนอขอและรายชื่อกรรมการมาจากคำสั่งในเอกสารที่ 4 แก้ในฉบับนี้ไม่ได้ */
@DisplayName("เอกสารที่ 7 — ช่องที่ดึงจากเอกสารก่อนหน้าถูกล็อก")
class Doc7CarriedFieldsTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    private UserDtls officer;
    private AcademicRequest request;

    @BeforeEach
    void cast() {
        officer = data.admin();
        request = data.evaluation(data.applicant(), RequestStatus.MEETING_SCHEDULED);
        data.academicDocument(request, 1,
                "{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"สมชาย ทดสอบยื่น\","
                        + "\"chk1\":\" \",\"chk2\":\"✓\"}");
        data.academicDocument(request, 3,
                "{\"committee_1_name\":\"กรรมการหนึ่ง\",\"committee_2_name\":\"กรรมการสอง\","
                        + "\"committee_3_name\":\"กรรมการสาม\"}");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asOfficer() {
        return user(officer.getEmail()).roles(officer.getRole().replace("ROLE_", ""));
    }

    @Test
    @DisplayName("หน้าฟอร์มแสดงค่าจากเอกสารต้นทางแบบอ่านอย่างเดียว ช่องคะแนนยังกรอกได้")
    void theFormShowsThemReadOnly() throws Exception {
        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/7").with(asOfficer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Document page = Jsoup.parse(html);

        assertThat(page.selectFirst("input[name=title]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=title]").val()).isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(page.selectFirst("input[name=applicant_name]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=requested_position]").val()).isEqualTo("รองศาสตราจารย์");
        assertThat(page.selectFirst("input[name=requested_position]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=committee_1_name]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=committee_2_name]").val()).isEqualTo("กรรมการสอง");
        assertThat(page.selectFirst("input[name=committee_3_name]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst(".sec-score-single").hasAttr("readonly")).isFalse();
    }

    @Test
    @DisplayName("ส่งค่าอื่นมาเอง ระบบยังบันทึกค่าจากเอกสารต้นทาง")
    void aTamperedPostKeepsTheSourceValues() throws Exception {
        mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/7")
                .with(asOfficer()).with(csrf())
                .param("action", "draft")
                .param("applicant_name", "แก้เอง")
                .param("committee_2_name", "คนอื่น"))
                .andExpect(status().is3xxRedirection());

        assertThat(academicService.getLatestDocumentData(request.getId(), 7))
                .containsEntry("applicant_name", "สมชาย ทดสอบยื่น")
                .containsEntry("committee_2_name", "กรรมการสอง");
    }

    @Test
    @DisplayName("บันทึกร่างอัตโนมัติ (ทางที่ปุ่มส่งเวียนลงนามใช้) ก็ใช้ค่าจากต้นทาง และทิ้งรหัสผู้ลงนามของคนเก่า")
    void theAutoDraftKeepsTheSourceValuesToo() throws Exception {
        mvc.perform(post("/api/draft/academic/" + request.getId() + "/7")
                .with(asOfficer()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"committee_1_name\":\"ประธานเก่า\",\"committee_1_name__signer\":\"999\","
                        + "\"requested_position\":\"ศาสตราจารย์\"}"))
                .andExpect(status().isOk());

        assertThat(academicService.getLatestDocumentData(request.getId(), 7))
                .containsEntry("committee_1_name", "กรรมการหนึ่ง")
                .containsEntry("requested_position", "รองศาสตราจารย์")
                .doesNotContainKey("committee_1_name__signer");
    }
}
