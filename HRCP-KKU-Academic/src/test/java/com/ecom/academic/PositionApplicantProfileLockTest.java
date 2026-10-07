package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * เฟส 2: คำนำหน้าและชื่อผู้ยื่นมาจากข้อมูลบุคลากรในทุกเอกสารของผู้ยื่น แก้เองไม่ได้
 * ช่องอื่นที่ระบบรู้ค่า (ตำแหน่ง หน่วยงาน สาขาวิชาตามเอกสารที่ 1 และงานสอนจากผลประเมิน) ดึงมาและล็อกเช่นกัน
 */
@DisplayName("เฟส 2: ล็อกชื่อผู้ยื่น และดึงงานสอนมาเติม")
class PositionApplicantProfileLockTest extends AbstractFlowTest {

    private static final String NAME = "สมชาย ใจดี";
    private static final String TITLE = "ผู้ช่วยศาสตราจารย์";

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private AcademicRequest linked;
    private PositionRequest request;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        applicant.setTitle("ผศ.ดร.");
        applicant = data.saveUser(applicant);
        linked = data.evaluationForCourse(applicant, "CP001101", "2568");
        request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, linked);
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private Map<String, String> formData(int docType) throws Exception {
        Object json = mvc.perform(get("/user/position/request/" + request.getId() + "/document/" + docType)
                .with(as(applicant)))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("existingData");
        return json == null ? Map.of()
                : new ObjectMapper().readValue((String) json, new TypeReference<Map<String, String>>() {
                });
    }

    @Test
    @DisplayName("ร่างอัตโนมัติที่ส่งชื่ออื่นมา ถูกเขียนทับด้วยชื่อจากข้อมูลบุคลากร")
    void autoDraftKeepsProfileName() throws Exception {
        mvc.perform(post("/api/draft/position/" + request.getId() + "/3")
                .with(as(applicant)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"นาย\",\"applicant_name\":\"ชื่อปลอม\"}"))
                .andExpect(status().isOk());

        assertThat(positionService.getLatestDocumentData(request.getId(), 3))
                .containsEntry("title", TITLE)
                .containsEntry("applicant_name", NAME);
    }

    @Test
    @DisplayName("บันทึกผ่านฟอร์ม — เอกสารที่ 6 ใช้ช่อง applicant_title ก็ถูกล็อกเช่นกัน")
    void formSaveKeepsProfileName() throws Exception {
        mvc.perform(post("/user/position/request/" + request.getId() + "/document/6")
                .with(as(applicant)).with(csrf())
                .param("action", "draft")
                .param("applicant_title", "นาย")
                .param("applicant_name", "ชื่อปลอม"))
                .andExpect(status().is3xxRedirection());

        assertThat(positionService.getLatestDocumentData(request.getId(), 6))
                .containsEntry("applicant_title", TITLE)
                .containsEntry("applicant_name", NAME);
    }

    @Test
    @DisplayName("เปิดฟอร์ม — ชื่อที่บันทึกไว้เดิมถูกแทนด้วยชื่อจากข้อมูลบุคลากร และช่องเป็นแบบอ่านอย่างเดียว")
    void formShowsLockedName() throws Exception {
        data.positionDocument(request, 9, "{\"applicant_name\":\"ชื่อเก่า\"}");

        assertThat(formData(9)).containsEntry("applicant_name", NAME).doesNotContainKey("title");

        String html = mvc.perform(get("/user/position/request/" + request.getId() + "/document/1")
                .with(as(applicant)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).containsPattern("name=\"title\" class=\"form-control\" readonly");
        assertThat(html).containsPattern("name=\"applicant_name\" class=\"form-control\" required\\s+readonly");
    }

    @Test
    @DisplayName("งานสอนดึงจากผลประเมินย้อนหลัง ๓ ปี — ไม่เอาแบบร่างและปีที่เก่ากว่านั้น")
    void teachingRowsFromEvaluations() throws Exception {
        data.evaluationForCourse(applicant, "CP002201", "2567");
        data.evaluationForCourse(applicant, "CP003301", "2560");
        data.evaluation(applicant, RequestStatus.DRAFT);

        Map<String, String> form = formData(1);

        assertThat(form)
                .containsEntry("teaching_subject_1", "CP001101 วิชาทดสอบ CP001101")
                .containsEntry("teaching_semester_1", "1/2568")
                .containsEntry("teaching_subject_2", "CP002201 วิชาทดสอบ CP002201")
                .containsEntry("teaching_semester_2", "1/2567")
                .doesNotContainKey("teaching_subject_3");
    }

    @Test
    @DisplayName("งานสอนที่ดึงมาล็อก — ค่าที่บันทึกไว้ถูกแทน ส่วนระดับ/ชั่วโมง และแถวที่ผู้ยื่นเพิ่มเองยังอยู่")
    void pulledTeachingRowsAreLocked() throws Exception {
        data.positionDocument(request, 1, "{\"teaching_subject_1\":\"วิชาที่กรอกเอง\","
                + "\"teaching_level_1\":\"ป.ตรี\",\"teaching_subject_2\":\"วิชาเพิ่มเอง\"}");

        assertThat(formData(1))
                .containsEntry("teaching_subject_1", "CP001101 วิชาทดสอบ CP001101")
                .containsEntry("teaching_level_1", "ป.ตรี")
                .containsEntry("teaching_subject_2", "วิชาเพิ่มเอง");

        mvc.perform(post("/api/draft/position/" + request.getId() + "/1")
                .with(as(applicant)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teaching_subject_1\":\"แก้เอง\",\"teaching_semester_1\":\"2/2560\"}"))
                .andExpect(status().isOk());
        assertThat(positionService.getLatestDocumentData(request.getId(), 1))
                .containsEntry("teaching_subject_1", "CP001101 วิชาทดสอบ CP001101")
                .containsEntry("teaching_semester_1", "1/2568");
    }

    @Test
    @DisplayName("สถานะในเอกสารที่ 2 ตามประเภทบุคลากรในเอกสารที่ 1 ของผลประเมินการสอนที่เลือก")
    void doc2StatusFollowsEvaluationDoc1() throws Exception {
        data.academicDocument(linked, 1, "{\"employee_type\":\"ข้าราชการ\"}");

        assertThat(formData(2)).containsEntry("status", "ข้าราชการ");
        String html = mvc.perform(get("/user/position/request/" + request.getId() + "/document/2")
                .with(as(applicant)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).containsPattern("name=\"status\" class=\"form-control\" readonly");

        mvc.perform(post("/api/draft/position/" + request.getId() + "/2")
                .with(as(applicant)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"พนักงานมหาวิทยาลัย\"}"))
                .andExpect(status().isOk());
        assertThat(positionService.getLatestDocumentData(request.getId(), 2))
                .containsEntry("status", "ข้าราชการ");
    }

    @Test
    @DisplayName("ตำแหน่งปัจจุบัน หน่วยงาน และสาขาวิชาตามเอกสารที่ 1 ถูกล็อก")
    void derivedFieldsAreLocked() throws Exception {
        data.positionDocument(request, 1, "{\"major\":\"วิทยาการคอมพิวเตอร์\"}");

        assertThat(formData(1))
                .containsEntry("current_position", "อาจารย์")
                .containsEntry("faculty", "วิทยาลัยการคอมพิวเตอร์")
                .containsEntry("university", "มหาวิทยาลัยขอนแก่น");
        assertThat(formData(4))
                .containsEntry("affiliation", "วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");

        mvc.perform(post("/api/draft/position/" + request.getId() + "/2")
                .with(as(applicant)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"major\":\"สาขาอื่น\",\"current_position\":\"ศาสตราจารย์\"}"))
                .andExpect(status().isOk());
        assertThat(positionService.getLatestDocumentData(request.getId(), 2))
                .containsEntry("major", "วิทยาการคอมพิวเตอร์")
                .containsEntry("current_position", "อาจารย์");
    }
}
