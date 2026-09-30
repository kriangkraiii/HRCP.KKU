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
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestCertificates;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * เจ้าหน้าที่พิมพ์หมายเหตุในเอกสารที่ 2 หลังผู้ยื่นลงนามแล้ว — ยาวเกินช่องในไฟล์ลงนามต้องเห็นเตือนทันที
 * ไม่ใช่ไปรู้ตอนส่งเวียน หรือแย่กว่านั้นคือให้ผู้ลงนามคนถัดไปเจอ
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:2",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("E2E: หมายเหตุยาวเกินช่องในไฟล์ลงนาม — เตือนระหว่างพิมพ์")
class LateFieldFitBrowserTest extends PlaywrightTestBase {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private StaffMemberRepository staffMembers;

    /** ลบก่อน {@code data.reset()} ของเทสถัดไป ซึ่งลบผู้ใช้ที่แถวเหล่านี้ชี้อยู่ */
    @org.junit.jupiter.api.AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    @Test
    @DisplayName("พิมพ์ยาวเกิน → ขึ้นแดงพร้อมข้อความ → ย่อแล้วหาย")
    void warnsWhileTyping() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        UserDtls applicant = data.applicant();
        UserDtls officer = data.admin();
        UserSignature signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant);
        staffMembers.deleteAll();
        StaffMember hr = new StaffMember();
        hr.setFirstName(officer.getFirstName());
        hr.setLastName(officer.getLastName());
        hr.setStaffRole("HR");
        hr.setIsActive(true);
        hr.setUser(officer);
        staffMembers.save(hr);

        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        StringBuilder json = new StringBuilder("{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"")
                .append(applicant.getName()).append('"');
        for (int i = 1; i <= 5; i++) {
            json.append(",\"chk_app_").append(i).append("\":\"✓\"");
        }
        json.append('}');
        academicService.saveDraft(request, 2, json.toString(), "เอกสารที่ 2", null);
        // อย่างที่ผู้ยื่นส่งจริง: ซองมีแต่ช่องของผู้ยื่น เจ้าหน้าที่ตรวจแล้วค่อยส่งต่อให้นักทรัพยากรบุคคล
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2,
                "เอกสารที่ 2", json.toString(),
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())),
                null, applicant, SignatureWorkflowService.ActorContext.none()).request();
        var applicantStep = steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
        assertThat(workflow.sign(applicantStep.getId(), applicant, signature.getId(), true,
                SignatureWorkflowService.ActorContext.none(), TestCertificates.PIN).ok()).isTrue();

        signIn(officer.getEmail(), TestDataFactory.PASSWORD);
        page.navigate("/admin/academic/request/" + request.getId() + "/document/2");

        Locator remark = page.locator("[name=text_1]");
        assertThat(remark.isEnabled()).as("ผู้ยื่นลงนามแล้ว เจ้าหน้าที่ต้องกรอกหมายเหตุได้").isTrue();
        Locator warning = page.locator(".late-fit-feedback");

        remark.fill("ไม่พบเอกสารแผนการสอนฉบับสมบูรณ์ในแฟ้มที่ส่งมาทั้งหมดเลยสักชุด");
        warning.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        assertThat(warning.innerText()).contains("ยาวเกินช่องในเอกสาร");
        assertThat(remark.getAttribute("class")).contains("is-invalid");

        remark.fill("ไม่เห็นเอกสาร");
        warning.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
        assertThat(remark.getAttribute("class")).doesNotContain("is-invalid");
    }
}
