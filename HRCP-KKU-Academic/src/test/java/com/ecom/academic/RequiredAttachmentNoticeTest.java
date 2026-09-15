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

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * เอกสารที่ 2 — การแนบไฟล์หรือลิงก์เพิ่มเติม (ไม่บังคับ)
 */
@DisplayName("เอกสารที่ 2: ตรวจสอบการแสดงผลและบันทึกเอกสารที่ 2 (ไม่บังคับแนบไฟล์)")
class RequiredAttachmentNoticeTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicRequestService;

    private AcademicRequest draftFor(UserDtls applicant) {
        return data.evaluation(applicant, RequestStatus.DRAFT);
    }

    @Test
    @DisplayName("เปิดหน้าครั้งแรกที่ยังไม่มีไฟล์แนบ — ต้องระบุว่าเป็นทางเลือก (ไม่บังคับ)")
    void thePageSaysAttachmentsAreOptional() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        String page = mvc.perform(get("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("ผู้ยื่นต้องเห็นข้อความว่าการแนบไฟล์/ลิงก์เป็นทางเลือก ไม่บังคับ")
                .contains("ไม่บังคับ");
        assertThat(page)
                .as("ต้องไม่แสดงข้อความว่าจำเป็นต้องแนบ")
                .doesNotContain("จำเป็นต้องแนบ");
    }

    @Test
    @DisplayName("กดบันทึกทั้งที่ยังไม่มีไฟล์แนบ — ต้องบันทึกสำเร็จและส่งต่อไปยังหน้ารายละเอียดคำร้อง")
    void savingWithoutAttachmentsSucceeds() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        expectAccepted(mvc.perform(post("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)).with(csrf())
                .param("action", "submit")),
                "/user/academic/request/" + draft.getId() + "?success=doc2_submitted");

        assertThat(academicRequestService.getDocumentsByType(draft.getId(), 2))
                .as("แม้ไม่มีไฟล์แนบ เอกสารที่ 2 ก็ต้องถูกบันทึกสำเร็จ")
                .isNotEmpty();
    }

    @Test
    @DisplayName("หน้าที่ระบุ query error=no_attachments ยังคงแสดงข้อความเตือนให้ผู้ใช้ทราบ")
    void theBouncedPageExplainsWhy() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        String page = mvc.perform(get("/user/academic/request/" + draft.getId() + "/document-2")
                .param("error", "no_attachments")
                .with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("query string ที่ส่งเข้ามา error=no_attachments ต้องถูกแปลเป็นข้อความบนหน้าจอ")
                .contains("กรุณาแนบไฟล์เอกสารประกอบการประเมินผลการสอนอย่างน้อย 1 ไฟล์");
    }
}
