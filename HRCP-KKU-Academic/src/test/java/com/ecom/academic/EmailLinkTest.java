package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.ecom.academic.dto.SignatureNotice;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.AcademicEmailService;
import com.ecom.academic.service.SignatureNotifier;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.RecordingMailSender;
import com.ecom.support.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ปุ่มในอีเมลต้องพาผู้รับไปถึงหน้าที่ปุ่มบอกจริง
 *
 * <p>แต่ละกรณีส่งอีเมลด้วยเส้นทางจริงของระบบ แกะลิงก์ออกจากเนื้ออีเมล แล้วเปิดลิงก์นั้นในฐานะผู้รับ
 * ผ่าน security filter เต็มชุด — ลิงก์ที่สะกดถูกแต่ผู้รับไม่มีสิทธิ์เปิด (เช่น ผู้ยื่นได้ลิงก์ {@code /admin/**})
 * ก็ถือว่าพังเหมือนลิงก์ 404
 */
@DisplayName("ปุ่มในอีเมล: กดแล้วไปถึงหน้าที่ถูกต้อง")
class EmailLinkTest extends AbstractFlowTest {

    private static final Pattern HREF = Pattern.compile("href='([^']+)'");
    /** ลิงก์เว็บไซต์วิทยาลัยท้ายจดหมาย ไม่ใช่ปุ่มของระบบ */
    private static final String FOOTER_SITE = "https://computing.kku.ac.th";

    @Autowired
    private AcademicEmailService academicEmails;

    @Autowired
    private SignatureNotifier signatureNotifier;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void people() {
        applicant = data.applicant();
        officer = data.admin();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("ขอความอนุเคราะห์ลงนาม → หน้าลงนามของผู้รับ")
    void signingRequestOpensTheSigningPage() throws Exception {
        UserDtls dean = data.user("dean@" + TestDataFactory.DOMAIN, "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
        AcademicRequest request = data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);
        data.academicDocument(request, 3, data.appointmentOrder());
        data.academicDocument(request, 4, data.appointmentOrder());
        String json = new ObjectMapper().writeValueAsString(Map.of("memo_no", "อว 660301.26.4/ว.1"));
        data.academicDocument(request, 5, json);

        SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 5, json, officer,
                List.of(new SignerAssignment("dean", dean.getId())));
        officerFilledIn(SignatureModule.ACADEMIC, request.getId(), 5);
        var started = signatureWorkflow.startCirculation(envelope.getId(), officer, ActorContext.none());
        assertThat(started.ok()).as(started.error()).isTrue();

        SignatureStep step = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(envelope.getId()).get(0);
        String link = onlyButtonIn(awaitMail(dean, "ขอความอนุเคราะห์ลงนาม"));

        assertThat(link).isEqualTo("/esign/sign/" + step.getId());
        assertOpensFor(dean, link);
    }

    @Test
    @DisplayName("ลงนามครบ (ผู้ยื่นเป็นผู้ส่งเวียน) → หน้าคำร้องฝั่งผู้ยื่น ไม่ใช่หน้าแอดมิน")
    void completedNoticeToAnApplicantOpensTheirOwnRequest() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);

        signatureNotifier.notifyCompleted(notice(SignatureModule.ACADEMIC, request.getId(), applicant));

        String link = onlyButtonIn(awaitMail(applicant, "ลงนามครบแล้ว"));
        assertThat(link).isEqualTo("/user/academic/request/" + request.getId());
        assertOpensFor(applicant, link);
    }

    @Test
    @DisplayName("ลงนามครบ (เจ้าหน้าที่เป็นผู้ส่งเวียน) → หน้าคำร้องฝั่งแอดมิน")
    void completedNoticeToAnOfficerOpensTheAdminRequest() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);

        signatureNotifier.notifyCompleted(notice(SignatureModule.POSITION, request.getId(), officer));

        String link = onlyButtonIn(awaitMail(officer, "ลงนามครบแล้ว"));
        assertThat(link).isEqualTo("/admin/position/request/" + request.getId());
        assertOpensFor(officer, link);
    }

    @Test
    @DisplayName("ผู้ลงนามปฏิเสธ (ผู้ยื่นเป็นผู้ส่งเวียน) → หน้าคำร้องฝั่งผู้ยื่น")
    void declinedNoticeToAnApplicantOpensTheirOwnRequest() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

        signatureNotifier.notifyDeclined(notice(SignatureModule.POSITION, request.getId(), applicant));

        String link = onlyButtonIn(awaitMail(applicant, "ผู้ลงนามไม่ลงนาม"));
        assertThat(link).isEqualTo("/user/position/request/" + request.getId());
        assertOpensFor(applicant, link);
    }

    @Test
    @DisplayName("ผลการตรวจสอบจากผู้ลงนาม → ผู้ยื่นไปหน้าของตัวเอง เจ้าหน้าที่ไปหน้าแอดมิน")
    void signerFindingReachesBothSides() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        signatureNotifier.notifySignerChoiceAlert(List.of(officer, applicant), SignatureModule.ACADEMIC,
                request.getId(), "เอกสารที่ 2", "หัวหน้าสาขา", "ความครบถ้วน", "ไม่ครบถ้วน");

        assertOpensFor(applicant, onlyButtonIn(awaitMail(applicant, "ผลการตรวจสอบ")));
        assertOpensFor(officer, onlyButtonIn(awaitMail(officer, "ผลการตรวจสอบ")));
    }

    @Test
    @DisplayName("ขอให้แก้ไขและลงนามใหม่ (ประเมินการสอน เอกสารที่ 1, 2) → ฟอร์มเอกสารฉบับนั้นของผู้ยื่น")
    void resignRequestOpensTheAcademicForm() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        for (int type : new int[] { 1, 2 }) {
            mail().clear();
            signatureWorkflow.requestDocumentResign(SignatureModule.ACADEMIC, request.getId(), type,
                    "แก้ชื่อหลักสูตร", officer, ActorContext.none());

            String link = onlyButtonIn(awaitMail(applicant, "ขอให้แก้ไขเอกสารและลงนามใหม่"));
            assertThat(link).isEqualTo("/user/academic/request/" + request.getId() + "/document/" + type);
            assertOpensFor(applicant, link);
        }
    }

    @Test
    @DisplayName("ขอให้แก้ไขและลงนามใหม่ (ขอตำแหน่ง) → ฟอร์มเอกสารฉบับนั้นของผู้ยื่น")
    void resignRequestOpensThePositionForm() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null,
                "ผู้ช่วยศาสตราจารย์");

        signatureWorkflow.requestDocumentResign(SignatureModule.POSITION, request.getId(), 2,
                "แก้ข้อมูลผลงาน", officer, ActorContext.none());

        String link = onlyButtonIn(awaitMail(applicant, "ขอให้แก้ไขเอกสารและลงนามใหม่"));
        assertThat(link).isEqualTo("/user/position/request/" + request.getId() + "/document/2");
        assertOpensFor(applicant, link);
    }

    @Test
    @DisplayName("หนังสือแจ้งผลออกแล้ว → หน้าคำร้องของผู้ยื่น")
    void resultLetterOpensTheEvaluation() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED);

        academicEmails.sendResultLetterEmail(request.getId(), Map.of("memo_no", "อว 1/2569",
                "date", "1 ตุลาคม 2569", "result_level", "ดี", "expiration_date", "1 ตุลาคม 2571"));

        String link = onlyButtonIn(awaitMail(applicant, "หนังสือแจ้งผลการประเมินผลการสอน"));
        assertThat(link).isEqualTo("/user/academic/request/" + request.getId());
        assertOpensFor(applicant, link);
    }

    @Test
    @DisplayName("ลืมรหัสผ่าน → หน้าตั้งรหัสผ่านใหม่ เปิดได้โดยไม่ต้องเข้าระบบ")
    void passwordResetOpensTheResetForm() throws Exception {
        mvc.perform(post("/forgot-password").param("email", applicant.getEmail()).with(csrf()));

        String link = onlyButtonIn(awaitMail(applicant, "การตั้งรหัสผ่านใหม่"));
        assertThat(link).startsWith("/reset-password?token=");

        MockHttpServletResponse page = mvc.perform(get(link)).andReturn().getResponse();
        assertThat(page.getStatus()).as("เปิด %s", link).isEqualTo(200);
    }

    /**
     * อีเมลเปิดจากโปรแกรมอ่านอีเมล เบราว์เซอร์จึงมักยังไม่มี session — ต้องเข้าระบบก่อน
     * แล้วระบบต้องพากลับมาหน้าที่ปุ่มชี้ ไม่ใช่ทิ้งไว้ที่แดชบอร์ด
     */
    @Test
    @DisplayName("กดปุ่มตอนยังไม่เข้าระบบ → เข้าระบบแล้วกลับมาหน้าที่ปุ่มชี้")
    void signingInFromAnEmailLinkReturnsToIt() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED);
        academicEmails.sendResultLetterEmail(request.getId(), Map.of("memo_no", "อว 1/2569"));
        String link = onlyButtonIn(awaitMail(applicant, "หนังสือแจ้งผลการประเมินผลการสอน"));

        MockHttpSession browser = new MockHttpSession();
        String toSignIn = mvc.perform(get(link).session(browser)).andReturn().getResponse().getRedirectedUrl();
        assertThat(toSignIn).as("ยังไม่เข้าระบบต้องถูกพาไปหน้าเข้าสู่ระบบ").startsWith("/signin");

        String afterSignIn = mvc.perform(post("/login").session(browser).with(csrf())
                .param("email", applicant.getEmail()).param("password", TestDataFactory.PASSWORD))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(afterSignIn).isEqualTo(link);
    }

    // ------------------------------------------------------------------

    private SignatureNotice notice(SignatureModule module, Long requestId, UserDtls recipient) {
        return new SignatureNotice(1L, module, requestId, "เอกสารที่ 1", null, "2/2", null, null,
                "หัวหน้าสาขา", "ผู้ลงนาม ทดสอบ", "ข้อมูลไม่ครบ", List.of(recipient), null, null);
    }

    private RecordingMailSender.Sent awaitMail(UserDtls to, String subjectFragment) {
        awaitCondition("อีเมล \"" + subjectFragment + "\" ถึง " + to.getEmail(),
                () -> mail().to(to.getEmail()).stream().anyMatch(m -> m.subjectContains(subjectFragment)));
        return mail().to(to.getEmail()).stream().filter(m -> m.subjectContains(subjectFragment))
                .reduce((first, second) -> second).orElseThrow();
    }

    /** ลิงก์ปุ่มของระบบในอีเมล เป็น path + query ที่เปิดใน MockMvc ได้ */
    private static String onlyButtonIn(RecordingMailSender.Sent mail) {
        Matcher m = HREF.matcher(mail.body());
        java.util.Set<String> links = new java.util.LinkedHashSet<>();
        while (m.find()) {
            String href = m.group(1).replace("&amp;", "&");
            if (href.startsWith(FOOTER_SITE)) {
                continue;
            }
            URI uri = URI.create(href);
            assertThat(uri.getScheme()).as("ลิงก์ในอีเมลต้องเป็น URL เต็ม เปิดจากโปรแกรมอีเมลได้: %s", href)
                    .isIn("http", "https");
            links.add(uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        }
        assertThat(links).as("ปุ่มในอีเมล \"%s\"", mail.subject()).hasSize(1);
        return links.iterator().next();
    }

    /**
     * เปิดลิงก์ในฐานะผู้รับ แล้วตามการ redirect ภายในระบบจนถึงหน้าที่แสดงผล
     *
     * <p>redirect ไม่ได้แปลว่าพังเสมอ — คำร้องที่ยังเป็นแบบร่างถูกพาไปหน้าแก้แบบร่างของมันเอง แต่ถ้าปลายทาง
     * เป็นหน้าเข้าสู่ระบบ หน้า 403 หรือแดชบอร์ด แปลว่าปุ่มไม่ได้พาไปที่ที่บอกไว้
     */
    private void assertOpensFor(UserDtls who, String link) throws Exception {
        String at = link;
        MockHttpServletResponse page = null;
        for (int hop = 0; hop < 3; hop++) {
            page = mvc.perform(get(at).with(user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""))))
                    .andReturn().getResponse();
            if (page.getRedirectedUrl() == null) {
                break;
            }
            at = page.getRedirectedUrl();
        }
        assertThat(page.getStatus())
                .as("%s (%s) เปิด %s แล้วไปจบที่ %s ด้วย %s", who.getEmail(), who.getRole(), link, at, page.getStatus())
                .isEqualTo(200);
        assertThat(at).as("%s เปิด %s แล้วถูกพาไปหน้าอื่นแทน", who.getEmail(), link)
                .doesNotStartWith("/signin").doesNotStartWith("/403").doesNotContain("/dashboard");
    }
}
