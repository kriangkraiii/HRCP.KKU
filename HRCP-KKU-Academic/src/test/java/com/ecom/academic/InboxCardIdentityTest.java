package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * การ์ดในหน้ารอลงนามต้องบอกได้ว่าเป็นของคำร้องไหน
 *
 * <p>เดิมการ์ดมีแค่ชื่อเอกสาร ตำแหน่งที่ลงนาม และความคืบหน้า เอกสารชนิดเดียวกันจากสองคำร้องที่ถึงขั้นเดียวกัน
 * จึงขึ้นสองการ์ดที่เหมือนกันทุกตัวอักษร ผู้ลงนามนึกว่าระบบส่งซ้ำ
 */
@DisplayName("การ์ดรอลงนามบอกผู้ยื่นและเลขคำร้อง")
class InboxCardIdentityTest extends AbstractFlowTest {

    /** เอกสารที่ 1 ที่กรอกครบ — ต้องครบ ไม่งั้นระบบไม่ยอมสร้างซองให้ */
    private static final String DOC1 = """
            {"title":"ผศ.","applicant_name":"สมชาย ใจดี","employee_type":"ข้าราชการ",
             "current_position":"อาจารย์","chk1":"✓","chk2":"  ","course_code":"CS101",
             "course_name":"การเขียนโปรแกรม","academic_year":"1/2569"}""";

    private AcademicRequest openDocumentOne(UserDtls applicant) {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
        data.academicDocument(request, 1, DOC1);
        var created = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "เอกสารที่ 1", DOC1, List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        assertThat(created.error()).isNull();
        return request;
    }

    @Test
    @DisplayName("สองคำร้อง เอกสารเดียวกัน: แต่ละการ์ดมีเลขคำร้องของตัวเอง")
    void eachCardNamesItsRequest() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest first = openDocumentOne(applicant);
        AcademicRequest second = openDocumentOne(applicant);

        String html = mvc.perform(get("/esign/inbox")
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(first.getRequestCode()).contains(second.getRequestCode());
        assertThat(html).contains(applicant.getName());
    }

    @Test
    @DisplayName("ส่งเอกสารเดิมไปลงนามซ้ำ: ไม่ได้ซองที่สอง")
    void sendingTheSameDocumentTwiceIsRefused() {
        UserDtls applicant = data.applicant();
        AcademicRequest request = openDocumentOne(applicant);

        var again = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "เอกสารที่ 1", DOC1, List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());

        assertThat(again.ok()).isFalse();
        assertThat(signatureWorkflow.findInbox(applicant)).hasSize(1);
    }
}
