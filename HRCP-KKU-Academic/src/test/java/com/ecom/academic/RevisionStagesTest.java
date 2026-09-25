package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.RevisionProgress.Stage;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่ส่งกลับให้แก้: ลงนาม = ยื่นการแก้ไข
 *
 * <p>เคยหายจากแดชบอร์ดทันทีที่เปิดซองลงนาม ทั้งที่ผู้ยื่นยังไม่ได้ลงนาม และเจ้าหน้าที่ไม่ได้รับแจ้ง
 * เมื่อผู้ยื่นลงนามแล้ว ซองจึงรออยู่เงียบ ๆ
 */
@DisplayName("ขั้นของเอกสารที่ส่งกลับให้แก้")
class RevisionStagesTest extends AbstractFlowTest {

    private static final String DOC = "{\"course_name\":\"x\"}";

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private NotificationRepository notifications;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private SignatureRequest openForApplicant(AcademicRequest request) {
        var created = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "เอกสารที่ 1", DOC, List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        assertThat(created.error()).isNull();
        return created.request();
    }

    private void signAsApplicant(SignatureRequest envelope) {
        SignatureStep step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> "applicant".equals(s.getSlotKey()))
                .findFirst().orElseThrow();
        var result = signatureWorkflow.sign(step.getId(), applicant, data.signatureFor(applicant).getId(), true,
                ActorContext.none(), null, null);
        assertThat(result.error()).isNull();
    }

    private Stage stageOf(AcademicRequest request, int type) {
        var doc = academicService.revisionProgress(request).documents().get(type);
        return doc == null ? null : doc.stage();
    }

    private boolean officerWasToldOfRevision() {
        return notifications.findAll().stream()
                .anyMatch(n -> n.getType() == NotificationType.REVISION_SUBMITTED
                        && n.getRecipient() != null && n.getRecipient().getId().equals(officer.getId()));
    }

    @Test
    @DisplayName("ต้องแก้ไข → เปิดซองแล้วยังไม่ลงนาม: รอท่านลงนาม → ลงนามแล้ว: จบ และแจ้งเจ้าหน้าที่")
    void editThenSignThenDone() {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        data.academicDocument(request, 1, DOC);
        academicService.openDocumentForRevision(request.getId(), 1, "แก้ชื่อรายวิชา");

        assertThat(stageOf(request, 1)).isEqualTo(Stage.TO_EDIT);

        SignatureRequest envelope = openForApplicant(request);
        assertThat(stageOf(request, 1))
                .as("เอกสารถูกล็อกแล้ว แต่ผู้ยื่นยังไม่ได้ลงนาม ต้องยังเตือนอยู่")
                .isEqualTo(Stage.TO_SIGN);
        assertThat(academicService.revisionProgress(request).isActionRequired()).isTrue();

        signAsApplicant(envelope);
        assertThat(stageOf(request, 1))
                .as("เอกสารที่ 1 มีแต่ช่องลงนามของผู้ยื่น ลงนามแล้วจึงจบ")
                .isNull();
        awaitCondition("เจ้าหน้าที่ได้รับแจ้งว่าผู้ยื่นแก้ไขแล้ว", this::officerWasToldOfRevision);
    }

    @Test
    @DisplayName("เอกสารที่ไม่มีช่องลงนามของผู้ยื่น: กดยื่นการแก้ไขแล้วแก้ต่อไม่ได้ และแจ้งเจ้าหน้าที่")
    void documentWithoutApplicantSlotIsSubmittedByButton() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, 6, DOC);
        positionService.openDocumentForRevision(request.getId(), 6, "แก้ข้อมูล");

        assertThat(positionService.canSubmitRevision(request, 6)).isTrue();
        assertThat(positionService.revisionProgress(request).documents().get(6).stage()).isEqualTo(Stage.TO_EDIT);

        mvc.perform(post("/user/position/request/" + request.getId() + "/document/6/submit-revision")
                .with(user(applicant.getEmail()).roles("USER")).with(csrf()));

        assertThat(positionService.canApplicantEditDocument(request, 6))
                .as("ยื่นแล้วประตูแก้ไขต้องปิด").isFalse();
        assertThat(positionService.canSubmitRevision(request, 6)).isFalse();
        assertThat(positionService.revisionProgress(request).documents())
                .as("คณบดียังไม่เคยลงนามฉบับนี้ ไม่มีใครต้องลงนามใหม่ จึงจบทันที")
                .doesNotContainKey(6);
        awaitCondition("เจ้าหน้าที่ได้รับแจ้งว่าผู้ยื่นแก้ไขแล้ว", this::officerWasToldOfRevision);
    }

    @Test
    @DisplayName("เอกสารที่มีช่องลงนามของผู้ยื่น ใช้ปุ่มยื่นการแก้ไขข้ามการลงนามไม่ได้")
    void signedDocumentsCannotBeSubmittedByButton() {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, 2, DOC);
        positionService.openDocumentForRevision(request.getId(), 2, null);

        assertThat(positionService.canSubmitRevision(request, 2)).isFalse();
        assertThat(positionService.submitRevision(request, 2, applicant)).isNotNull();
        assertThat(positionService.canApplicantEditDocument(request, 2)).isTrue();
    }

    @Test
    @DisplayName("ส่งกลับรอบใหม่หลังยื่นแล้ว: ต้องแก้ไขอีกครั้ง")
    void sendingBackAgainReopensTheDocument() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, 6, DOC);
        positionService.openDocumentForRevision(request.getId(), 6, "รอบแรก");
        assertThat(positionService.submitRevision(request, 6, applicant)).isNull();
        assertThat(positionService.canApplicantEditDocument(request, 6)).isFalse();

        Thread.sleep(5); // เวลาส่งกลับรอบใหม่ต้องอยู่หลังเวลายื่น
        positionService.openDocumentForRevision(request.getId(), 6, "รอบสอง");

        assertThat(positionService.canApplicantEditDocument(request, 6)).isTrue();
        assertThat(positionService.revisionProgress(request).documents().get(6).note()).isEqualTo("รอบสอง");
    }
}
