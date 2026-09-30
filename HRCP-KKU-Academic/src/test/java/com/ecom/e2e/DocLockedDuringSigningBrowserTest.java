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
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Response;

/**
 * เอกสารที่ 2 เฟส 1 ระหว่างเวียนลงนาม บนหน้าของเจ้าหน้าที่
 *
 * <p>สคริปต์ล็อกช่อง (_admin_field_lock :: script) เคยพลาดสองทาง:
 * ปิดปุ่ม "ดูตัวอย่างเอกสาร" ไปพร้อมปุ่มอื่นในฟอร์ม (ปุ่มลอยเรียก .click() ของปุ่มที่ถูกปิดจึงเงียบ)
 * และปล่อยช่องติ๊กคอลัมน์ "เจ้าหน้าที่" ไว้ เพราะช่องติ๊กไม่มี name — เขียนค่าผ่าน data-target
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:2",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("E2E: เอกสารที่อยู่ระหว่างเวียนลงนาม — ดูตัวอย่างได้ แต่แก้ไม่ได้")
class DocLockedDuringSigningBrowserTest extends PlaywrightTestBase {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private StaffMemberRepository staffMembers;

    /** ลบก่อน {@code data.reset()} ของเทสถัดไป ซึ่งลบผู้ใช้ที่แถวเหล่านี้ชี้อยู่ */
    @org.junit.jupiter.api.AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    @Test
    @DisplayName("ปุ่มดูตัวอย่างเปิดไฟล์ที่ลงนามได้ และช่องติ๊กของเจ้าหน้าที่ถูกล็อก")
    void previewWorksAndOfficerTicksAreLocked() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        UserDtls applicant = data.applicant();
        UserDtls officer = data.admin();
        data.signatureFor(applicant);
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
            json.append(",\"chk_app_").append(i).append("\":\"✓\",\"chk_off_").append(i).append("\":\"✓\"");
        }
        json.append('}');
        academicService.saveDraft(request, 2, json.toString(), "เอกสารที่ 2", null);
        assertThat(workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 2, "เอกสารที่ 2",
                json.toString(), List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId()),
                        new SignatureWorkflowService.SignerAssignment("hr", officer.getId())),
                null, officer, SignatureWorkflowService.ActorContext.none()).ok()).isTrue();

        signIn(officer.getEmail(), TestDataFactory.PASSWORD);
        page.navigate("/admin/academic/request/" + request.getId() + "/document/2");

        assertThat((Boolean) page.evaluate("() => Array.from(document.querySelectorAll('.chk-toggle'))"
                + ".every(c => c.disabled)"))
                .as("ช่องติ๊กคอลัมน์เจ้าหน้าที่ต้องถูกล็อกระหว่างเวียน").isTrue();
        assertThat((Boolean) page.evaluate("() => document.querySelector('[data-action=docPreviewShow]').disabled"))
                .as("ปุ่มดูตัวอย่างต้องไม่ถูกปิด").isFalse();

        Response pdf = page.waitForResponse(r -> r.url().contains("/document/2/download?format=pdf"),
                () -> page.locator(".floating-doc-btn-preview").click());
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(page.locator(".docx-preview-overlay.active").isVisible()).isTrue();
    }
}
