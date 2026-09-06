package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.UserDigitalCertificateService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;
import com.ecom.support.TestDataFactory;

/**
 * ด่านใบรับรอง Digital ID (.p12) ที่หน้าสุดของการลงนาม
 *
 * <p>{@code SigningController.sign} ตรวจใบรับรองเป็นอย่างแรกสุด ก่อนจะแตะ workflow
 * ใด ๆ ทั้งสิ้น — ไม่มีใบรับรอง หรือใบรับรองหมดอายุ ก็ถูกเด้งกลับพร้อมข้อความ
 *
 * <p>ด่านนี้ถูกเพิ่มเข้ามาในคอมมิต {@code Add .p12 e-signing} และ {@code Require .p12}
 * โดย <b>ไม่มีเทสใดคุมเลยแม้แต่ตัวเดียว</b> สิ่งที่เกิดขึ้นคือเทสเส้นทางเต็มเส้น
 * ({@code FullJourneyMockMvcTest}) แดงเงียบ ๆ มาตั้งแต่วันนั้น เพราะผู้ใช้ในเทส
 * ไม่มีใบรับรอง และไม่มีอะไรบอกว่านั่นคือสาเหตุ
 *
 * <p>คลาสนี้จึงคุมทั้งสี่ทางออกของด่าน: ไม่มีใบรับรอง / ใบรับรองหมดอายุ /
 * รหัสผ่านผิด / ลงนามสำเร็จ
 */
@DisplayName("ด่านใบรับรอง Digital ID (.p12) ก่อนลงนาม")
class DigitalIdGateTest extends AbstractFlowTest {

    @Autowired
    private SignatureWorkflowService signatureWorkflow;

    @Autowired
    private SignatureStepRepository signatureSteps;

    @Autowired
    private UserDigitalCertificateService certificateService;

    /** ซองลงนามเอกสารที่ 0 พร้อมช่องของผู้ยื่น รอให้เซ็น */
    private SignatureStep aStepWaitingForTheApplicant(UserDtls applicant) {
        AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);

        var created = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC,
                draft.getId(), 0, "บันทึกข้อความ ขอรับการประเมินผลการสอน",
                "{\"applicant_name\":\"" + applicant.getName() + "\"}",
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())),
                null, applicant, SignatureWorkflowService.ActorContext.none());

        assertThat(created.ok()).as("สร้างซองลงนามไม่สำเร็จ: %s", created.error()).isTrue();

        return signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(created.request().getId())
                .stream()
                .filter(s -> "applicant".equals(s.getSlotKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ซองลงนามไม่มีช่องของผู้ยื่น"));
    }

    private String signWith(SignatureStep step, UserSignature signature, String pin) throws Exception {
        return mvc.perform(post("/esign/sign/" + step.getId())
                .param("userSignatureId", String.valueOf(signature.getId()))
                .param("digitalCertPin", pin == null ? "" : pin)
                .param("consent", "true")
                .with(csrf())
                .with(user(TestDataFactory.APPLICANT_EMAIL).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    /** ระบบปฏิเสธการลงนามด้วยการเด้งกลับมาที่หน้าลงนามเดิม */
    private String pageFor(SignatureStep step) {
        return "/esign/sign/" + step.getId();
    }

    @Test
    @DisplayName("ยังไม่ได้ติดตั้งใบรับรอง — ต้องถูกปฏิเสธ และเอกสารต้องยังไม่ถูกนับว่าลงนามแล้ว")
    void withoutACertificateTheSignatureIsRefused() throws Exception {
        UserDtls applicant = data.applicant();
        UserSignature signature = data.signatureFor(applicant);
        SignatureStep step = aStepWaitingForTheApplicant(applicant);

        assertThat(signWith(step, signature, "anything"))
                .as("ไม่มีใบรับรองต้องถูกเด้งกลับมาที่หน้าลงนามเดิม")
                .isEqualTo(pageFor(step));

        assertThat(signatureSteps.findById(step.getId()).orElseThrow().getSignedAt())
                .as("ถูกปฏิเสธแล้วต้องไม่มีการบันทึกว่าลงนามเสร็จ")
                .isNull();
    }

    @Test
    @DisplayName("ใบรับรองหมดอายุ — ต้องถูกปฏิเสธตั้งแต่ตอนติดตั้ง")
    void anExpiredCertificateIsRefusedAtInstallTime() throws Exception {
        UserDtls applicant = data.applicant();

        var result = certificateService.registerCertificate(applicant,
                TestCertificates.expiredP12("อาจารย์หมดอายุ"),
                "expired.p12", TestCertificates.PIN, true);

        assertThat(result.ok())
                .as("ใบรับรองที่หมดอายุแล้วต้องติดตั้งไม่ได้ตั้งแต่แรก")
                .isFalse();
        assertThat(certificateService.findActive(applicant))
                .as("ติดตั้งไม่สำเร็จแล้วต้องไม่มีใบรับรองที่ใช้งานได้ค้างอยู่")
                .isEmpty();
    }

    @Test
    @DisplayName("รหัสผ่านใบรับรองผิด — ต้องถูกปฏิเสธ")
    void theWrongPinIsRefused() throws Exception {
        UserDtls applicant = data.applicant();
        UserSignature signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant);
        SignatureStep step = aStepWaitingForTheApplicant(applicant);

        assertThat(signWith(step, signature, "รหัสผ่านที่ไม่ถูกต้อง"))
                .as("รหัสผ่านผิดต้องถูกเด้งกลับมาที่หน้าลงนามเดิม")
                .isEqualTo(pageFor(step));

        assertThat(signatureSteps.findById(step.getId()).orElseThrow().getSignedAt())
                .as("รหัสผ่านผิดแล้วต้องไม่มีการบันทึกว่าลงนามเสร็จ")
                .isNull();
    }

    @Test
    @DisplayName("มีใบรับรองและรหัสผ่านถูก — ลงนามสำเร็จ")
    void aValidCertificateLetsTheSignatureThrough() throws Exception {
        UserDtls applicant = data.applicant();
        UserSignature signature = data.signatureFor(applicant);
        String pin = data.digitalCertificateFor(applicant);
        SignatureStep step = aStepWaitingForTheApplicant(applicant);

        assertThat(signWith(step, signature, pin))
                .as("ลงนามสำเร็จต้องไม่ถูกเด้งกลับมาที่หน้าเดิม")
                .isNotEqualTo(pageFor(step));

        assertThat(signatureSteps.findById(step.getId()).orElseThrow().getSignedAt())
                .as("ลงนามสำเร็จแล้วต้องถูกบันทึกเวลาไว้")
                .isNotNull();
    }
}
