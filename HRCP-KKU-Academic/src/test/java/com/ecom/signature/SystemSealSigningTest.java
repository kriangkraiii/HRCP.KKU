package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
                + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"1/2569\"}";
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

    @Test
    @DisplayName("อีเมลขอให้ลงนามถึงคนนอก มข. — ปุ่มเป็นลิงก์เข้าสู่ระบบใช้ครั้งเดียว ไม่ใช่หน้าลงนามที่ต้องผ่าน KKU SSO")
    void theRequestEmailCarriesASignInLink() {
        // ส่งตอนเริ่มเวียนใน @BeforeEach — ผู้ลงนามภายนอกเป็นคนแรก
        awaitCondition("อีเมลขอให้ลงนามถึงผู้ลงนามภายนอก", () -> mail().to(guest.getEmail()).stream()
                .anyMatch(m -> m.subjectContains("ขอความอนุเคราะห์ลงนาม")));
        var ask = mail().to(guest.getEmail()).stream().filter(m -> m.subjectContains("ขอความอนุเคราะห์ลงนาม"))
                .findFirst().orElseThrow();

        assertThat(ask.body()).contains("/auth/link/").contains("ใช้ได้ครั้งเดียว")
                .doesNotContain("KKU SSO").doesNotContain("/esign/sign/" + step.getId());
    }

    @Test
    @DisplayName("หน้าลงนามและหน้าลายเซ็นของคนนอก มข. — ยืนยันทางอีเมลเป็นทางหลัก .p12 ของหน่วยงานไม่บังคับ ไม่มีคำแนะนำของ มข.")
    void outsidersSeeTheirOwnGuidance() throws Exception {
        String signPage = mvc.perform(get("/esign/sign/" + step.getId()).with(user(guest.getEmail()).roles("EXTERNAL")))
                .andReturn().getResponse().getContentAsString();
        assertThat(signPage).contains("data-email-otp-signing").contains("ownDigitalIdOption").contains("ไม่บังคับ")
                .doesNotContain("i.kku.ac.th").doesNotContain("KKU Mail");

        String mySignatures = mvc.perform(get("/esign/my-signatures").with(user(guest.getEmail()).roles("EXTERNAL")))
                .andReturn().getResponse().getContentAsString();
        assertThat(mySignatures).as("สร้างลายเซ็นได้โดยไม่ต้องติดตั้ง .p12 ก่อน")
                .contains("sealSignerNotice").contains("signatureEditor").contains("ไม่บังคับ")
                .doesNotContain("i.kku.ac.th").doesNotContain("KKU Mail");
    }

    @Test
    @DisplayName("กรอกรหัสผิดครบ 5 ครั้ง — รหัสนั้นทิ้ง รหัสที่ถูกก็ใช้ไม่ได้แล้ว ต้องขอใหม่")
    void fiveWrongCodesDiscardTheCode() {
        String otp = twoFactorService.generateOtp(guest);

        String lastError = null;
        for (int i = 0; i < TwoFactorService.SIGNING_OTP_MAX_ATTEMPTS; i++) {
            var wrong = signatureWorkflow.sign(step.getId(), guest, signature.getId(), true, ActorContext.none(),
                    null, null, null, "00000000");
            assertThat(wrong.ok()).isFalse();
            lastError = wrong.error();
        }
        assertThat(lastError).contains("ผิดครบ");

        var tooLate = signatureWorkflow.sign(step.getId(), guest, signature.getId(), true, ActorContext.none(),
                null, null, null, otp);
        assertThat(tooLate.ok()).isFalse();
    }

    @Test
    @DisplayName("กดส่งรหัสซ้ำทันที — ไม่ส่งอีเมลฉบับที่สอง")
    void aSecondCodeRequestWaitsForTheCooldown() {
        assertThat(signatureWorkflow.sendSigningOtp(step.getId(), guest)).isNull();
        assertThat(signatureWorkflow.sendSigningOtp(step.getId(), guest)).contains("เพิ่งส่งรหัสยืนยัน");
    }

    @Test
    @DisplayName("บุคลากร มข. ต่างคณะที่เป็นผู้ลงนามภายนอก — ใช้ตราระบบไม่ได้ ต้องลงนามด้วย .p12 ของ มข.")
    void aKkuColleagueMustUseAKkuDigitalId() throws Exception {
        // ขึ้นต้นด้วย "test." — บัญชีทดสอบ ระบบไม่ส่งอีเมลจริงไปโดเมน kku.ac.th แต่ยังนับเป็นบุคลากร มข.
        UserDtls colleague = externalSigners.invite(new ExternalSignerService.Invite("ผศ.", "สมศรี", "ต่างคณะ",
                "test.somsri.seal@kku.ac.th", "คณะวิศวกรรมศาสตร์ มหาวิทยาลัยขอนแก่น"), data.admin());
        data.signatureFor(colleague);
        AcademicRequest other = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        String json = "{\"applicant_name\":\"สมศรี ต่างคณะ\",\"title\":\"ขอรับการประเมินผลการสอน\","
                + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"1/2569\"}";
        SignatureRequest theirs = circulate(SignatureModule.ACADEMIC, other.getId(), 1, json, data.admin(),
                List.of(new SignerAssignment("applicant", colleague.getId())));
        SignatureStep theirStep = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(theirs.getId()).get(0);

        assertThat(signatureWorkflow.sendSigningOtp(theirStep.getId(), colleague)).contains(".p12");
        String otp = twoFactorService.generateOtp(colleague);
        var refused = signatureWorkflow.sign(theirStep.getId(), colleague, data.signatureFor(colleague).getId(), true,
                ActorContext.none(), null, null, null, otp);
        assertThat(refused.ok()).isFalse();

        String signPage = mvc.perform(get("/esign/sign/" + theirStep.getId())
                        .with(user(colleague.getEmail()).roles("EXTERNAL")))
                .andReturn().getResponse().getContentAsString();
        assertThat(signPage).doesNotContain("data-email-otp-signing").contains("i.kku.ac.th");
    }
}
