package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.pdf.IncrementalSigningService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * ตัวอย่างเอกสารบนหน้าลงนามต้องแสดงคำตอบที่ผู้ลงนามติ๊กทันที ไม่ใช่โผล่หลังลงนามแล้ว
 * (หัวหน้าสาขาในเอกสารที่ 3: "( ✓ ) เห็นควรแต่งตั้งคณะอนุกรรมการประเมิน")
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:3" })
@DisplayName("ตัวอย่างบนหน้าลงนาม — วาดคำตอบที่ติ๊กไว้ก่อนลงนาม")
class SigningPreviewChoiceTest extends AbstractFlowTest {

    private UserDtls head;
    private SignatureStep headStep;

    @BeforeEach
    void headIsUp() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        UserDtls officer = data.admin();
        head = data.user("head@" + TestDataFactory.DOMAIN, "สมศักดิ์", "หัวหน้าสาขา", "ROLE_USER");
        UserDtls assocDean = data.user("assoc@" + TestDataFactory.DOMAIN, "วิชัย", "รองคณบดี", "ROLE_USER");
        UserDtls dean = data.user("dean@" + TestDataFactory.DOMAIN, "ประสิทธิ์", "คณบดี", "ROLE_USER");
        AcademicRequest request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        // เอกสารที่ 3: ผู้ลงนามคือคนที่ชื่ออยู่ในแบบฟอร์ม (SignatureAnchorRegistry.isSignerNamedInForm)
        String names = "{\"department_head\":\"" + staff(head, "HEAD") + "\",\"associate_dean_name\":\""
                + staff(assocDean, "DEAN") + "\",\"dean_name\":\"" + staff(dean, "DEAN") + "\",\"hr_staff_name\":\""
                + staff(officer, "HR") + "\"}";
        SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 3, names, officer, List.of(
                new SignerAssignment("head", head.getId()),
                new SignerAssignment("associate_dean", assocDean.getId()),
                new SignerAssignment("dean", dean.getId()),
                new SignerAssignment("hr", officer.getId())));
        assertThat(signatureWorkflow.startCirculation(envelope.getId(), officer, ActorContext.none()).ok()).isTrue();
        headStep = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> "head".equals(s.getSlotKey())).findFirst().orElseThrow();
    }

    @org.springframework.beans.factory.annotation.Autowired
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

    @org.junit.jupiter.api.AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    private MockHttpServletRequestBuilder preview() {
        return get("/esign/sign/" + headStep.getId() + "/preview").with(user(head.getEmail()).roles("USER"));
    }

    @Test
    @DisplayName("ติ๊ก 'เห็นควร' — ตัวอย่างมี ✓ ในวงเล็บแล้ว ยังไม่ลงนามและไม่บันทึกอะไร")
    void theTickIsDrawnBeforeSigning() throws Exception {
        byte[] withChoice = mvc.perform(preview().param("choice", SignatureAnchorRegistry.APPROVED))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] without = mvc.perform(preview()).andReturn().getResponse().getContentAsByteArray();

        assertThat(IncrementalSigningService.values(withChoice)).containsEntry("chk_cs_head2", SignatureAnchorRegistry.TICK);
        assertThat(IncrementalSigningService.values(without).getOrDefault("chk_cs_head2", "")).isBlank();
        assertThat(signatureSteps.findById(headStep.getId()).orElseThrow().getSignerChoiceValue())
                .as("ตัวอย่างไม่บันทึกคำตอบ").isNull();
    }

    @Test
    @DisplayName("คำตอบที่ไม่ใช่ตัวเลือกของช่องนี้ — ปฏิเสธ")
    void anAnswerThatIsNotAnOptionIsRefused() throws Exception {
        assertThat(mvc.perform(preview().param("choice", "อนุมัติทุกอย่าง")).andReturn().getResponse().getStatus())
                .isEqualTo(400);
    }
}
