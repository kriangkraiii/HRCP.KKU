package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ออกเลขที่หนังสือและวันที่ครบแล้ว เอกสารจบ — กล่องต้องเขียว และแก้ไขอะไรไม่ได้อีก
 *
 * <p>เคสที่เจอจริง: ผู้ยื่นกด "ส่งและลงนาม" เอกสารที่ 1 ซึ่งบันทึกผ่านร่างอัตโนมัติ แถวจึงติดธงร่าง
 * ไปตลอด เจ้าหน้าที่ออกเลขและลงวันที่ครบแล้วกล่องก็ยังส้ม เพราะการลงเลขไม่แตะธงร่างเลย
 */
@DisplayName("ออกเลขที่หนังสือครบแล้วเอกสารจบ")
class OfficeIssuedLockTest extends AbstractFlowTest {

    private static final String MEMO = "อว 660301.26.8/123";
    private static final String DATE = "29 กันยายน 2569";

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls officer;
    private AcademicRequest request;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
        request = data.evaluation(applicant, RequestStatus.RECEIVED);
        // ส่งลงนามผ่านร่างอัตโนมัติ — แถวยังเป็นร่าง
        academicService.saveDraft(request, 1,
                "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\",\"memo_no\":\"อว 660301.26.8/\",\"date\":\"\"}",
                "เอกสารที่ 1", null);
        signed(1);
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private void signed(int docType) {
        String frozen = "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}";
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(SignatureModule.ACADEMIC);
        envelope.setRequestId(request.getId());
        envelope.setDocumentType(docType);
        envelope.setStatus(SignatureRequestStatus.COMPLETED);
        envelope.setFrozenJson(frozen);
        envelope.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        envelope.setVerificationCode("VC" + System.nanoTime());
        envelope.setCreatedAt(LocalDateTime.now());
        envelope = envelopes.save(envelope);
        // ผู้ยื่นเซ็นช่องเดียวของเอกสารที่ 1 แล้ว — ไม่มีใครเหลือให้ส่งต่อ
        com.ecom.academic.model.SignatureStep step = new com.ecom.academic.model.SignatureStep();
        step.setSignatureRequest(envelope);
        step.setStepOrder(1);
        step.setSlotKey("applicant");
        step.setAnchorPlaceholder("{{sig_applicant}}");
        step.setStatus(com.ecom.academic.model.SignatureStepStatus.SIGNED);
        signatureSteps.save(step);
    }

    private void issue(String memo, String date) throws Exception {
        mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                .with(as(officer)).with(csrf())
                .param("action", "submit")
                .param("memo_no", memo)
                .param("date", date))
                .andExpect(status().is3xxRedirection());
    }

    private String card() throws Exception {
        return card(1);
    }

    private String card(int documentType) throws Exception {
        String html = mvc.perform(get("/admin/academic/request/" + request.getId()).with(as(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("class=\"doc-grid-item[ \"](.*?)(?=class=\"doc-grid-item[ \"]|$)",
                Pattern.DOTALL).matcher(html);
        while (m.find()) {
            if (m.group(1).contains("เอกสารที่ " + documentType + ":")) {
                return m.group(0);
            }
        }
        throw new AssertionError("ไม่พบกล่องของเอกสารที่ " + documentType);
    }

    private String form() throws Exception {
        return mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/1")
                .with(as(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("ลงนามแล้วแต่ยังไม่ออกเลข — ส้มพร้อมป้ายรอออกเลข ไม่ใช่ส้มเพราะเป็นร่าง")
    void signedButNotIssuedWaitsForTheNumber() throws Exception {
        assertThat(card()).contains("dgi-draft").contains("รอออกเลข");
    }

    @Test
    @DisplayName("ออกเลขและวันที่ครบ — เขียว แม้แถวเคยติดธงร่าง")
    void issuedIsGreen() throws Exception {
        issue(MEMO, DATE);

        assertThat(card()).contains("dgi-completed")
                .doesNotContain("dgi-draft")
                .doesNotContain("รอออกเลข");
    }

    @Test
    @DisplayName("มีแต่รหัสหน่วยงานไม่มีเลข — ยังไม่นับว่าออกเลข และยังแก้ได้")
    void aBarePrefixIsNotANumber() throws Exception {
        issue("อว 660301.26.8/", DATE);
        assertThat(academicService.isOfficeIssued(request.getId(), 1)).isFalse();
        assertThat(card()).contains("รอออกเลข");

        issue(MEMO, DATE);
        assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                .containsEntry("memo_no", MEMO);
    }

    @Test
    @DisplayName("ออกเลขแล้ว บันทึกทับไม่ได้อีก")
    void issuedCannotBeOverwritten() throws Exception {
        issue(MEMO, DATE);

        mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                .with(as(officer)).with(csrf())
                .param("action", "submit")
                .param("memo_no", "อว 660301.26.8/999")
                .param("date", "1 ตุลาคม 2569"))
                .andExpect(redirectedUrl("/admin/academic/request/" + request.getId()
                        + "/document/1?error=office_issued"));

        assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                .containsEntry("memo_no", MEMO)
                .containsEntry("date", DATE);
    }

    @Test
    @DisplayName("ออกเลขแล้ว บันทึกร่างอัตโนมัติก็ทับไม่ได้")
    void issuedRefusesAutoDraft() throws Exception {
        issue(MEMO, DATE);

        String body = mvc.perform(post("/api/draft/academic/" + request.getId() + "/1")
                .with(as(officer)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo_no\":\"อว 660301.26.8/999\"}"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("ออกเลขที่หนังสือ");
        assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                .containsEntry("memo_no", MEMO);
    }

    @Test
    @DisplayName("ออกเลขแล้ว ส่งกลับให้ผู้ยื่นแก้ไม่ได้ ลายเซ็นเดิมต้องอยู่")
    void issuedCannotBeSentBack() throws Exception {
        issue(MEMO, DATE);

        mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1/request-resign")
                .with(as(officer)).with(csrf())
                .param("reason", "ขอแก้ชื่อวิชา"))
                .andExpect(redirectedUrl("/admin/academic/request/" + request.getId()
                        + "/document/1?error=office_issued"));

        assertThat(signatureWorkflow.isSigningComplete(SignatureModule.ACADEMIC, request.getId(), 1))
                .isTrue();
    }

    @Test
    @DisplayName("หน้าฟอร์ม: ก่อนออกเลขมีปุ่มบันทึก ออกเลขแล้วปุ่มบันทึกและปุ่มส่งกลับหายไป")
    void formShowsTheLock() throws Exception {
        String before = form();
        assertThat(before).contains("id=\"btnSubmitDoc\"")
                .contains("data-issues-office=\"true\"")
                .contains("id=\"sendBackPanel\"");

        issue(MEMO, DATE);

        String after = form();
        assertThat(after).doesNotContain("id=\"btnSubmitDoc\"")
                .doesNotContain("id=\"sendBackPanel\"")
                .contains("ออกเลขที่หนังสือและวันที่เอกสารแล้ว แก้ไขไม่ได้อีก");
    }

    @Test
    @DisplayName("เอกสารที่ 2: ผู้ยื่นเซ็นแล้ว นักทรัพยากรบุคคลยังไม่เซ็น — ยังไม่เขียว ขึ้นป้ายรอลงนาม")
    void aDocumentWaitingForTheNextSignerIsNotGreen() throws Exception {
        String doc2 = "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}";
        data.academicDocument(request, 2, doc2);
        var created = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2,
                "เอกสารที่ 2", doc2,
                java.util.List.of(new com.ecom.academic.service.SignatureWorkflowService.SignerAssignment(
                        "applicant", applicant.getId())),
                null, applicant, com.ecom.academic.service.SignatureWorkflowService.ActorContext.none());
        assertThat(created.error()).isNull();
        var step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(created.request().getId()).get(0);
        var signed = signatureWorkflow.sign(step.getId(), applicant, data.signatureFor(applicant).getId(), true,
                com.ecom.academic.service.SignatureWorkflowService.ActorContext.none(), null, null);
        assertThat(signed.error()).isNull();
        assertThat(signatureWorkflow.awaitsMoreSigners(SignatureModule.ACADEMIC, request.getId(), 2)).isTrue();

        assertThat(card(2)).contains("dgi-draft")
                .doesNotContain("dgi-completed")
                .contains("รอลงนาม");
    }
}
