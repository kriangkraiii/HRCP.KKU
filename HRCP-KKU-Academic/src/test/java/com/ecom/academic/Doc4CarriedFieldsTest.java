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

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/** เอกสารที่ 4: ข้อมูลผู้ยื่นและรายชื่อกรรมการมาจากเอกสารก่อนหน้า แก้ในฉบับนี้ไม่ได้ */
@DisplayName("เอกสารที่ 4 — ช่องที่ดึงจากเอกสารก่อนหน้าถูกล็อก")
class Doc4CarriedFieldsTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    private UserDtls officer;
    private AcademicRequest request;

    @BeforeEach
    void cast() {
        officer = data.admin();
        request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        data.academicDocument(request, 1,
                "{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"สมชาย ทดสอบยื่น\","
                        + "\"employee_type\":\"พนักงานมหาวิทยาลัย\",\"current_position\":\"ผู้ช่วยศาสตราจารย์\","
                        + "\"chk1\":\" \",\"chk2\":\"✓\"}");
        data.academicDocument(request, 3,
                "{\"committee_1_name\":\"กรรมการหนึ่ง\",\"committee_2_name\":\"กรรมการสอง\","
                        + "\"committee_3_name\":\"กรรมการสาม\"}");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asOfficer() {
        return user(officer.getEmail()).roles(officer.getRole().replace("ROLE_", ""));
    }

    @Test
    @DisplayName("หน้าฟอร์มแสดงค่าจากเอกสารต้นทางแบบอ่านอย่างเดียว")
    void theFormShowsThemReadOnly() throws Exception {
        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/4").with(asOfficer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Document page = Jsoup.parse(html);

        assertThat(page.selectFirst("input[name=applicant_name]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=applicant_name]").val()).isEqualTo("สมชาย ทดสอบยื่น");
        assertThat(page.selectFirst("input[name=requested_position]").val()).isEqualTo("รองศาสตราจารย์");
        assertThat(page.selectFirst("input[name=committee_2_name]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=committee_2_name]").val()).isEqualTo("กรรมการสอง");
        assertThat(page.selectFirst("input[name=order_no]").hasAttr("readonly")).isFalse();
    }

    @Test
    @DisplayName("ส่งค่าอื่นมาเอง ระบบยังบันทึกค่าจากเอกสารต้นทาง")
    void aTamperedPostKeepsTheSourceValues() throws Exception {
        mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/4")
                .with(asOfficer()).with(csrf())
                .param("action", "draft")
                .param("order_no", "123")
                .param("applicant_name", "แก้เอง")
                .param("committee_1_name", "คนอื่น"))
                .andExpect(status().is3xxRedirection());

        assertThat(academicService.getLatestDocumentData(request.getId(), 4))
                .containsEntry("order_no", "123")
                .containsEntry("applicant_name", "สมชาย ทดสอบยื่น")
                .containsEntry("committee_1_name", "กรรมการหนึ่ง");
    }
}
