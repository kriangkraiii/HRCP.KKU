package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignedPdfRevision;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestCertificates;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;

/**
 * ขั้นออกเลขที่หนังสือของเอกสารลงนามแบบใส่ทับ: กดบันทึกก่อน แล้วค่อยกรอกรหัสผ่าน Digital ID
 * ในกล่องยืนยัน
 *
 * <p>เดิมช่องรหัสผ่านวางข้างปุ่ม และสคริปต์ล็อกช่องของเอกสารที่ลงนามแล้วปิดมันไปด้วย
 * เจ้าหน้าที่จึงพิมพ์รหัสไม่ได้และออกเลขไม่ได้เลย — เทส MockMvc เห็นแค่ HTML จับไม่ได้
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:1",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("E2E: ออกเลขที่หนังสือ — กดบันทึกแล้วกรอกรหัสผ่าน Digital ID ในกล่องยืนยัน")
class OfficeIssueDialogBrowserTest extends PlaywrightTestBase {

    private static final String MEMO = "อว 660301.26.8/1234";

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private SignedPdfRevisionService revisions;

    @Test
    @DisplayName("ไม่มีช่องรหัสผ่านข้างปุ่ม → กดบันทึก → กล่องถามรหัส → ไม่กรอกถูกกั้น → กรอกแล้วออกเลขและปิดไฟล์")
    void saveThenEnterTheDigitalIdPassword() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        UserDtls applicant = data.applicant();
        UserDtls officer = data.admin();
        UserSignature signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant);
        data.digitalCertificateFor(officer);
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        String json = "{\"applicant_name\":\"" + applicant.getName() + "\",\"title\":\"ขอรับการประเมินผลการสอน\","
                + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"1/2569\","
                + "\"memo_no\":\"อว 660301.26.8/\",\"date\":\"\"}";
        academicService.saveDraft(request, 1, json, "เอกสารที่ 1", null);
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "เอกสารที่ 1", json, List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())),
                null, applicant, SignatureWorkflowService.ActorContext.none()).request();
        SignatureStep step = steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
        assertThat(workflow.sign(step.getId(), applicant, signature.getId(), true,
                SignatureWorkflowService.ActorContext.none(), TestCertificates.PIN).ok()).isTrue();

        signIn(officer.getEmail(), TestDataFactory.PASSWORD);
        page.navigate("/admin/academic/request/" + request.getId() + "/document/1");

        Locator pin = page.locator("input[name=officeCertPin]");
        assertThat(pin.isVisible()).as("ช่องรหัสผ่านต้องไม่อยู่ข้างปุ่มแล้ว").isFalse();

        page.locator("[name=memo_no]").fill(MEMO);
        page.locator("[name=date]").fill("1 ตุลาคม 2569");
        page.locator("#btnSubmitDoc").click();

        Locator modal = page.locator("#officeIssueModal");
        modal.waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        page.waitForFunction("() => document.activeElement && document.activeElement.name === 'officeCertPin'");
        assertThat(pin.isEnabled()).as("ต้องพิมพ์รหัสผ่านได้").isTrue();

        // ไม่กรอกรหัส: กั้นไว้ในกล่อง ไม่ส่งฟอร์ม
        modal.locator("[data-office-issue-confirm]").click();
        assertThat(pin.getAttribute("class")).contains("is-invalid");
        assertThat(academicService.isOfficeIssued(request.getId(), 1)).isFalse();

        pin.fill(TestCertificates.PIN);
        // Enter ยืนยันแทนการคลิก — ต้องผ่านกล่อง ไม่ใช่ให้เบราว์เซอร์ส่งฟอร์มเองโดยข้ามการตรวจ
        pin.press("Enter");
        page.waitForURL("**saved=office**");

        assertThat(academicService.isOfficeIssued(request.getId(), 1)).isTrue();
        assertThat(revisions.chain(envelope.getId())).extracting(SignedPdfRevision::getKind)
                .endsWith(SignedPdfRevision.Kind.LOCK);
    }
}
