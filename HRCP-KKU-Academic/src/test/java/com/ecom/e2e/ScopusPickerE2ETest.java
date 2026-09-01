package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;

/**
 * The publication picker, driven the way a professor drives it.
 *
 * <p>This is the reported fault's home ground. Everything about the picker lives
 * in the browser — the modal is built by script, the rows grow on demand, the
 * buttons are wired through {@code data-call} because the Content-Security-Policy
 * forbids inline handlers — and none of it is reachable from MockMvc. A template
 * test can prove a button is absent from the markup; only this can prove the
 * buttons that are present actually work.
 */
@EnabledIfDockerAvailable
@DisplayName("E2E: เลือกงานวิจัยจาก Scopus ในแบบ ก.พ.ว.มข.03")
class ScopusPickerE2ETest extends PlaywrightTestBase {

    private static final long FS_ID = 9001L;

    private Long openADraftPositionRequestFor(UserDtls professor) {
        data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
        data.publication(FS_ID, "Deep learning for Thai handwriting recognition", 2023, 18);
        data.publication(FS_ID, "Edge computing for smart agriculture", 2024, 6);
        return data.positionRequest(professor, PositionRequestStatus.DRAFT, null).getId();
    }

    private void openDocumentOne(Long requestId) {
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/request/" + requestId + "/document/1");
        page.waitForSelector("#targetPos");
    }

    /**
     * Picks the target position — which is not the {@code <select>} the template
     * declares.
     *
     * <p>{@code select_to_datalist.js} replaces every {@code <select>} in a
     * document form with an {@code <input list="…">} on load, keeping the id.
     * So by the time anyone interacts with this page the control is a free-text
     * combobox, and {@code selectOption} fails with "Element is not a &lt;select&gt;".
     * Nothing outside a browser can see that: the template, the served HTML and
     * every MockMvc assertion all still say {@code <select>}.
     *
     * <p>Handles both shapes so the test keeps working either way.
     */
    private void chooseTargetPosition(String position) {
        Locator control = page.locator("#targetPos");
        String tag = (String) control.evaluate("el => el.tagName");
        if ("SELECT".equals(tag)) {
            control.selectOption(position);
        } else {
            control.fill(position);
            control.dispatchEvent("change");
        }
        page.waitForTimeout(100);
    }

    /**
     * Waits for a form field to receive a value.
     *
     * <p>Not {@code page.waitForFunction}: that ships a predicate as a string for
     * the page to eval, and this application's Content-Security-Policy allows no
     * {@code unsafe-eval}. The CSP is correct — the test has to work within it.
     * Playwright's own assertions poll from the driver side and need no eval.
     */
    private void awaitFilled(String fieldName) {
        com.microsoft.playwright.assertions.PlaywrightAssertions
                .assertThat(page.locator("[name='" + fieldName + "']"))
                .not().hasValue("");
    }

    /** The "เลือกจาก Scopus" buttons that are actually on screen right now. */
    private Locator visiblePickerButtons() {
        return page.locator("button[data-call='openScopusPreset']:visible");
    }

    @Test
    @DisplayName("GAP-10: เลือกตำแหน่ง ผศ. แล้วต้องเห็นปุ่ม 'เลือกจาก Scopus' ครบทั้งสามกลุ่ม")
    void assistantProfessorSeesAPickerButtonInEveryGroup() {
        UserDtls professor = data.applicant();
        openDocumentOne(openADraftPositionRequestFor(professor));

        chooseTargetPosition("ผู้ช่วยศาสตราจารย์");

        assertThat(visiblePickerButtons().count())
                .as("""
                        อาการที่ผู้ใช้รายงานคือเปิดหน้ากรอกผลงานของ ผศ. แล้วไม่เห็นปุ่มเลยสักปุ่ม
                        จึงเข้าใจว่าระบบไม่มีให้เลือกงานวิจัย ทั้งที่ picker ทำงานได้ปกติ
                        ตอนนี้ต้องมีครบสามกลุ่ม: งานวิจัย, ผลงานอื่น, ตำรา/หนังสือ""")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("เลือกตำแหน่ง รศ. แล้วมีปุ่มครบทั้งสี่กลุ่ม (รวมวิธีที่ ๓)")
    void associateProfessorSeesAPickerButtonInEveryGroup() {
        UserDtls professor = data.applicant();
        openDocumentOne(openADraftPositionRequestFor(professor));

        chooseTargetPosition("รองศาสตราจารย์");

        assertThat(visiblePickerButtons().count())
                .as("ส่วน รศ. มีสี่กลุ่ม: งานวิจัย, ผลงานอื่น, ตำรา/หนังสือ และงานวิจัยวิธีที่ ๓")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("เปิด modal แล้วเห็นเฉพาะผลงานของตัวเอง และเลือกใส่ฟอร์มได้จริง")
    void pickingAPublicationFillsTheForm() {
        UserDtls professor = data.applicant();
        // A second professor with work of their own, to prove it never appears.
        data.otherApplicant();
        data.faculty(9002L, TestDataFactory.OTHER_APPLICANT_EMAIL);
        data.publication(9002L, "Somebody else's paper on quantum error correction", 2024, 99);

        openDocumentOne(openADraftPositionRequestFor(professor));
        chooseTargetPosition("รองศาสตราจารย์");

        visiblePickerButtons().first().click();
        page.waitForSelector("#scopusPickerModal.show");

        String modalText = page.locator("#spList").innerText();
        assertThat(modalText)
                .as("ต้องเห็นผลงานของตัวเอง")
                .contains("Deep learning for Thai handwriting recognition");
        assertThat(modalText)
                .as("และต้องไม่เห็นผลงานของอาจารย์ท่านอื่นเด็ดขาด")
                .doesNotContain("quantum error correction");

        // Tick the first row and remember which paper that actually is — the
        // list is ordered by the server (newest first), not by the order the
        // fixture created them.
        Locator firstRow = page.locator("#spList .list-group-item").first();
        String tickedTitle = firstRow.locator(".fw-semibold").innerText().trim();
        firstRow.locator("input[type='checkbox']").check();

        page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("ใส่ในแบบฟอร์ม"))
                .click();

        awaitFilled("assoc_research_working_1");

        assertThat(page.inputValue("[name='assoc_research_working_1']"))
                .as("ข้อความอ้างอิงของผลงานที่ติ๊ก ต้องถูกใส่ลงช่องแรกที่ว่าง")
                .contains(tickedTitle);
    }

    @Test
    @DisplayName("ข้อความที่พิมพ์เองไว้แล้ว ต้องไม่ถูกเขียนทับ")
    void existingTextIsNotOverwritten() {
        UserDtls professor = data.applicant();
        openDocumentOne(openADraftPositionRequestFor(professor));
        chooseTargetPosition("รองศาสตราจารย์");

        String typedByHand = "ผลงานที่พิมพ์เองไว้ก่อน ห้ามถูกเขียนทับ";
        page.fill("[name='assoc_research_working_1']", typedByHand);

        visiblePickerButtons().first().click();
        page.waitForSelector("#scopusPickerModal.show");
        page.locator("#spList input[type='checkbox']").first().check();
        page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("ใส่ในแบบฟอร์ม"))
                .click();

        awaitFilled("assoc_research_working_2");

        assertThat(page.inputValue("[name='assoc_research_working_1']"))
                .as("ช่องที่กรอกเองไว้ต้องเหมือนเดิมทุกตัวอักษร")
                .isEqualTo(typedByHand);
        assertThat(page.inputValue("[name='assoc_research_working_2']"))
                .as("ผลงานที่เลือกต้องไปลงช่องว่างช่องถัดไป และระบบต้องสร้างแถวให้เอง")
                .isNotBlank();
    }

    @Test
    @DisplayName("บัญชีที่ไม่มีคู่ในระบบคณะ — modal บอกเหตุผล ไม่ใช่ค้างว่างเปล่า")
    void unlinkedAccountIsToldWhy() {
        UserDtls stranger = data.user("stranger@" + TestDataFactory.DOMAIN,
                "ไม่ผูก", "บัญชี", "ROLE_USER");
        Long requestId = data.positionRequest(stranger, PositionRequestStatus.DRAFT, null).getId();

        signIn("stranger@" + TestDataFactory.DOMAIN, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/position/request/" + requestId + "/document/1");
        page.waitForSelector("#targetPos");
        chooseTargetPosition("รองศาสตราจารย์");

        visiblePickerButtons().first().click();
        page.waitForSelector("#scopusPickerModal.show");
        page.waitForSelector("#spList .alert");

        assertThat(page.locator("#spList").innerText())
                .as("ต้องอธิบายว่าอีเมลไม่ตรงกับระบบ Fund Management ไม่ใช่ปล่อยให้เดาเอง")
                .contains("Fund Management");
    }
}
