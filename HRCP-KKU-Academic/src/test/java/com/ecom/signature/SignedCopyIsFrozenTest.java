package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.ecom.service.UploadPaths;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่ลงนามครบแล้ว ต้องเปิดได้จากสำเนาที่เก็บไว้ ไม่ใช่สร้างใหม่จากเทมเพลตของรุ่นที่ deploy อยู่
 *
 * <p>deploy รุ่นใหม่ที่แก้เทมเพลตต้องไม่ทำให้เอกสารที่เซ็นไปแล้วหน้าตาเปลี่ยนหรือลายเซ็นหาย
 * แต่ค่าที่สำนักงานกรอกทีหลัง (เลขที่หนังสือ วันที่ตรวจ) ต้องยังขึ้นบนเอกสารได้ไม่ว่าจะกรอกตอนไหน
 */
@DisplayName("เอกสารที่ลงนามครบแล้วใช้สำเนาที่เก็บไว้ และยังรับค่าที่สำนักงานกรอกทีหลังได้")
class SignedCopyIsFrozenTest extends AbstractFlowTest {

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

    @Autowired
    private UploadPaths uploadPaths;

    private UserDtls applicant;
    private PositionRequest request;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, DOC_ETHICS, FROZEN);
    }

    private SignatureRequest completedEnvelope() throws InterruptedException {
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.POSITION, request.getId(),
                DOC_ETHICS, "แบบรับรองจริยธรรม", FROZEN,
                List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none()).request();
        SignatureStep step = steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
        UserSignature signature = data.signatureFor(applicant);
        var question = workflow.signerChoiceFor(step.getId());
        Result signed = workflow.sign(step.getId(), applicant, signature.getId(), true, ActorContext.none(),
                null, question != null ? question.options().get(0) : null);
        assertThat(signed.ok()).as(signed.error()).isTrue();

        // The archive is written off-thread after commit; wait for it so it cannot
        // land in the middle of what the test does to the files.
        for (int i = 0; i < 300; i++) {
            SignatureRequest reloaded = envelopes.findById(envelope.getId()).orElseThrow();
            if (reloaded.getSignedDocxPath() != null) {
                assertThat(reloaded.getStatus()).isEqualTo(SignatureRequestStatus.COMPLETED);
                return reloaded;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("สำเนาที่ลงนามครบไม่ถูกเก็บภายใน 30 วินาที");
    }

    private Path archivedDocx(SignatureRequest envelope) {
        return uploadPaths.dir("academic", "signed", String.valueOf(envelope.getRequestId()))
                .resolve("p2doc_" + DOC_ETHICS + "_signed_" + envelope.getVerificationCode() + ".docx");
    }

    private static String text(byte[] docx) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    @Test
    @DisplayName("เปิดเอกสารที่ลงนามครบ ได้ไฟล์ที่เก็บไว้ทุกไบต์ ไม่ได้สร้างใหม่จากเทมเพลตปัจจุบัน")
    void completedDocumentIsServedFromTheArchive() throws Exception {
        SignatureRequest envelope = completedEnvelope();
        Path archived = archivedDocx(envelope);
        assertThat(archived).isRegularFile();
        assertThat(envelope.getSignedDocxPath()).isEqualTo(uploadPaths.toStored(archived));

        // Stand-in for "the template changed in a later deploy": whatever the
        // archive holds is what must come back, not a fresh render.
        byte[] archivedBytes = renderer.renderForDownload(envelope, "docx");
        assertThat(archivedBytes).isEqualTo(Files.readAllBytes(archived));
        byte[] marker = java.util.Arrays.copyOf(archivedBytes, archivedBytes.length + 1);
        Files.write(archived, marker);

        assertThat(renderer.renderForDownload(envelope, "docx")).isEqualTo(marker);
    }

    @Test
    @DisplayName("สำนักงานกรอกวันที่ตรวจหลังลงนามครบ สำเนาถูกสร้างใหม่และมีค่าที่กรอก")
    void officeFieldsFilledAfterCompletionReachTheArchive() throws Exception {
        SignatureRequest envelope = completedEnvelope();
        assertThat(text(renderer.renderForDownload(envelope, "docx"))).doesNotContain("3 ตุลาคม 2569");

        positionService.saveOfficeFieldsAcrossCopies(request, DOC_ETHICS,
                Map.of("verify_date", "3 ตุลาคม 2569"), "แบบรับรองจริยธรรม", true);

        byte[] served = renderer.renderForDownload(envelopes.findById(envelope.getId()).orElseThrow(), "docx");
        assertThat(text(served)).contains("3 ตุลาคม 2569");
        assertThat(Files.readAllBytes(archivedDocx(envelope)))
                .as("สำเนาที่เก็บไว้ต้องเป็นฉบับที่มีค่าใหม่แล้ว")
                .isEqualTo(served);
    }

    @Test
    @DisplayName("ไฟล์สำเนาหายไป (เช่นย้ายเครื่อง) เปิดเอกสารแล้วสร้างสำเนาขึ้นใหม่ให้เอง")
    void aMissingArchiveIsRebuilt() throws Exception {
        SignatureRequest envelope = completedEnvelope();
        Files.delete(archivedDocx(envelope));

        byte[] served = renderer.renderForDownload(envelope, "docx");

        assertThat(served).isNotEmpty();
        assertThat(archivedDocx(envelope)).isRegularFile();
    }
}
