package com.ecom.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * The whole thing, end to end: one professor, from signing in to กองทรัพยากรบุคคล.
 *
 * <p>Everything else in this suite tests a rule in isolation. This walks the
 * flow document from step 1 to step 31 as two real people using the real HTTP
 * endpoints — a professor and the HR officer — handing the request back and
 * forth. It is the test that would have caught the faults the suite was written
 * for, because those only appear when the steps are done in order and each one
 * depends on the last.
 *
 * <p>Nothing is set up behind the application's back except the two accounts and
 * the professor's saved signature. Every request is created, filled, signed,
 * submitted and advanced through the same URLs a browser would post to, through
 * the real security filter chain.
 *
 * <p>Written as one method rather than several: a journey has an order, and
 * splitting it into independent tests would either lose that order or fake it
 * with shared static state. The steps are numbered to match the PDF, so a
 * failure names the step of the real process that broke.
 *
 * <p>{@code FullJourneyE2ETest} does the same walk in a real browser. This one
 * runs in CI on every commit; that one needs Docker and a browser download.
 */
@DisplayName("เส้นทางผู้ใช้จริงตั้งแต่ต้นจนจบ (ข้อ 1-31)")
class FullJourneyMockMvcTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureWorkflowService signatureWorkflow;

    @Autowired
    private SignatureStepRepository signatureSteps;

    private UserDtls professor;
    private UserDtls officer;
    private UserSignature professorSignature;
    private String professorCertPin;

    @Test
    @DisplayName("อาจารย์ยื่นประเมินการสอน → ผ่าน → ยื่นขอตำแหน่ง ผศ. → ส่งออกกองทรัพยากรบุคคล")
    void aProfessorWalksTheWholeProcess() throws Exception {
        professor = data.applicant();
        officer = data.admin();
        professorSignature = data.signatureFor(professor);
        // ภาพลายเซ็นอย่างเดียวไม่พออีกต่อไป — ระบบบังคับให้ต้องมีใบรับรอง
        // Digital ID (.p12) ติดตั้งไว้ก่อนจึงจะลงนามได้
        professorCertPin = data.digitalCertificateFor(professor);

        Long evaluationId = phase1_teachingEvaluation();
        phase2_positionRequest(evaluationId);
    }

    // =====================================================================
    // เฟส 1 — การยื่นเอกสารประเมินการสอน (ข้อ 1-11)
    // =====================================================================

    private Long phase1_teachingEvaluation() throws Exception {
        // --- ก่อนข้อ 1: อาจารย์เปิดหน้ายื่นคำร้อง ระบบสร้างแบบร่างให้ ---
        mvc.perform(get("/user/academic/new-request").with(asProfessor()))
                .andExpect(status().isOk());

        AcademicRequest draft = academicService.findDraftByApplicant(professor.getId());
        assertThat(draft).as("เปิดหน้ายื่นคำร้องแล้วต้องได้แบบร่าง").isNotNull();
        Long id = draft.getId();
        assertThat(draft.getRequestCode()).as("แบบร่างต้องมีรหัสคำร้อง").isNotBlank();

        // --- กรอกเอกสารที่ 1: บันทึกข้อความ ขอรับการประเมินผลการสอน ---
        expectAccepted(mvc.perform(formPost("/user/academic/request/" + id + "/document-1",
                document1Fields()).with(asProfessor())),
                "/user/academic/request/" + id + "?success=doc1_submitted");

        // --- แนบไฟล์ประกอบ 7 หมวดตามที่เอกสารกำหนด ---
        //
        // Before เอกสารที่ 2, not after: the form refuses to be submitted while
        // the request has no attachments (?error=no_attachments). That is the
        // order the real page imposes too — the upload control lives on the
        // เอกสารที่ 2 page, above its submit button.
        uploadTheSevenRequiredAttachments(id);
        assertThat(academicService.countAttachments(id))
                .as("เอกสารประกอบการประเมินการสอนตามข้อ 1 มี 7 หมวด")
                .isEqualTo(7);

        // --- กรอกเอกสารที่ 2: แบบตรวจสอบเบื้องต้นเอกสารประกอบการประเมิน ---
        expectAccepted(mvc.perform(formPost("/user/academic/request/" + id + "/document-2",
                document2Fields()).with(asProfessor())),
                "/user/academic/request/" + id + "?success=doc2_submitted");
        assertThat(academicService.getDocumentsByType(id, 2))
                .as("เอกสารที่ 2 ต้องถูกบันทึกจริง ไม่ใช่แค่ถูก redirect กลับ")
                .isNotEmpty();

        // --- ยังไม่ลงนาม: ส่งคำร้องต้องไม่ผ่าน ---
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .isEqualTo(RequestStatus.DRAFT);
        expectAccepted(mvc.perform(post("/user/academic/request/" + id + "/submit")
                .with(csrf()).with(asProfessor())), "/user/academic/new-request");
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ยังไม่ลงนาม จึงต้องยังเป็นแบบร่างอยู่")
                .isEqualTo(RequestStatus.DRAFT);

        // --- ลงนามอิเล็กทรอนิกส์ในเอกสารที่ 1 และ 2 ---
        signAsApplicant(SignatureModule.ACADEMIC, id, 1, "บันทึกข้อความ ขอรับการประเมินผลการสอน");
        signAsApplicant(SignatureModule.ACADEMIC, id, 2, "แบบตรวจสอบเบื้องต้น");

        // --- ข้อ 1: ส่งคำร้อง — หน่วยสารบรรณรับเรื่อง ---
        expectAccepted(mvc.perform(post("/user/academic/request/" + id + "/submit")
                .with(csrf()).with(asProfessor())),
                "/user/academic/request/" + id + "?success=submitted");
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 1 — หน่วยสารบรรณรับเรื่อง")
                .isEqualTo(RequestStatus.RECEIVED);

        // --- แอดมินเห็นคำร้องใหม่ในรายการ ---
        String adminList = mvc.perform(get("/admin/academic/requests").with(asOfficer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(adminList)
                .as("คำร้องที่ส่งแล้วต้องปรากฏในรายการของเจ้าหน้าที่")
                .contains(draft.getRequestCode());

        // --- ข้อ 3-4: คำสั่งแต่งตั้งคณะอนุกรรมการประเมินการสอน 3 คน (เอกสารที่ 4) ---
        expectAccepted(mvc.perform(formPost("/admin/academic/request/" + id + "/document/4",
                subCommitteeOrderFields()).with(asOfficer())),
                "/admin/academic/request/" + id);
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 4 — แต่งตั้งคณะอนุกรรมการ")
                .isEqualTo(RequestStatus.SUB_COMMITTEE_APPOINTED);

        // --- ข้อ 6: นัดหมายวันประชุม (เอกสารที่ 5) ---
        mvc.perform(formPost("/admin/academic/request/" + id + "/document/5",
                officerFields(Map.of("meeting_date", "15 กันยายน 2569",
                        "meeting_place", "ห้องประชุมวิทยาลัยการคอมพิวเตอร์")))
                .with(asOfficer())).andExpect(status().is3xxRedirection());
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 6 — นัดหมายวันประชุม")
                .isEqualTo(RequestStatus.MEETING_SCHEDULED);

        // --- ข้อ 8: ที่ประชุมคณะอนุกรรมการมีมติ 'ผ่าน' (เอกสารที่ 7) ---
        expectAccepted(mvc.perform(formPost("/admin/academic/request/" + id + "/document/7",
                evaluationScoreFields()).with(asOfficer())),
                "/admin/academic/request/" + id);
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 8 — ผลการประเมินจากคณะอนุกรรมการ")
                .isEqualTo(RequestStatus.COMPLETED_PASS);
        assertThat(academicService.getDocumentsByType(id, 7).get(0).getJsonData())
                .as("คะแนน 4.5 ทั้งสี่ส่วน = 90 คะแนน ต้องได้ระดับ 'เชี่ยวชาญ'")
                .contains("\"eval_result_level\":\"เชี่ยวชาญ\"");

        // --- ข้อ 9-10: คณะกรรมการประจำวิทยาลัยฯ ประชุมรับรองผลประเมินการสอน ---
        expectAccepted(mvc.perform(post("/admin/academic/request/" + id + "/status")
                .param("status", RequestStatus.COLLEGE_ENDORSED.name())
                .param("note", "ข้อ 10 — ที่ประชุมกรรมการประจำวิทยาลัยฯ รับรองผลประเมินการสอน")
                .with(csrf()).with(asOfficer())),
                "/admin/academic/request/" + id);
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 9-10 — รับรองผลโดยกรรมการประจำวิทยาลัยฯ")
                .isEqualTo(RequestStatus.COLLEGE_ENDORSED);

        // --- ข้อ 11: แจ้งผลให้ผู้ขอกำหนดตำแหน่งทราบ (เอกสารที่ 9) ---
        mvc.perform(formPost("/admin/academic/request/" + id + "/document/9",
                officerFields(Map.of("evaluation_date", "20 กันยายน 2569",
                        "evaluation_result", "ผ่าน")))
                .with(asOfficer()))
                .andExpect(status().is3xxRedirection());
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 11 — แจ้งผลการประเมิน กระบวนการเฟส 1 เสร็จสิ้น")
                .isEqualTo(RequestStatus.COMPLETED);

        // --- อาจารย์เห็นผลบนแดชบอร์ด พร้อมวันหมดอายุ ---
        mvc.perform(get("/user/academic/dashboard").with(asProfessor()))
                .andExpect(status().isOk());
        assertThat(academicService.getLatestEvaluationExpiry(professor.getId()))
                .as("ผลประเมินที่ผ่านแล้วต้องมีวันหมดอายุให้ผู้ยื่นเห็น")
                .isNotNull();

        return id;
    }

    // =====================================================================
    // เฟส 2 — การขอกำหนดตำแหน่งทางวิชาการ (ข้อ 16-31)
    // =====================================================================

    private void phase2_positionRequest(Long evaluationId) throws Exception {
        // --- ผลประเมินที่จบแล้วต้องถูกเสนอให้เลือกในหน้าสร้างคำร้อง ---
        assertThat(positionService.getEligibleEvaluations(professor.getId()))
                .as("""
                        ข้อ 11 บอกว่าเมื่อทราบผลแล้วให้ยื่นขอกำหนดตำแหน่งต่อได้
                        ถ้ารายการนี้ว่าง ผู้ยื่นจะเจอหน้า 'ไม่มีผลประเมินให้เลือก'""")
                .extracting(AcademicRequest::getId)
                .containsExactly(evaluationId);

        mvc.perform(get("/user/position/new-request").with(asProfessor()))
                .andExpect(status().isOk());

        // --- สร้างคำร้องขอตำแหน่ง ผูกกับผลประเมินที่ผ่านมา ---
        expectAccepted(mvc.perform(post("/user/position/create-request")
                .param("evaluationId", String.valueOf(evaluationId))
                .with(csrf()).with(asProfessor())), "/user/position/request/");

        PositionRequest request = positionService.findDraftByApplicant(professor.getId())
                .orElseThrow(() -> new AssertionError("ไม่พบแบบร่างคำร้องขอตำแหน่ง"));
        Long id = request.getId();
        assertThat(request.getLinkedEvaluation()).isNotNull();
        assertThat(request.getLinkedEvaluation().getId()).isEqualTo(evaluationId);

        // --- ข้อ 16-19: กรอกเอกสารที่ผู้ยื่นรับผิดชอบให้ครบ ---
        for (int docType : PositionRequestService.APPLICANT_DOCS) {
            expectAccepted(mvc.perform(formPost("/user/position/request/" + id
                    + "/document/" + docType, positionDocumentFields(docType))
                    .with(asProfessor())), "/user/position/request/" + id);
        }
        assertThat(positionService.getCompletedDocTypes(id))
                .as("เอกสารของผู้ยื่นต้องครบทุกฉบับก่อนส่ง")
                .containsAll(PositionRequestService.APPLICANT_DOCS);

        // --- ยังไม่ลงนาม: ส่งคำร้องต้องไม่ผ่าน ---
        expectAccepted(mvc.perform(post("/user/position/request/" + id + "/submit")
                .with(csrf()).with(asProfessor())), "/user/position");
        assertThat(positionService.findById(id).orElseThrow().getCurrentStatus())
                .as("ยังไม่ลงนามครบ จึงต้องยังเป็นแบบร่าง")
                .isEqualTo(PositionRequestStatus.DRAFT);

        // --- ลงนามทุกฉบับที่ต้องลงนาม ---
        for (int docType : PositionRequestService.APPLICANT_DOCS) {
            signAsApplicant(SignatureModule.POSITION, id, docType,
                    positionService.getDocLabel(docType));
        }

        // --- ข้อ 19: ส่งคำร้องขอกำหนดตำแหน่ง ---
        expectAccepted(mvc.perform(post("/user/position/request/" + id + "/submit")
                .with(csrf()).with(asProfessor())), "/user/position/dashboard");
        assertThat(positionService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 19 — ส่งเอกสารเข้าสู่กระบวนการ")
                .isEqualTo(PositionRequestStatus.DOCUMENT_RECEIVED);

        // --- ข้อ 20-31: เจ้าหน้าที่เดินเรื่องผ่านที่ประชุมทั้งสองชุด ---
        advanceAsOfficer(id, PositionRequestStatus.DOCUMENT_VERIFICATION,
                "ข้อ 20 — ตรวจสอบความถูกต้องและครบถ้วน");
        advanceAsOfficer(id, PositionRequestStatus.SCREENING_COMMITTEE,
                "ข้อ 21 — เสนอวาระคณะกรรมการกลั่นกรองฯ");
        advanceAsOfficer(id, PositionRequestStatus.SCREENING_APPROVED,
                "ข้อ 22-23 — มติกลั่นกรองฯ เห็นชอบ");
        advanceAsOfficer(id, PositionRequestStatus.COLLEGE_COMMITTEE,
                "ข้อ 25-26 — เสนอวาระคณะกรรมการประจำวิทยาลัยฯ");
        advanceAsOfficer(id, PositionRequestStatus.COLLEGE_APPROVED,
                "ข้อ 27-29 — มติคณะกรรมการประจำวิทยาลัยฯ เห็นชอบ");
        advanceAsOfficer(id, PositionRequestStatus.SENT_TO_HR,
                "ข้อ 31 — ส่งออกกองทรัพยากรบุคคล มข.");

        // --- ปลายทาง ---
        PositionRequest finished = positionService.findById(id).orElseThrow();
        assertThat(finished.getCurrentStatus().isTerminal())
                .as("ข้อ 31 คือปลายทางของกระบวนการในระบบนี้")
                .isTrue();

        assertThat(positionService.getStatusHistory(id))
                .as("ทุกก้าวต้องอยู่ในไทม์ไลน์ให้ตรวจสอบย้อนหลังได้")
                .hasSize(7);

        assertThat(mail().to(TestDataFactory.APPLICANT_EMAIL))
                .as("ผู้ยื่นต้องได้รับแจ้งความคืบหน้าตลอดทาง ไม่ใช่ต้องมาถามเอง")
                .isNotEmpty();

        // --- ผู้ยื่นเปิดดูคำร้องที่จบแล้วได้ และดาวน์โหลดเอกสารได้จริง ---
        mvc.perform(get("/user/position/request/" + id).with(asProfessor()))
                .andExpect(status().isOk());

        byte[] document = mvc.perform(get("/user/position/request/" + id + "/document/1/download")
                .param("format", "docx")
                .with(asProfessor()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(document).as("ไฟล์ที่ดาวน์โหลดต้องไม่ว่าง").isNotEmpty();
        assertThat(new String(document, 0, 2, StandardCharsets.ISO_8859_1))
                .as("DOCX เป็นไฟล์ ZIP จึงต้องขึ้นต้นด้วย PK")
                .isEqualTo("PK");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private org.springframework.test.web.servlet.request.RequestPostProcessor asProfessor() {
        return user(professor.getEmail()).roles("USER");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asOfficer() {
        return user(officer.getEmail()).roles("ADMIN");
    }

    private void advanceAsOfficer(Long id, PositionRequestStatus target, String step)
            throws Exception {
        expectAccepted(mvc.perform(post("/admin/position/request/" + id + "/status")
                .param("status", target.name())
                .param("note", step)
                .with(csrf()).with(asOfficer())), "/admin/position/request/" + id);

        assertThat(positionService.findById(id).orElseThrow().getCurrentStatus())
                .as(step)
                .isEqualTo(target);
    }

    /**
     * Signs a document as the applicant, the way the browser does it: create the
     * envelope from the form, then post the signature on the step page.
     */
    private void signAsApplicant(SignatureModule module, Long requestId, int docType, String label)
            throws Exception {
        // Not every document has a place for the applicant to sign — position
        // document 6, for one, is signed by others entirely. Asked this way the
        // question is the same one the submit gate asks, so the two can never
        // disagree about which documents the applicant owes a signature on.
        if (signatureWorkflow.getUnsignedApplicantDocTypes(module, requestId, List.of(docType))
                .isEmpty()) {
            return;
        }

        var created = signatureWorkflow.createEnvelope(module, requestId, docType, label,
                "{\"applicant_name\":\"" + professor.getName() + "\"}",
                List.of(new SignatureWorkflowService.SignerAssignment("applicant",
                        professor.getId())),
                null, professor, SignatureWorkflowService.ActorContext.none());

        // Anything failing from here is a real failure, not a document that
        // simply has no applicant slot. Swallowing it once let an unsigned
        // request reach the submit step, where the refusal looked like a bug in
        // the submit rule instead of a signature that never happened.
        assertThat(created.ok())
                .as("สร้างซองลงนามเอกสารที่ " + docType + " ไม่สำเร็จ: " + created.error())
                .isTrue();

        SignatureStep myStep = signatureSteps
                .findBySignatureRequestIdOrderByStepOrderAsc(created.request().getId()).stream()
                .filter(s -> "applicant".equals(s.getSlotKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ซองลงนามไม่มีช่องของผู้ยื่น"));

        // ปลายทางต้องไม่ใช่ /esign/sign/{id} ซึ่งเป็นที่ที่ระบบเด้งกลับมาเมื่อ
        // ปฏิเสธการลงนาม เดิมตรวจด้วยคำนำหน้า "/" ซึ่งเป็นคำนำหน้าของ *ทุก* path
        // การถูกปฏิเสธจึงผ่านด่านนี้ไปได้ แล้วไปโผล่เป็น assertion ที่ล้มอีกสามบรรทัดถัดมา
        // โดยชี้ไปผิดที่ ตอนที่ระบบเริ่มบังคับใช้ .p12
        String landedOn = mvc.perform(post("/esign/sign/" + myStep.getId())
                .param("userSignatureId", String.valueOf(professorSignature.getId()))
                .param("digitalCertPin", professorCertPin)
                .param("consent", "true")
                .with(csrf()).with(asProfessor()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(landedOn)
                .as("ถูกเด้งกลับมาที่หน้าลงนามเดิม แปลว่าระบบปฏิเสธการลงนามเอกสารที่ "
                        + docType)
                .doesNotStartWith("/esign/sign/" + myStep.getId());
        assertThat(landedOn)
                .as("ถูกเด้งไปหน้าเข้าสู่ระบบ แปลว่า POST ไม่ผ่าน")
                .doesNotStartWith("/signin");

        assertThat(signatureWorkflow.isApplicantSignatureCompleted(module, requestId, docType))
                .as("ลงนามเอกสารที่ " + docType + " แล้วต้องถูกบันทึกว่าลงนามเสร็จ")
                .isTrue();
    }

    private void uploadTheSevenRequiredAttachments(Long requestId) throws Exception {
        // The seven categories the flow document lists under ข้อ 1.
        List<String> categories = List.of(
                "01-เอกสารประกอบการสอน.pdf",
                "02-มคอ3-และ-มคอ5.pdf",
                "03-แผนการจัดการเรียนรู้.pdf",
                "04-สื่อการสอนและเกณฑ์การวัดผล.pdf",
                "05-ผลการประเมินโดยนักศึกษา.pdf",
                "06-สรุปทุกวิชาที่สอน.pdf",
                "07-VDO-ประกอบการสอน.pdf");

        for (String filename : categories) {
            expectAccepted(mvc.perform(
                    multipart("/user/academic/request/" + requestId + "/document-2/attachments")
                            .file(new MockMultipartFile("files", filename, "application/pdf",
                                    ("%PDF-1.4 " + filename).getBytes(StandardCharsets.UTF_8)))
                            .with(csrf()).with(asProfessor())),
                    "/user/academic/request/" + requestId);
        }
    }

    /** Exactly the fields {@code isDoc1Complete} demands, and nothing spare. */
    private Map<String, String> document1Fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("date", "1 กันยายน 2569");
        fields.put("title", "ขอรับการประเมินผลการสอน");
        fields.put("applicant_name", professor.getName());
        fields.put("employee_type", "พนักงานมหาวิทยาลัย");
        fields.put("current_position", "อาจารย์");
        fields.put("chk1", "✓");
        fields.put("course_code", "CP101");
        fields.put("course_name", "การเขียนโปรแกรมคอมพิวเตอร์");
        fields.put("academic_year", "2569");
        return fields;
    }

    /** The five confirmations {@code isDoc2Complete} demands. */
    private Map<String, String> document2Fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            fields.put("chk_app_" + i, "✓");
        }
        return fields;
    }

    /**
     * Fields for a document the officer fills in.
     *
     * <p>{@code sendNotify} now only decides whether the applicant is e-mailed;
     * the status advances either way. It is set here because that is what an
     * officer working a real request does — they want the applicant told. The
     * case where it is left unticked is covered on its own in
     * {@code AcademicStatusFlowTest}.
     */
    private Map<String, String> officerFields(Map<String, String> fields) {
        Map<String, String> withNotify = new LinkedHashMap<>(fields);
        withNotify.put("sendNotify", "true");
        return withNotify;
    }

    private Map<String, String> subCommitteeOrderFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_no", "123/2569");
        fields.put("order_date", "5 กันยายน 2569");
        // ข้อ 3 — อนุกรรมการประเมินการสอน จำนวน 3 คน
        // ชื่อฟิลด์ตรงกับ doc_fragments/3.html ซึ่งสร้างด้วย th:name
        // และตรงกับ placeholder {{committee_N_name}} ในเทมเพลต docx
        fields.put("committee_1_name", "รศ.ดร. กรรมการ หนึ่ง");
        fields.put("committee_2_name", "รศ.ดร. กรรมการ สอง");
        fields.put("committee_3_name", "ผศ.ดร. กรรมการ สาม");
        return officerFields(fields);
    }

    /**
     * The four section scores the subcommittee awards, out of 5 each.
     *
     * <p>{@code eval_result_level} is not something the officer types — it is
     * derived: each section is weighted (20/30/30/20), summed, and the total
     * banded into ไม่ผ่าน / ชำนาญ / ชำนาญพิเศษ / เชี่ยวชาญ. Sending the level
     * directly, as this test first tried, is silently ignored and the request
     * scores zero, which reads as "ไม่ผ่าน".
     *
     * <p>4.5 in every section gives (4.5/5) × 100 = 90 → เชี่ยวชาญ.
     */
    private Map<String, String> evaluationScoreFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int section = 1; section <= 4; section++) {
            fields.put("sec_score_" + section, "4.5");
        }
        fields.put("evaluator_name", officer.getName());
        return officerFields(fields);
    }

    private Map<String, String> positionDocumentFields(int docType) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("applicant_name", professor.getName());
        fields.put("document_date", "1 ตุลาคม 2569");
        if (docType == 2) {
            // Document 2 feeds the request's headline fields back to the entity.
            fields.put("request_position", "ผู้ช่วยศาสตราจารย์");
            fields.put("major", "วิทยาการคอมพิวเตอร์");
            fields.put("evaluation_method", "วิธีที่ 1");
        }
        return fields;
    }

    /** Posts a map as ordinary form fields, the way a browser submits a form. */
    private MockHttpServletRequestBuilder formPost(String url, Map<String, String> fields) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        fields.forEach(params::add);
        return post(url).params(params).with(csrf());
    }
}
