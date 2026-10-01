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
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Request;

/** ติ๊กคำตอบบนหน้าลงนามแล้ว ตัวอย่างเอกสารโหลดใหม่พร้อมคำตอบนั้นทันที */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:3",
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("E2E: หน้าลงนาม — ติ๊กแล้วตัวอย่างเอกสารอัปเดตทันที")
class SigningPreviewChoiceBrowserTest extends PlaywrightTestBase {

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private com.ecom.academic.repository.StaffMemberRepository staffMembers;

    /** ทะเบียนบุคลากรที่ผูกบัญชีแล้ว — คืนชื่อแสดงที่ใช้กรอกในแบบฟอร์ม */
    private String staff(UserDtls user, String role) {
        var s = new com.ecom.academic.model.StaffMember();
        s.setFirstName(user.getFirstName());
        s.setLastName(user.getLastName());
        s.setStaffRole(role);
        s.setIsActive(true);
        s.setUser(user);
        return staffMembers.save(s).getDisplayName();
    }

    /** ลบก่อน {@code data.reset()} ของเทสถัดไป ซึ่งลบผู้ใช้ที่แถวเหล่านี้ชี้อยู่ */
    @org.junit.jupiter.api.AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    @Test
    @DisplayName("ติ๊ก 'เห็นควร' → โหลดตัวอย่างใหม่พร้อม choice")
    void tickingReloadsThePreviewWithTheAnswer() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        UserDtls officer = data.admin();
        UserDtls head = data.user("head@" + TestDataFactory.DOMAIN, "สมศักดิ์", "หัวหน้าสาขา", "ROLE_USER");
        UserDtls assocDean = data.user("assoc@" + TestDataFactory.DOMAIN, "วิชัย", "รองคณบดี", "ROLE_USER");
        UserDtls dean = data.user("dean@" + TestDataFactory.DOMAIN, "ประสิทธิ์", "คณบดี", "ROLE_USER");
        data.signatureFor(head);
        data.digitalCertificateFor(head); // ฟอร์มลงนาม (รวมคำถาม) ขึ้นเมื่อมี Digital ID ที่ใช้ได้
        AcademicRequest request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        // เอกสารที่ 3: ผู้ลงนามคือคนที่ชื่ออยู่ในแบบฟอร์ม (SignatureAnchorRegistry.isSignerNamedInForm)
        String names = "{\"department_head\":\"" + staff(head, "HEAD") + "\",\"associate_dean_name\":\""
                + staff(assocDean, "DEAN") + "\",\"dean_name\":\"" + staff(dean, "DEAN") + "\",\"hr_staff_name\":\""
                + staff(officer, "HR") + "\"}";
        SignatureRequest envelope = workflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 3, "เอกสารที่ 3",
                names, List.of(new SignerAssignment("head", head.getId()),
                        new SignerAssignment("associate_dean", assocDean.getId()),
                        new SignerAssignment("dean", dean.getId()),
                        new SignerAssignment("hr", officer.getId())),
                null, officer, ActorContext.none()).request();
        assertThat(workflow.startCirculation(envelope.getId(), officer, ActorContext.none()).ok()).isTrue();
        Long headStep = steps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> "head".equals(s.getSlotKey())).findFirst().orElseThrow().getId();

        signIn(head.getEmail(), TestDataFactory.PASSWORD);
        // ตัวอย่างฉบับแรก (ยังไม่ติ๊ก) โหลดเสร็จก่อน แล้วค่อยติ๊ก
        page.waitForResponse(r -> r.url().contains("/preview") && "GET".equals(r.request().method()),
                () -> page.navigate("/esign/sign/" + headStep));

        Request reload = page.waitForRequest(r -> r.url().contains("/preview?") && r.url().contains("choice="),
                () -> page.locator("label[for='signerChoice_0']").click());
        assertThat(java.net.URLDecoder.decode(reload.url(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("choice=" + com.ecom.academic.service.SignatureAnchorRegistry.APPROVED);
    }
}
