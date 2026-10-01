package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.ExternalSignerService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.pdf.PdfIncrementService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.service.TwoFactorService;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;

/**
 * ทางสำรอง B: ผู้ลงนามภายนอกที่ไม่มี Digital ID ยืนยันตัวตนทางอีเมล แล้วระบบลงนามลงไฟล์ด้วยใบรับรองของระบบ
 * — Foxit/Adobe ยังขึ้นใบรับรองของขั้นนั้น และเหตุผลระบุชื่อ/อีเมลของผู้ลงนาม (docs/PLAN-external-signer.md)
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:1" })
@DisplayName("ผู้ลงนามภายนอกไม่มี Digital ID — ยืนยันทางอีเมล ระบบประทับรับรองแทน")
class SystemSealSigningTest extends AbstractFlowTest {

    private static Path sealFile;

    @DynamicPropertySource
    static void systemSeal(DynamicPropertyRegistry registry) throws Exception {
        sealFile = Files.createTempFile("system-seal", ".p12");
        Files.write(sealFile, TestCertificates.validP12("ระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์"));
        registry.add("app.esign.system-seal.p12-path", () -> sealFile.toString());
        registry.add("app.esign.system-seal.password", () -> TestCertificates.PIN);
    }

    @Autowired
    private ExternalSignerService externalSigners;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private SignedPdfRevisionService revisions;

    private UserDtls guest;
    private UserSignature signature;
    private SignatureStep step;
    private SignatureRequest envelope;

    @BeforeEach
    void anExternalSignerIsUp() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        UserDtls officer = data.admin();
        guest = externalSigners.invite(new ExternalSignerService.Invite("รศ.ดร.", "วิภา", "ภายนอก",
                "wipa.test@example.invalid", "มหาวิทยาลัยเชียงใหม่"), officer);
        signature = data.signatureFor(guest);
        AcademicRequest request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        String json = "{\"applicant_name\":\"วิภา ภายนอก\",\"title\":\"ขอรับการประเมินผลการสอน\","
                + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"2569\"}";
        envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 1, json, officer,
                List.of(new SignerAssignment("applicant", guest.getId())));
        step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
    }

    @Test
    @DisplayName("ไม่มีรหัสยืนยัน / รหัสผิด — ลงนามไม่ได้")
    void aMissingOrWrongCodeIsRefused() {
        assertThat(signatureWorkflow.sendSigningOtp(step.getId(), guest)).isNull();

        var missing = signatureWorkflow.sign(step.getId(), guest, signature.getId(), true, ActorContext.none(),
                null, null, null, null);
        assertThat(missing.ok()).isFalse();
        assertThat(missing.error()).contains("รหัสยืนยัน");

        var wrong = signatureWorkflow.sign(step.getId(), guest, signature.getId(), true, ActorContext.none(),
                null, null, null, "00000000");
        assertThat(wrong.ok()).isFalse();
    }

    @Test
    @DisplayName("รหัสถูก — ไฟล์มีลายมือชื่อดิจิทัลของขั้นนี้ (ใบรับรองของระบบ) เหตุผลระบุชื่อและอีเมลผู้ลงนาม")
    void theSystemSealsTheSignatureIntoThePdf() throws Exception {
        String otp = twoFactorService.generateOtp(guest);

        var signed = signatureWorkflow.sign(step.getId(), guest, signature.getId(), true, ActorContext.none(),
                null, null, null, otp);

        assertThat(signed.ok()).as(signed.error()).isTrue();
        byte[] pdf = revisions.latest(envelope.getId());
        var checks = PdfIncrementService.verify(pdf);
        assertThat(checks).extracting(PdfIncrementService.SignatureCheck::field).containsExactly("sig_applicant");
        assertThat(checks).allMatch(PdfIncrementService.SignatureCheck::valid);
        assertThat(checks.get(0).signerDn()).contains("ระบบพัฒนาบุคลากร");
        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getSignatureDictionaries().get(0).getReason())
                    .contains("วิภา ภายนอก").contains("wipa.test@example.invalid").contains("ยืนยันตัวตนทางอีเมล");
        }
    }

    @Test
    @DisplayName("ผู้ใช้ใน มข. ที่ไม่มี Digital ID ใช้ทางสำรองนี้ไม่ได้")
    void internalUsersCannotUseTheSeal() {
        assertThat(signatureWorkflow.sendSigningOtp(step.getId(), data.applicant())).isNotNull();
    }
}
