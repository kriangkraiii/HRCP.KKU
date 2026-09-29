package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * กรอบสีของกล่องเอกสารต้องบอกความจริงสามระดับ
 *
 * <p>เดิมนับว่า "มีแถวของเอกสารนี้" = เสร็จแล้ว กรอบจึงเขียวทันทีที่บันทึกร่างอัตโนมัติ
 * ทั้งที่เจ้าหน้าที่ยังไม่ได้กดบันทึกเอกสารเลย แยกไม่ออกว่าอะไรทำเสร็จจริง
 *
 * <p>ข้อมูลแยกร่างออกได้อยู่แล้วผ่าน {@code isDraft} — {@code saveDraft} ตั้ง true,
 * {@code saveDocument} ตั้ง false หน้าเว็บแค่ไม่ได้ใช้
 *
 * <p>เขียวแปลว่าจบจริง: บันทึกแล้ว ลงนามครบทุกช่อง และออกเลขที่หนังสือ/วันที่ครบ (ถ้ามีช่องเหล่านี้)
 * บันทึกแล้วแต่ยังไม่ได้ลงนามยังไม่จบ — ดู {@link com.ecom.academic.service.DocumentProgress}
 */
@DisplayName("กรอบสีของกล่องเอกสาร")
class DocumentCardStateTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /** ลงนามครบทุกช่องของเอกสารฉบับนี้แล้ว โดยไม่ต้องเดินเวียนลงนามจริง */
    private void fullySigned(SignatureModule module, Long requestId, int docType) {
        String frozen = "{}";
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(module);
        envelope.setRequestId(requestId);
        envelope.setDocumentType(docType);
        envelope.setStatus(SignatureRequestStatus.COMPLETED);
        envelope.setFrozenJson(frozen);
        envelope.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        envelope.setVerificationCode("VC" + System.nanoTime());
        envelope.setCreatedAt(java.time.LocalDateTime.now());
        envelope = envelopes.save(envelope);
        int order = 1;
        for (SignatureAnchorRegistry.SignatureSlot slot : SignatureAnchorRegistry.slotsOf(module, docType)) {
            SignatureStep step = new SignatureStep();
            step.setSignatureRequest(envelope);
            step.setStepOrder(order++);
            step.setSlotKey(slot.slotKey());
            step.setAnchorPlaceholder("{{sig_" + slot.slotKey() + "}}");
            step.setStatus(SignatureStepStatus.SIGNED);
            signatureSteps.save(step);
        }
    }

    /** กล่องของเอกสารฉบับหนึ่ง แบ่งตาม doc-grid-item ไม่ใช่ตามข้อความ */
    private static String cardFor(String html, int documentType) {
        Matcher m = Pattern.compile("class=\"doc-grid-item[ \"](.*?)(?=class=\"doc-grid-item[ \"]|$)",
                Pattern.DOTALL).matcher(html);
        while (m.find()) {
            if (m.group(1).contains("เอกสารที่ " + documentType + ":")) {
                return m.group(0);
            }
        }
        throw new AssertionError("ไม่พบกล่องของเอกสารที่ " + documentType);
    }

    @Nested
    @DisplayName("เฟส 1")
    class Phase1 {

        private String page(AcademicRequest request) throws Exception {
            return mvc.perform(get("/admin/academic/request/" + request.getId()).with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }

        @Test
        @DisplayName("ยังไม่เคยทำ — ไม่มีสี")
        void untouchedHasNoColour() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            String card = cardFor(page(request), 3);
            assertThat(card).doesNotContain("dgi-completed").doesNotContain("dgi-draft");
        }

        @Test
        @DisplayName("บันทึกร่างแล้วแต่ยังไม่ได้บันทึกเอกสาร — เหลือง")
        void draftIsYellow() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            academicService.saveDraft(request, 3, "{\"dean_name\":\"ร่างไว้ก่อน\"}", "เอกสารที่ 3", null);

            String card = cardFor(page(request), 3);
            assertThat(card).contains("dgi-draft").doesNotContain("dgi-completed");
        }

        @Test
        @DisplayName("บันทึกแล้วแต่ยังไม่ได้ลงนาม — ส้ม ป้ายรอลงนาม ยังไม่จบ")
        void savedButUnsignedIsNotGreen() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 3, "{\"dean_name\":\"บันทึกจริง\"}");

            String card = cardFor(page(request), 3);
            assertThat(card).contains("dgi-draft").doesNotContain("dgi-completed").contains("รอลงนาม");
        }

        @Test
        @DisplayName("บันทึกและลงนามครบแล้ว — เขียว")
        void savedAndSignedIsGreen() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 3, "{\"dean_name\":\"บันทึกจริง\"}");
            fullySigned(SignatureModule.ACADEMIC, request.getId(), 3);

            String card = cardFor(page(request), 3);
            assertThat(card).contains("dgi-completed").doesNotContain("dgi-draft").contains("เสร็จแล้ว");
        }

        @Test
        @DisplayName("แถวไฟล์ใต้กล่อง — เส้นข้างเดินตามสถานะ ไม่เขียวเสมอ")
        void fileRowsFollowTheDocument() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 3, "{\"dean_name\":\"บันทึกจริง\"}");
            assertThat(cardFor(page(request), 3)).contains("dgs-pending").doesNotContain("dgs-done");

            fullySigned(SignatureModule.ACADEMIC, request.getId(), 3);
            assertThat(cardFor(page(request), 3)).contains("dgs-done").doesNotContain("dgs-pending");
        }

        @Test
        @DisplayName("ลงนามแล้วแต่ยังไม่ได้ออกเลขที่หนังสือ — เหลือง ไม่ใช่เขียว")
        void savedWithoutAMemoNumberIsNotGreen() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 5, "{\"memo_no\":\"\",\"date\":\"\"}");
            fullySigned(SignatureModule.ACADEMIC, request.getId(), 5);

            String card = cardFor(page(request), 5);
            assertThat(card)
                    .as("งานสารบรรณยังไม่จบ กล่องจึงยังไม่เขียว")
                    .contains("dgi-draft")
                    .doesNotContain("dgi-completed")
                    .contains("รอออกเลข");
        }

        @Test
        @DisplayName("ออกเลขและลงวันที่ครบแล้ว — เขียว")
        void savedWithOfficeFieldsIsGreen() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 5,
                    "{\"memo_no\":\"อว 660301.26.4/ว.17\",\"date\":\"๒๑ กันยายน ๒๕๖๙\"}");
            fullySigned(SignatureModule.ACADEMIC, request.getId(), 5);

            String card = cardFor(page(request), 5);
            assertThat(card).contains("dgi-completed")
                    .doesNotContain("dgi-draft")
                    .doesNotContain("รอออกเลข");
        }

        @Test
        @DisplayName("เอกสารที่ไม่มีช่องสารบรรณ — ลงนามครบก็เขียว ไม่ต้องรอออกเลข")
        void documentsWithoutOfficeFieldsAreUnaffected() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 3, "{\"dean_name\":\"บันทึกจริง\"}");
            fullySigned(SignatureModule.ACADEMIC, request.getId(), 3);

            String card = cardFor(page(request), 3);
            assertThat(card).contains("dgi-completed").doesNotContain("รอออกเลข");
        }

        @Test
        @DisplayName("เอกสารที่ 5 มีทั้งแถวร่างและสามสำเนา — ต้องเป็นเขียว")
        void aDraftAlongsideRealCopiesIsStillGreen() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            academicService.saveDraft(request, 5, "{\"memo_no\":\"ร่าง\"}", "เอกสารที่ 5", null);
            for (int copy = 1; copy <= 3; copy++) {
                data.academicDocument(request, 5,
                        "{\"memo_no\":\"อว 660301.26.4/ว.1\",\"date\":\"๑ กันยายน ๒๕๖๙\"}", copy);
            }
            fullySigned(SignatureModule.ACADEMIC, request.getId(), 5);

            String card = cardFor(page(request), 5);
            assertThat(card).contains("dgi-completed").doesNotContain("dgi-draft");
        }
    }

    @Nested
    @DisplayName("เฟส 2")
    class Phase2 {

        private String page(PositionRequest request) throws Exception {
            return mvc.perform(get("/admin/position/request/" + request.getId()).with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }

        @Test
        @DisplayName("บันทึกร่างแล้ว — เหลือง ไม่ใช่ไม่มีสี")
        void draftIsYellow() throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            positionService.saveDraft(request, 7, "{\"applicant_name\":\"ร่างไว้ก่อน\"}",
                    "เอกสารที่ 7", "ADMIN");

            String card = cardFor(page(request), 7);
            assertThat(card).contains("dgi-draft").doesNotContain("dgi-completed");
        }

        @Test
        @DisplayName("บันทึกแล้วแต่ยังไม่ได้ลงนาม — ส้ม ป้ายรอลงนาม")
        void savedButUnsignedIsNotGreen() throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 7, "{\"applicant_name\":\"บันทึกจริง\"}");

            String card = cardFor(page(request), 7);
            assertThat(card).contains("dgi-draft").doesNotContain("dgi-completed").contains("รอลงนาม");
        }

        @Test
        @DisplayName("บันทึกและลงนามครบแล้ว — เขียว")
        void savedAndSignedIsGreen() throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 7, "{\"applicant_name\":\"บันทึกจริง\"}");
            fullySigned(SignatureModule.POSITION, request.getId(), 7);

            String card = cardFor(page(request), 7);
            assertThat(card).contains("dgi-completed").doesNotContain("dgi-draft").contains("เสร็จแล้ว");
        }

        @Test
        @DisplayName("ลงนามแล้วแต่ยังไม่ได้ลงวันที่ — เหลือง และป้ายต้องเดินตามกรอบ")
        void savedWithoutTheDateIsNotGreen() throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 8, "{\"date\":\"\"}");
            fullySigned(SignatureModule.POSITION, request.getId(), 8);

            String card = cardFor(page(request), 8);
            assertThat(card).contains("dgi-draft")
                    .doesNotContain("dgi-completed")
                    .contains("รอออกเลข")
                    .as("กรอบเหลืองแต่ป้ายเขียวคือบอกคนละเรื่องกันในกล่องเดียว")
                    .doesNotContain("เสร็จแล้ว");
        }

        @Test
        @DisplayName("ลงวันที่แล้ว — เขียว")
        void savedWithTheDateIsGreen() throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 8, "{\"date\":\"๒๑ กันยายน ๒๕๖๙\"}");
            fullySigned(SignatureModule.POSITION, request.getId(), 8);

            String card = cardFor(page(request), 8);
            assertThat(card).contains("dgi-completed")
                    .doesNotContain("dgi-draft")
                    .doesNotContain("รอออกเลข")
                    .contains("เสร็จแล้ว");
        }
    }
}
