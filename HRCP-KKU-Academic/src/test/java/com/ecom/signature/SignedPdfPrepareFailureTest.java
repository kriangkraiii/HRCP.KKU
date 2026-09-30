package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PdfMode;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignedDocumentRenderer;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * LibreOffice แปลงไฟล์ล้ม ตอนส่งเอกสารที่ลงนามแบบใส่ทับไปเวียน
 *
 * <p>เดิมระบบถอยไปใช้แบบเดิมเงียบ ๆ (ซอง 70 วันที่ 30 ก.ย. 2569) ไฟล์ที่ได้ไม่มีใบรับรองของใครเลย
 * และไม่มีใครรู้ ตอนนี้ลองใหม่ก่อน ถ้ายังไม่ได้ให้ปฏิเสธการส่ง
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:1" })
@DisplayName("สร้างไฟล์ลงนามไม่สำเร็จ — ลองใหม่ แล้วปฏิเสธการส่ง ไม่ถอยไปแบบไม่มีใบรับรอง")
class SignedPdfPrepareFailureTest extends AbstractFlowTest {

    @MockitoSpyBean
    private SignedDocumentRenderer renderer;

    @Autowired
    private SignatureRequestRepository envelopes;

    private UserDtls applicant;
    private AcademicRequest request;

    @BeforeEach
    void setUp() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        applicant = data.applicant();
        request = data.evaluation(applicant, RequestStatus.DRAFT);
    }

    private SignatureWorkflowService.Result send() {
        return signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1, "เอกสารที่ 1",
                "{\"applicant_name\":\"" + applicant.getName() + "\",\"title\":\"ขอรับการประเมินผลการสอน\","
                        + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"2569\"}",
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())),
                null, applicant, SignatureWorkflowService.ActorContext.none());
    }

    @Test
    @DisplayName("ล้มครั้งเดียวแล้วสำเร็จ — ส่งได้ และเป็นไฟล์ที่ลงนามด้วยใบรับรอง")
    void aSingleFailedConversionIsRetried() throws Exception {
        doThrow(new IOException("LibreOffice conversion failed (exit 1): ")).doCallRealMethod()
                .when(renderer).toPdf(any());

        var result = send();

        assertThat(result.ok()).as(result.error()).isTrue();
        assertThat(envelopes.findById(result.request().getId()).orElseThrow().getPdfMode())
                .isEqualTo(PdfMode.INCREMENTAL);
        verify(renderer, atLeast(2)).toPdf(any());
    }

    @Test
    @DisplayName("ล้มทุกครั้ง — ปฏิเสธการส่ง ไม่มีซองค้าง และไม่มีซองแบบเดิมหลุดออกไป")
    void aConversionThatKeepsFailingIsRefused() throws Exception {
        doThrow(new IOException("LibreOffice conversion failed (exit 1): ")).when(renderer).toPdf(any());

        var result = send();

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("สร้างไฟล์ลงนามไม่สำเร็จ");
        assertThat(envelopes.findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(
                SignatureModule.ACADEMIC, request.getId(), 1))
                .as("ซองต้องถูกย้อนกลับทั้งหมด").isEmpty();
        verify(renderer, times(3)).toPdf(any());

        // LibreOffice กลับมาปกติ — ส่งใหม่ได้
        doCallRealMethod().when(renderer).toPdf(any());
        assertThat(send().ok()).isTrue();
    }
}
