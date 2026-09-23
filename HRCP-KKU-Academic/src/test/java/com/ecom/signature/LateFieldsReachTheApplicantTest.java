package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignedDocumentRenderer;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่ผู้ยื่นกรอกและลงนามแล้ว แอดมินกรอกต่อ แล้วส่งต่อให้คนอื่นลงนาม — ผู้ยื่นต้องเห็นฉบับล่าสุด
 *
 * <p>ใช้เอกสารที่ 3 เฟส 2 (แบบรับรองจริยธรรม): ผู้ยื่นลงนามตอนที่ 1 แอดมินกรอกวันที่ตรวจ
 * ({@code verify_date}) แล้วส่งต่อให้คณบดีลงนามตอนที่ 2
 */
@DisplayName("ค่าที่แอดมินกรอกหลังผู้ยื่นลงนาม และลายเซ็นของคนถัดไป ต้องถึงเอกสารที่ผู้ยื่นเห็น")
class LateFieldsReachTheApplicantTest extends AbstractFlowTest {

    private static final int DOC_ETHICS = 3;
    private static final String FROZEN = "{\"title\":\"นาย\",\"applicant_name\":\"สมชาย ใจดี\","
            + "\"certification_date\":\"1 ตุลาคม 2569\"}";

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignedDocumentRenderer renderer;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private UserDtls dean;
    private PositionRequest request;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        dean = data.user("dean-late@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
        request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, DOC_ETHICS, FROZEN);
    }

    private SignatureRequest applicantSigned() {
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.POSITION, request.getId(),
                DOC_ETHICS, "แบบรับรองจริยธรรม", FROZEN,
                List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none()).request();
        Result signed = sign(applicant, stepFor(envelope, "applicant"));
        assertThat(signed.ok()).as(signed.error()).isTrue();
        return envelopes.findById(envelope.getId()).orElseThrow();
    }

    private SignatureStep stepFor(SignatureRequest envelope, String slotKey) {
        return steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> slotKey.equals(s.getSlotKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ไม่พบช่องลงนาม " + slotKey));
    }

    private Result sign(UserDtls signer, SignatureStep step) {
        UserSignature signature = data.signatureFor(signer);
        var question = workflow.signerChoiceFor(step.getId());
        String answer = question != null ? question.options().get(0) : null;
        return workflow.sign(step.getId(), signer, signature.getId(), true, ActorContext.none(), null, answer);
    }

    private String rendered(SignatureRequest envelope) throws IOException {
        byte[] docx = renderer.renderDocx(envelopes.findById(envelope.getId()).orElseThrow());
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String documentXml(byte[] docx) throws IOException {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(docx))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("ไม่พบ word/document.xml");
    }

    @Test
    @DisplayName("ผู้ยื่นเซ็นแล้ว ยังไม่ส่งต่อ: แอดมินกรอกวันที่ตรวจได้และขึ้นบนเอกสารทันที")
    void adminFieldsFilledBeforeForwardingShowUp() throws IOException {
        SignatureRequest envelope = applicantSigned();
        assertThat(workflow.awaitsMoreSigners(SignatureModule.POSITION, request.getId(), DOC_ETHICS)).isTrue();

        positionService.saveOfficeFieldsAcrossCopies(request, DOC_ETHICS,
                Map.of("verify_date", "3 ตุลาคม 2569"), "แบบรับรองจริยธรรม", true);

        assertThat(rendered(envelope)).contains("3 ตุลาคม 2569");
    }

    @Test
    @DisplayName("ส่งต่อให้คณบดีแล้ว ค่าของแอดมินยังอยู่ และหลังคณบดีเซ็น ลายเซ็นก็ขึ้นด้วย")
    void forwardedRoundKeepsAdminFieldsAndAddsTheNextSignature() throws IOException {
        SignatureRequest envelope = applicantSigned();
        positionService.saveOfficeFieldsAcrossCopies(request, DOC_ETHICS,
                Map.of("verify_date", "3 ตุลาคม 2569"), "แบบรับรองจริยธรรม", true);

        Result forwarded = workflow.forwardToNextSigners(envelope.getId(),
                List.of(new SignerAssignment("dean", dean.getId())), null, data.admin(), ActorContext.none());
        assertThat(forwarded.ok()).as(forwarded.error()).isTrue();
        assertThat(envelopes.findById(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.IN_PROGRESS);

        assertThat(rendered(envelope))
                .as("ระหว่างรอคณบดี ค่าที่แอดมินกรอกต้องไม่หายไป")
                .contains("3 ตุลาคม 2569");

        Result deanSigned = sign(dean, stepFor(envelope, "dean"));
        assertThat(deanSigned.ok()).as(deanSigned.error()).isTrue();

        byte[] docx = renderer.renderDocx(envelopes.findById(envelope.getId()).orElseThrow());
        assertThat(documentXml(docx).split("r:embed=\"rIdHrcpSig", -1).length - 1)
                .as("มีรูปลายเซ็นสองรูป: ผู้ยื่นและคณบดี")
                .isEqualTo(2);
        assertThat(rendered(envelope)).contains("3 ตุลาคม 2569");
    }

    @Test
    @DisplayName("เซ็นครบทุกช่องแล้ว ช่องของแอดมินกลายเป็นเนื้อความที่เซ็นรับรองไปแล้ว แก้ไม่ได้อีก")
    void afterEveryoneSignedAdminFieldsAreClosed() throws IOException {
        SignatureRequest envelope = applicantSigned();
        positionService.saveOfficeFieldsAcrossCopies(request, DOC_ETHICS,
                Map.of("verify_date", "3 ตุลาคม 2569"), "แบบรับรองจริยธรรม", true);
        workflow.forwardToNextSigners(envelope.getId(),
                List.of(new SignerAssignment("dean", dean.getId())), null, data.admin(), ActorContext.none());
        sign(dean, stepFor(envelope, "dean"));

        assertThat(workflow.awaitsMoreSigners(SignatureModule.POSITION, request.getId(), DOC_ETHICS)).isFalse();
    }
}
