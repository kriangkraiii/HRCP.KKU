package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureAuditEventType;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.repository.SignatureAuditEventRepository;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผู้ยื่นกลับไปแก้เอกสารของตัวเองได้ ทั้งก่อนและหลังลงนาม — จนกว่าจะส่งคำร้อง
 *
 * <p>กด "ส่งและลงนาม" แล้วฟอร์มถูกล็อกทันที ซึ่งถูกต้อง เพราะลายเซ็นรับรองเนื้อหาที่แช่แข็งไว้
 * แต่เดิมไม่มีทางถอยให้ผู้ยื่นเลย: ก่อนเซ็นมีแค่ปุ่ม "ปฏิเสธการลงนาม" ที่ออกแบบให้ผู้ลงนามคนอื่น
 * (บังคับเหตุผล ส่งอีเมลหาตัวเอง บันทึกว่าเอกสารถูกปฏิเสธ) หลังเซ็นไม่มีอะไรเลย ต้องตาม
 * เจ้าหน้าที่มาปลดให้
 *
 * <p>อีกด้านที่ต้องล็อกไว้: หลังยื่นแล้วผู้ยื่นต้องถอนเองไม่ได้ — เดิมยิง POST ตรงได้ทุกเมื่อ
 * ทั้งที่ลายเซ็นของคณบดีจะเป็นโมฆะไปด้วย
 */
@DisplayName("ผู้ยื่นถอนเพื่อกลับไปแก้เอกสารของตัวเอง")
class ApplicantWithdrawSignatureTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignatureAuditEventRepository audits;

    /** เอกสารที่ 1 ที่กรอกครบ — ต้องครบ ไม่งั้นระบบไม่ยอมสร้างซองให้ */
    private static final String DOC1 = """
            {"title":"ผศ.","applicant_name":"สมชาย ใจดี","employee_type":"ข้าราชการ",
             "current_position":"อาจารย์","chk1":"✓","chk2":"  ","course_code":"CS101",
             "course_name":"การเขียนโปรแกรม","academic_year":"1/2569"}""";

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /** ผู้ยื่นกด "ส่งและลงนาม" เอกสารที่ 1 ของเฟส 1 — ช่องลงนามมีแค่ผู้ยื่น */
    private SignatureRequest openDocumentOne(AcademicRequest request) {
        data.academicDocument(request, 1, DOC1);
        var created = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 1,
                "เอกสารที่ 1", DOC1, List.of(new SignerAssignment("applicant", applicant.getId())),
                null, applicant, ActorContext.none());
        assertThat(created.error()).isNull();
        return created.request();
    }

    private SignatureStep applicantStep(SignatureRequest envelope) {
        return signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).stream()
                .filter(s -> "applicant".equals(s.getSlotKey()))
                .findFirst().orElseThrow();
    }

    private void sign(SignatureStep step, UserDtls who) {
        var result = signatureWorkflow.sign(step.getId(), who, data.signatureFor(who).getId(), true,
                ActorContext.none(), null, null);
        assertThat(result.error()).isNull();
    }

    private SignatureRequestStatus statusOf(SignatureRequest envelope) {
        return envelopes.findById(envelope.getId()).orElseThrow().getStatus();
    }

    @Nested
    @DisplayName("ก่อนส่งคำร้อง")
    class BeforeSubmitting {

        @Test
        @DisplayName("เซ็นแล้ว: ถอนลายเซ็น แก้เอกสารได้ และต้องเซ็นใหม่ก่อนส่ง")
        void aSignedDraftCanBeWithdrawn() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            SignatureRequest envelope = openDocumentOne(request);
            sign(applicantStep(envelope), applicant);
            assertThat(statusOf(envelope))
                    .as("ผู้ยื่นเซ็นคนเดียว ซองจึงปิดทันที — เดิม cancel() ไม่รับซองที่ปิดแล้ว")
                    .isEqualTo(SignatureRequestStatus.COMPLETED);
            assertThat(academicService.canApplicantEditDocument(request, 1)).isFalse();

            String landed = mvc.perform(post("/esign/envelope/" + envelope.getId() + "/cancel")
                    .with(as(applicant)).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn().getResponse().getRedirectedUrl();

            assertThat(landed).as("กลับไปที่ฟอร์มเอกสารเพื่อแก้").contains("/document");
            assertThat(statusOf(envelope)).isEqualTo(SignatureRequestStatus.CANCELLED);
            assertThat(academicService.canApplicantEditDocument(request, 1)).isTrue();
            assertThat(signatureWorkflow.getUnsignedApplicantDocTypes(SignatureModule.ACADEMIC,
                    request.getId(), List.of(1)))
                    .as("ลายเซ็นที่ถอนแล้วใช้ส่งคำร้องไม่ได้ ต้องเซ็นใหม่")
                    .containsExactly(1);
        }

        @Test
        @DisplayName("เซ็นใหม่หลังถอนได้ตามปกติ")
        void signingAgainAfterWithdrawing() {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            SignatureRequest first = openDocumentOne(request);
            sign(applicantStep(first), applicant);
            assertThat(signatureWorkflow.withdrawByApplicant(first.getId(), applicant, ActorContext.none()).error())
                    .isNull();

            SignatureRequest second = openDocumentOne(request);
            sign(applicantStep(second), applicant);

            assertThat(signatureWorkflow.getUnsignedApplicantDocTypes(SignatureModule.ACADEMIC,
                    request.getId(), List.of(1))).isEmpty();
        }

        @Test
        @DisplayName("ยังไม่เซ็น: \"ยกเลิก กลับไปแก้ไข\" — ไม่ใช่การปฏิเสธ ไม่มีอีเมลหาตัวเอง")
        void backingOutBeforeSigningIsNotADecline() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            SignatureRequest envelope = openDocumentOne(request);
            // อีเมล "รอการลงนาม" ตอนสร้างซองเป็นเรื่องปกติ ล้างทิ้งก่อน เทสต์นี้ถามแค่ตอนถอย
            settle();
            mail().clear();

            // หน้าลงนามรุ่นเก่าที่ยังเปิดค้างอยู่ยิงมาที่ปุ่มปฏิเสธ — ช่องของผู้ยื่นต้องกลายเป็นการถอน
            mvc.perform(post("/esign/sign/" + applicantStep(envelope).getId() + "/decline")
                    .param("reason", "พิมพ์ผิด")
                    .with(as(applicant)).with(csrf()))
                    .andExpect(status().is3xxRedirection());

            assertThat(statusOf(envelope))
                    .as("บันทึกเป็นการถอน ไม่ใช่ DECLINED ที่ดูเหมือนมีคนพิจารณาแล้วไม่รับ")
                    .isEqualTo(SignatureRequestStatus.CANCELLED);
            assertThat(audits.findBySignatureRequestIdOrderByCreatedAtAsc(envelope.getId()))
                    .extracting(e -> e.getEventType())
                    .contains(SignatureAuditEventType.WITHDRAWN)
                    .doesNotContain(SignatureAuditEventType.DECLINED);
            settle();
            assertThat(mail().to(applicant.getEmail()))
                    .as("ผู้ยื่นกดเอง ไม่ต้องส่งอีเมลบอกตัวเองว่าเอกสารถูกปฏิเสธ")
                    .isEmpty();
        }

        @Test
        @DisplayName("ผู้ยื่นคนอื่นถอนแทนไม่ได้")
        void someoneElseCannotWithdraw() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            SignatureRequest envelope = openDocumentOne(request);
            sign(applicantStep(envelope), applicant);

            mvc.perform(post("/esign/envelope/" + envelope.getId() + "/cancel")
                    .with(as(data.otherApplicant())).with(csrf()))
                    .andExpect(status().is3xxRedirection());

            assertThat(statusOf(envelope)).isEqualTo(SignatureRequestStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("หลังส่งคำร้อง")
    class AfterSubmitting {

        @Test
        @DisplayName("ผู้ยื่นถอนเองไม่ได้ ต้องให้เจ้าหน้าที่ส่งกลับมาแก้")
        void cannotWithdrawOnceSubmitted() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            SignatureRequest envelope = openDocumentOne(request);
            sign(applicantStep(envelope), applicant);
            academicService.updateStatus(request.getId(), RequestStatus.RECEIVED, applicant, "ส่งคำร้อง", false);

            mvc.perform(post("/esign/envelope/" + envelope.getId() + "/cancel")
                    .with(as(applicant)).with(csrf()))
                    .andExpect(status().is3xxRedirection());

            assertThat(statusOf(envelope)).isEqualTo(SignatureRequestStatus.COMPLETED);
        }

        @Test
        @DisplayName("ช่องโหว่เดิม: ยิง POST ยกเลิกซองที่คณบดีกำลังเวียนอยู่ต้องไม่ผ่าน")
        void cannotVoidOthersSignaturesAfterSubmitting() throws Exception {
            UserDtls dean = data.user("dean@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
            PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
            data.positionDocument(request, 3, "{\"applicant_name\":\"สมชาย ใจดี\"}");
            var created = signatureWorkflow.createEnvelope(SignatureModule.POSITION, request.getId(), 3,
                    "เอกสารที่ 3", "{\"applicant_name\":\"สมชาย ใจดี\"}",
                    List.of(new SignerAssignment("applicant", applicant.getId()),
                            new SignerAssignment("dean", dean.getId())),
                    null, applicant, ActorContext.none());
            assertThat(created.error()).isNull();
            SignatureRequest envelope = created.request();
            sign(applicantStep(envelope), applicant);
            positionService.updateStatus(request.getId(), PositionRequestStatus.DOCUMENT_RECEIVED,
                    applicant, "ส่งคำร้อง", false);
            signatureWorkflow.startCirculation(envelope.getId(), officer, ActorContext.none());
            assertThat(statusOf(envelope)).isEqualTo(SignatureRequestStatus.IN_PROGRESS);

            mvc.perform(post("/esign/envelope/" + envelope.getId() + "/cancel")
                    .with(as(applicant)).with(csrf()))
                    .andExpect(status().is3xxRedirection());

            assertThat(statusOf(envelope))
                    .as("ปุ่มแค่ซ่อนจากผู้ยื่น เดิมฝั่งเซิร์ฟเวอร์ปล่อยผ่าน")
                    .isEqualTo(SignatureRequestStatus.IN_PROGRESS);
        }
    }

    @Test
    @DisplayName("มีคนอื่นลงนามไปแล้ว ถอนไม่ได้แม้คำร้องยังเป็นร่าง")
    void cannotWithdrawOnceSomeoneElseHasSigned() {
        UserDtls dean = data.user("dean@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        data.positionDocument(request, 3, "{\"applicant_name\":\"สมชาย ใจดี\"}");
        SignatureRequest envelope = circulate(SignatureModule.POSITION, request.getId(), 3,
                "{\"applicant_name\":\"สมชาย ใจดี\"}", applicant,
                List.of(new SignerAssignment("applicant", applicant.getId()),
                        new SignerAssignment("dean", dean.getId())));
        signEveryStep(envelope, officer);

        assertThat(signatureWorkflow.withdrawByApplicant(envelope.getId(), applicant, ActorContext.none()).error())
                .contains("ผู้ลงนามท่านอื่น");
        assertThat(statusOf(envelope)).isEqualTo(SignatureRequestStatus.COMPLETED);
    }

    @Nested
    @DisplayName("หน้าจอ")
    class Screens {

        @Test
        @DisplayName("หน้าลงนามของผู้ยื่น: มีปุ่มกลับไปแก้ ไม่มีฟอร์มปฏิเสธ")
        void signPageOffersABackOutNotADecline() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            SignatureRequest envelope = openDocumentOne(request);

            String page = mvc.perform(get("/esign/sign/" + applicantStep(envelope).getId()).with(as(applicant)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // ผู้ยื่นในเทสต์ไม่มีใบรับรอง .p12 โดยตั้งใจ — ต้องถอยได้แม้เซ็นไม่ได้
            assertThat(page).contains("ยกเลิก กลับไปแก้ไข").doesNotContain("/decline");
        }

        @Test
        @DisplayName("ฟอร์มเอกสารของผู้ยื่นที่เซ็นแล้ว: มีปุ่มถอน — หลังส่งคำร้องไม่มี")
        void documentFormOffersWithdrawOnlyBeforeSubmitting() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
            SignatureRequest envelope = openDocumentOne(request);
            sign(applicantStep(envelope), applicant);
            String url = "/user/academic/request/" + request.getId() + "/document/1";

            assertThat(mvc.perform(get(url).with(as(applicant))).andReturn().getResponse().getContentAsString())
                    .contains("ถอนลายเซ็นเพื่อแก้ไข");

            academicService.updateStatus(request.getId(), RequestStatus.RECEIVED, applicant, "ส่งคำร้อง", false);
            assertThat(mvc.perform(get(url).with(as(applicant))).andReturn().getResponse().getContentAsString())
                    .doesNotContain("ถอนลายเซ็นเพื่อแก้ไข");
        }
    }
}
