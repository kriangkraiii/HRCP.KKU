package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PdfMode;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignedPdfRevision;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignedDocumentRenderer;
import com.ecom.academic.service.pdf.BasePdfBuilder;
import com.ecom.academic.service.pdf.IncrementalSigningService;
import com.ecom.academic.service.pdf.PdfIncrementService;
import com.ecom.academic.service.pdf.SignedPdfRevisionService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestCertificates;

/**
 * เส้นทางจริงผ่านหน้าเว็บ: ส่งเวียน → ผู้ยื่นลงนามด้วย .p12 (ต้องพิมพ์รหัส ไม่ใช้รหัสที่บันทึกไว้)
 * → เจ้าหน้าที่ออกเลขที่หนังสือพร้อมลงนามปิดไฟล์ด้วย .p12 ของตัวเอง
 */
@TestPropertySource(properties = { "app.esign.pdf-mode=incremental", "app.esign.incremental-docs=ACADEMIC:1",
        // runner has no local test properties; the encryption test needs a master key
        "app.esign.p12-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@DisplayName("ลงนามแบบใส่ทับผ่านหน้าเว็บ — ลงนาม ออกเลข ปิดเอกสาร")
class IncrementalSigningFlowTest extends AbstractFlowTest {

    private static final String MEMO = "อว 660301.26.8/1234";
    private static final String DATE = "1 ตุลาคม 2569";

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignedPdfRevisionService revisions;

    @Autowired
    private SignedDocumentRenderer renderer;

    private UserDtls applicant;
    private UserDtls officer;
    private UserSignature signature;
    private AcademicRequest request;
    private SignatureRequest envelope;
    private SignatureStep step;

    @BeforeEach
    void circulateDocumentOne() {
        assumeTrue(new DocumentGenerationService().isPdfConversionAvailable(), "LibreOffice is not installed");
        applicant = data.applicant();
        officer = data.admin();
        signature = data.signatureFor(applicant);
        data.digitalCertificateFor(applicant); // saved PIN too — it must not be used here
        data.digitalCertificateFor(officer);
        request = data.evaluation(applicant, RequestStatus.RECEIVED);
        String json = "{\"applicant_name\":\"" + applicant.getName() + "\",\"title\":\"ขอรับการประเมินผลการสอน\","
                + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\",\"academic_year\":\"1/2569\","
                + "\"memo_no\":\"อว 660301.26.8/\",\"date\":\"\"}";
        academicService.saveDraft(request, 1, json, "เอกสารที่ 1", null);
        envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 1, json, applicant,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", applicant.getId())));
        step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private String sign(String pin) throws Exception {
        return mvc.perform(post("/esign/sign/" + step.getId())
                .param("userSignatureId", String.valueOf(signature.getId()))
                .param("digitalCertPin", pin)
                .param("consent", "true")
                .with(csrf()).with(as(applicant)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    private String issue(String pin) throws Exception {
        MockHttpServletRequestBuilder req = post("/admin/academic/request/" + request.getId() + "/document/1")
                .with(as(officer)).with(csrf())
                .param("action", "submit")
                .param("memo_no", MEMO)
                .param("date", DATE);
        if (pin != null) {
            req.param("officeCertPin", pin);
        }
        return mvc.perform(req).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
    }

    private SignatureRequest reload() {
        return envelopes.findById(envelope.getId()).orElseThrow();
    }

    private List<SignedPdfRevision.Kind> kinds() {
        return revisions.chain(envelope.getId()).stream().map(SignedPdfRevision::getKind).toList();
    }

    @Test
    @DisplayName("ส่งเวียนแล้วได้ไฟล์ตั้งต้นทันที")
    void circulatingPreparesTheBase() {
        assertThat(reload().getPdfMode()).isEqualTo(PdfMode.INCREMENTAL);
        assertThat(kinds()).containsExactly(SignedPdfRevision.Kind.BASE);
    }

    @Test
    @DisplayName("ไม่พิมพ์รหัสผ่าน — ปฏิเสธ แม้จะมีรหัสที่บันทึกไว้ และไม่มีอะไรถูกบันทึก")
    void aSavedPinIsNotEnough() throws Exception {
        assertThat(sign("")).isEqualTo("/esign/sign/" + step.getId());
        assertThat(signatureSteps.findById(step.getId()).orElseThrow().getSignedAt()).isNull();
        assertThat(kinds()).containsExactly(SignedPdfRevision.Kind.BASE);
    }

    @Test
    @DisplayName("ลงนาม → ออกเลขโดยไม่ใส่รหัส (ปฏิเสธ) → ออกเลขพร้อมรหัส: ไฟล์ปิดด้วยลายเซ็นสองอัน")
    void signIssueAndClose() throws Exception {
        assertThat(sign(TestCertificates.PIN)).isNotEqualTo("/esign/sign/" + step.getId());
        SignatureStep signed = signatureSteps.findById(step.getId()).orElseThrow();
        assertThat(signed.getSignedAt()).isNotNull();
        assertThat(signed.getPdfRevisionNo()).isEqualTo(1);
        assertThat(kinds()).containsExactly(SignedPdfRevision.Kind.BASE, SignedPdfRevision.Kind.SIGN);

        // ไม่มีรหัส: ไม่บันทึกเลขที่ ไม่ปิดไฟล์
        assertThat(issue(null)).contains("error=office_sign");
        assertThat(academicService.isOfficeIssued(request.getId(), 1)).isFalse();
        assertThat(kinds()).hasSize(2);

        assertThat(issue(TestCertificates.PIN)).contains("saved=office");
        assertThat(academicService.isOfficeIssued(request.getId(), 1)).isTrue();
        assertThat(kinds()).containsExactly(SignedPdfRevision.Kind.BASE, SignedPdfRevision.Kind.SIGN,
                SignedPdfRevision.Kind.FILL, SignedPdfRevision.Kind.LOCK);
        assertThat(reload().getPdfLockedAt()).isNotNull();

        byte[] pdf = revisions.latest(envelope.getId());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "incremental"));
        java.nio.file.Files.write(java.nio.file.Path.of("target", "incremental", "doc1-web-flow-issued.pdf"), pdf);
        assertThat(PdfIncrementService.verify(pdf)).extracting(PdfIncrementService.SignatureCheck::field)
                .containsExactly("sig_applicant", BasePdfBuilder.LOCK_FIELD);
        assertThat(PdfIncrementService.verify(pdf)).allMatch(PdfIncrementService.SignatureCheck::valid);
        assertThat(IncrementalSigningService.values(pdf)).containsEntry("memo_no", MEMO).containsEntry("date", DATE);
        assertThat(renderer.renderForDownload(reload(), "pdf")).as("ดาวน์โหลดต้องได้ไฟล์ที่ลงนามจริง").isEqualTo(pdf);

        // รหัสผ่านต้องไม่หลุดลงเนื้อเอกสาร
        assertThat(academicService.getLatestDocumentData(request.getId(), 1)).doesNotContainKey("officeCertPin")
                .doesNotContainValue(TestCertificates.PIN);
    }

    @Autowired
    private com.ecom.academic.service.DigitalCertificateStorage certificateStorage;

    @Autowired
    private com.ecom.academic.service.UserDigitalCertificateService certificateService;

    @Test
    @DisplayName("ไฟล์ .p12 ที่เก็บไว้ถูกเข้ารหัส และยังใช้ลงนามได้")
    void storedCertificatesAreEncrypted() throws Exception {
        String path = certificateService.findActive(applicant).orElseThrow().getCertificatePath();
        assertThat(com.ecom.academic.service.CertificatePinEncryptionService
                .isEncryptedFile(certificateStorage.readStored(path))).isTrue();
        assertThat(sign(TestCertificates.PIN)).isNotEqualTo("/esign/sign/" + step.getId());
    }

    @Test
    @DisplayName("ตัวอย่างตอนลงนามคือไฟล์จริงบวกลายเซ็นที่จะลง — ไม่ถูกบันทึกเป็น revision")
    void previewIsTheRealFile() throws Exception {
        byte[] current = revisions.latest(envelope.getId());
        byte[] preview = mvc.perform(get("/esign/sign/" + step.getId() + "/preview")
                .param("userSignatureId", String.valueOf(signature.getId())).with(as(applicant)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(preview.length).isGreaterThan(current.length);
        assertThat(java.util.Arrays.equals(current, 0, current.length, preview, 0, current.length))
                .as("ตัวอย่างต้องต่อท้ายไฟล์จริง ไม่ใช่เรนเดอร์ใหม่").isTrue();
        assertThat(kinds()).containsExactly(SignedPdfRevision.Kind.BASE);
    }

    @Test
    @DisplayName("ไฟล์ Word ที่ดาวน์โหลดไม่มีรูปลายเซ็น")
    void wordCopyHasNoSignature() throws Exception {
        sign(TestCertificates.PIN);
        var env = reload();
        assertThat(mediaCount(renderer.renderDocx(env))).isGreaterThan(mediaCount(renderer.renderUnsignedDocx(env)));
        assertThat(mediaCount(renderer.renderForDownload(env, "docx")))
                .isEqualTo(mediaCount(renderer.renderUnsignedDocx(env)));
    }

    private static int mediaCount(byte[] docx) throws java.io.IOException {
        int n = 0;
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docx))) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().startsWith("word/media/")) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    @DisplayName("ระหว่างเวียน: ปุ่มส่งกลับกับปุ่มยกเลิกการเวียนมีคำอธิบาย (i) บอกว่าต่างกันอย่างไร")
    void sendBackAndCancelExplainThemselves() throws Exception {
        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/1").with(as(officer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("ส่งกลับให้แก้ไขและลงนามใหม่").contains("ยกเลิกการเวียนลงนาม")
                .contains("ใช้เมื่อส่วนของผู้ยื่นต้องแก้")
                .contains("ใช้เมื่อส่วนของเจ้าหน้าที่ต้องแก้");
    }

    @Test
    @DisplayName("หน้าออกเลขแสดงช่องรหัสผ่าน Digital ID ของเจ้าหน้าที่")
    void theIssueFormAsksForThePin() throws Exception {
        sign(TestCertificates.PIN);
        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/1").with(as(officer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("name=\"officeCertPin\"");
        // กดบันทึกก่อนแล้วค่อยกรอกรหัสผ่าน: ช่องรหัสผ่านอยู่ในกล่องยืนยัน ไม่ได้วางข้างปุ่ม
        assertThat(html).containsOnlyOnce("id=\"officeIssueModal\"");
        assertThat(html.indexOf("name=\"officeCertPin\"")).isGreaterThan(html.indexOf("id=\"officeIssueModal\""));
        // ช่องรหัสผ่านอยู่ในฟอร์มและไม่ใช่ช่องสารบรรณ — สคริปต์ล็อกช่องต้องข้ามมัน ไม่งั้นถูกปิดจนพิมพ์ไม่ได้
        assertThat(html).containsPattern("<input[^>]*name=\"officeCertPin\"[^>]*data-office-pin");
        assertThat(html).contains("el.hasAttribute('data-office-pin')");
    }
}
