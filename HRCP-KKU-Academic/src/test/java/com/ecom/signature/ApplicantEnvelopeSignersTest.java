package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผู้ยื่นส่งเอกสารของตัวเองไปลงนามได้ — แต่เลือกได้แค่ตัวเองในช่องผู้ยื่น
 *
 * <p>แผงลงนามฝั่งผู้ยื่นส่งรหัสบัญชีมาเฉพาะช่องผู้ยื่น ช่องอื่นว่าง เจ้าหน้าที่เป็นคนเลือกหลังส่งคำร้อง
 * แต่เซิร์ฟเวอร์เคยไม่ตรวจ ผู้ยื่นยิง POST ใส่บัญชีตัวเองในช่องเจ้าหน้าที่ได้ ขั้นนั้นรอด่านตรวจ
 * แล้วพอเจ้าหน้าที่ส่งเวียน การส่งต่อข้ามช่องที่มีคนอยู่แล้ว ผู้ยื่นจึงได้ลงนามแทนเจ้าหน้าที่เอง
 */
@DisplayName("ผู้ยื่นเลือกผู้ลงนามได้เฉพาะช่องของตัวเอง")
class ApplicantEnvelopeSignersTest extends AbstractFlowTest {

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private AcademicRequest request;

    @BeforeEach
    void draft() {
        applicant = data.applicant();
        request = data.evaluation(applicant, RequestStatus.DRAFT);
    }

    private org.springframework.test.web.servlet.ResultActions create(int documentType,
            String[] slotKeys, String[] signerIds) throws Exception {
        return mvc.perform(post("/esign/envelope/create")
                .param("module", "ACADEMIC")
                .param("requestId", String.valueOf(request.getId()))
                .param("documentType", String.valueOf(documentType))
                .param("slotKeys", slotKeys)
                .param("signerUserIds", signerIds)
                .with(csrf()).with(user(applicant.getEmail()).roles("USER")));
    }

    private boolean anyEnvelope(int documentType) {
        return !envelopes.findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(
                SignatureModule.ACADEMIC, request.getId(), documentType).isEmpty();
    }

    @Test
    @DisplayName("ใส่บัญชีตัวเองในช่องนักทรัพยากรบุคคลไม่ได้")
    void cannotTakeTheStaffSlot() throws Exception {
        data.academicDocument(request, 2, "{\"chk_app_1\":\"✓\"}");
        String me = String.valueOf(applicant.getId());

        create(2, new String[] { "applicant", "hr" }, new String[] { me, me })
                .andExpect(flash().attribute("errorMsg", Matchers.containsString("เลือกผู้ลงนาม")));

        assertThat(anyEnvelope(2)).as("ต้องไม่มีซองที่ผู้ยื่นเป็นผู้ลงนามช่องเจ้าหน้าที่").isFalse();
    }

    @Test
    @DisplayName("ให้คนอื่นลงนามในช่องผู้ยื่นแทนไม่ได้")
    void cannotHandTheApplicantSlotToSomeoneElse() throws Exception {
        data.academicDocument(request, 2, "{\"chk_app_1\":\"✓\"}");

        create(2, new String[] { "applicant" }, new String[] { String.valueOf(data.otherApplicant().getId()) })
                .andExpect(flash().attribute("errorMsg", Matchers.containsString("เลือกผู้ลงนาม")));

        assertThat(anyEnvelope(2)).isFalse();
    }

    @Test
    @DisplayName("เปิดรอบเวียนของเจ้าหน้าที่ที่หมดเวลาแล้วขึ้นมาใหม่ไม่ได้")
    void cannotReviveAStaffRound() throws Exception {
        request = data.evaluation(applicant, RequestStatus.RECEIVED);
        UserDtls admin = data.admin();
        var created = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2,
                "แบบตรวจสอบเอกสาร", "{\"applicant_name\":\"ทดสอบ\"}",
                java.util.List.of(new com.ecom.academic.service.SignatureWorkflowService.SignerAssignment("hr",
                        admin.getId())),
                null, admin, com.ecom.academic.service.SignatureWorkflowService.ActorContext.none());
        assertThat(created.ok()).as(created.error()).isTrue();
        assertThat(signatureWorkflow.expire(created.request().getId()).ok()).isTrue();

        mvc.perform(post("/esign/envelope/" + created.request().getId() + "/extend-due")
                .param("dueAt", java.time.LocalDateTime.now().plusDays(7).withNano(0).toString())
                .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                .andExpect(flash().attribute("errorMsg", Matchers.containsString("ไม่มีสิทธิ์")));

        assertThat(envelopes.findById(created.request().getId()).orElseThrow().getStatus())
                .isEqualTo(com.ecom.academic.model.SignatureRequestStatus.EXPIRED);
    }

    @Test
    @DisplayName("ส่งเอกสารของเจ้าหน้าที่ไปลงนามไม่ได้")
    void cannotCirculateAnOfficeDocument() throws Exception {
        data.academicDocument(request, 7, "{\"total_score\":\"90\"}");
        String me = String.valueOf(applicant.getId());

        create(7, new String[] { "committee_1" }, new String[] { me })
                .andExpect(flash().attribute("errorMsg", Matchers.containsString("ไม่มีสิทธิ์")));

        assertThat(anyEnvelope(7)).isFalse();
    }
}
