package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;

/**
 * The journey again, but in a browser, on PostgreSQL, over HTTP.
 *
 * <p>{@code FullJourneyMockMvcTest} already walks the same flow and runs on
 * every commit. This one costs a container and a browser, so it earns its place
 * by covering what that one structurally cannot:
 *
 * <ul>
 *   <li>signing in through the real form, with a real session cookie and a real
 *       CSRF token rather than a test post-processor;</li>
 *   <li>the pages rendering — a Thymeleaf mistake compiles fine and only shows
 *       up when someone opens the page;</li>
 *   <li>two people using the system in turn, each having to sign in and find
 *       their way to the request, rather than a switch of a mock principal;</li>
 *   <li>PostgreSQL underneath, so anything dialect-specific surfaces here.</li>
 * </ul>
 *
 * <p>The heavier form filling stays in the MockMvc journey: driving forty fields
 * through a browser adds minutes and finds nothing that posting them does not.
 * What this asserts is that every screen along the route opens, shows the right
 * state to the right person, and that the request really does end up with
 * กองทรัพยากรบุคคล.
 */
@EnabledIfDockerAvailable
@DisplayName("E2E: เส้นทางผู้ใช้จริงบนเบราว์เซอร์ ตั้งแต่ต้นจนจบ")
class FullJourneyE2ETest extends PlaywrightTestBase {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Test
    @DisplayName("อาจารย์และเจ้าหน้าที่ผลัดกันเข้าระบบ จนคำร้องถึงกองทรัพยากรบุคคล")
    void twoPeopleWalkTheProcessInABrowser() {
        UserDtls professor = data.applicant();
        UserDtls officer = data.admin();

        // ---------- อาจารย์: เข้าสู่ระบบด้วยฟอร์มจริง ----------
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        assertThat(page.url())
                .as("เข้าสู่ระบบสำเร็จต้องไม่ค้างอยู่หน้า /signin")
                .doesNotContain("/signin");

        // ---------- เปิดหน้ายื่นคำร้อง ระบบสร้างแบบร่างให้ ----------
        page.navigate(baseUrl() + "/user/academic/new-request");
        AcademicRequest draft = academicService.findDraftByApplicant(professor.getId());
        assertThat(draft).as("เปิดหน้ายื่นคำร้องแล้วต้องได้แบบร่าง").isNotNull();

        // ---------- ฟอร์มเอกสารที่ 0 ต้อง render ได้และมีช่องให้กรอกจริง ----------
        page.navigate(baseUrl() + "/user/academic/request/" + draft.getId() + "/document-0");
        assertThat(page.locator("input[name='applicant_name']").count())
                .as("ฟอร์มเอกสารที่ 0 ต้องมีช่องชื่อผู้ยื่น")
                .isPositive();

        // ---------- เดินกระบวนการเฟส 1 ให้จบ ----------
        // The step-by-step form filling is the MockMvc journey's job; here the
        // point is that each screen along the way opens for the right person.
        academicService.updateStatus(draft.getId(), RequestStatus.SUB_COMMITTEE_APPOINTED,
                officer, "ข้อ 4 — แต่งตั้งคณะอนุกรรมการ", false);
        academicService.updateStatus(draft.getId(), RequestStatus.MEETING_SCHEDULED,
                officer, "ข้อ 6 — นัดหมายวันประชุม", false);
        academicService.updateStatus(draft.getId(), RequestStatus.COMPLETED_PASS,
                officer, "ข้อ 8 — ผลการประเมินจากคณะอนุกรรมการ", false);
        academicService.updateStatus(draft.getId(), RequestStatus.COLLEGE_ENDORSED,
                officer, "ข้อ 9-10 — กรรมการประจำวิทยาลัยฯ รับรองผล", false);
        academicService.updateStatus(draft.getId(), RequestStatus.COMPLETED,
                officer, "ข้อ 11 — แจ้งผลการประเมิน", false);
        data.academicDocument(draft, 8, "{\"evaluation_result\":\"ผ่าน\"}");

        // ---------- อาจารย์เห็นผลบนแดชบอร์ด ----------
        page.navigate(baseUrl() + "/user/academic/dashboard");
        assertThat(page.locator("body").innerText())
                .as("แดชบอร์ดต้องแสดงรหัสคำร้องที่เพิ่งจบไป")
                .contains(draft.getRequestCode());

        // ---------- หน้าเลือกผลประเมินเพื่อยื่นขอตำแหน่ง ต้องมีรายการให้เลือก ----------
        page.navigate(baseUrl() + "/user/position/new-request");
        assertThat(page.locator("body").innerText())
                .as("""
                        ข้อ 11 บอกว่าเมื่อทราบผลแล้วให้ยื่นขอกำหนดตำแหน่งต่อ
                        ถ้าหน้านี้ไม่มีผลประเมินให้เลือก ผู้ยื่นจะไปต่อไม่ได้เลย""")
                .contains(draft.getRequestCode());

        // ---------- สร้างคำร้องขอตำแหน่ง ----------
        page.locator("form[action*='/user/position/create-request'] button[type='submit']")
                .first().click();
        page.waitForURL("**/user/position/request/**");

        PositionRequest positionRequest = positionService.findDraftByApplicant(professor.getId())
                .orElseThrow(() -> new AssertionError("ไม่พบแบบร่างคำร้องขอตำแหน่ง"));
        assertThat(positionRequest.getLinkedEvaluation())
                .as("คำร้องขอตำแหน่งต้องผูกกับผลประเมินที่เลือก")
                .isNotNull();

        // ---------- หน้ารายละเอียดคำร้อง render ได้ ----------
        assertThat(page.locator("body").innerText())
                .contains(positionRequest.getRequestCode());

        // ---------- เจ้าหน้าที่เข้าระบบและเดินเรื่องต่อ ----------
        signOut();
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);

        // A draft is the applicant's own unfinished work and must not be on the
        // officer's list yet.
        page.navigate(baseUrl() + "/admin/position/requests");
        assertThat(page.locator("body").innerText())
                .as("คำร้องที่ยังเป็นแบบร่าง ต้องไม่โผล่ในรายการของเจ้าหน้าที่")
                .doesNotContain(positionRequest.getRequestCode());

        // ข้อ 19 — ผู้ยื่นส่งคำร้อง เรื่องจึงเข้าสู่มือเจ้าหน้าที่
        positionService.submitRequest(positionRequest);

        page.navigate(baseUrl() + "/admin/position/requests");
        assertThat(page.locator("body").innerText())
                .as("ส่งคำร้องแล้ว เจ้าหน้าที่ต้องเห็นในรายการของตน")
                .contains(professor.getName());
        for (PositionRequestStatus status : new PositionRequestStatus[] {
                PositionRequestStatus.DOCUMENT_VERIFICATION,
                PositionRequestStatus.SCREENING_COMMITTEE,
                PositionRequestStatus.SCREENING_APPROVED,
                PositionRequestStatus.COLLEGE_COMMITTEE,
                PositionRequestStatus.COLLEGE_APPROVED,
                PositionRequestStatus.SENT_TO_HR }) {
            positionService.updateStatus(positionRequest.getId(), status, officer,
                    status.getThaiLabel(), false);
        }

        page.navigate(baseUrl() + "/admin/position/request/" + positionRequest.getId());
        assertThat(page.locator("body").innerText())
                .as("หน้ารายละเอียดฝั่งเจ้าหน้าที่ต้องแสดงสถานะปลายทาง")
                .contains(PositionRequestStatus.SENT_TO_HR.getThaiLabel());

        // ---------- อาจารย์กลับมาดูผลปลายทาง ----------
        signOut();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/request/" + positionRequest.getId());

        assertThat(page.locator("body").innerText())
                .as("ข้อ 31 — ผู้ยื่นต้องเห็นเองว่าเรื่องถูกส่งออกกองทรัพยากรบุคคลแล้ว")
                .contains(PositionRequestStatus.SENT_TO_HR.getThaiLabel());

        assertThat(positionService.findById(positionRequest.getId()).orElseThrow()
                .getCurrentStatus().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("อาจารย์อีกคนเปิด URL คำร้องของคนอื่นตรง ๆ ต้องเข้าไม่ได้")
    void anotherProfessorCannotOpenSomebodyElsesRequest() {
        UserDtls owner = data.applicant();
        data.otherApplicant();
        PositionRequest theirs =
                data.positionRequest(owner, PositionRequestStatus.DOCUMENT_RECEIVED, null);

        signIn(TestDataFactory.OTHER_APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/request/" + theirs.getId());

        assertThat(page.url())
                .as("ต้องถูกเด้งกลับแดชบอร์ดของตัวเอง ไม่ใช่เปิดคำร้องของคนอื่นได้")
                .contains("/user/position/dashboard");
        assertThat(page.locator("body").innerText())
                .doesNotContain(theirs.getRequestCode());
    }
}
