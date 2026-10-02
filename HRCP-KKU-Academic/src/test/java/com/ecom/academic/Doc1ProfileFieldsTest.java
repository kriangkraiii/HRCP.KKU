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
import com.ecom.repository.UserRepository;
import com.ecom.support.AbstractFlowTest;

/** เอกสารที่ 1 ของผู้ยื่น: คำนำหน้า ชื่อ และตำแหน่งปัจจุบันมาจากข้อมูลบุคลากร แก้ในฟอร์มไม่ได้ */
@DisplayName("เอกสารที่ 1 — ข้อมูลผู้ขอรับการประเมินถูกล็อก ยกเว้นประเภทบุคลากร")
class Doc1ProfileFieldsTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private UserRepository userRepository;

    private UserDtls applicant;
    private AcademicRequest request;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        applicant.setTitle("ผศ.ดร.");
        applicant.setAcademicPosition("ผู้ช่วยศาสตราจารย์");
        applicant = userRepository.save(applicant);
        request = data.evaluation(applicant, RequestStatus.DRAFT);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asApplicant() {
        return user(applicant.getEmail()).roles(applicant.getRole().replace("ROLE_", ""));
    }

    @Test
    @DisplayName("หน้าฟอร์มแสดงค่าจากโปรไฟล์แบบอ่านอย่างเดียว ประเภทบุคลากรยังเลือกได้")
    void theFormShowsThemReadOnly() throws Exception {
        String html = mvc.perform(get("/user/academic/request/" + request.getId() + "/document-1").with(asApplicant()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Document page = Jsoup.parse(html);

        assertThat(page.selectFirst("input[name=title]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=title]").val()).isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(page.selectFirst("input[name=applicant_name]").hasAttr("readonly")).isTrue();
        assertThat(page.selectFirst("input[name=applicant_name]").val()).isEqualTo("สมชาย ใจดี");
        assertThat(page.selectFirst("input[name=current_position]").hasAttr("readonly")).isTrue();
        assertThat(page.select("input[name=employee_type]")).allMatch(r -> !r.hasAttr("readonly") && !r.hasAttr("disabled"));
    }

    @Test
    @DisplayName("ส่งค่าอื่นมาเอง ระบบบันทึกค่าจากโปรไฟล์ แต่ประเภทบุคลากรเป็นค่าที่เลือก")
    void aTamperedPostKeepsTheProfileValues() throws Exception {
        mvc.perform(post("/user/academic/request/" + request.getId() + "/document-1")
                .with(asApplicant()).with(csrf())
                .param("action", "draft")
                .param("title", "ศาสตราจารย์")
                .param("applicant_name", "แก้เอง")
                .param("current_position", "รองศาสตราจารย์")
                .param("employee_type", "ข้าราชการ"))
                .andExpect(status().is3xxRedirection());

        assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                .containsEntry("title", "ผู้ช่วยศาสตราจารย์")
                .containsEntry("applicant_name", "สมชาย ใจดี")
                .containsEntry("current_position", "ผู้ช่วยศาสตราจารย์")
                .containsEntry("employee_type", "ข้าราชการ");
    }

    @Test
    @DisplayName("บันทึกร่างอัตโนมัติก็ใช้ค่าจากโปรไฟล์")
    void theAutoDraftKeepsTheProfileValuesToo() throws Exception {
        mvc.perform(post("/api/draft/academic/" + request.getId() + "/1")
                .with(asApplicant()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicant_name\":\"แก้เอง\",\"employee_type\":\"พนักงานมหาวิทยาลัย\"}"))
                .andExpect(status().isOk());

        assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                .containsEntry("applicant_name", "สมชาย ใจดี")
                .containsEntry("employee_type", "พนักงานมหาวิทยาลัย");
    }
}
