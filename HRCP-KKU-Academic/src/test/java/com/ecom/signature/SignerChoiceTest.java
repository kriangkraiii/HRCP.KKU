package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentFieldOwnership;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผู้ลงนามตอบคำถามของแบบฟอร์มได้ โดยลายเซ็นในซองไม่เป็นโมฆะ
 *
 * <p>แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา (ส่วนที่ ๒ ของแบบ ก.พ.ว. มข. ๐๓ — เฟส 2 เอกสารที่ 1)
 * ไม่ได้ขอแค่ลายเซ็น แต่ขอผลการตรวจสอบว่า "ครบถ้วน" หรือ "ไม่ครบถ้วน" และความเห็นคณบดีว่า
 * "เข้าข่าย" หรือ "ไม่เข้าข่าย" ซึ่งเป็นคำตัดสินของผู้ลงนาม ไม่ใช่ข้อมูลที่ผู้ยื่นกรอก
 *
 * <p><strong>ข้อที่สำคัญที่สุดคือเรื่องแฮช</strong> — ซองลายเซ็น freeze เนื้อหาเอกสารไว้ตอนเริ่ม
 * เวียน ถ้าคำตอบถูกเขียนลงเนื้อหาเอกสาร แฮชจะไม่ตรงและ {@code sign()} จะประกาศให้ลายเซ็นทั้งซอง
 * เป็นโมฆะ คำตอบจึงต้องเก็บไว้ที่ขั้นตอนลงนามและเติมลงเอกสารตอน render เท่านั้น
 */
@DisplayName("ตัวเลือกของผู้ลงนาม")
class SignerChoiceTest extends AbstractFlowTest {

    private static final int DOC_SUPERVISOR_REVIEW = 1;
    private static final String FROZEN = "{\"applicant_name\":\"สมชาย ใจดี\",\"major\":\"วิทยาการคอมพิวเตอร์\"}";

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignerNameResolver signerNameResolver;

    private UserDtls applicant;
    private UserDtls head;
    private UserDtls dean;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        head = data.user("head@example.invalid", "หัวหน้า", "สาขาวิชา", "ROLE_ADMIN");
        dean = data.user("dean@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
    }

    /**
     * ซองที่พร้อมให้หัวหน้าสาขาวิชาลงนามจริง
     *
     * <p>ผู้ยื่นเซ็นส่วนที่ ๑ ก่อน แล้วต้องปล่อยเวียนอีกที เพราะขั้นตอนที่ไม่ใช่ของผู้ยื่นจะรอ
     * เจ้าหน้าที่ตรวจแล้วปล่อยเสมอ (ดูประตูใน {@code SignatureWorkflowService.activateNextStep})
     */
    private SignatureRequest supervisorReviewSentForSignature() {
        PositionRequest request = data.positionRequest(applicant,
                PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, DOC_SUPERVISOR_REVIEW, FROZEN);
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.POSITION, request.getId(),
                DOC_SUPERVISOR_REVIEW, "แบบ ก.พ.ว. มข. 03", FROZEN,
                List.of(new SignerAssignment("applicant", applicant.getId()),
                        new SignerAssignment("head", head.getId()),
                        new SignerAssignment("dean", dean.getId())),
                null, applicant, ActorContext.none()).request();
        Result applicantSigned = signAs(applicant, stepFor(envelope, "applicant"), null);
        assertThat(applicantSigned.ok()).as("ผู้ยื่นลงนามส่วนที่ ๑").isTrue();
        return workflow.startCirculation(envelope.getId(), data.admin(), ActorContext.none()).request();
    }

    private SignatureStep stepFor(SignatureRequest envelope, String slotKey) {
        return steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> slotKey.equals(s.getSlotKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ไม่พบช่องลงนาม " + slotKey));
    }

    private Result signAs(UserDtls signer, SignatureStep step, String choice) {
        UserSignature signature = data.signatureFor(signer);
        return workflow.sign(step.getId(), signer, signature.getId(), true,
                ActorContext.none(), null, choice);
    }

    @Nested
    @DisplayName("ลายเซ็นต้องไม่เป็นโมฆะ")
    class TheEnvelopeSurvives {

        @Test
        @DisplayName("คนถัดไปยังลงนามต่อได้ แปลว่าเนื้อหายังตรงกับแฮชที่ freeze ไว้")
        void theNextSignerCanStillSign() {
            SignatureRequest envelope = supervisorReviewSentForSignature();

            Result first = signAs(head, stepFor(envelope, "head"), "ครบถ้วน");
            assertThat(first.ok()).as("หัวหน้าสาขาวิชาลงนาม").isTrue();

            Result second = signAs(dean, stepFor(envelope, "dean"), "เข้าข่าย");

            assertThat(second.ok())
                    .as("ถ้าคำตอบถูกเขียนลงเนื้อหาเอกสาร แฮชจะไม่ตรงและขั้นนี้จะถูกปฏิเสธ")
                    .isTrue();
        }

        @Test
        @DisplayName("ซองไม่ถูกทำให้เป็นโมฆะหลังบันทึกคำตอบ")
        void theEnvelopeIsNotVoided() {
            SignatureRequest envelope = supervisorReviewSentForSignature();

            signAs(head, stepFor(envelope, "head"), "ไม่ครบถ้วน");

            assertThat(envelopes.findById(envelope.getId()))
                    .get()
                    .extracting(SignatureRequest::getStatus)
                    .isNotEqualTo(SignatureRequestStatus.VOIDED);
        }

        @Test
        @DisplayName("เนื้อหาที่ freeze ไว้ไม่ถูกแตะเลย")
        void theFrozenContentIsLeftAlone() {
            SignatureRequest envelope = supervisorReviewSentForSignature();

            signAs(head, stepFor(envelope, "head"), "ครบถ้วน");

            assertThat(envelopes.findById(envelope.getId()))
                    .get()
                    .extracting(SignatureRequest::getFrozenJson)
                    .isEqualTo(FROZEN);
        }
    }

    @Nested
    @DisplayName("คำตอบปรากฏในเอกสาร")
    class TheAnswerReachesTheDocument {

        @Test
        @DisplayName("เติมตอน render ตามช่องที่ทะเบียนกำหนดไว้")
        void isFilledInAtRenderTime() {
            SignatureRequest envelope = supervisorReviewSentForSignature();
            signAs(head, stepFor(envelope, "head"), "ไม่ครบถ้วน");

            String rendered = signerNameResolver.fillInto(
                    envelopes.findById(envelope.getId()).orElseThrow(), FROZEN);

            assertThat(rendered).contains("qualification_status").contains("ไม่ครบถ้วน");
        }

        @Test
        @DisplayName("ช่องที่ยังไม่มีใครลงนามยังว่าง ไม่ใช่เดาค่าให้")
        void staysEmptyUntilSomeoneActuallyAnswers() {
            SignatureRequest envelope = supervisorReviewSentForSignature();
            signAs(head, stepFor(envelope, "head"), "ครบถ้วน");

            Map<String, String> answers = signerNameResolver.choicesForEnvelope(
                    envelopes.findById(envelope.getId()).orElseThrow());

            assertThat(answers)
                    .as("มีแค่คำตอบและวันที่ของหัวหน้าสาขา — ของคณบดียังไม่มีเพราะยังไม่ได้เซ็น")
                    .containsOnlyKeys("qualification_status", "head_sign_date");
        }

        @Test
        @DisplayName("วันที่ใต้เส้นลงนามคือวันที่เซ็นจริง")
        void theSigningDateIsTheDayItWasSigned() {
            SignatureRequest envelope = supervisorReviewSentForSignature();
            signAs(head, stepFor(envelope, "head"), "ครบถ้วน");

            SignatureStep signed = steps.findById(stepFor(envelope, "head").getId()).orElseThrow();
            Map<String, String> answers = signerNameResolver.choicesForEnvelope(
                    envelopes.findById(envelope.getId()).orElseThrow());

            assertThat(answers.get("head_sign_date"))
                    .isEqualTo(AcademicRequestService.formatThaiDate(signed.getSignedAt()));
        }
    }

    @Nested
    @DisplayName("ตรวจคำตอบก่อนรับ")
    class TheAnswerIsValidated {

        @Test
        @DisplayName("ไม่เลือกคำตอบ ลงนามไม่ได้")
        void anUnansweredQuestionRefusesTheSignature() {
            SignatureRequest envelope = supervisorReviewSentForSignature();
            SignatureStep step = stepFor(envelope, "head");

            Result result = signAs(head, step, null);

            assertThat(result.ok()).isFalse();
            assertThat(result.error()).contains("ผลการตรวจสอบคุณสมบัติ");
            assertThat(steps.findById(step.getId()))
                    .get()
                    .extracting(SignatureStep::getStatus)
                    .isEqualTo(SignatureStepStatus.ACTIVE);
        }

        @Test
        @DisplayName("ส่งค่านอกตัวเลือกที่แบบฟอร์มมีให้ ลงนามไม่ได้")
        void anAnswerTheFormNeverOfferedIsRefused() {
            SignatureRequest envelope = supervisorReviewSentForSignature();
            SignatureStep step = stepFor(envelope, "head");

            Result result = signAs(head, step, "ผ่านแบบมีเงื่อนไข");

            assertThat(result.ok()).isFalse();
            assertThat(steps.findById(step.getId()))
                    .get()
                    .extracting(SignatureStep::getSignerChoiceValue)
                    .isNull();
        }

        @Test
        @DisplayName("คณบดีตอบตามถ้อยคำของแบบฟอร์ม: เข้าข่าย/ไม่เข้าข่าย ไม่ใช่ ครบถ้วน")
        void theDeanAnswersInTheFormsOwnWords() {
            SignatureRequest envelope = supervisorReviewSentForSignature();
            signAs(head, stepFor(envelope, "head"), "ครบถ้วน");

            Result result = signAs(dean, stepFor(envelope, "dean"), "ครบถ้วน");

            assertThat(result.ok()).isFalse();
        }

        @Test
        @DisplayName("ช่องลงนามที่ไม่มีคำถาม ส่งคำตอบมาก็ลงนามได้ตามปกติ")
        void slotsWithoutAQuestionAreUnaffected() {
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 2, FROZEN);
            SignatureRequest envelope = workflow.createEnvelope(SignatureModule.POSITION,
                    request.getId(), 2, "หนังสือแจ้งความประสงค์", FROZEN,
                    List.of(new SignerAssignment("applicant", applicant.getId())),
                    null, applicant, ActorContext.none()).request();

            Result result = signAs(applicant, stepFor(envelope, "applicant"), "ค่าที่ไม่เกี่ยวข้อง");

            assertThat(result.ok()).isTrue();
            assertThat(steps.findById(stepFor(envelope, "applicant").getId()))
                    .get()
                    .extracting(SignatureStep::getSignerChoiceValue)
                    .as("ช่องที่ไม่มีคำถามต้องไม่เก็บค่าที่ส่งมามั่ว")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("เลือกผลที่ต้องสนใจ")
    class AnAdverseFinding {

        @Test
        @DisplayName("บันทึกแล้วเวียนลงนามต่อ ไม่ยกเลิกซอง")
        void isRecordedAndTheRoundCarriesOn() {
            SignatureRequest envelope = supervisorReviewSentForSignature();

            Result result = signAs(head, stepFor(envelope, "head"), "ไม่ครบถ้วน");

            assertThat(result.ok()).isTrue();
            assertThat(steps.findById(stepFor(envelope, "head").getId()))
                    .get()
                    .extracting(SignatureStep::getSignerChoiceValue)
                    .isEqualTo("ไม่ครบถ้วน");
            assertThat(stepFor(envelope, "dean").getStatus())
                    .as("คณบดีต้องได้คิวต่อ ไม่ใช่ถูกข้าม")
                    .isEqualTo(SignatureStepStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("ไม่มีใครกรอกช่องผลประเมินผ่านแบบฟอร์มได้")
    class TheFieldBelongsToNobodyWhoFillsForms {

        @Test
        @DisplayName("ทั้งแอดมินและผู้ยื่นส่งมาก็ถูกตัดทิ้ง")
        void neitherSideCanWriteIt() {
            Map<String, String> forged = new HashMap<>();
            forged.put("qualification_status", "ครบถ้วน");
            forged.put("major", "วิทยาการคอมพิวเตอร์");

            assertThat(DocumentFieldOwnership.merge(SignatureModule.POSITION, DOC_SUPERVISOR_REVIEW,
                    false, forged, Map.of())).doesNotContainKey("qualification_status");
            assertThat(DocumentFieldOwnership.merge(SignatureModule.POSITION, DOC_SUPERVISOR_REVIEW,
                    true, forged, Map.of())).doesNotContainKey("qualification_status");
        }
    }
}
