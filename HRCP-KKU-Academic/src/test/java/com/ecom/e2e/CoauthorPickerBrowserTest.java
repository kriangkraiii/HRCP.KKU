package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;

/**
 * เอกสารที่ 9: แถว "เพิ่มผู้ประพันธ์บรรณกิจ" เลือกผู้ลงนามจากรายชื่อในระบบเหมือนช่องบรรณกิจ (person_picker.js)
 * บรรณกิจแต่ละคนเป็นคนละคน และแผงลงนามแสดงแถวที่เพิ่มพร้อมชื่อที่เลือก
 */
@DisplayName("E2E: เอกสารที่ 9 — ผู้ประพันธ์บรรณกิจเพิ่มเติม เลือกจากรายชื่อและขึ้นในแผงลงนาม")
class CoauthorPickerBrowserTest extends PlaywrightTestBase {

    private static final String DOC1 = "{\"assoc_research_working_1\":\"งานวิจัยเรื่องแรก\"}";

    private Locator option(String text) {
        return page.locator(".person-picker-menu.show .dropdown-item",
                new com.microsoft.playwright.Page.LocatorOptions().setHasText(text));
    }

    private void pick(Locator input, String search, String printed) {
        input.fill(search);
        option(printed).first().waitFor();
        option(printed).first().dispatchEvent("mousedown");
    }

    @Test
    @DisplayName("เพิ่มแถว → เลือกจากรายชื่อ / คนที่เป็นบรรณกิจแล้วเลือกซ้ำไม่ได้ / แผงลงนามขึ้นชื่อ / ลบแถวแล้วรหัสบัญชีตามแถวไป")
    void extraCorrespondingAuthorRows() {
        UserDtls applicant = data.applicant();
        UserDtls first = data.user("corr-first@" + TestDataFactory.DOMAIN, "สมหญิง", "บรรณกิจ", "ROLE_USER");
        UserDtls second = data.user("corr-second@" + TestDataFactory.DOMAIN, "วิภา", "บรรณกิจ", "ROLE_USER");
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null, "รองศาสตราจารย์");
        data.positionDocument(request, 1, DOC1);
        String firstName = SignerNameResolver.printedName(first);
        String secondName = SignerNameResolver.printedName(second);

        signIn(applicant.getEmail(), TestDataFactory.PASSWORD);
        page.navigate("/user/position/request/" + request.getId() + "/document/901");

        pick(page.locator("input[name=corres_name]"), "สมหญิง", firstName);
        Locator panelRow = page.locator("[data-row-slot=coauthor_name_1]");
        assertThat(panelRow.isHidden()).as("ยังไม่มีแถว — แผงไม่แสดงช่องบรรณกิจคนที่ 2").isTrue();

        page.locator("#btnAddCoauthor").click();
        Locator row1 = page.locator("input[name=coauthor_name_1]");
        row1.fill("บรรณกิจ");
        option(secondName).first().waitFor();
        assertThat(option(firstName).count()).as("บรรณกิจคนแรกเลือกซ้ำในแถวไม่ได้").isZero();
        option(secondName).first().dispatchEvent("mousedown");

        assertThat(page.locator("input[name=coauthor_name_1__signer]").inputValue())
                .isEqualTo(String.valueOf(second.getId()));
        assertThat(panelRow.isVisible()).isTrue();
        assertThat(panelRow.innerText()).contains("ผู้ประพันธ์บรรณกิจ (คนที่ 2)").contains(secondName);

        // แถวที่ 2 ว่างไว้ แล้วลบแถวแรก — แถวที่เหลือเลื่อนขึ้นเป็นแถวที่ 1
        page.locator("#btnAddCoauthor").click();
        page.locator(".coauthor-row").first().locator("[data-call=removeCoauthor]").click();
        assertThat(page.locator("input[name=coauthor_name_1]").inputValue()).isEmpty();
        assertThat(page.locator("input[name=coauthor_name_1__signer]").inputValue()).isEmpty();
        assertThat(page.locator("input[name=coauthor_count]").inputValue()).isEqualTo("1");
        assertThat(page.locator("[data-row-slot=coauthor_name_2]").isHidden()).isTrue();
    }

    @Test
    @DisplayName("ร่างที่ติ๊กสถานะครบสามข้อ — ขึ้นคำเตือน ส่งลงนามไม่ได้ และเอาติ๊กออกได้จนเหลือแบบที่ถูกต้อง")
    void conflictingRolesCanBeUntickedAndBlockSigning() {
        UserDtls applicant = data.applicant();
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null, "รองศาสตราจารย์");
        data.positionDocument(request, 1, DOC1);
        data.positionDocument(request, 901, "{\"title_name\":\"งานวิจัยเรื่องแรก\",\"chk_ firstauthor\":\"☑\","
                + "\"chk_corresp\":\"☑\",\"chk_coauthor\":\"☑\"}");

        signIn(applicant.getEmail(), TestDataFactory.PASSWORD);
        page.navigate("/user/position/request/" + request.getId() + "/document/901");
        page.waitForFunction("() => !document.getElementById('applicantRoleError').hidden", null);
        assertThat(page.locator("#applicantRoleError").innerText()).contains("ผู้นิพนธ์ร่วม");
        assertThat(page.locator("#doc9Form").getAttribute("data-sign-blocked")).contains("ผู้นิพนธ์ร่วม");

        // ข้อที่ติ๊กอยู่ต้องไม่ถูกล็อก — เอาผู้นิพนธ์ร่วมออก เหลือผู้ประพันธ์อันดับแรกและบรรณกิจ
        page.locator("label[for=chkCoauthor]").click();
        assertThat(page.locator("#chkCoauthor").isChecked()).isFalse();
        assertThat(page.locator("#applicantRoleError").isHidden()).isTrue();
        assertThat(page.locator("#doc9Form").getAttribute("data-sign-blocked")).isNull();
        assertThat(page.locator("#chkCoauthor").getAttribute("aria-disabled")).as("ตอนนี้ผู้นิพนธ์ร่วมถูกล็อกตามกติกา").isEqualTo("true");
    }
}
