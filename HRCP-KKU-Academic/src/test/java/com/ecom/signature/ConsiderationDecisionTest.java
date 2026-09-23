package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.PositionDocumentRepository;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผู้ลงนามทุกคนที่ไม่ใช่ผู้ยื่นต้องบันทึกผลการพิจารณา และเขียนความเห็นได้
 *
 * <p>ก่อนหน้านี้การเวียนลงนามมีแค่ "เซ็น" กับ "ปฏิเสธ" คนที่เห็นด้วยไม่มีที่บันทึกข้อสังเกต และ
 * คนที่ไม่เห็นด้วยต้องใช้ปุ่มปฏิเสธซึ่งไม่ได้แยกว่า <em>พิจารณาแล้วไม่เห็นควร</em> ออกจาก
 * <em>ยังไม่พร้อมเซ็น</em> — สองเรื่องนี้ต่างกันในทางราชการ
 *
 * <p>ข้อที่เสียหายเงียบที่สุดถ้าพลาดคือ <strong>การตีกลับตามเจ้าของเอกสาร</strong>: ตีเอกสารของ
 * แอดมินกลับไปให้ผู้ยื่นก็เท่ากับไม่ได้ตีกลับ เพราะผู้ยื่นมองไม่เห็นเอกสารพวกนั้นด้วยซ้ำ
 */
@DisplayName("ผลการพิจารณาตอนเวียนลงนาม")
class ConsiderationDecisionTest extends AbstractFlowTest {

    /** เฟส 2 เอกสารที่ 4 — ของผู้ยื่น มีช่องลงนามของผู้บังคับบัญชาต่อท้าย */
    private static final int POSITION_DOC_APPLICANT_OWNED = 4;

    /** เฟส 1 เอกสารที่ 3 — คำสั่งแต่งตั้งคณะอนุกรรมการ ของแอดมินทั้งฉบับ */
    private static final int ACADEMIC_DOC_ADMIN_OWNED = 3;

    private static final String FROZEN = "{\"applicant_name\":\"สมชาย ใจดี\"}";

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignerNameResolver signerNameResolver;

    @Autowired
    private PositionDocumentRepository positionDocuments;

    @Autowired
    private AcademicDocumentRepository academicDocuments;

    private UserDtls applicant;
    private UserDtls head;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        head = data.user("head@example.invalid", "หัวหน้า", "สาขาวิชา", "ROLE_ADMIN");
    }

    // -----------------------------------------------------------------
    // ซองตัวอย่างสองแบบ: เอกสารของผู้ยื่น และเอกสารของแอดมิน
    // -----------------------------------------------------------------

    private PositionRequest applicantOwnedRequest;

    private SignatureStep headStepOnApplicantDocument() {
        applicantOwnedRequest = data.positionRequest(applicant,
                PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(applicantOwnedRequest, POSITION_DOC_APPLICANT_OWNED, FROZEN);
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.POSITION,
                applicantOwnedRequest.getId(), POSITION_DOC_APPLICANT_OWNED,
                "บันทึกรับรองผลงานทางวิชาการ", FROZEN,
                List.of(new SignerAssignment("applicant", applicant.getId()),
                        new SignerAssignment("head", head.getId())),
                null, applicant, ActorContext.none()).request();
        envelope = workflow.startCirculation(envelope.getId(), data.admin(), ActorContext.none())
                .request();

        // ผู้ยื่นเซ็นของตัวเองก่อน คิวจึงเลื่อนมาถึงหัวหน้าสาขา
        SignatureStep applicantStep = stepFor(envelope, "applicant");
        Result signed = workflow.sign(applicantStep.getId(), applicant,
                data.signatureFor(applicant).getId(), true, ActorContext.none(), null, null);
        assertThat(signed.ok()).as("ผู้ยื่นต้องเซ็นของตัวเองได้โดยไม่ต้องตอบผลการพิจารณา").isTrue();

        return stepFor(envelope, "head");
    }

    private AcademicRequest adminOwnedRequest;

    private SignatureStep headStepOnAdminDocument() {
        adminOwnedRequest = data.evaluation(applicant, RequestStatus.RECEIVED);
        data.academicDocument(adminOwnedRequest, ACADEMIC_DOC_ADMIN_OWNED, FROZEN);
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.ACADEMIC,
                adminOwnedRequest.getId(), ACADEMIC_DOC_ADMIN_OWNED,
                "คำสั่งแต่งตั้งคณะอนุกรรมการ", FROZEN,
                List.of(new SignerAssignment("head", head.getId())),
                null, data.admin(), ActorContext.none()).request();
        envelope = workflow.startCirculation(envelope.getId(), data.admin(), ActorContext.none())
                .request();
        return stepFor(envelope, "head");
    }

    private SignatureStep stepFor(SignatureRequest envelope, String slotKey) {
        return steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> slotKey.equals(s.getSlotKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ไม่พบช่องลงนาม " + slotKey));
    }

    private SignatureStep reload(SignatureStep step) {
        return steps.findById(step.getId()).orElseThrow();
    }

    // =================================================================
    @Nested
    @DisplayName("ใครถูกถาม")
    class WhoIsAsked {

        @Test
        @DisplayName("ช่องของผู้ลงนามที่ไม่ใช่ผู้ยื่น ถูกถามผลการพิจารณา")
        void reviewersAreAsked() {
            SignatureStep step = headStepOnApplicantDocument();

            assertThat(workflow.signerChoiceFor(step.getId()))
                    .isSameAs(SignatureAnchorRegistry.CONSIDERATION);
        }

        @Test
        @DisplayName("ช่องของผู้ยื่นไม่ถูกถาม — คนยื่นไม่ได้พิจารณาคำร้องตัวเอง")
        void theApplicantIsNotAsked() {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, POSITION_DOC_APPLICANT_OWNED, FROZEN);
            SignatureRequest envelope = workflow.createEnvelope(SignatureModule.POSITION,
                    request.getId(), POSITION_DOC_APPLICANT_OWNED, "เอกสาร", FROZEN,
                    List.of(new SignerAssignment("applicant", applicant.getId())),
                    null, applicant, ActorContext.none()).request();

            assertThat(workflow.signerChoiceFor(stepFor(envelope, "applicant").getId())).isNull();
        }

        @Test
        @DisplayName("เอกสารที่มีคำถามเฉพาะของตัวเอง ใช้คำถามนั้น ไม่ถูกถามซ้ำสองรอบ")
        void documentsWithTheirOwnQuestionKeepIt() {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            // ส่วนที่ ๒ ของแบบ ก.พ.ว. มข. 03 (เอกสารที่ 1) — หัวหน้าสาขาวิชามีคำถามของตัวเอง
            data.positionDocument(request, 1, FROZEN);
            SignatureRequest envelope = workflow.createEnvelope(SignatureModule.POSITION,
                    request.getId(), 1, "แบบ ก.พ.ว. มข. 03", FROZEN,
                    List.of(new SignerAssignment("head", head.getId())),
                    null, applicant, ActorContext.none()).request();

            var choice = workflow.signerChoiceFor(stepFor(envelope, "head").getId());

            assertThat(choice).isNotSameAs(SignatureAnchorRegistry.CONSIDERATION);
            assertThat(choice.options()).containsExactly("ครบถ้วน", "ไม่ครบถ้วน");
        }

        @Test
        @DisplayName("ไม่ตอบผลการพิจารณา เซ็นไม่ผ่าน")
        void answeringIsMandatory() {
            SignatureStep step = headStepOnApplicantDocument();

            Result result = workflow.sign(step.getId(), head, data.signatureFor(head).getId(),
                    true, ActorContext.none(), null, null);

            assertThat(result.ok()).isFalse();
            assertThat(result.error()).contains("ผลการพิจารณา");
            assertThat(reload(step).getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("เห็นควร")
    class Approved {

        @Test
        @DisplayName("บันทึกผลและความเห็นไว้ แล้วเอกสารเดินต่อ")
        void theDecisionAndCommentAreKept() {
            SignatureStep step = headStepOnApplicantDocument();

            Result result = workflow.sign(step.getId(), head, data.signatureFor(head).getId(),
                    true, ActorContext.none(), null, SignatureAnchorRegistry.APPROVED,
                    "  ตรวจสอบเอกสารครบถ้วนแล้ว  ");

            assertThat(result.ok()).isTrue();
            SignatureStep after = reload(step);
            assertThat(after.getStatus()).isEqualTo(SignatureStepStatus.SIGNED);
            assertThat(after.getSignerChoiceValue()).isEqualTo(SignatureAnchorRegistry.APPROVED);
            assertThat(after.getSignerComment())
                    .as("ช่องว่างหัวท้ายต้องถูกตัด")
                    .isEqualTo("ตรวจสอบเอกสารครบถ้วนแล้ว");
            assertThat(result.request().getStatus())
                    .isNotEqualTo(SignatureRequestStatus.DECLINED);
        }

        @Test
        @DisplayName("ไม่เขียนความเห็นก็เซ็นผ่านได้ และไม่เก็บเป็นช่องว่าง")
        void theCommentIsOptionalWhenApproving() {
            SignatureStep step = headStepOnApplicantDocument();

            Result result = workflow.sign(step.getId(), head, data.signatureFor(head).getId(),
                    true, ActorContext.none(), null, SignatureAnchorRegistry.APPROVED, "   ");

            assertThat(result.ok()).isTrue();
            assertThat(reload(step).getSignerComment()).isNull();
        }

        @Test
        @DisplayName("เอกสารของผู้ยื่นไม่ถูกเปิดให้แก้ เพราะไม่มีอะไรต้องแก้")
        void nothingIsSentBack() {
            SignatureStep step = headStepOnApplicantDocument();

            workflow.sign(step.getId(), head, data.signatureFor(head).getId(), true,
                    ActorContext.none(), null, SignatureAnchorRegistry.APPROVED, null);

            assertThat(revisionOpenedOnPositionDocument()).isFalse();
        }
    }

    // =================================================================
    @Nested
    @DisplayName("ไม่เห็นควร")
    class NotApproved {

        @Test
        @DisplayName("หยุดการเวียนทั้งสาย และบันทึกว่าคนนี้ตัดสินว่าอะไร")
        void circulationStops() {
            SignatureStep step = headStepOnApplicantDocument();

            Result result = workflow.notApproved(step.getId(), head,
                    "ผลงานยังไม่เข้าเกณฑ์ กรุณาทบทวน", ActorContext.none());

            assertThat(result.ok()).isTrue();
            SignatureStep after = reload(step);
            assertThat(after.getStatus()).isEqualTo(SignatureStepStatus.DECLINED);
            assertThat(after.getSignerChoiceValue())
                    .as("คอลัมน์เดียวต้องตอบได้ว่าตัดสินว่าอะไร ไม่ต้องไปดูสถานะของ step")
                    .isEqualTo(SignatureAnchorRegistry.NOT_APPROVED);
            assertThat(after.getSignerComment()).isEqualTo("ผลงานยังไม่เข้าเกณฑ์ กรุณาทบทวน");
            assertThat(after.getDeclineReason())
                    .as("โค้ดเดิมที่อ่าน declineReason ต้องยังทำงานได้")
                    .isEqualTo("ผลงานยังไม่เข้าเกณฑ์ กรุณาทบทวน");
            assertThat(result.request().getStatus())
                    .isEqualTo(SignatureRequestStatus.DECLINED);
        }

        @Test
        @DisplayName("ไม่เขียนความเห็น ไม่ให้ผ่าน")
        void theCommentIsRequired() {
            SignatureStep step = headStepOnApplicantDocument();

            Result result = workflow.notApproved(step.getId(), head, "   ", ActorContext.none());

            assertThat(result.ok()).isFalse();
            assertThat(reload(step).getStatus()).isEqualTo(SignatureStepStatus.ACTIVE);
        }

        @Test
        @DisplayName("เอกสารของผู้ยื่นถูกเปิดให้ผู้ยื่นกลับมาแก้ได้จริง")
        void anApplicantDocumentIsReopenedForTheApplicant() {
            SignatureStep step = headStepOnApplicantDocument();

            workflow.notApproved(step.getId(), head, "ขาดเอกสารแนบ", ActorContext.none());

            PositionDocument doc = positionDocuments
                    .findByRequestIdAndDocType(applicantOwnedRequest.getId(),
                            POSITION_DOC_APPLICANT_OWNED)
                    .get(0);
            assertThat(doc.getRevisionRequestedAt())
                    .as("ถ้าไม่เปิดประตูนี้ ตีกลับไปแล้วผู้ยื่นก็แก้ไม่ได้อยู่ดี")
                    .isNotNull();
            assertThat(doc.getRevisionNote()).isEqualTo("ขาดเอกสารแนบ");
        }

        @Test
        @DisplayName("เอกสารของแอดมินไม่ถูกเปิดให้ผู้ยื่น — เขามองไม่เห็นเอกสารนั้นด้วยซ้ำ")
        void anAdminDocumentIsNotHandedToTheApplicant() {
            SignatureStep step = headStepOnAdminDocument();

            Result result = workflow.notApproved(step.getId(), head, "แก้ชื่อกรรมการ",
                    ActorContext.none());

            assertThat(result.ok()).isTrue();
            AcademicDocument doc = academicDocuments
                    .findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(
                            adminOwnedRequest.getId(), ACADEMIC_DOC_ADMIN_OWNED)
                    .get(0);
            assertThat(doc.getRevisionRequestedAt()).isNull();
        }
    }

    // =================================================================
    @Nested
    @DisplayName("ความเห็นที่พิมพ์ลงเอกสาร (คำสั่งแต่งตั้งคณะอนุกรรมการ)")
    class MarksOnTheAppointmentOrder {

        @Test
        @DisplayName("\"เพื่อโปรดพิจารณา\" ติ๊กเองตามผู้ลงนามที่อยู่ในซอง ไม่ต้องให้แอดมินติ๊กแทน")
        void theForwardingTickComesFromTheCirculationItself() {
            adminOwnedRequest = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(adminOwnedRequest, ACADEMIC_DOC_ADMIN_OWNED, FROZEN);
            SignatureRequest envelope = workflow.createEnvelope(SignatureModule.ACADEMIC,
                    adminOwnedRequest.getId(), ACADEMIC_DOC_ADMIN_OWNED, "คำสั่งแต่งตั้ง", FROZEN,
                    List.of(new SignerAssignment("head", head.getId())),
                    null, data.admin(), ActorContext.none()).request();

            java.util.Map<String, String> marks = signerNameResolver.choicesForEnvelope(
                    envelopes.findById(envelope.getId()).orElseThrow());

            assertThat(marks)
                    .as("ยังไม่มีใครเซ็น แต่เอกสารถูกเวียนถึงคนเหล่านี้แล้ว ข้อความจึงเป็นจริงแล้ว")
                    .containsEntry("chk_cs_head", "✓")
                    .containsEntry("chk_dean_sign1", "✓");
        }

        @Test
        @DisplayName("ความเห็นของแต่ละช่องมาจากคนที่เซ็นช่องนั้น และพิมพ์เป็นเครื่องหมายถูก")
        void eachOpinionComesFromItsOwnSigner() {
            SignatureStep step = headStepOnAdminDocument();

            workflow.sign(step.getId(), head, data.signatureFor(head).getId(), true,
                    ActorContext.none(), null, SignatureAnchorRegistry.APPROVED, null);

            java.util.Map<String, String> marks = signerNameResolver.choicesForEnvelope(
                    envelopes.findById(step.getSignatureRequest().getId()).orElseThrow());

            assertThat(marks)
                    .as("วงเล็บในแบบฟอร์มรับได้แค่เครื่องหมายถูก การเขียนคำว่า 'เห็นควร' ลงไปจะอ่านไม่รู้เรื่อง")
                    .containsEntry("chk_cs_head2", "✓");
        }

        @Test
        @DisplayName("ตอบว่าไม่เห็นควรแล้ว ช่องติ๊กต้องว่าง ไม่ใช่ติ๊กให้")
        void aNegativeAnswerLeavesTheBoxEmpty() {
            SignatureStep step = headStepOnAdminDocument();

            workflow.notApproved(step.getId(), head, "รายชื่อไม่ครบ", ActorContext.none());

            java.util.Map<String, String> marks = signerNameResolver.choicesForEnvelope(
                    envelopes.findById(step.getSignatureRequest().getId()).orElseThrow());

            assertThat(marks).containsEntry("chk_cs_head2", "");
        }
    }

    private boolean revisionOpenedOnPositionDocument() {
        return positionDocuments
                .findByRequestIdAndDocType(applicantOwnedRequest.getId(),
                        POSITION_DOC_APPLICANT_OWNED)
                .stream()
                .anyMatch(d -> d.getRevisionRequestedAt() != null);
    }
}
