package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เฟส 2 ต้องปิดเอกสารเมื่อออกเลขที่หนังสือ/วันที่ครบ แบบเดียวกับเฟส 1
 *
 * <p>ต่างจากเฟส 1 ตรงที่เอกสารบางฉบับมีผู้ลงนามหลายคน (เอกสารที่ 4: ผู้ยื่นแล้วต่อด้วยหัวหน้าสาขา)
 * ช่วงที่ผู้ยื่นเซ็นแล้วแต่ยังไม่ส่งต่อ ต้องยังไม่ปิด แม้เลขกับวันที่จะครบแล้ว
 */
@DisplayName("เฟส 2: ออกเลขที่หนังสือครบแล้วเอกสารจบ")
class PositionOfficeIssuedLockTest extends AbstractFlowTest {

    private static final String DATE = "29 กันยายน 2569";

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls officer;
    private PositionRequest request;

    @BeforeEach
    void cast() {
        officer = data.admin();
        request = data.positionRequest(data.applicant(), PositionRequestStatus.SCREENING_COMMITTEE, null);
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /** ซองที่ปิดแล้ว พร้อมช่องที่ลงนามไปแล้วตามรายชื่อ */
    private void signedBy(int docType, String... slotKeys) {
        String frozen = "{\"date\":\"\"}";
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(SignatureModule.POSITION);
        envelope.setRequestId(request.getId());
        envelope.setDocumentType(docType);
        envelope.setStatus(SignatureRequestStatus.COMPLETED);
        envelope.setFrozenJson(frozen);
        envelope.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        envelope.setVerificationCode("VC" + System.nanoTime());
        envelope.setCreatedAt(LocalDateTime.now());
        envelope = envelopes.save(envelope);
        int order = 1;
        for (String slot : slotKeys) {
            SignatureStep step = new SignatureStep();
            step.setSignatureRequest(envelope);
            step.setStepOrder(order++);
            step.setSlotKey(slot);
            step.setAnchorPlaceholder("{{sig_" + slot + "}}");
            step.setStatus(SignatureStepStatus.SIGNED);
            signatureSteps.save(step);
        }
    }

    private void save(int docType, String field, String value) throws Exception {
        mvc.perform(post("/admin/position/request/" + request.getId() + "/document/" + docType)
                .with(as(officer)).with(csrf())
                .param(field, value))
                .andExpect(status().is3xxRedirection());
    }

    private static final String ISSUED_BANNER = "— ออกเลขที่หนังสือและวันที่เอกสารแล้ว แก้ไขไม่ได้อีก";

    /** ตัวปุ่มเอง ไม่ใช่ชื่อ attribute ที่สคริปต์บนหน้าอ้างถึง */
    private static boolean hasSaveButton(String html) {
        return java.util.regex.Pattern.compile("<button[^>]*data-office-save").matcher(html).find();
    }

    private String form(int docType) throws Exception {
        return mvc.perform(get("/admin/position/request/" + request.getId() + "/document/" + docType)
                .with(as(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("ลงวันที่แล้ว บันทึกทับไม่ได้อีก ทั้งปุ่มบันทึกและร่างอัตโนมัติ")
    void issuedCannotBeOverwritten() throws Exception {
        positionService.saveDraft(request, 8, "{\"date\":\"\"}", "เอกสารที่ 8", "ADMIN");
        signedBy(8, "hr");

        save(8, "date", DATE);
        assertThat(positionService.isOfficeIssued(request.getId(), 8)).isTrue();

        mvc.perform(post("/admin/position/request/" + request.getId() + "/document/8")
                .with(as(officer)).with(csrf())
                .param("date", "1 ตุลาคม 2569"))
                .andExpect(redirectedUrl("/admin/position/request/" + request.getId()
                        + "/document/8?error=office_issued"));

        mvc.perform(post("/api/draft/position/" + request.getId() + "/8")
                .with(as(officer)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"1 ตุลาคม 2569\"}"))
                .andExpect(status().isConflict());

        assertThat(positionService.getLatestDocumentData(request.getId(), 8))
                .containsEntry("date", DATE);
    }

    @Test
    @DisplayName("หน้าฟอร์ม: ก่อนลงวันที่มีปุ่มบันทึก ลงแล้วปุ่มหายและขึ้นว่าแก้ไม่ได้")
    void formShowsTheLock() throws Exception {
        data.positionDocument(request, 8, "{\"date\":\"\"}");
        signedBy(8, "hr");

        assertThat(hasSaveButton(form(8))).isTrue();

        save(8, "date", DATE);

        String after = form(8);
        assertThat(hasSaveButton(after)).isFalse();
        assertThat(after).contains(ISSUED_BANNER);
    }

    @Test
    @DisplayName("ผู้ยื่นเซ็นแล้วแต่ยังไม่ส่งต่อหัวหน้าสาขา — ยังไม่ปิด แม้เลขกับวันที่ครบ")
    void notLockedWhileMoreSignersAreDue() throws Exception {
        data.positionDocument(request, 4, "{\"memo_no\":\"\",\"date\":\"\"}");
        signedBy(4, "applicant");

        save(4, "memo_no", "อว 660301.26.8/55");
        save(4, "date", DATE);

        String page = form(4);
        assertThat(hasSaveButton(page))
                .as("ช่วงที่ยังต้องส่งต่อ บันทึกผ่านร่างอัตโนมัติ ไม่ใช่ปุ่มออกเลข")
                .isFalse();
        assertThat(page).doesNotContain(ISSUED_BANNER);
        save(4, "date", "1 ตุลาคม 2569");
        assertThat(positionService.getLatestDocumentData(request.getId(), 4))
                .containsEntry("date", "1 ตุลาคม 2569");
    }
}
