package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * เวียนลงนามจริงบนเบราว์เซอร์ ทุกตำแหน่ง ทุกเอกสาร
 *
 * <p>{@code FullJourneyBrowserTest} เดินสถานะผ่าน service และไม่ได้ลงนามแม้แต่ช่องเดียว
 * ส่วนเทสลงนามที่มีอยู่ยิง POST ตรงเข้า controller ทั้งคู่จึงไม่เคยเห็นสิ่งที่ผู้ใช้จริงเจอ:
 * แผงเลือกผู้ลงนาม, modal ยืนยัน, ตัวบันทึกแบบร่างก่อนส่ง, กล่องงานลงนาม และหน้าลงนาม
 *
 * <p>เทสนี้ให้แต่ละตำแหน่งเข้าระบบด้วยฟอร์มจริง หาเอกสารของตัวเองจาก "งานลงนามของฉัน"
 * แล้วกดลงนามเอง ตามลำดับใน {@code docs/references/Flow การขอกำหนดตำแหน่งทางวิชาการ}
 * ถ้าตำแหน่งไหนเซ็นไม่ได้ เทสจะล้มที่ตำแหน่งนั้นพร้อมบอกว่าเอกสารไหน
 */
@DisplayName("E2E: เวียนลงนามทุกตำแหน่งบนเบราว์เซอร์จริง")
// ผู้ลงนามหกคนผลัดกันเข้าระบบจากเครื่องเดียวในไม่กี่นาที — เกินเพดานต่อ IP
// ที่ตั้งไว้กันการเดารหัสผ่าน ในการใช้งานจริงแต่ละคนเข้าจากเครื่องของตัวเอง
@org.springframework.test.context.TestPropertySource(properties = {
        "app.rate-limit.login=1000", "app.rate-limit.general=100000" })
class SigningCirculationBrowserTest extends PlaywrightTestBase {

    private static final String HEAD_EMAIL = "head@" + TestDataFactory.DOMAIN;
    private static final String ASSOC_DEAN_EMAIL = "assocdean@" + TestDataFactory.DOMAIN;
    private static final String DEAN_EMAIL = "dean@" + TestDataFactory.DOMAIN;
    private static final String CHAIR_EMAIL = "chair@" + TestDataFactory.DOMAIN;
    private static final String AUTHOR1_EMAIL = "author1@" + TestDataFactory.DOMAIN;
    private static final String AUTHOR2_EMAIL = "author2@" + TestDataFactory.DOMAIN;

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Autowired
    private StaffMemberRepository staffMembers;

    private UserDtls professor;
    private UserDtls officer;
    private UserDtls head;
    private UserDtls assocDean;
    private UserDtls dean;
    private UserDtls chair;
    private UserDtls author1;
    private UserDtls author2;

    /** อีเมล → ผู้ใช้ ใช้หาว่าขั้นตอนที่ active อยู่เป็นของใคร */
    private Map<Integer, String> emailById;

    /** สถานะ HTTP ของหน้าล่าสุดที่เบราว์เซอร์เปิด — หน้า error หลังกดปุ่มคือบั๊ก */
    private volatile int lastNavigationStatus;
    private volatile String lastNavigationUrl;
    private volatile int navigations;
    private final java.util.List<String> consoleTail = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @BeforeEach
    void seedPeople() {
        page.onConsoleMessage(m -> { if (!m.text().contains("Content Security Policy")) consoleTail.add(m.type() + ": " + m.text()); });
        page.onDialog(d -> { consoleTail.add("DIALOG " + d.type() + ": " + d.message()); d.dismiss(); });
        page.onPageError(e -> consoleTail.add("PAGEERROR " + e));
        page.onRequest(r -> { if (!r.url().contains("/css/") && !r.url().contains("/js/") && !r.url().contains("/img/")) consoleTail.add("REQ " + r.method() + " " + r.url()); });
        page.onResponse(r -> {
            if (r.request().isNavigationRequest() && r.frame() == page.mainFrame()) {
                lastNavigationStatus = r.status();
                navigations++;
                lastNavigationUrl = r.url();
            }
        });
        staffMembers.deleteAll();

        professor = data.applicant();
        officer = data.admin();
        head = data.user(HEAD_EMAIL, "สมศักดิ์", "หัวหน้าสาขา", "ROLE_USER");
        assocDean = data.user(ASSOC_DEAN_EMAIL, "วิชัย", "รองคณบดี", "ROLE_USER");
        dean = data.user(DEAN_EMAIL, "ประสิทธิ์", "คณบดี", "ROLE_USER");
        chair = data.user(CHAIR_EMAIL, "บุญมี", "ประธานกรรมการ", "ROLE_USER");
        author1 = data.user(AUTHOR1_EMAIL, "กิตติ", "ร่วมวิจัย", "ROLE_USER");
        author2 = data.user(AUTHOR2_EMAIL, "สุภาวดี", "บรรณกิจ", "ROLE_USER");

        staff(officer, "HR");
        staff(head, "HEAD");
        staff(assocDean, "DEAN");
        staff(dean, "DEAN");
        staff(chair, "COMMITTEE");

        for (UserDtls u : List.of(professor, officer, head, assocDean, dean, chair, author1, author2)) {
            data.signatureFor(u);
            data.digitalCertificateFor(u);
        }

        emailById = new java.util.HashMap<>();
        for (UserDtls u : List.of(professor, officer, head, assocDean, dean, chair, author1, author2)) {
            emailById.put(u.getId(), u.getEmail());
        }
    }

    /** ลบก่อน {@code data.reset()} ของเทสถัดไป ซึ่งลบผู้ใช้ที่แถวเหล่านี้ชี้อยู่ */
    @org.junit.jupiter.api.AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    private void staff(UserDtls user, String role) {
        StaffMember s = new StaffMember();
        s.setFirstName(user.getFirstName());
        s.setLastName(user.getLastName());
        s.setStaffRole(role);
        s.setIsActive(true);
        s.setUser(user);
        staffMembers.save(s);
    }

    // =====================================================================
    // เฟส 1 — ประเมินผลการสอน (Flow ข้อ 1-11)
    // =====================================================================

    @Test
    @DisplayName("เฟส 1: ผู้ยื่น → สารบรรณ → หัวหน้าสาขา → รองคณบดี → คณบดี → ประธานอนุฯ ลงนามได้ครบทุกฉบับ")
    void phaseOneEverySignerSigns() {
        // ---------- ผู้ยื่น: เอกสารที่ 1 และ 2 ----------
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/academic/new-request");
        AcademicRequest request = academicService.findDraftByApplicant(professor.getId());
        assertThat(request).as("เปิดหน้ายื่นคำร้องแล้วต้องได้แบบร่าง").isNotNull();
        Long id = request.getId();

        openAndFill("/user/academic/request/" + id + "/document-1", "chk1");
        sendForSignature("ผู้ยื่น เอกสารที่ 1");
        signHere("ผู้ยื่น เอกสารที่ 1", null);
        expectEnvelope(SignatureModule.ACADEMIC, id, 1, "ผู้ยื่น เอกสารที่ 1")
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(SignatureRequestStatus.COMPLETED));
        assertThat(academicService.canApplicantEditDocument(academicService.findById(id).orElseThrow(), 1))
                .as("ลงนามแล้วต้องแก้เนื้อเอกสารไม่ได้ แม้คำร้องยังเป็นแบบร่าง")
                .isFalse();

        openAndFill("/user/academic/request/" + id + "/document-2", "*");
        sendForSignature("ผู้ยื่น เอกสารที่ 2");
        signHere("ผู้ยื่น เอกสารที่ 2", null);

        // ข้อ 1 — ส่งคำร้องให้หน่วยสารบรรณ
        page.navigate(baseUrl() + "/user/academic/new-request");
        Locator openConfirm = page.locator("button[data-bs-target='#confirmSubmitModal']");
        if (openConfirm.count() == 0) {
            shot("no-submit-button");
            throw new AssertionError("ผู้ยื่นลงนามเอกสารที่ 1-2 ครบแล้ว แต่ปุ่มส่งคำร้องยังไม่เปิด — "
                    + page.locator(".submit-section-actions").first().innerText());
        }
        openConfirm.first().click();
        page.locator("#confirmSubmitModal.show").waitFor();
        submitViaForm("#confirmSubmitModal.show form[action$='/request/" + id + "/submit']", "ผู้ยื่นส่งคำร้อง");
        assertThat(academicService.findById(id).orElseThrow().getCurrentStatus())
                .as("ข้อ 1 — ส่งแล้วต้องถึงหน่วยสารบรรณ")
                .isEqualTo(RequestStatus.RECEIVED);
        signOut();

        // ---------- เจ้าหน้าที่: ลงนามรับเรื่องในเอกสารที่ 2 ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 2, Map.of("hr", officer));
        signOut();
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 2, null);
        expectCompleted(SignatureModule.ACADEMIC, id, 2);

        // ---------- ข้อ 2-3: หัวหน้าสาขา → รองคณบดี → คณบดี → เจ้าหน้าที่ ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 3, Map.of(
                "head", head, "associate_dean", assocDean, "dean", dean, "hr", officer));
        signOut();
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 3, "เห็นควร");
        expectCompleted(SignatureModule.ACADEMIC, id, 3);

        // ---------- ข้อ 4: คำสั่งแต่งตั้งอนุกรรมการ — คณบดีลงนาม ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 4, Map.of("dean", dean));
        signOut();
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 4, null);
        expectCompleted(SignatureModule.ACADEMIC, id, 4);
        awaitStatus(id, RequestStatus.SUB_COMMITTEE_APPOINTED, "ข้อ 4 — คำสั่งแต่งตั้งลงนามครบ");

        // ---------- ข้อ 5: บันทึกส่งอนุกรรมการ — คณบดีลงนาม ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 5, Map.of("dean", dean));
        signOut();
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 5, null);
        expectCompleted(SignatureModule.ACADEMIC, id, 5);
        awaitStatus(id, RequestStatus.MEETING_SCHEDULED, "ข้อ 5-6 — บันทึกถึงอนุกรรมการลงนามครบ");

        // ---------- ข้อ 8: ผลการประเมินของคณะอนุกรรมการ ----------
        for (int doc : new int[] { 7, 8 }) {
            signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
            if (doc == 7) {
                // คะแนนรายส่วน 4/5 ทุกส่วน = 80 คะแนน → ชำนาญพิเศษ
                formOverrides = Map.of("sec_score_1", "4", "sec_score_2", "4",
                        "sec_score_3", "4", "sec_score_4", "4");
            }
            circulateAsOfficer(SignatureModule.ACADEMIC, id, doc, Map.of("committee_chair", chair));
            signOut();
            signEveryActiveStep(SignatureModule.ACADEMIC, id, doc, null);
            expectCompleted(SignatureModule.ACADEMIC, id, doc);
        }
        awaitStatus(id, RequestStatus.COMPLETED_PASS, "ข้อ 8 — อนุกรรมการประเมินผ่าน");

        // ---------- ข้อ 9-10: กรรมการประจำวิทยาลัยฯ รับรอง (เจ้าหน้าที่บันทึกเอง) ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        academicService.updateStatus(id, RequestStatus.COLLEGE_ENDORSED, officer,
                "ข้อ 9-10 — กรรมการประจำวิทยาลัยฯ รับรองผล", false);

        // ---------- ข้อ 11: หนังสือแจ้งผล — คณบดีลงนาม ----------
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 9, Map.of("dean", dean));
        signOut();
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 9, null);
        expectCompleted(SignatureModule.ACADEMIC, id, 9);
        awaitStatus(id, RequestStatus.COMPLETED, "ข้อ 11 — แจ้งผลการประเมิน");
    }

    // =====================================================================
    // เฟส 2 — ขอกำหนดตำแหน่งทางวิชาการ (Flow ข้อ 16-31)
    // =====================================================================

    @Test
    @DisplayName("เฟส 2: ผู้เสนอขอ → หัวหน้าสาขา → คณบดี → เจ้าหน้าที่ → ผู้ร่วมประพันธ์ ลงนามได้ครบทุกฉบับ")
    void phaseTwoEverySignerSigns() {
        data.evaluationForCourse(professor, "CP001101", "2568");

        // ---------- ผู้เสนอขอ: สร้างคำร้องจากผลประเมินการสอน ----------
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/new-request");
        page.locator("form[action*='/user/position/create-request'] button[type='submit']").first().click();
        page.waitForURL("**/user/position/request/**");
        Long id = positionService.findDraftByApplicant(professor.getId())
                .orElseThrow(() -> new AssertionError("ไม่พบแบบร่างคำร้องขอตำแหน่ง")).getId();

        // ---------- ข้อ 16-17, 19: เอกสารของผู้เสนอขอ ----------
        for (int doc : new int[] { 1, 2, 3, 4, 6, 9 }) {
            String who = "ผู้เสนอขอ เอกสารตำแหน่งที่ " + doc;
            openAndFill("/user/position/request/" + id + "/document/" + doc, null);
            boolean applicantSigns = com.ecom.academic.service.SignatureAnchorRegistry.slotsOf(SignatureModule.POSITION, doc).stream()
                    .anyMatch(slot -> "applicant".equals(slot.slotKey()));
            if (applicantSigns) {
                sendForSignature(who);
                signHere(who, null);
                assertThat(positionService.canApplicantEditDocument(
                        positionService.findById(id).orElseThrow(), doc))
                        .as(who + ": ลงนามแล้วต้องแก้เนื้อเอกสารไม่ได้ แม้คำร้องยังเป็นแบบร่าง")
                        .isFalse();
            } else {
                // ฉบับที่ผู้ยื่นกรอกแต่หัวหน้าสาขา/คณบดีเป็นผู้ลงนาม — ต้องไม่มีปุ่มส่งลงนามที่กดแล้วตัน
                assertThat(page.locator("#signaturePanel form[action$='/esign/envelope/create']").count())
                        .as(who + ": ผู้ยื่นไม่มีช่องลงนาม จึงต้องไม่เห็นปุ่มส่งลงนาม").isZero();
                clickAndSettle(page.locator("#panelSaveDocBtn"), who + ": บันทึกเอกสาร");
                assertNoErrorFlash(who + ": บันทึกเอกสาร");
            }
        }

        // ---------- ส่งคำร้อง ----------
        page.navigate(baseUrl() + "/user/position/request/" + id);
        assertThat(page.locator("body").innerText())
                .as("ลงนามทุกฉบับที่ผู้ยื่นต้องลงแล้ว — ต้องไม่มีป้าย \"รอผู้ยื่นลงนาม\" (เอกสาร 6 ผู้ยื่นไม่ได้ลงนาม)")
                .doesNotContain("รอผู้ยื่นลงนาม");
        Locator openConfirm = page.locator("button[data-bs-target='#confirmSubmitModal']");
        if (openConfirm.count() == 0) {
            shot("no-position-submit");
            throw new AssertionError("ผู้เสนอขอทำเอกสารครบแล้ว แต่ไม่มีปุ่มส่งคำร้อง — "
                    + page.locator("body").innerText().replaceAll("\\s+", " "));
        }
        openConfirm.first().click();
        page.locator("#confirmSubmitModal.show").waitFor();
        submitViaForm("#confirmSubmitModal.show form[action$='/request/" + id + "/submit']", "ผู้เสนอขอส่งคำร้อง");
        awaitPositionStatus(id, PositionRequestStatus.DOCUMENT_RECEIVED, "ส่งคำร้องแล้วต้องถึงเจ้าหน้าที่");
        signOut();

        // ---------- เจ้าหน้าที่ส่งเวียนต่อ แต่ละตำแหน่งลงนาม ----------
        Map<Integer, Map<String, UserDtls>> plan = new java.util.LinkedHashMap<>();
        plan.put(1, Map.of("head", head, "dean", dean));                     // ข้อ 16, 18 ก.พ.ว. 03 ส่วนที่ ๒ ครบถ้วน/เข้าข่าย
        plan.put(3, Map.of("dean", dean));                                   // ข้อ 17 บันทึกเสนอคณบดี
        plan.put(4, Map.of("head", head));                                   // ข้อ 16 หัวหน้าสาขา
        plan.put(6, Map.of("dean", dean));
        plan.put(7, Map.of("hr", officer, "dean", dean));                    // ข้อ 20 ตรวจสอบคุณสมบัติ
        plan.put(8, Map.of("hr", officer));                                  // ข้อ 21 เสนอกลั่นกรอง
        plan.put(9, Map.of("first_author", author1, "corresponding_author", author2));

        for (var entry : plan.entrySet()) {
            int doc = entry.getKey();
            signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
            circulateAsOfficer(SignatureModule.POSITION, id, doc, entry.getValue());
            signOut();
            // null = ตัวเลือกแรกของแต่ละช่อง: หัวหน้าสาขา "ครบถ้วน" คณบดี "เข้าข่าย"
            signEveryActiveStep(SignatureModule.POSITION, id, doc, null);
            expectCompleted(SignatureModule.POSITION, id, doc);
            if (doc == 7) {
                awaitPositionStatus(id, PositionRequestStatus.DOCUMENT_VERIFICATION, "ข้อ 20 — เอกสารที่ 7 ลงนามครบ");
            }
            if (doc == 8) {
                awaitPositionStatus(id, PositionRequestStatus.SCREENING_COMMITTEE, "ข้อ 21 — เอกสารที่ 8 ลงนามครบ");
            }
        }

        // ผู้ร่วมประพันธ์ต้องเป็นคนที่เจ้าหน้าที่เลือก ไม่ใช่ผู้เสนอขอเซ็นแทน
        SignatureRequest doc9 = workflow.findEnvelope(SignatureModule.POSITION, id, 9).orElseThrow();
        Map<String, Integer> signerBySlot = new java.util.HashMap<>();
        steps.findBySignatureRequestIdOrderByStepOrderAsc(doc9.getId())
                .forEach(st -> signerBySlot.put(st.getSlotKey(), st.getSigner().getId()));
        assertThat(signerBySlot)
                .as("เอกสารที่ 9: ผู้ประพันธ์อันดับแรก/บรรณกิจต้องลงนามเอง")
                .containsEntry("first_author", author1.getId())
                .containsEntry("corresponding_author", author2.getId());
    }

    // =====================================================================
    // ทางหยุด และด่านกันลงนามผิดคน/ผิดลำดับ
    // =====================================================================

    @Test
    @DisplayName("ข้อ 2 (NO): หัวหน้าสาขา \"ไม่เห็นควร\" → การเวียนหยุด เจ้าหน้าที่แก้แล้วส่งเวียนใหม่จนลงนามครบ")
    void headRefusesThenOfficerCirculatesAgain() {
        AcademicRequest request = data.evaluation(professor, RequestStatus.RECEIVED);
        Long id = request.getId();

        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 3, Map.of(
                "head", head, "associate_dean", assocDean, "dean", dean, "hr", officer));
        signOut();

        SignatureRequest first = workflow.findEnvelope(SignatureModule.ACADEMIC, id, 3).orElseThrow();
        SignatureStep headStep = stepFor(first, "head");
        SignatureStep deanStep = stepFor(first, "dean");

        // ---------- ด่าน: คณบดียังไม่ถึงคิว เปิดลิงก์ของตัวเองหรือของหัวหน้าสาขาก็ลงนามไม่ได้ ----------
        signIn(DEAN_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/esign/inbox");
        assertThat(page.locator("a[href='/esign/sign/" + deanStep.getId() + "']").count())
                .as("คณบดียังไม่ถึงคิว ต้องไม่เห็นงานนี้ในกล่องงาน").isZero();
        page.navigate(baseUrl() + "/esign/sign/" + deanStep.getId());
        assertThat(page.locator("#signForm").count()).as("ยังไม่ถึงคิว ต้องไม่มีฟอร์มลงนาม").isZero();
        page.navigate(baseUrl() + "/esign/sign/" + headStep.getId());
        assertThat(page.locator("#signForm").count()).as("ขั้นตอนของคนอื่น ต้องไม่มีฟอร์มลงนาม").isZero();
        signOut();

        // ---------- หัวหน้าสาขาเลือก "ไม่เห็นควร" ----------
        signIn(HEAD_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/esign/sign/" + headStep.getId());
        page.locator("input[name='signerChoice'][value='ไม่เห็นควร']").check();
        page.locator("#signerComment").fill("รายชื่ออนุกรรมการยังไม่ครบองค์ประกอบ");
        clickAndSettle(page.locator("#signSubmitBtn"), "หัวหน้าสาขา ไม่เห็นควร");
        assertNoErrorFlash("หัวหน้าสาขา ไม่เห็นควร");
        signOut();

        SignatureRequest stopped = workflow.findEnvelope(first.getId()).orElseThrow();
        assertThat(stopped.getStatus()).as("ไม่เห็นควร = การเวียนหยุด").isEqualTo(SignatureRequestStatus.DECLINED);
        assertThat(steps.findById(deanStep.getId()).orElseThrow().getStatus())
                .as("คนที่ยังไม่ถึงคิวต้องไม่ถูกขอให้ลงนามอีก")
                .isNotEqualTo(SignatureStepStatus.ACTIVE);

        // ---------- เจ้าหน้าที่แก้แล้วส่งเวียนใหม่ ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 3, Map.of(
                "head", head, "associate_dean", assocDean, "dean", dean, "hr", officer));
        signOut();
        SignatureRequest second = workflow.findEnvelope(SignatureModule.ACADEMIC, id, 3).orElseThrow();
        assertThat(second.getId()).as("ส่งเวียนรอบใหม่ต้องเป็นซองใหม่").isNotEqualTo(first.getId());
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 3, "เห็นควร");
        expectCompleted(SignatureModule.ACADEMIC, id, 3);
    }

    @Test
    @DisplayName("ข้อ 18: คณบดีปฏิเสธลงนามแบบประเมินคุณสมบัติ → ผู้เสนอขอแก้เอกสารที่ 1 ได้อีกครั้ง")
    void deanDeclinesQualificationFormAndApplicantCanFixIt() {
        AcademicRequest evaluation = data.evaluationForCourse(professor, "CP001101", "2568");
        PositionRequest request = data.positionRequest(professor, PositionRequestStatus.DOCUMENT_RECEIVED,
                evaluation, "ผู้ช่วยศาสตราจารย์");
        Long id = request.getId();
        data.positionDocument(request, 1, "{\"target_position\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"สมชาย ใจดี\"}");

        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        circulateAsOfficer(SignatureModule.POSITION, id, 1, Map.of("head", head, "dean", dean));
        signOut();

        SignatureRequest env = workflow.findEnvelope(SignatureModule.POSITION, id, 1).orElseThrow();
        SignatureStep headStep = stepFor(env, "head");
        SignatureStep deanStep = stepFor(env, "dean");

        // เจ้าของประวัติลงนามส่วนที่ ๑ ก่อน ถ้าเจ้าหน้าที่ใส่ช่องของผู้ยื่นไว้ในซอง
        steps.findBySignatureRequestIdOrderByStepOrderAsc(env.getId()).stream()
                .filter(st -> "applicant".equals(st.getSlotKey()))
                .findFirst()
                .ifPresent(applicantStep -> {
                    signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
                    page.navigate(baseUrl() + "/esign/sign/" + applicantStep.getId());
                    signHere("เจ้าของประวัติ เอกสารตำแหน่งที่ 1", null);
                    signOut();
                });

        // หัวหน้าสาขาลงนาม "ครบถ้วน"
        signIn(HEAD_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/esign/sign/" + headStep.getId());
        signHere("หัวหน้าสาขา เอกสารตำแหน่งที่ 1", "ครบถ้วน");
        signOut();

        // คณบดีปฏิเสธ
        signIn(DEAN_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/esign/sign/" + deanStep.getId());
        Locator decline = page.locator("form[action$='/esign/sign/" + deanStep.getId() + "/decline']");
        if (decline.count() == 0) {
            // ช่องนี้ใช้คำถาม เข้าข่าย/ไม่เข้าข่าย — ตัวเลือก "ไม่เข้าข่าย" คือทางปฏิเสธ
            page.locator("input[name='signerChoice'][value='ไม่เข้าข่าย']").check();
            page.locator("#signerComment").fill("ขาดหลักฐานผลงานตีพิมพ์");
            clickAndSettle(page.locator("#signSubmitBtn"), "คณบดี ไม่เข้าข่าย");
        } else {
            decline.locator("textarea[name='reason']").fill("ขาดหลักฐานผลงานตีพิมพ์");
            clickAndSettle(decline.locator("button[type='submit']"), "คณบดีปฏิเสธ");
        }
        assertNoErrorFlash("คณบดีปฏิเสธ");
        signOut();

        SignatureRequest after = workflow.findEnvelope(env.getId()).orElseThrow();
        assertThat(after.getStatus()).as("คณบดีปฏิเสธ = การเวียนหยุด").isEqualTo(SignatureRequestStatus.DECLINED);

        // ผู้เสนอขอต้องกลับมาแก้เอกสารได้
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/request/" + id + "/document/1");
        Locator field = page.locator("form[data-auto-draft] input[name='applicant_name']");
        assertThat(field.count()).as("ต้องเปิดฟอร์มเอกสารที่ 1 ได้").isPositive();
        assertThat(field.first().isEditable())
                .as("คณบดีตีกลับแล้ว ผู้เสนอขอต้องแก้เอกสารที่ 1 ได้ — ซอง: " + after.getStatus())
                .isTrue();
    }

    // =====================================================================
    // ข้อ 12-15 — กรณีมีแก้ไข (เฟส 1) และ ข้อ 22-31 (เฟส 2) รวมทางตีกลับ
    // =====================================================================

    @Test
    @DisplayName("ข้อ 12-15: อนุฯ ให้แก้ → ผู้ยื่นส่งฉบับแก้ → ประชุมใหม่ → ประธานลงนามผล → แจ้งผลจนเสร็จสิ้น")
    void phaseOneRevisionLoop() {
        AcademicRequest request = data.evaluation(professor, RequestStatus.MEETING_SCHEDULED);
        Long id = request.getId();
        // เอกสารที่ 1 ที่ผู้ยื่นกรอกไว้ตั้งแต่ข้อ 1 — เอกสารที่ 7-9 ดึงชื่อและตำแหน่งที่ขอจากฉบับนี้
        data.academicDocument(request, 1, "{\"title\":\"อาจารย์\",\"applicant_name\":\"สมชาย ใจดี\","
                + "\"current_position\":\"อาจารย์\",\"chk1\":\"✓\",\"course_code\":\"CP001101\","
                + "\"course_name\":\"วิชาทดสอบ\",\"academic_year\":\"2568\"}");
        String suggestion = "ให้ปรับแผนการสอนหัวข้อที่ 3 และแนบสื่อการสอนเพิ่ม";

        // ---------- ข้อ 12: เจ้าหน้าที่แจ้งผล (กรณีมีแก้ไข) ผ่านเอกสารที่ 6 ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/admin/academic/request/" + id + "/document/6");
        page.locator("textarea[name='suggestions_text']").fill(suggestion);
        page.locator("#btnSendSuggestion").click();
        page.locator("#suggestionConfirmModal.show").waitFor();
        clickAndSettle(page.locator("#btnConfirmSendSuggestion"), "เจ้าหน้าที่ส่งข้อเสนอแนะ");
        assertNoErrorFlash("เจ้าหน้าที่ส่งข้อเสนอแนะ");
        awaitStatus(id, RequestStatus.COMPLETED_REVISE, "ข้อ 12 — แจ้งผล กรณีมีแก้ไข");
        awaitMail(TestDataFactory.APPLICANT_EMAIL, suggestion, "ข้อ 12 — ผู้ยื่นต้องได้รับข้อเสนอแนะทางอีเมล");
        signOut();

        // ---------- ข้อ 13: ผู้ยื่นส่งเอกสารที่แก้ไขแล้วกลับมา ----------
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/academic/request/" + id);
        assertThat(page.locator("body").innerText()).contains("ต้องแก้ไขเอกสาร");
        Locator upload = page.locator("form[action$='/upload-revision/" + id + "']");
        if (upload.count() == 0) {
            shot("no-revision-upload");
            throw new AssertionError("ข้อ 13: ผู้ยื่นไม่มีช่องทางส่งเอกสารที่แก้ไขแล้วกลับมาในหน้าคำร้อง");
        }
        upload.locator("input[type='file']").setInputFiles(new com.microsoft.playwright.options.FilePayload(
                "เอกสารประเมินการสอน-แก้ไข.pdf", "application/pdf", "%PDF-1.4 revised".getBytes()));
        clickAndSettle(upload.locator("button[type='submit']"), "ผู้ยื่นส่งฉบับแก้ไข");
        assertNoErrorFlash("ผู้ยื่นส่งฉบับแก้ไข");
        awaitStatus(id, RequestStatus.REVISION_SUBMITTED, "ข้อ 13 — ส่งเอกสารที่แก้ไขแล้ว");
        signOut();

        // ---------- ข้อ 14-15: เจ้าหน้าที่เห็นไฟล์ฉบับแก้ ส่งอนุฯ และนัดประชุมใหม่ ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/admin/academic/request/" + id);
        Locator revisionLink = page.locator("a[href*='/revision-file']");
        assertThat(revisionLink.count()).as("ข้อ 14 — เจ้าหน้าที่ต้องเปิดไฟล์ที่ผู้ยื่นแก้ไขมาได้").isPositive();
        var download = page.waitForDownload(() -> revisionLink.first().click());
        assertThat(download.suggestedFilename()).contains("แก้ไข");
        setStatusViaAdminForm("academic", id, "MEETING_SCHEDULED", "ข้อ 14-15 — ส่งเอกสารฉบับแก้ให้อนุฯ นัดประชุมใหม่");
        awaitStatus(id, RequestStatus.MEETING_SCHEDULED, "ข้อ 15 — นัดประชุมอนุกรรมการอีกรอบ");

        // ---------- ข้อ 15 → 9-11: ประชุมรอบใหม่เห็นชอบ ----------
        formOverrides = Map.of("sec_score_1", "4", "sec_score_2", "4", "sec_score_3", "4", "sec_score_4", "4");
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 7, Map.of("committee_chair", chair));
        signOut();
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 7, null);
        awaitStatus(id, RequestStatus.COMPLETED_PASS, "ข้อ 15 — อนุฯ เห็นชอบรอบใหม่");

        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        setStatusViaAdminForm("academic", id, "COLLEGE_ENDORSED", "ข้อ 9-10 — กรรมการประจำวิทยาลัยฯ รับรองผล");
        circulateAsOfficer(SignatureModule.ACADEMIC, id, 9, Map.of("dean", dean));
        signOut();
        signEveryActiveStep(SignatureModule.ACADEMIC, id, 9, null);
        awaitStatus(id, RequestStatus.COMPLETED, "ข้อ 11 — แจ้งผลการประเมิน");
    }

    @Test
    @DisplayName("ข้อ 22 NO → 19 และ 22-31: กลั่นกรองให้แก้เอกสารที่ผู้เสนอขอกับคณบดีลงนามแล้ว → ต้องลงนามใหม่ทั้งสองคนก่อนเดินต่อ จนส่งกองทรัพยากรบุคคล")
    void phaseTwoScreeningSendsBackThenReachesHr() {
        AcademicRequest evaluation = data.evaluationForCourse(professor, "CP001101", "2568");
        PositionRequest request = data.positionRequest(professor, PositionRequestStatus.DRAFT,
                evaluation, "ผู้ช่วยศาสตราจารย์");
        Long id = request.getId();
        // เอกสารที่ 3 (แบบรับรองจริยธรรม) — ผู้เสนอขอลงนาม แล้วคณบดีลงนาม
        String doc3 = "{\"applicant_name\":\"สมชาย ใจดี\",\"position\":\"อาจารย์\"}";
        data.positionDocument(request, 3, doc3);
        var none = SignatureWorkflowService.ActorContext.none();
        var created = workflow.createEnvelope(SignatureModule.POSITION, id, 3, "แบบรับรองจริยธรรม", doc3,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", professor.getId()),
                        new SignatureWorkflowService.SignerAssignment("dean", dean.getId())),
                null, officer, none);
        assertThat(created.ok()).as(String.valueOf(created.error())).isTrue();
        Long firstEnvelope = created.request().getId();
        workflow.startCirculation(firstEnvelope, officer, none);
        assertThat(workflow.sign(stepFor(created.request(), "applicant").getId(), professor,
                data.signatureFor(professor).getId(), true, none, com.ecom.support.TestCertificates.PIN, null).ok()).isTrue();
        assertThat(workflow.sign(stepFor(created.request(), "dean").getId(), dean,
                data.signatureFor(dean).getId(), true, none, com.ecom.support.TestCertificates.PIN, "เห็นควร").ok()).isTrue();
        expectCompleted(SignatureModule.POSITION, id, 3);
        for (PositionRequestStatus st : new PositionRequestStatus[] { PositionRequestStatus.DOCUMENT_RECEIVED,
                PositionRequestStatus.DOCUMENT_VERIFICATION, PositionRequestStatus.SCREENING_COMMITTEE }) {
            positionService.updateStatus(id, st, officer, st.getThaiLabel(), false);
        }

        // ---------- ข้อ 22 NO: กลั่นกรองให้แก้ เจ้าหน้าที่ส่งเอกสารที่ 3 กลับ ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        sendBackAsOfficer("position", id, 3, "กรรมการกลั่นกรองให้แก้ข้อความรับรองจริยธรรมให้ครบตามแบบ");
        setStatusViaAdminForm("position", id, "REVISION_REQUESTED", "ข้อ 22-23 — มติกลั่นกรอง: ให้แก้ไข");
        awaitPositionStatus(id, PositionRequestStatus.REVISION_REQUESTED, "ข้อ 23 — แจ้งมติให้แก้");

        SignatureRequest old = workflow.findEnvelope(firstEnvelope).orElseThrow();
        assertThat(old.getStatus()).as("ส่งกลับแล้ว ซองเดิมพร้อมลายเซ็นของผู้เสนอขอและคณบดีต้องถูกยกเลิก")
                .isEqualTo(SignatureRequestStatus.CANCELLED);
        assertThat(workflow.isApplicantSignatureCompleted(SignatureModule.POSITION, id, 3))
                .as("ลายเซ็นในซองที่ยกเลิกต้องไม่นับว่าผู้เสนอขอลงนามแล้ว").isFalse();

        // ยังไม่ลงนามใหม่ → เดินเรื่องต่อไม่ได้ และหน้าจอบอกว่าติดเอกสารฉบับไหน
        expectStatusRefused("position", id, "DOCUMENT_VERIFICATION", "เอกสารที่ 3");
        signOut();

        // ---------- ข้อ 19 (รอบใหม่): ผู้เสนอขอเห็นว่าต้องแก้ แก้แล้วลงนามใหม่ ----------
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/request/" + id);
        assertThat(page.locator("#sentBackAlert").count()).as("ผู้เสนอขอต้องเห็นว่าต้องแก้และลงนามใหม่").isPositive();
        assertThat(page.locator("#sentBackAlert").innerText()).contains("ข้อความรับรองจริยธรรม");
        openAndFill("/user/position/request/" + id + "/document/3", null);
        assertThat(page.locator("form[data-auto-draft] [name='applicant_name']").first().isEditable())
                .as("ผู้เสนอขอต้องแก้เอกสารที่ถูกส่งกลับได้").isTrue();
        sendForSignature("ผู้เสนอขอ ลงนามเอกสารที่ 3 ใหม่");
        signHere("ผู้เสนอขอ ลงนามเอกสารที่ 3 ใหม่", null);
        page.navigate(baseUrl() + "/user/position/request/" + id);
        assertThat(page.locator("#sentBackAlert").count()).as("ลงนามใหม่แล้ว ป้ายต้องแก้ต้องหายไป").isZero();
        signOut();

        // ผู้เสนอขอเซ็นแล้ว แต่คณบดียังไม่เซ็นซ้ำ → ยังเดินต่อไม่ได้
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        expectStatusRefused("position", id, "DOCUMENT_VERIFICATION", "เอกสารที่ 3");

        // ---------- เจ้าหน้าที่ส่งเวียนให้คณบดีลงนามใหม่ ----------
        circulateAsOfficer(SignatureModule.POSITION, id, 3, Map.of("dean", dean));
        signOut();
        signEveryActiveStep(SignatureModule.POSITION, id, 3, null);
        SignatureRequest again = workflow.findEnvelope(SignatureModule.POSITION, id, 3).orElseThrow();
        assertThat(again.getId()).as("ต้องเป็นซองใหม่ ไม่ใช่ซองเดิมที่ถูกยกเลิก").isNotEqualTo(firstEnvelope);
        assertThat(steps.findBySignatureRequestIdOrderByStepOrderAsc(again.getId()).stream()
                .filter(st -> st.getStatus() == SignatureStepStatus.SIGNED).map(SignatureStep::getSlotKey).toList())
                .as("ซองใหม่ต้องมีลายเซ็นใหม่ของทั้งผู้เสนอขอและคณบดี")
                .containsExactlyInAnyOrder("applicant", "dean");

        // ---------- ข้อ 20-31: ลงนามใหม่ครบแล้ว เดินเรื่องจนส่งกองทรัพยากรบุคคล ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/admin/position/request/" + id);
        assertThat(page.locator("#awaitingResignAlert").count()).as("ลงนามใหม่ครบแล้ว ต้องไม่มีป้ายรอลงนามใหม่").isZero();
        for (PositionRequestStatus st : new PositionRequestStatus[] { PositionRequestStatus.DOCUMENT_VERIFICATION,
                PositionRequestStatus.SCREENING_COMMITTEE, PositionRequestStatus.SCREENING_APPROVED,
                PositionRequestStatus.COLLEGE_COMMITTEE, PositionRequestStatus.COLLEGE_APPROVED,
                PositionRequestStatus.SENT_TO_HR }) {
            setStatusViaAdminForm("position", id, st.name(), st.getThaiLabel());
            awaitPositionStatus(id, st, st.getThaiLabel());
        }
        signOut();

        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/request/" + id);
        assertThat(page.locator("body").innerText())
                .as("ข้อ 31 — ผู้เสนอขอต้องเห็นว่าเรื่องส่งกองทรัพยากรบุคคลแล้ว")
                .contains(PositionRequestStatus.SENT_TO_HR.getThaiLabel());
    }

    @Test
    @DisplayName("เฟส 1: เจ้าหน้าที่ส่งเอกสารที่ 1 กลับให้ผู้ยื่นแก้ → ต้องลงนามใหม่ก่อนเดินเรื่องต่อ")
    void phaseOneSentBackDocumentMustBeResigned() {
        // ผู้ยื่นทำเอกสารที่ 1-2 และส่งคำร้องตามปกติ (แบบร่างเปล่าที่สร้างล่วงหน้าจะถูกระบบซ่อน/ลบ
        // ตอนเปิดแดชบอร์ด จึงให้ระบบสร้างแบบร่างเองจากหน้ายื่นคำร้อง)
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/academic/new-request");
        Long id = academicService.findDraftByApplicant(professor.getId()).getId();
        openAndFill("/user/academic/request/" + id + "/document-1", "chk1");
        sendForSignature("ผู้ยื่น เอกสารที่ 1");
        signHere("ผู้ยื่น เอกสารที่ 1", null);
        openAndFill("/user/academic/request/" + id + "/document-2", "*");
        sendForSignature("ผู้ยื่น เอกสารที่ 2");
        signHere("ผู้ยื่น เอกสารที่ 2", null);
        academicService.submitDraftRequest(academicService.findById(id).orElseThrow());
        signOut();
        Long firstEnvelope = workflow.findEnvelope(SignatureModule.ACADEMIC, id, 1).orElseThrow().getId();

        // เจ้าหน้าที่ตรวจแล้วพบว่าเอกสารที่ 1 ผิด ส่งกลับ
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        sendBackAsOfficer("academic", id, 1, "ชื่อรายวิชาไม่ตรงกับที่ลงทะเบียน");
        assertThat(workflow.findEnvelope(firstEnvelope).orElseThrow().getStatus())
                .as("ลายเซ็นเดิมของผู้ยื่นต้องถูกยกเลิก").isEqualTo(SignatureRequestStatus.CANCELLED);
        expectStatusRefused("academic", id, "SUB_COMMITTEE_APPOINTED", "เอกสารที่ 1");
        signOut();

        // ผู้ยื่นเห็นว่าต้องแก้ แก้แล้วลงนามใหม่
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/academic/request/" + id);
        assertThat(page.locator("body").innerText())
                .contains("เจ้าหน้าที่ส่งเอกสารกลับมาให้ท่านแก้ไข")
                .contains("ชื่อรายวิชาไม่ตรงกับที่ลงทะเบียน");
        page.navigate(baseUrl() + "/user/academic/request/" + id + "/document-1");
        page.locator("form[data-auto-draft] [name='course_name']").first().fill("วิชาที่แก้ชื่อแล้ว");
        sendForSignature("ผู้ยื่น ลงนามเอกสารที่ 1 ใหม่");
        signHere("ผู้ยื่น ลงนามเอกสารที่ 1 ใหม่", null);
        signOut();
        SignatureRequest again = workflow.findEnvelope(SignatureModule.ACADEMIC, id, 1).orElseThrow();
        assertThat(again.getId()).isNotEqualTo(firstEnvelope);
        assertThat(again.getFrozenJson()).as("ซองใหม่ต้องแช่แข็งเนื้อหาที่แก้แล้ว").contains("วิชาที่แก้ชื่อแล้ว");

        // ลงนามใหม่ครบ → เดินเรื่องต่อได้
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        setStatusViaAdminForm("academic", id, "SUB_COMMITTEE_APPOINTED", "ข้อ 3-4");
        awaitStatus(id, RequestStatus.SUB_COMMITTEE_APPOINTED, "ลงนามใหม่แล้วเดินต่อได้");
    }

    /** เจ้าหน้าที่กด "ส่งกลับให้ผู้ยื่นแก้ไข" ในหน้าเอกสาร พร้อมเหตุผล */
    private void sendBackAsOfficer(String module, Long id, int doc, String reason) {
        String who = "เจ้าหน้าที่ส่งเอกสารที่ " + doc + " กลับ";
        page.navigate(baseUrl() + "/admin/" + module + "/request/" + id + "/document/" + doc);
        Locator sendBack = page.locator("form[action$='/document/" + doc + "/request-resign']");
        if (sendBack.count() == 0) {
            shot("no-send-back");
            throw new AssertionError(who + ": ไม่มีปุ่มส่งกลับให้ผู้ยื่นแก้");
        }
        Locator opener = page.locator("[data-bs-target='#sendBackPanel'], [data-bs-target^='#modal-resign-']");
        if (!sendBack.first().isVisible() && opener.count() > 0) {
            opener.first().click();
            sendBack.first().waitFor();
        }
        sendBack.first().locator("textarea[name='reason']").fill(reason);
        clickAndSettle(sendBack.first().locator("button[type='submit']"), who);
        assertNoErrorFlash(who);
    }

    /** ฟอร์มสถานะต้องปฏิเสธ พร้อมบอกว่าติดเอกสารฉบับไหน และสถานะต้องไม่ขยับ */
    private void expectStatusRefused(String module, Long id, String status, String mustMention) {
        page.navigate(baseUrl() + "/admin/" + module + "/request/" + id);
        Locator alert = page.locator("#awaitingResignAlert");
        assertThat(alert.count()).as("หน้าเจ้าหน้าที่ต้องเตือนว่ายังรอลงนามใหม่").isPositive();
        assertThat(alert.innerText()).contains(mustMention);

        Locator form = page.locator("form[action$='/request/" + id + "/status']").first();
        form.locator("select[name='status']").selectOption(status);
        form.locator("[name='note']").first().fill("ลองเดินต่อก่อนลงนามใหม่");
        int before = navigations;
        form.locator("button[type='submit']").first().click();
        page.waitForCondition(() -> navigations > before && lastNavigationStatus < 300);
        page.waitForLoadState();
        assertThat(page.locator("body").innerText())
                .as("ต้องบอกเหตุผลที่เปลี่ยนสถานะไม่ได้")
                .contains("ยังลงนามใหม่ไม่ครบ")
                .contains(mustMention);
        String now = "academic".equals(module)
                ? academicService.findById(id).orElseThrow().getCurrentStatus().name()
                : positionService.findById(id).orElseThrow().getCurrentStatus().name();
        assertThat(now).as("สถานะต้องไม่ขยับ").isNotEqualTo(status);
    }

    /** เลือกสถานะใหม่จากฟอร์ม "อัปเดตสถานะ" ในหน้ารายละเอียดคำร้องของเจ้าหน้าที่ */
    private void setStatusViaAdminForm(String module, Long id, String status, String note) {
        String who = "เจ้าหน้าที่เปลี่ยนสถานะเป็น " + status;
        page.navigate(baseUrl() + "/admin/" + module + "/request/" + id);
        Locator form = page.locator("form[action$='/request/" + id + "/status']").first();
        Locator option = form.locator("select[name='status'] option[value='" + status + "']");
        if (option.count() == 0) {
            shot("no-status-" + status);
            throw new AssertionError(who + ": ไม่มีตัวเลือกนี้ในฟอร์ม — มีแค่ "
                    + form.locator("select[name='status']").innerText().replaceAll("\\s+", " "));
        }
        form.locator("select[name='status']").selectOption(status);
        Locator noteField = form.locator("[name='note']");
        if (noteField.count() > 0) {
            noteField.first().fill(note);
        }
        clickAndSettle(form.locator("button[type='submit']").first(), who);
        assertNoErrorFlash(who);
    }

    private void awaitMail(String to, String text, String why) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (mail().to(to).stream().anyMatch(m -> m.body() != null && m.body().contains(text))) {
                return;
            }
            page.waitForTimeout(200);
        }
        throw new AssertionError(why + " — ไม่พบอีเมลถึง " + to + " ที่มีข้อความนี้");
    }

    private SignatureStep stepFor(SignatureRequest env, String slot) {
        return steps.findBySignatureRequestIdOrderByStepOrderAsc(env.getId()).stream()
                .filter(st -> slot.equals(st.getSlotKey()))
                .findFirst().orElseThrow(() -> new AssertionError("ซองไม่มีช่อง " + slot));
    }

    // =====================================================================
    // Browser helpers
    // =====================================================================

    /**
     * เปิดฟอร์มเอกสารแล้วกรอกทุกช่องที่ยังว่าง แบบที่ผู้ใช้ที่ใจร้อนทำ
     *
     * @param tick ชื่อ checkbox ที่ต้องติ๊ก — {@code "*"} ติ๊กทุกช่อง, {@code null} ไม่ติ๊ก
     */
    private void openAndFill(String path, String tick) {
        page.navigate(baseUrl() + path);
        page.waitForLoadState();
        fillEmptyFields(tick);
    }

    private void fillEmptyFields(String tick) {
        page.evaluate("""
                (tick) => {
                  const form = document.querySelector('form[data-auto-draft]')
                            || document.querySelector('form[id^="doc"]');
                  if (!form) return 0;
                  if (form.__autoDraft) {
                    form.__autoDraft.touched = true;
                  }
                  let n = 0;
                  const seenRadio = new Set();
                  for (const el of form.querySelectorAll('input, textarea, select')) {
                    if (el.disabled || el.readOnly || el.type === 'hidden') continue;
                    if (el.type === 'file' || el.type === 'button' || el.type === 'submit') continue;
                    if (el.name === 'dueAt' || el.closest('#signaturePanel')) continue;
                    if (el.type === 'checkbox') {
                      if (tick === '*' || (tick && el.name === tick) || el.required) {
                        if (!el.checked) { el.checked = true; n++; }
                      }
                    } else if (el.type === 'radio') {
                      if (seenRadio.has(el.name)) continue;
                      seenRadio.add(el.name);
                      const group = form.querySelectorAll('input[type=radio][name="' + el.name + '"]');
                      if (![...group].some(r => r.checked)) { el.checked = true; n++; }
                    } else if (el.tagName === 'SELECT') {
                      if (!el.value) {
                        const opt = [...el.options].find(o => o.value);
                        if (opt) { el.value = opt.value; n++; }
                      }
                    } else if (!el.value) {
                      const t = el.type;
                      el.value = t === 'number' ? '1'
                               : t === 'date' ? '2026-09-01'
                               : t === 'datetime-local' ? '2026-09-01T10:00'
                               : t === 'time' ? '10:00'
                               : t === 'email' ? 'someone@example.invalid'
                               : t === 'tel' ? '0812345678'
                               : t === 'url' ? 'https://example.invalid'
                               : 'ทดสอบ';
                      n++;
                    } else {
                      continue;
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                  }
                  return n;
                }
                """, tick);
    }

    /** กดปุ่มส่งในแผงลงนาม แล้วยืนยันใน modal */
    private void sendForSignature(String who) {
        Locator send = page.locator("#signaturePanel form[action$='/esign/envelope/create'] button[type='submit']");
        assertThat(send.count()).as(who + ": ต้องมีปุ่มส่งลงนามในแผงลงนาม").isPositive();
        clickAndSettle(send.first(), who + ": กดส่งลงนาม");
        assertNoErrorFlash(who + ": ส่งลงนาม");
    }

    private void submitViaForm(String selector, String who) {
        Locator form = page.locator(selector);
        assertThat(form.count()).as(who + ": ไม่พบฟอร์ม " + selector).isPositive();
        clickAndSettle(form.first().locator("button[type='submit']").first(), who);
        assertNoErrorFlash(who);
    }

    /**
     * กดปุ่ม แล้วรอจนหน้าใหม่โหลดเสร็จ — กด "ยืนยัน" ใน modal ให้ถ้ามันโผล่
     *
     * <p>นับจากการนำทางจริง ไม่ใช่จาก URL เพราะหลายปุ่ม redirect กลับมาหน้าเดิม
     * และ modal ยืนยันของแผงลงนามโผล่หลังการบันทึกแบบร่างเสร็จ ซึ่งช้าเร็วไม่เท่ากัน
     */
    private void clickAndSettle(Locator button, String who) {
        int before = navigations;
        button.click();
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (navigations > before && (lastNavigationStatus < 300 || lastNavigationStatus >= 400)) {
                page.waitForLoadState();
                return;
            }
            Locator ok = page.locator("#appGlobalConfirmModal.show #confirmModalOkBtn");
            if (ok.count() > 0 && ok.isVisible()) {
                ok.click();
            }
            String errors = visibleErrors();
            if (!errors.isBlank()) {
                shot("stuck");
                throw new AssertionError(who + " — หน้าแจ้ง: " + errors);
            }
            page.waitForTimeout(150);
        }
        shot("nothing-happened");
        Object state = page.evaluate("""
                () => { const f = document.querySelector('form[data-auto-draft]');
                  const p = document.querySelector('form[data-presave-doc]');
                  return JSON.stringify({blocked: f && f.getAttribute('data-sign-blocked'),
                    presaveDone: p && p._preSaveDone, confirmed: p && p._inAppConfirmed,
                    modal: !!document.querySelector('#appGlobalConfirmModal.show'),
                    modalEl: !!document.getElementById('appGlobalConfirmModal'),
                    modalCls: (document.getElementById('appGlobalConfirmModal')||{}).className,
                    bs: typeof bootstrap, appConfirm: typeof window.appConfirm,
                    openModals: [...document.querySelectorAll('.modal.show, .modal-backdrop')].map(e => e.id || e.className),
                    invalid: [...document.querySelectorAll(':invalid')].map(e => e.name || e.id).slice(0, 10)}); }
                """);
        List<String> tail = consoleTail.subList(Math.max(0, consoleTail.size() - 25), consoleTail.size());
        throw new AssertionError(who + ": กดแล้วไม่มีอะไรเกิดขึ้นภายใน 20 วินาที (url " + page.url() + ") state="
                + state + "\n  " + String.join("\n  ", tail));
    }

    private void assertNoErrorFlash(String who) {
        String errors = visibleErrors();
        if (!errors.isBlank()) {
            shot("error");
            throw new AssertionError(who + " — ระบบแจ้งข้อผิดพลาด: " + errors + " (url " + page.url() + ")");
        }
    }

    private String visibleErrors() {
        Locator alerts = page.locator(".alert-danger:visible, .presave-error:visible, .toast-error:visible, .required-fields-error:visible");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < alerts.count(); i++) {
            String text = alerts.nth(i).innerText().trim();
            // กล่องเตือน "ยังไม่ได้ติดตั้ง Digital ID" ไม่ควรโผล่ เพราะทุกคนติดตั้งแล้ว — นับเป็นข้อผิดพลาดด้วย
            if (!text.isEmpty()) {
                sb.append(text.replaceAll("\\s+", " ")).append(" | ");
            }
        }
        return sb.toString();
    }

    /**
     * ลงนามในหน้าลงนามที่เปิดอยู่
     *
     * @param choice คำตอบของช่องที่ถาม (เห็นควร / ครบถ้วน) — {@code null} เลือกตัวแรกถ้ามีคำถาม
     */
    private void signHere(String who, String choice) {
        assertThat(page.url()).as(who + ": ต้องอยู่หน้าลงนาม").contains("/esign/sign/");
        Locator form = page.locator("#signForm");
        if (form.count() == 0) {
            shot("no-sign-form");
            throw new AssertionError(who + ": หน้าลงนามไม่มีฟอร์มลงนาม — " + visibleErrors()
                    + " / " + page.locator("main, body").first().innerText().replaceAll("\\s+", " ")
                            .substring(0, 400));
        }
        Locator sig = page.locator("input[name='userSignatureId']");
        if (sig.count() > 0 && page.locator("input[name='userSignatureId']:checked").count() == 0) {
            page.locator("label[for='" + sig.first().getAttribute("id") + "']").click();
        }
        Locator choices = page.locator("input[name='signerChoice']");
        if (choices.count() > 0) {
            Locator pick = choice == null ? choices.first()
                    : page.locator("input[name='signerChoice'][value='" + choice + "']");
            assertThat(pick.count()).as(who + ": ไม่มีตัวเลือก " + choice).isPositive();
            pick.check();
        }
        Locator pin = page.locator("#digitalCertPin");
        if (pin.count() > 0) {
            pin.fill(com.ecom.support.TestCertificates.PIN);
        }
        page.locator("#consentCheck").check();

        String before = page.url();
        clickAndSettle(page.locator("#signSubmitBtn"), who + ": กดยืนยันการลงนาม");
        assertThat(page.url())
                .as(who + ": ลงนามไม่สำเร็จ ถูกเด้งกลับหน้าเดิม — " + visibleErrors())
                .isNotEqualTo(before);
        assertNoErrorFlash(who + ": ลงนาม");
        assertThat(lastNavigationStatus)
                .as(who + ": ลงนามสำเร็จแล้วถูกพาไปหน้าที่เปิดไม่ได้ " + lastNavigationUrl)
                .isLessThan(400);
    }

    /**
     * เจ้าหน้าที่เปิดเอกสาร เลือกผู้ลงนามในแผง แล้วส่งเวียน
     *
     * <p>ถ้าหลังส่งแล้วระบบยังรอให้ "เริ่มเวียนลงนาม" อีกที ก็กดให้ — แต่นับไว้ว่าเกิดขึ้น
     */
    private void circulateAsOfficer(SignatureModule module, Long id, int doc, Map<String, UserDtls> signers) {
        String who = "เจ้าหน้าที่ส่งเวียนเอกสารที่ " + doc;
        page.navigate(baseUrl() + "/admin/" + module.name().toLowerCase() + "/request/" + id + "/document/" + doc);
        page.waitForLoadState();

        Locator startBtn = page.locator("#signaturePanel form[action$='/start'] button[type='submit']");
        Locator forward = page.locator("#signaturePanel form[action$='/forward']");
        Locator create = page.locator("#signaturePanel form[action$='/esign/envelope/create']");

        if (create.count() > 0) {
            if (!formOverrides.isEmpty()) {
                page.evaluate("""
                        (vals) => {
                          const form = document.querySelector('form[data-auto-draft]');
                          if (form && form.__autoDraft) {
                            form.__autoDraft.touched = true;
                          }
                          for (const [k, v] of Object.entries(vals)) {
                            const el = form ? form.querySelector('[name="' + k + '"]') : null;
                            if (!el) continue;
                            el.value = v;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                          }
                        }
                        """, formOverrides);
                formOverrides = Map.of();
            }
            fillEmptyFields(null);
            pickSigners(create.first(), signers, who);
            sendForSignature(who);
            if (workflow.findEnvelope(module, id, doc).isEmpty()) {
                shot("no-envelope-doc" + doc);
                throw new AssertionError(who + ": กดส่งเวียนแล้วไม่เกิดซองลงนาม — url " + page.url()
                        + " / " + page.locator("#signaturePanel").innerText().replaceAll("\\s+", " "));
            }
        } else if (forward.count() > 0) {
            pickSigners(forward.first(), signers, who);
            clickAndSettle(forward.first().locator("button[type='submit']").first(), who + ": ส่งต่อ");
            assertNoErrorFlash(who + ": ส่งต่อ");
        } else if (startBtn.count() > 0) {
            // ซองที่ผู้ยื่นส่งมาเองรอเจ้าหน้าที่ตรวจ — กรณีนี้ปุ่มเริ่มเวียนคือทางที่ถูก
            clickAndSettle(startBtn.first(), who + ": เริ่มเวียน");
            assertNoErrorFlash(who + ": เริ่มเวียน");
            return;
        } else {
            shot("no-panel-doc" + doc);
            throw new AssertionError(who + ": ไม่มีแผงส่งลงนาม/ส่งต่อ/เริ่มเวียน ให้กด — "
                    + visibleErrors());
        }

        // ถ้าลงนามเองในขั้นแรก ระบบพาไปหน้าลงนามทันที
        if (page.url().contains("/esign/sign/")) {
            return;
        }
        page.navigate(baseUrl() + "/admin/" + module.name().toLowerCase() + "/request/" + id + "/document/" + doc);
        startBtn = page.locator("#signaturePanel form[action$='/start'] button[type='submit']");
        if (startBtn.count() > 0) {
            // ปุ่ม "ยืนยันความถูกต้องและส่งเวียนลงนาม" ต้องปล่อยเวียนในตัว — ถ้ายังต้องกดอีกปุ่ม
            // เจ้าหน้าที่จริงไม่รู้ และคณบดีไม่เคยได้รับแจ้ง
            shot("needs-extra-start-doc" + doc);
            throw new AssertionError(who + ": กดส่งเวียนแล้วซองยังรอปุ่ม \"เริ่มเวียนลงนาม\" อีกปุ่ม — "
                    + page.locator("#signaturePanel").innerText().replaceAll("\\s+", " "));
        }
    }

    /** ค่าที่เจ้าหน้าที่พิมพ์เองก่อนส่งเวียนครั้งถัดไป (เช่นคะแนนประเมิน) — ใช้แล้วล้าง */
    private Map<String, String> formOverrides = Map.of();

    private void pickSigners(Locator form, Map<String, UserDtls> signers, String who) {
        Locator slotKeys = form.locator("input[name='slotKeys']");
        Locator selects = form.locator("[name='signerUserIds']");
        int n = slotKeys.count();
        for (int i = 0; i < n; i++) {
            String slot = slotKeys.nth(i).getAttribute("value");
            Locator field = selects.nth(i);
            UserDtls signer = signers.get(slot);
            if ("select".equalsIgnoreCase((String) field.evaluate("e => e.tagName"))) {
                String value = signer == null ? "" : String.valueOf(signer.getId());
                field.evaluate("(e, v) => { e.value = v; e.dispatchEvent(new Event('change', {bubbles:true})); }", value);
                assertThat((String) field.evaluate("e => e.value"))
                        .as(who + ": เลือก " + slot + " = " + (signer == null ? "-" : signer.getEmail())
                                + " ไม่ได้ (ไม่มีในรายการ)")
                        .isEqualTo(value);
            }
        }
    }

    /**
     * ทุกคนที่ถึงคิวเข้าระบบเอง เปิด "งานลงนามของฉัน" แล้วลงนาม จนซองไม่มีขั้นตอนที่ active
     */
    private void signEveryActiveStep(SignatureModule module, Long id, int doc, String choice) {
        for (int guard = 0; guard < 8; guard++) {
            SignatureRequest env = expectEnvelope(module, id, doc, "เอกสารที่ " + doc).actual();
            SignatureStep active = steps.findBySignatureRequestIdOrderByStepOrderAsc(env.getId()).stream()
                    .filter(s -> s.getStatus() == SignatureStepStatus.ACTIVE)
                    .findFirst().orElse(null);
            if (active == null) {
                return;
            }
            String email = emailById.get(active.getSigner().getId());
            String who = active.getSlotKey() + " (" + email + ") เอกสาร " + module + " ที่ " + doc;

            signIn(email, TestDataFactory.PASSWORD);
            page.navigate(baseUrl() + "/esign/inbox");
            Locator link = page.locator("a[href='/esign/sign/" + active.getId() + "']");
            if (link.count() == 0) {
                shot("inbox-missing");
                throw new AssertionError(who + ": ไม่เห็นงานของตัวเองใน /esign/inbox");
            }
            link.first().click();
            page.waitForURL("**/esign/sign/**");
            signHere(who, choice);

            SignatureStep after = steps.findById(active.getId()).orElseThrow();
            assertThat(after.getStatus()).as(who + ": หลังลงนามขั้นตอนต้องเป็น SIGNED")
                    .isEqualTo(SignatureStepStatus.SIGNED);
            signOut();
        }
        throw new AssertionError("เอกสารที่ " + doc + ": วนลงนามเกิน 8 รอบ");
    }

    private org.assertj.core.api.ObjectAssert<SignatureRequest> expectEnvelope(SignatureModule module, Long id,
            int doc, String who) {
        SignatureRequest env = workflow.findEnvelope(module, id, doc).orElse(null);
        assertThat(env).as(who + ": ไม่มีซองลงนาม").isNotNull();
        return assertThat(env);
    }

    private void expectCompleted(SignatureModule module, Long id, int doc) {
        SignatureRequest env = workflow.findEnvelope(module, id, doc).orElseThrow();
        List<SignatureStep> all = steps.findBySignatureRequestIdOrderByStepOrderAsc(env.getId());
        assertThat(env.getStatus())
                .as(module + " เอกสารที่ " + doc + ": ต้องลงนามครบ — ขั้นตอน: "
                        + all.stream().map(s -> s.getSlotKey() + "=" + s.getStatus()).toList())
                .isEqualTo(SignatureRequestStatus.COMPLETED);
    }

    private void awaitStatus(Long id, RequestStatus expected, String step) {
        long deadline = System.currentTimeMillis() + 10_000;
        RequestStatus now = null;
        while (System.currentTimeMillis() < deadline) {
            now = academicService.findById(id).orElseThrow().getCurrentStatus();
            if (now == expected) {
                return;
            }
            page.waitForTimeout(200);
        }
        assertThat(now).as(step).isEqualTo(expected);
    }

    private void awaitPositionStatus(Long id, PositionRequestStatus expected, String step) {
        long deadline = System.currentTimeMillis() + 10_000;
        PositionRequestStatus now = null;
        while (System.currentTimeMillis() < deadline) {
            now = positionService.findById(id).orElseThrow().getCurrentStatus();
            if (now == expected) {
                return;
            }
            page.waitForTimeout(200);
        }
        assertThat(now).as(step).isEqualTo(expected);
    }

    private int shots;

    private void shot(String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target", "playwright", "shots", (++shots) + "-" + name + ".png"))
                .setFullPage(true));
    }
}
