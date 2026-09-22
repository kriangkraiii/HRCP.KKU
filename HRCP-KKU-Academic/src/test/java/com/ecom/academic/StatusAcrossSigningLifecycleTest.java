package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignedDocumentStatusAdvancer;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * สถานะคำร้องขยับตอนไหน เมื่อเทียบกับจังหวะของการลงนาม
 *
 * <p>เอกสารหนึ่งฉบับเดินผ่านสี่จังหวะ: เจ้าหน้าที่บันทึก → ส่งเวียนลงนาม → ลงนามครบ →
 * สารบรรณออกเลขที่ให้ทีหลัง เทสต์ชุดนี้ตรึงว่าสถานะขยับที่จังหวะที่สามจังหวะเดียว
 *
 * <p>เดิมขยับตั้งแต่จังหวะแรก ซึ่งเร็วกว่าความจริงหนึ่งขั้น — ตอนกดบันทึก หนังสือยังเป็น
 * แค่ร่างที่ยังไม่มีใครลงนามและยังไม่ได้ส่งออกไปไหน แต่ผู้ยื่นได้รับอีเมลว่าคำร้องเดินไปแล้ว
 *
 * <p>ที่ต้องเขียนไว้เพราะทั้งสี่จังหวะอยู่กันคนละไฟล์ — ประตูล็อกและจุดปิดซองอยู่ใน
 * {@link SignatureWorkflowService} การเลื่อนสถานะอยู่ใน {@link SignedDocumentStatusAdvancer}
 * ส่วนการออกเลขที่หนังสือแยกไปอีกเส้นทางหนึ่ง อ่านทีละไฟล์จะไม่เห็นว่าผลรวมเป็นอย่างไร
 */
@DisplayName("สถานะคำร้องกับจังหวะการลงนาม")
class StatusAcrossSigningLifecycleTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignedDocumentStatusAdvancer statusAdvancer;

    /** เอกสารที่ 4 ต้องมีชื่ออนุกรรมการครบสามคนถึงจะเลื่อนขั้นได้ */
    private static final String APPOINTMENT_ORDER = """
            {"committee_1_name":"ก","committee_2_name":"ข","committee_3_name":"ค"}""";

    private UserDtls applicant;
    private UserDtls officer;
    private UserDtls dean;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
        dean = data.user("dean@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /**
     * ปลายทางของ redirect
     *
     * <p>ไม่ใช้ {@code redirectedUrlPattern} เพราะ {@code ?} ใน AntPathMatcher แปลว่า
     * "อักขระใดก็ได้หนึ่งตัว" ไม่ใช่ query string — ลายที่เขียนตรง ๆ จึงไม่มีวันตรง
     */
    private String redirectOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    /** ซองที่ปิดไปแล้ว โดยไม่ต้องเดินเวียนลงนามจริง — ใช้กับเคสที่พูดเรื่องอื่น */
    private SignatureRequest envelopeFor(SignatureModule module, Long requestId, int docType,
            SignatureRequestStatus status) {
        String frozen = "{\"memo_no\":\"อว 660301.26.4/\"}";
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(module);
        envelope.setRequestId(requestId);
        envelope.setDocumentType(docType);
        envelope.setStatus(status);
        envelope.setFrozenJson(frozen);
        envelope.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        envelope.setVerificationCode("VC" + System.nanoTime());
        envelope.setCreatedAt(LocalDateTime.now());
        return envelopes.save(envelope);
    }

    @Nested
    @DisplayName("เฟส 1 — การประเมินการสอน")
    class Phase1 {

        private RequestStatus statusOf(Long id) {
            return academicService.findById(id).orElseThrow().getCurrentStatus();
        }

        @Test
        @DisplayName("บันทึกเอกสารเฉย ๆ: สถานะไม่ขยับ และผู้ยื่นไม่ได้รับอีเมล")
        void savingAloneDoesNotMoveTheStatus() throws Exception {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/5")
                    .with(as(officer)).with(csrf())
                    .param("memo_no", "อว 660301.26.4/ว.1"))
                    .andExpect(status().is3xxRedirection());

            assertThat(statusOf(request.getId()))
                    .as("หนังสือยังไม่มีใครลงนามและยังไม่ได้ส่งออกไปไหน")
                    .isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);
            assertThat(academicService.getStatusHistory(request.getId())).isEmpty();
            settle();
            assertThat(mail().to(applicant.getEmail())).isEmpty();
        }

        @Test
        @DisplayName("ระหว่างเวียนลงนาม: บันทึกไม่ได้ สถานะจึงไม่ขยับ")
        void whileCirculatingNothingMoves() throws Exception {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);
            data.academicDocument(request, 5, "{\"memo_no\":\"อว 660301.26.4/ว.1\"}");
            envelopeFor(SignatureModule.ACADEMIC, request.getId(), 5,
                    SignatureRequestStatus.IN_PROGRESS);

            assertThat(redirectOf(post("/admin/academic/request/" + request.getId() + "/document/5")
                    .with(as(officer)).with(csrf())
                    .param("memo_no", "อว 660301.26.4/ว.2")))
                    .endsWith("/document/5?error=document_locked_for_signing");

            assertThat(statusOf(request.getId())).isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);
            assertThat(academicService.getStatusHistory(request.getId())).isEmpty();
        }

        @Test
        @DisplayName("ลงนามครบ ซองปิด: สถานะเดิน และผู้ยื่นได้รับอีเมล")
        void signingCompletesTheStep() {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);
            data.academicDocument(request, 5, "{\"memo_no\":\"อว 660301.26.4/ว.1\"}");

            SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 5,
                    "{\"memo_no\":\"อว 660301.26.4/ว.1\"}", officer,
                    List.of(new SignerAssignment("dean", dean.getId())));
            signEveryStep(envelope, officer);

            assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                    .isEqualTo(SignatureRequestStatus.COMPLETED);
            assertThat(statusOf(request.getId())).isEqualTo(RequestStatus.MEETING_SCHEDULED);
            awaitCondition("อีเมลถึงผู้ยื่น", () -> !mail().to(applicant.getEmail()).isEmpty());
        }

        @Test
        @DisplayName("ตัดสินจากฉบับที่ลงนาม: ไม่มีชื่ออนุกรรมการครบสาม สถานะก็ไม่เดิน")
        void theSignedContentDecides() {
            AcademicRequest complete = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(complete, 4, APPOINTMENT_ORDER);
            signEveryStep(circulate(SignatureModule.ACADEMIC, complete.getId(), 4,
                    APPOINTMENT_ORDER, officer,
                    List.of(new SignerAssignment("dean", dean.getId()))), officer);

            AcademicRequest halfDone = data.evaluation(data.otherApplicant(), RequestStatus.RECEIVED);
            data.academicDocument(halfDone, 4, "{\"committee_1_name\":\"ก\"}");
            signEveryStep(circulate(SignatureModule.ACADEMIC, halfDone.getId(), 4,
                    "{\"committee_1_name\":\"ก\"}", officer,
                    List.of(new SignerAssignment("dean", dean.getId()))), officer);

            assertThat(statusOf(complete.getId())).isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);
            assertThat(statusOf(halfDone.getId()))
                    .as("คำสั่งแต่งตั้งที่ไม่ได้ตั้งใคร ลงนามแล้วก็ยังไม่ได้แต่งตั้ง")
                    .isEqualTo(RequestStatus.RECEIVED);
        }

        @Test
        @DisplayName("ลงนามครบแล้วลงเลขที่หนังสือ: เลขเข้า แต่สถานะไม่ขยับ")
        void numberingAfterSigningDoesNotMoveTheStatus() throws Exception {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
            data.academicDocument(request, 5, "{\"memo_no\":\"อว 660301.26.4/\"}");
            envelopeFor(SignatureModule.ACADEMIC, request.getId(), 5,
                    SignatureRequestStatus.COMPLETED);

            assertThat(redirectOf(post("/admin/academic/request/" + request.getId() + "/document/5")
                    .with(as(officer)).with(csrf())
                    .param("memo_no", "อว 660301.26.4/ว.17")
                    .param("date", "๒๑ กันยายน ๒๕๖๙")))
                    .endsWith("/document/5?saved=office");

            assertThat(academicService.getLatestDocumentData(request.getId(), 5))
                    .containsEntry("memo_no", "อว 660301.26.4/ว.17")
                    .containsEntry("date", "๒๑ กันยายน ๒๕๖๙");
            assertThat(statusOf(request.getId()))
                    .as("การออกเลขที่หนังสือเป็นงานสารบรรณ ไม่ใช่ขั้นตอนใหม่ของคำร้อง")
                    .isEqualTo(RequestStatus.MEETING_SCHEDULED);
            assertThat(academicService.getStatusHistory(request.getId())).isEmpty();
        }

        @Test
        @DisplayName("ซองปิดซ้ำรอบสอง: ไม่เลื่อนสถานะซ้ำ")
        void aSecondCompletionDoesNotAdvanceTwice() {
            AcademicRequest request =
                    data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);
            data.academicDocument(request, 5, "{\"memo_no\":\"อว 660301.26.4/ว.1\"}");

            SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 5,
                    "{\"memo_no\":\"อว 660301.26.4/ว.1\"}", officer,
                    List.of(new SignerAssignment("dean", dean.getId())));
            signEveryStep(envelope, officer);

            int afterFirstRound = academicService.getStatusHistory(request.getId()).size();
            statusAdvancer.advanceFor(envelope.getId());

            assertThat(academicService.getStatusHistory(request.getId()))
                    .as("คำร้องพ้นขั้นนี้ไปแล้ว การปิดซองอีกครั้งไม่ใช่ขั้นใหม่")
                    .hasSize(afterFirstRound);
            assertThat(statusOf(request.getId())).isEqualTo(RequestStatus.MEETING_SCHEDULED);
        }
    }

    @Nested
    @DisplayName("เฟส 2 — การขอกำหนดตำแหน่ง")
    class Phase2 {

        private PositionRequestStatus statusOf(Long id) {
            return positionService.findById(id).orElseThrow().getCurrentStatus();
        }

        @Test
        @DisplayName("บันทึกเอกสารเฉย ๆ: สถานะไม่ขยับ")
        void savingAloneDoesNotMoveTheStatus() throws Exception {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_VERIFICATION, null);

            mvc.perform(post("/admin/position/request/" + request.getId() + "/document/8")
                    .with(as(officer)).with(csrf())
                    .param("date", "๒๑ กันยายน ๒๕๖๙"))
                    .andExpect(status().is3xxRedirection());

            assertThat(statusOf(request.getId()))
                    .isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
        }

        @Test
        @DisplayName("ลงนามครบ ซองปิด: สถานะเดิน")
        void signingCompletesTheStep() {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_VERIFICATION, null);
            data.positionDocument(request, 8, "{\"date\":\"๑ กันยายน ๒๕๖๙\"}");

            signEveryStep(circulate(SignatureModule.POSITION, request.getId(), 8,
                    "{\"date\":\"๑ กันยายน ๒๕๖๙\"}", officer,
                    List.of(new SignerAssignment("hr", officer.getId()))), officer);

            assertThat(statusOf(request.getId()))
                    .isEqualTo(PositionRequestStatus.SCREENING_COMMITTEE);
        }

        @Test
        @DisplayName("ระหว่างเวียนลงนาม: บันทึกไม่ได้ สถานะจึงไม่ขยับ")
        void whileCirculatingNothingMoves() throws Exception {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_VERIFICATION, null);
            data.positionDocument(request, 8, "{\"date\":\"๑ กันยายน ๒๕๖๙\"}");
            envelopeFor(SignatureModule.POSITION, request.getId(), 8,
                    SignatureRequestStatus.IN_PROGRESS);

            assertThat(redirectOf(post("/admin/position/request/" + request.getId() + "/document/8")
                    .with(as(officer)).with(csrf())
                    .param("date", "๒๑ กันยายน ๒๕๖๙")))
                    .endsWith("/document/8?error=document_locked_for_signing");

            assertThat(statusOf(request.getId()))
                    .isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
        }

        @Test
        @DisplayName("ลงนามครบแล้วลงวันที่: ค่าเข้า แต่สถานะไม่ขยับ")
        void numberingAfterSigningDoesNotMoveTheStatus() throws Exception {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.SCREENING_COMMITTEE, null);
            data.positionDocument(request, 8, "{\"date\":\"๑ กันยายน ๒๕๖๙\"}");
            envelopeFor(SignatureModule.POSITION, request.getId(), 8,
                    SignatureRequestStatus.COMPLETED);

            assertThat(redirectOf(post("/admin/position/request/" + request.getId() + "/document/8")
                    .with(as(officer)).with(csrf())
                    .param("date", "๒๑ กันยายน ๒๕๖๙")))
                    .endsWith("/document/8?saved=office");

            assertThat(positionService.getLatestDocumentData(request.getId(), 8))
                    .containsEntry("date", "๒๑ กันยายน ๒๕๖๙");
            assertThat(statusOf(request.getId()))
                    .isEqualTo(PositionRequestStatus.SCREENING_COMMITTEE);
        }
    }
}
