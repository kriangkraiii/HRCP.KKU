package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
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
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.ExternalSignerService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.RecordingMailSender;
import com.ecom.support.TestCertificates;
import com.microsoft.playwright.Locator;

/**
 * ผู้ลงนามจากนอก มข. ลงนามได้ตั้งแต่ต้นจนจบบนเบราว์เซอร์จริง โดยไม่มีบัญชี KKU SSO และไม่มี .p12:
 * อีเมลขอให้ลงนาม → ลิงก์เข้าสู่ระบบ → ขอรหัสทางอีเมล → ลงนาม → ไฟล์มีตราระบบที่ระบุอีเมลผู้ลงนาม
 * (docs/PLAN-external-magic-link.md)
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:1",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("E2E: ผู้ลงนามนอก มข. — ลิงก์ในอีเมล → ยืนยันทางอีเมล → ลงนามด้วยตราระบบ")
class ExternalSignerMagicLinkBrowserTest extends PlaywrightTestBase {

    private static final Pattern LINK = Pattern.compile("/auth/link/([A-Za-z0-9_-]{20,})");
    private static final Pattern CODE = Pattern.compile(">(\\d{8})<");

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
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private SignedPdfRevisionService revisions;

    @Test
    @DisplayName("ไม่มี SSO ไม่มี .p12 — ลงนามได้ครบ และไฟล์ระบุอีเมลของผู้ลงนาม")
    void anOutsiderSignsWithNothingButTheirEmail() throws Exception {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        UserDtls officer = data.admin();
        UserDtls guest = externalSigners.invite(new ExternalSignerService.Invite("รศ.ดร.", "วิภา", "ภายนอก",
                "wipa.e2e@partner.example.invalid", "มหาวิทยาลัยเชียงใหม่"), officer);
        data.signatureFor(guest);
        AcademicRequest request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        String json = "{\"applicant_name\":\"วิภา ภายนอก\",\"title\":\"ขอรับการประเมินผลการสอน\","
                + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"1/2569\"}";
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "เอกสารที่ 1", json, List.of(new SignerAssignment("applicant", guest.getId())), null, officer,
                ActorContext.none()).request();
        SignatureStep step = steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);

        // 1. อีเมลขอให้ลงนาม — ปุ่มเป็นลิงก์เข้าสู่ระบบ
        String token = awaitInMail(guest.getEmail(), "ขอความอนุเคราะห์ลงนาม", LINK);

        // 2. เปิดลิงก์ แล้วกดเข้าสู่ระบบ — ไปหน้าลงนามของขั้นนี้เลย ไม่ผ่าน /signin
        page.navigate("/auth/link/" + token);
        page.locator("#linkSignInBtn").click();
        page.waitForURL("**/esign/sign/" + step.getId());

        Locator otpCard = page.locator("[data-email-otp-signing]");
        assertThat(otpCard.isVisible()).as("ไม่มี .p12 — ต้องเห็นการ์ดยืนยันทางอีเมล").isTrue();
        assertThat(page.locator("#ownDigitalIdOption").isVisible()).isTrue();
        assertThat(page.content()).doesNotContain("i.kku.ac.th");

        // 3. ขอรหัสทางอีเมล — ปุ่มนี้ต้องส่งรหัส ไม่ใช่เปิดหน้าต่างยืนยันการลงนาม
        mail().clear();
        otpCard.locator("button[type='submit']").click();
        page.waitForLoadState();
        assertThat(page.locator("#signConfirmModal.show").count()).as("ปุ่มส่งรหัสเปิดหน้าต่างลงนามแทน").isZero();
        String code = awaitInMail(guest.getEmail(), "OTP", CODE);

        // 4. กรอกรหัส แล้วลงนาม
        Locator signature = page.locator("input[name='userSignatureId']");
        if (signature.count() > 0 && page.locator("input[name='userSignatureId']:checked").count() == 0) {
            page.locator("label[for='" + signature.first().getAttribute("id") + "']").click();
        }
        page.locator("#emailOtp").fill(code);
        page.locator("#signSubmitBtn").click();
        page.locator("#signConfirmModal.show #signConfirmBtn").click();
        page.waitForURL(url -> !url.contains("/esign/sign/" + step.getId()));

        // 5. ลงนามแล้ว ไฟล์มีตราระบบ เหตุผลระบุอีเมลของผู้ลงนาม
        assertThat(steps.findById(step.getId()).orElseThrow().getStatus()).isEqualTo(SignatureStepStatus.SIGNED);
        try (var doc = Loader.loadPDF(revisions.latest(envelope.getId()))) {
            assertThat(doc.getSignatureDictionaries()).hasSize(1);
            assertThat(doc.getSignatureDictionaries().get(0).getReason())
                    .contains("wipa.e2e@partner.example.invalid").contains("ยืนยันตัวตนทางอีเมล");
        }
    }

    /** รออีเมลถึงผู้รับที่หัวเรื่องมีคำนี้ แล้วดึงค่ากลุ่มแรกของ pattern ออกจากเนื้ออีเมล */
    private String awaitInMail(String to, String subjectPart, Pattern pattern) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            for (RecordingMailSender.Sent sent : mail().to(to)) {
                if (sent.subjectContains(subjectPart) && sent.body() != null) {
                    Matcher m = pattern.matcher(sent.body());
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
            page.waitForTimeout(100);
        }
        throw new AssertionError("ไม่มีอีเมล \"" + subjectPart + "\" ถึง " + to + " (จับได้ " + mail().count() + " ฉบับ)");
    }
}
