package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.ExternalSignerService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.academic.service.pdf.PdfIncrementService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.TwoFactorService;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;
import com.ecom.support.TestDataFactory;

/**
 * กรรมการคนที่ 2 เป็นอนุกรรมการภายนอก: เจ้าหน้าที่เพิ่มเขาเป็นผู้ลงนามนอก มข. ในเอกสารที่ 3
 * แล้วเขาลงนามแบบประเมินผลการสอน (เอกสารที่ 7) ต่อจากประธาน โดยไม่มี Digital ID — ยืนยันทางอีเมล ระบบประทับรับรองแทน
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:7" })
@DisplayName("เอกสารที่ 7: กรรมการภายนอก (นอก มข.) ลงนามต่อจากประธาน")
class ExternalCommitteeMemberTest extends AbstractFlowTest {

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

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private UserRepository users;

    private UserDtls officer;
    private UserDtls chair;
    private UserDtls guest;
    private UserDtls member3;
    private AcademicRequest request;

    @BeforeEach
    void committeeWithAnOutsider() throws Exception {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        officer = data.admin();
        chair = data.user("chair@" + TestDataFactory.DOMAIN, "บุญมี", "ประธานกรรมการ", "ROLE_USER");
        member3 = data.user("member3@" + TestDataFactory.DOMAIN, "อรุณี", "เลขานุการ", "ROLE_USER");
        guest = externalSigners.invite(new ExternalSignerService.Invite("รศ.ดร.", "วิภา", "ภายนอก",
                "wipa.committee@example.invalid", "มหาวิทยาลัยเชียงใหม่"), officer);
        request = data.evaluation(data.applicant(), RequestStatus.MEETING_SCHEDULED);
        data.academicDocument(request, 1,
                "{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"สมชาย ทดสอบยื่น\",\"chk2\":\"✓\"}");

        // เอกสารที่ 3 ตามที่ตัวค้นหาชื่อบันทึก: คนที่ 2 คือบัญชีภายนอกที่เพิ่งเชิญ
        UserDtls[] picked = { chair, guest, member3 };
        Map<String, String> doc3 = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= 3; i++) {
            doc3.put("committee_" + i + "_name", SignerNameResolver.printedName(picked[i - 1]));
            doc3.put("committee_" + i + "_name__signer", String.valueOf(picked[i - 1].getId()));
        }
        data.academicDocument(request, 3, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(doc3));

        mvc.perform(post("/api/draft/academic/" + request.getId() + "/7")
                .with(user(officer.getEmail()).roles(officer.getRole().replace("ROLE_", ""))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sec_score_1\":\"4\",\"sec_score_2\":\"4\",\"sec_score_3\":\"4\",\"sec_score_4\":\"4\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ประธานลงนามด้วย Digital ID → กรรมการภายนอกยืนยันทางอีเมล → คนที่ 3 ลงนาม — ไฟล์มีลายมือชื่อดิจิทัลครบสามขั้น")
    void theOutsiderSignsBetweenChairAndSecretary() throws Exception {
        String chairPin = data.digitalCertificateFor(chair);
        String member3Pin = data.digitalCertificateFor(member3);
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(academicService.getLatestDocumentData(request.getId(), 7));
        SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 7, json, officer, List.of());
        officerFilledIn(SignatureModule.ACADEMIC, request.getId(), 7);
        var started = signatureWorkflow.startCirculation(envelope.getId(), officer, ActorContext.none());
        assertThat(started.ok()).as(started.error()).isTrue();

        List<SignatureStep> steps = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId());
        assertThat(steps).extracting(SignatureStep::getSlotKey)
                .containsExactly("committee_chair", "committee_member_2", "committee_member_3");
        assertThat(steps.get(1).getSigner().getId()).isEqualTo(guest.getId());

        // กรรมการภายนอกยังลงนามไม่ได้จนกว่าประธานจะลงนาม
        assertThat(steps.get(1).getStatus()).isEqualTo(SignatureStepStatus.WAITING);

        var chairSigned = signatureWorkflow.sign(steps.get(0).getId(), fresh(chair), data.signatureFor(chair).getId(),
                true, ActorContext.none(), chairPin, null);
        assertThat(chairSigned.ok()).as(chairSigned.error()).isTrue();

        // อีเมลขอให้ลงนามถึงกรรมการภายนอก: จดหมายทางการที่บอกว่าเป็นเรื่องของใคร ฐานะอะไร และลงนามอย่างไร
        String guestEmail = guest.getEmail();
        awaitCondition("อีเมลขอให้ลงนามถึงกรรมการภายนอก", () -> mail().to(guestEmail).stream()
                .anyMatch(m -> m.subjectContains("ขอความอนุเคราะห์ลงนาม")));
        var ask = mail().to(guestEmail).stream().filter(m -> m.subjectContains("ขอความอนุเคราะห์ลงนาม")).findFirst()
                .orElseThrow();
        assertThat(ask.subject()).contains("สมชาย ทดสอบยื่น");
        assertThat(ask.body()).contains("รศ.ดร.วิภา ภายนอก")
                .contains("อนุกรรมการประเมินผลการสอน (ผู้ทรงคุณวุฒิภายนอก)")
                .contains("ขั้นตอนการลงนาม")
                .contains("http").contains("/esign/sign/" + steps.get(1).getId())
                .contains("จึงเรียนมาเพื่อโปรดพิจารณาลงนาม");

        assertThat(signatureWorkflow.sendSigningOtp(steps.get(1).getId(), fresh(guest))).isNull();
        String otp = twoFactorService.generateOtp(fresh(guest));
        var guestSigned = signatureWorkflow.sign(steps.get(1).getId(), fresh(guest), data.signatureFor(guest).getId(),
                true, ActorContext.none(), null, null, null, otp);
        assertThat(guestSigned.ok()).as(guestSigned.error()).isTrue();

        var lastSigned = signatureWorkflow.sign(steps.get(2).getId(), fresh(member3),
                data.signatureFor(member3).getId(), true, ActorContext.none(), member3Pin, null);
        assertThat(lastSigned.ok()).as(lastSigned.error()).isTrue();

        assertThat(signatureWorkflow.findEnvelope(envelope.getId()).orElseThrow().getStatus())
                .isEqualTo(SignatureRequestStatus.COMPLETED);
        byte[] pdf = revisions.latest(envelope.getId());
        var checks = PdfIncrementService.verify(pdf);
        assertThat(checks).extracting(PdfIncrementService.SignatureCheck::field)
                .containsExactly("sig_committee_chair", "sig_committee_member_2", "sig_committee_member_3");
        assertThat(checks).allMatch(PdfIncrementService.SignatureCheck::valid);
        assertThat(checks.get(1).signerDn()).contains("ระบบพัฒนาบุคลากร");
        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getSignatureDictionaries().get(1).getReason())
                    .contains("wipa.committee@example.invalid").contains("ยืนยันตัวตนทางอีเมล");
        }
    }

    /** ผู้ใช้ที่โหลดใหม่ เหมือนผู้ใช้ที่ล็อกอินอยู่ — ไม่ใช่ proxy ที่หลุดจาก session */
    private UserDtls fresh(UserDtls user) {
        return users.findById(user.getId()).orElseThrow();
    }
}
