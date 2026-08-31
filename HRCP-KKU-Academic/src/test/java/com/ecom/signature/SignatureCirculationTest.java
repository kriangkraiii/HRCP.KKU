package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureAuditEvent;
import com.ecom.academic.model.SignatureAuditEventType;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * การเวียนลงนาม — the signature chain both phases run on.
 *
 * <p>Every document in this system that matters is signed, and both flows stop
 * dead without it: a teaching-evaluation request cannot be submitted until the
 * applicant has signed documents 0 and 1, and a position request cannot be
 * submitted until they have signed all six of theirs. Steps 4, 17 and 18 of the
 * flow document are signatures and nothing else — คณบดีลงนามคำสั่งแต่งตั้ง,
 * ผู้บังคับบัญชาลงความเห็น, คณบดีลงความเห็น.
 *
 * <p>So this covers the chain itself: who may sign, in what order, what happens
 * when someone refuses, and what stops a document being edited out from under a
 * signature that has already been given.
 */
@DisplayName("ระบบลงนามอิเล็กทรอนิกส์: การเวียนลงนามทั้งสองเฟส")
class SignatureCirculationTest extends AbstractFlowTest {

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private UserDtls hrOfficer;
    private UserDtls dean;
    private UserSignature applicantSignature;

    private void cast() {
        applicant = data.applicant();
        hrOfficer = data.admin();
        dean = data.user("dean@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
        applicantSignature = data.signatureFor(applicant);
    }

    private Result sendForSignature(SignatureModule module, Long requestId, int docType,
            List<SignerAssignment> signers) {
        return workflow.createEnvelope(module, requestId, docType, "เอกสารทดสอบ",
                "{\"applicant_name\":\"สมชาย ใจดี\"}", signers, null, applicant,
                ActorContext.none());
    }

    /**
     * Finds a step by its slot, through the repository rather than
     * {@code envelope.getSteps()}.
     *
     * <p>The collection is lazy and an envelope fetched with {@code findById} is
     * detached by the time a test looks at it, so walking it here threw
     * LazyInitializationException on every reload. Going to the repository is
     * also what the production code does.
     */
    private SignatureStep stepFor(SignatureRequest envelope, String slotKey) {
        return steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> slotKey.equals(s.getSlotKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ไม่พบช่องลงนาม " + slotKey));
    }

    /** The slot keys on an envelope, in step order. */
    private List<String> slotKeysOf(SignatureRequest envelope) {
        return steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .map(SignatureStep::getSlotKey)
                .toList();
    }

    private Result signAs(UserDtls signer, SignatureStep step) {
        UserSignature signature = signer.getId().equals(applicant.getId())
                ? applicantSignature
                : data.signatureFor(signer);
        return workflow.sign(step.getId(), signer, signature.getId(), true, ActorContext.none());
    }

    // =================================================================
    @Nested
    @DisplayName("ลำดับการลงนาม (ตามลำดับขั้น)")
    class Sequencing {

        /**
         * Academic document 1 has two slots in order: the applicant, then HR.
         * The whole point of a chain is that the second cannot go first.
         */
        @Test
        @DisplayName("คนที่สองลงนามก่อนคนแรกไม่ได้ ต้องรอให้ถึงคิว")
        void secondSignerMustWaitTheirTurn() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 1,
                    List.of(new SignerAssignment("applicant", applicant.getId()),
                            new SignerAssignment("hr", hrOfficer.getId()))).request();

            assertThat(stepFor(envelope, "applicant").getStatus())
                    .as("คนแรกต้องพร้อมลงนามทันที")
                    .isEqualTo(SignatureStepStatus.ACTIVE);
            assertThat(stepFor(envelope, "hr").getStatus())
                    .as("คนที่สองต้องรออยู่ก่อน")
                    .isEqualTo(SignatureStepStatus.WAITING);

            Result tooEarly = signAs(hrOfficer, stepFor(envelope, "hr"));
            assertThat(tooEarly.ok()).isFalse();
            assertThat(tooEarly.error()).contains("ยังไม่ถึงคิว");
        }

        /**
         * The review gate, and the reason the chain is not simply automatic: the
         * applicant signs their own part whenever they like, but everyone after
         * them waits until staff have read the document and released it. A
         * mistake must not reach the dean because the system forwarded it on its
         * own.
         */
        @Test
        @DisplayName("ผู้ยื่นลงนามแล้ว คนถัดไปยังไม่ถูกปลุก — รอเจ้าหน้าที่ตรวจก่อน")
        void theChainHoldsForStaffReview() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 1,
                    List.of(new SignerAssignment("applicant", applicant.getId()),
                            new SignerAssignment("hr", hrOfficer.getId()))).request();

            assertThat(signAs(applicant, stepFor(envelope, "applicant")).ok()).isTrue();

            assertThat(stepFor(envelope, "applicant").getStatus())
                    .isEqualTo(SignatureStepStatus.SIGNED);
            assertThat(stepFor(envelope, "hr").getStatus())
                    .as("ยังไม่ปล่อยเวียน คนถัดไปจึงต้องยังไม่ถูกทวงให้ลงนาม")
                    .isEqualTo(SignatureStepStatus.WAITING);
            assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                    .isEqualTo(SignatureRequestStatus.IN_PROGRESS);

            assertThat(workflow.findInbox(hrOfficer))
                    .as("และต้องยังไม่โผล่ในกล่องรอลงนามของเขา")
                    .isEmpty();
        }

        @Test
        @DisplayName("เจ้าหน้าที่ตรวจแล้วปล่อยเวียน คิวจึงเลื่อนไปคนถัดไป")
        void staffReleaseTheChainForCirculation() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 1,
                    List.of(new SignerAssignment("applicant", applicant.getId()),
                            new SignerAssignment("hr", hrOfficer.getId()))).request();
            signAs(applicant, stepFor(envelope, "applicant"));

            Result released = workflow.startCirculation(envelope.getId(), hrOfficer,
                    ActorContext.none());

            assertThat(released.ok()).as("ปล่อยเวียนไม่สำเร็จ: %s", released.error()).isTrue();
            assertThat(stepFor(envelope, "hr").getStatus())
                    .isEqualTo(SignatureStepStatus.ACTIVE);
            assertThat(workflow.findInbox(hrOfficer))
                    .as("ตอนนี้จึงจะโผล่ในกล่องรอลงนาม")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("ลงนามครบทุกคน ซองปิดเป็น 'ลงนามครบแล้ว'")
        void envelopeCompletesWhenEveryoneHasSigned() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 1,
                    List.of(new SignerAssignment("applicant", applicant.getId()),
                            new SignerAssignment("hr", hrOfficer.getId()))).request();

            signAs(applicant, stepFor(envelope, "applicant"));
            workflow.startCirculation(envelope.getId(), hrOfficer, ActorContext.none());
            signAs(hrOfficer, stepFor(envelope, "hr"));

            assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                    .isEqualTo(SignatureRequestStatus.COMPLETED);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("ใครลงนามได้บ้าง")
    class Authorisation {

        @Test
        @DisplayName("คนที่ไม่ได้ถูกมอบหมาย ลงนามแทนไม่ได้")
        void onlyTheAssignedSignerMaySign() {
            cast();
            UserDtls outsider = data.otherApplicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            Result result = signAs(outsider, stepFor(envelope, "applicant"));

            assertThat(result.ok()).isFalse();
            assertThat(result.error()).contains("ไม่ใช่ผู้ที่ได้รับมอบหมาย");
        }

        @Test
        @DisplayName("ไม่กดยอมรับข้อความยินยอม ลงนามไม่ได้ — ความยินยอมคือสิ่งที่ทำให้เป็นลายเซ็น")
        void consentIsMandatory() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            Result result = workflow.sign(stepFor(envelope, "applicant").getId(), applicant,
                    applicantSignature.getId(), false, ActorContext.none());

            assertThat(result.ok()).isFalse();
            assertThat(result.error()).contains("ยินยอม");
        }

        @Test
        @DisplayName("ใช้ลายเซ็นของคนอื่นไม่ได้")
        void cannotSignWithSomebodyElsesSignature() {
            cast();
            UserDtls otherPerson = data.otherApplicant();
            UserSignature theirSignature = data.signatureFor(otherPerson);
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            Result result = workflow.sign(stepFor(envelope, "applicant").getId(), applicant,
                    theirSignature.getId(), true, ActorContext.none());

            assertThat(result.ok()).isFalse();
            assertThat(result.error()).contains("ไม่พบลายเซ็นที่เลือก");
        }

        @Test
        @DisplayName("กล่องรอลงนามแสดงเฉพาะงานของตัวเอง")
        void inboxShowsOnlyYourOwnSteps() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            sendForSignature(SignatureModule.ACADEMIC, request.getId(), 1,
                    List.of(new SignerAssignment("applicant", applicant.getId()),
                            new SignerAssignment("hr", hrOfficer.getId())));

            assertThat(workflow.findInbox(applicant))
                    .as("ผู้ยื่นต้องเห็นเฉพาะขั้นตอนของตัวเองที่ถึงคิวแล้ว")
                    .extracting(SignatureStep::getSlotKey)
                    .containsExactly("applicant");
            assertThat(workflow.findInbox(data.otherApplicant()))
                    .as("คนที่ไม่เกี่ยวข้องต้องไม่เห็นอะไรเลย")
                    .isEmpty();
        }
    }

    // =================================================================
    @Nested
    @DisplayName("การล็อกเอกสารระหว่างเวียนลงนาม")
    class DocumentLocking {

        /**
         * The reason the lock exists: editing a document that is out for
         * signature changes the content underneath a signature its signer never
         * saw.
         */
        @Test
        @DisplayName("ส่งเวียนลงนามแล้ว เอกสารถูกล็อก แก้ไขไม่ได้")
        void circulatingDocumentIsLocked() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            assertThat(workflow.isDocumentLocked(SignatureModule.ACADEMIC, request.getId(), 0))
                    .as("ก่อนส่งลงนาม ยังแก้ได้ตามปกติ")
                    .isFalse();

            sendForSignature(SignatureModule.ACADEMIC, request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId())));

            assertThat(workflow.isDocumentLocked(SignatureModule.ACADEMIC, request.getId(), 0))
                    .isTrue();
            assertThat(academicService.canApplicantEditDocument(request, 0))
                    .as("ผู้ยื่นต้องแก้เอกสารที่กำลังเวียนลงนามไม่ได้")
                    .isFalse();
        }

        @Test
        @DisplayName("ส่งซองลงนามซ้ำสำหรับเอกสารเดิมไม่ได้")
        void cannotOpenASecondEnvelopeForTheSameDocument() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            sendForSignature(SignatureModule.ACADEMIC, request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId())));

            Result second = sendForSignature(SignatureModule.ACADEMIC, request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId())));

            assertThat(second.ok()).isFalse();
            assertThat(second.error()).contains("อยู่ระหว่างการเวียนลงนาม");
        }

        /**
         * Belt and braces on top of the lock. If a row is edited directly in the
         * database, the frozen hash no longer matches and every signature on the
         * envelope is voided rather than silently endorsing new content.
         */
        @Test
        @DisplayName("เนื้อหาถูกแก้หลังส่งลงนาม — ลายเซ็นทั้งซองเป็นโมฆะ")
        void tamperingVoidsTheEnvelope() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            SignatureRequest stored = envelopes.findById(envelope.getId()).orElseThrow();
            stored.setFrozenJson("{\"applicant_name\":\"ชื่อที่ถูกแก้ทีหลัง\"}");
            envelopes.save(stored);

            Result result = signAs(applicant, stepFor(stored, "applicant"));

            assertThat(result.ok()).isFalse();
            assertThat(result.error()).contains("ถูกแก้ไขหลังส่งลงนาม");
            assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                    .isEqualTo(SignatureRequestStatus.VOIDED);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("การปฏิเสธและการยกเลิก")
    class DeclineAndCancel {

        @Test
        @DisplayName("ผู้ลงนามปฏิเสธ — ซองปิดทันที คนถัดไปไม่ต้องรอ")
        void decliningStopsTheChain() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 1,
                    List.of(new SignerAssignment("applicant", applicant.getId()),
                            new SignerAssignment("hr", hrOfficer.getId()))).request();

            Result declined = workflow.decline(stepFor(envelope, "applicant").getId(), applicant,
                    "ข้อมูลในเอกสารยังไม่ถูกต้อง", ActorContext.none());

            assertThat(declined.ok()).isTrue();
            SignatureRequest reloaded = envelopes.findById(envelope.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(SignatureRequestStatus.DECLINED);
            assertThat(stepFor(reloaded, "applicant").getDeclineReason())
                    .as("เหตุผลต้องถูกเก็บไว้ ไม่งั้นไม่มีใครรู้ว่าต้องแก้อะไร")
                    .isEqualTo("ข้อมูลในเอกสารยังไม่ถูกต้อง");
        }

        @Test
        @DisplayName("คำร้องประเมินถูกปฏิเสธ — ซองลงนามที่ค้างอยู่ถูกยกเลิกตามไปด้วย")
        void rejectingAnEvaluationCancelsItsOpenEnvelopes() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            academicService.updateStatus(request.getId(), RequestStatus.REJECTED, hrOfficer,
                    "เอกสารไม่ครบถ้วน", false);

            SignatureRequest reloaded = envelopes.findById(envelope.getId()).orElseThrow();
            assertThat(reloaded.getStatus())
                    .as("ปล่อยให้ค้างไว้ = ยังมีคนถูกทวงให้ลงนามในเรื่องที่ปิดไปแล้ว")
                    .isEqualTo(SignatureRequestStatus.CANCELLED);
            assertThat(reloaded.getCancelReason()).contains("เอกสารไม่ครบถ้วน");
            assertThat(stepFor(reloaded, "applicant").getStatus())
                    .isEqualTo(SignatureStepStatus.SKIPPED);
        }

        @Test
        @DisplayName("คำร้องขอตำแหน่งถูกปฏิเสธ — ซองลงนามถูกยกเลิกเช่นกัน")
        void rejectingAPositionRequestCancelsItsOpenEnvelopes() {
            cast();
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);
            SignatureRequest envelope = sendForSignature(SignatureModule.POSITION,
                    request.getId(), 2,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            positionService.updateStatus(request.getId(), PositionRequestStatus.REJECTED,
                    hrOfficer, "คุณสมบัติไม่เข้าข่าย", false);

            assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                    .isEqualTo(SignatureRequestStatus.CANCELLED);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("การส่งเวียนต่อ (ข้อ 17-18)")
    class Forwarding {

        /**
         * Step 17 and 18 of the flow: the applicant signs their part, staff check
         * it, and only then does it go to the dean. Without forwarding, a round
         * could never grow past the signers chosen when it was first sent.
         */
        @Test
        @DisplayName("ผู้ยื่นลงนามแล้ว เจ้าหน้าที่ส่งเวียนต่อไปยังคณบดีได้")
        void staffForwardTheSignedDocumentOnwards() {
            cast();
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);

            // Position document 3 has two slots: ผู้เสนอขอ then คณบดี.
            SignatureRequest envelope = sendForSignature(SignatureModule.POSITION,
                    request.getId(), 3,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            assertThat(signAs(applicant, stepFor(envelope, "applicant")).ok()).isTrue();
            assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                    .as("ยังไม่มีใครในช่องคณบดี ซองจึงถือว่าจบรอบนี้ไปก่อน")
                    .isEqualTo(SignatureRequestStatus.COMPLETED);

            Result forwarded = workflow.forwardToNextSigners(envelope.getId(),
                    List.of(new SignerAssignment("dean", dean.getId())), null, hrOfficer,
                    ActorContext.none());

            assertThat(forwarded.ok())
                    .as("ต้องเปิดรอบต่อได้: %s", forwarded.error())
                    .isTrue();

            SignatureRequest reopened = envelopes.findById(envelope.getId()).orElseThrow();
            assertThat(reopened.getStatus()).isEqualTo(SignatureRequestStatus.IN_PROGRESS);
            assertThat(stepFor(reopened, "dean").getStatus())
                    .isEqualTo(SignatureStepStatus.ACTIVE);
            assertThat(stepFor(reopened, "applicant").getStatus())
                    .as("ลายเซ็นที่ให้ไปแล้วต้องไม่ถูกรีเซ็ต")
                    .isEqualTo(SignatureStepStatus.SIGNED);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("หลักฐานและร่องรอยการตรวจสอบ")
    class Evidence {

        @Test
        @DisplayName("ทุกเหตุการณ์ถูกบันทึกไว้ในร่องรอยการตรวจสอบ")
        void everyEventIsAudited() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();
            signAs(applicant, stepFor(envelope, "applicant"));

            assertThat(workflow.auditTrail(envelope.getId()))
                    .extracting(SignatureAuditEvent::getEventType)
                    .contains(SignatureAuditEventType.CREATED, SignatureAuditEventType.SIGNED);
        }

        @Test
        @DisplayName("ลงนามแล้วต้องเก็บหลักฐานครบ: เวลา ลายเซ็น ความยินยอม และ hash เอกสาร")
        void aSignatureCarriesItsEvidence() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            signAs(applicant, stepFor(envelope, "applicant"));

            SignatureStep signed = stepFor(
                    envelopes.findById(envelope.getId()).orElseThrow(), "applicant");
            assertThat(signed.getSignedAt()).isNotNull();
            assertThat(signed.getConsentAccepted()).isTrue();
            assertThat(signed.getImagePathSnapshot())
                    .as("เก็บสำเนาไฟล์ลายเซ็นไว้ ลบลายเซ็นทีหลังต้องไม่กระทบเอกสารที่ลงนามแล้ว")
                    .isNotBlank();
            assertThat(signed.getDocHashSigned())
                    .as("ต้องผูกกับเนื้อหาที่ลงนามจริง")
                    .isEqualTo(envelope.getFrozenHash());
            assertThat(signed.getEvidenceHmac()).isNotBlank();
        }

        @Test
        @DisplayName("ตรวจสอบเอกสารด้วยรหัสยืนยันได้")
        void aSignedDocumentCanBeVerifiedByItsCode() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            SignatureRequest envelope = sendForSignature(SignatureModule.ACADEMIC,
                    request.getId(), 0,
                    List.of(new SignerAssignment("applicant", applicant.getId()))).request();

            assertThat(envelope.getVerificationCode()).isNotBlank();
            assertThat(workflow.findByVerificationCode(envelope.getVerificationCode()))
                    .isPresent();
            assertThat(workflow.findByVerificationCode("รหัสมั่ว-ไม่มีอยู่จริง")).isEmpty();
        }
    }

    // =================================================================
    @Nested
    @DisplayName("ผังผู้ลงนามต้องตรงกับเอกสาร Flow")
    class SlotMapMatchesTheFlowDocument {

        @Test
        @DisplayName("ข้อ 16-18: แบบประเมินคุณสมบัติ ต้องมีทั้งผู้บังคับบัญชาและคณบดี")
        void qualificationFormNeedsBothSupervisorAndDean() {
            cast();
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DOCUMENT_RECEIVED, null);

            // Document 5 = แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา
            Result created = workflow.createEnvelope(SignatureModule.POSITION, request.getId(), 5,
                    "แบบประเมินคุณสมบัติ", "{\"applicant_name\":\"สมชาย\"}",
                    List.of(new SignerAssignment("head", dean.getId()),
                            new SignerAssignment("dean", hrOfficer.getId())),
                    null, applicant, ActorContext.none());

            assertThat(created.ok()).isTrue();
            assertThat(slotKeysOf(created.request()))
                    .as("ข้อ 16 = หัวหน้าสาขาวิชา, ข้อ 18 = คณบดี — ต้องมีครบทั้งสอง ตามลำดับ")
                    .containsExactly("head", "dean");
        }

        @Test
        @DisplayName("ข้อ 4: คำสั่งแต่งตั้งอนุกรรมการ ต้องมีคณบดีลงนาม")
        void subCommitteeOrderNeedsTheDean() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            Result created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 3,
                    "คำสั่งแต่งตั้งคณะอนุกรรมการ", "{\"order_no\":\"123/2569\"}",
                    List.of(new SignerAssignment("dean", dean.getId())),
                    null, hrOfficer, ActorContext.none());

            assertThat(created.ok()).isTrue();
            assertThat(slotKeysOf(created.request())).containsExactly("dean");
        }

        @Test
        @DisplayName("เอกสารที่ไม่มีจุดลงนาม (ข้อเสนอแนะอนุกรรมการ) ส่งลงนามไม่ได้")
        void aDocumentWithNoSignatureBlockCannotBeCirculated() {
            cast();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            Result created = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 5,
                    "ข้อเสนอแนะจากคณะอนุกรรมการ", "{\"suggestions_text\":\"ควรเพิ่มสื่อการสอน\"}",
                    List.of(new SignerAssignment("dean", dean.getId())),
                    null, hrOfficer, ActorContext.none());

            assertThat(created.ok()).isFalse();
            assertThat(created.error()).contains("ไม่มีจุดลงนาม");
        }

        @Test
        @DisplayName("เอกสารของผู้ยื่นในเฟส 2 ทุกฉบับต้องรู้ว่าต้องลงนามหรือไม่")
        void everyApplicantDocumentHasAKnownSignatureRequirement() {
            cast();
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.DRAFT, null);

            // The submit gate asks this for each document; an answer it cannot
            // give would leave the applicant unable to submit and unable to see why.
            assertThat(workflow.getUnsignedApplicantDocTypes(SignatureModule.POSITION,
                    request.getId(), PositionRequestService.APPLICANT_DOCS))
                    .as("เอกสาร 1,2,3,4,9 ต้องมีลายเซ็นผู้ยื่น ส่วนเอกสาร 6 เป็นของคณบดี")
                    .containsExactly(1, 2, 3, 4, 9);
        }
    }
}
