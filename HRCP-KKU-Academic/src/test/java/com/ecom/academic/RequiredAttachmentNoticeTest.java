package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * เอกสารที่ 2 — ต้องแนบเอกสารประกอบการประเมินผลการสอน (ไฟล์หรือลิงก์) อย่างน้อยหนึ่งรายการ
 *
 * <p>เอกสารชุดนี้ถูกส่งต่อให้กรรมการทั้งสามท่านพร้อมหนังสือเชิญ (เอกสารที่ 5) ถ้าไม่มี
 * กรรมการก็ไม่มีอะไรให้ประเมิน
 */
@DisplayName("เอกสารที่ 2: บังคับแนบเอกสารประกอบการประเมินผลการสอน")
class RequiredAttachmentNoticeTest extends AbstractFlowTest {

    private static final String DOC2_ALL_CHECKED =
            "{\"chk_app_1\":\"✓\",\"chk_app_2\":\"✓\",\"chk_app_3\":\"✓\",\"chk_app_4\":\"✓\",\"chk_app_5\":\"✓\"}";

    @Autowired
    private AcademicRequestService academicRequestService;

    private AcademicRequest draftFor(UserDtls applicant) {
        return data.evaluation(applicant, RequestStatus.DRAFT);
    }

    private void attachLink(AcademicRequest request) {
        AcademicAttachment link = new AcademicAttachment();
        link.setRequest(request);
        link.setOriginalFilename("แผนการสอน");
        link.setStoredFilePath("https://drive.example.invalid/teaching-plan");
        link.setFileType("LINK");
        link.setFileSize(0L);
        link.setChecklistItem(1);
        academicRequestService.saveAttachment(link);
    }

    @Test
    @DisplayName("หน้าเอกสารที่ 2 บอกว่าการแนบเอกสารเป็นเรื่องบังคับ")
    void thePageSaysAttachmentsAreRequired() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        String page = mvc.perform(get("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("หัวกล่องแนบเอกสารต้องติดป้ายบังคับ")
                .contains(">บังคับ</span>");
    }

    @Test
    @DisplayName("กดบันทึกทั้งที่ยังไม่มีไฟล์แนบ — ไม่บันทึก และเด้งกลับมาบอกเหตุผล")
    void savingWithoutAttachmentsIsRefused() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        expectAccepted(mvc.perform(post("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)).with(csrf())
                .param("action", "submit")),
                "/user/academic/request/" + draft.getId() + "/document-2?error=no_attachments");

        assertThat(academicRequestService.getDocumentsByType(draft.getId(), 2))
                .as("ไม่มีไฟล์แนบ เอกสารที่ 2 ต้องไม่ถูกบันทึก")
                .isEmpty();
    }

    @Test
    @DisplayName("แนบลิงก์แล้วกดบันทึก — บันทึกสำเร็จ (ลิงก์นับเป็นเอกสารแนบ)")
    void savingWithALinkSucceeds() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);
        attachLink(draft);

        expectAccepted(mvc.perform(post("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)).with(csrf())
                .param("action", "submit")),
                "/user/academic/request/" + draft.getId() + "/document/2?saved=1");

        assertThat(academicRequestService.getDocumentsByType(draft.getId(), 2)).isNotEmpty();
    }

    @Test
    @DisplayName("บันทึกร่างได้แม้ยังไม่มีไฟล์แนบ")
    void draftsDoNotNeedAttachments() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        expectAccepted(mvc.perform(post("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)).with(csrf())
                .param("action", "draft")),
                "/user/academic/request/" + draft.getId() + "/document-2?saved=draft");
    }

    @Test
    @DisplayName("หน้าที่ระบุ error=no_attachments แสดงข้อความเตือน")
    void theBouncedPageExplainsWhy() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        String page = mvc.perform(get("/user/academic/request/" + draft.getId() + "/document-2")
                .param("error", "no_attachments")
                .with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("กรุณาแนบเอกสารประกอบการประเมินผลการสอน (ไฟล์หรือลิงก์) อย่างน้อย 1 รายการ");
    }

    @Test
    @DisplayName("ยื่นคำร้องที่เอกสารที่ 2 บันทึกไว้แต่ไม่มีไฟล์แนบ — ไม่ส่ง ยังเป็นแบบร่าง")
    void submittingWithoutAttachmentsIsRefused() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);
        data.academicDocument(draft, 1, "{\"title\":\"นาย\",\"applicant_name\":\"ผู้ยื่น ทดสอบ\","
                + "\"employee_type\":\"พนักงานมหาวิทยาลัย\",\"current_position\":\"อาจารย์\",\"chk1\":\"✓\","
                + "\"course_code\":\"CP001\",\"course_name\":\"วิชาทดสอบ\",\"academic_year\":\"1/2569\"}");
        data.academicDocument(draft, 2, DOC2_ALL_CHECKED);

        String error = (String) mvc.perform(post("/user/academic/request/" + draft.getId() + "/submit")
                .with(csrf()).with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getFlashMap().get("error");

        assertThat(error).contains("แนบเอกสารประกอบการประเมินผลการสอน");
        assertThat(academicRequestService.findById(draft.getId()).orElseThrow().getCurrentStatus())
                .isEqualTo(RequestStatus.DRAFT);

        // มีเอกสารแนบแล้ว ด่านนี้ผ่าน — ไปติดด่านถัดไปคือการลงนามของผู้ยื่น
        attachLink(draft);
        error = (String) mvc.perform(post("/user/academic/request/" + draft.getId() + "/submit")
                .with(csrf()).with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getFlashMap().get("error");
        assertThat(error).doesNotContain("แนบเอกสาร").contains("ลงนาม");
    }
}
