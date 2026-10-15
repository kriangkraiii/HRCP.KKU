package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicCommitteeMember;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.repository.AcademicCommitteeMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;

/**
 * รายชื่อผู้ทรงคุณวุฒิในเอกสารที่ 8 (ฝั่งเจ้าหน้าที่): เริ่มที่แถวเดียว เลือกจากฐานข้อมูลหรือเพิ่มแถวกรอกเอง แทนการวาง 10 แถวว่างไว้ทั้งหมด
 *
 * <p>แถวทั้ง 10 ยังอยู่ในหน้าเพื่อให้ชื่อช่อง name1..name10 ตรงกับแบบฟอร์ม การซ่อน/แสดงและการเลื่อนแถวตอนลบ
 * ทำด้วยสคริปต์ทั้งหมด เทส MockMvc เห็นแค่ HTML จึงต้องเปิดในเบราว์เซอร์
 */
@DisplayName("E2E: เอกสารที่ 8 — เพิ่ม/ลบผู้ทรงคุณวุฒิทีละแถว")
class ExpertRowsBrowserTest extends PlaywrightTestBase {

    @Autowired
    private AcademicCommitteeMemberRepository committee;

    private Locator visibleRows() {
        return page.locator("[data-expert-row]:not(.d-none)");
    }

    private void open(String json) {
        UserDtls applicant = data.applicant();
        UserDtls officer = data.admin();
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.SCREENING_COMMITTEE, null);
        if (json != null) {
            data.positionDocument(request, 8, json);
        }
        signIn(officer.getEmail(), TestDataFactory.PASSWORD);
        page.navigate("/admin/position/request/" + request.getId() + "/document/8");
        page.locator("#btnAddExpert").waitFor();
    }

    @Test
    @DisplayName("เริ่มแถวเดียว → เพิ่มทีละแถว → ลบแถวแรกแล้วแถวถัดไปเลื่อนขึ้นมาแทน")
    void addThenRemoveShiftsTheRowsUp() {
        open(null);

        assertThat(visibleRows().count()).as("ยังไม่มีข้อมูล แสดงแถวเดียว").isEqualTo(1);
        assertThat(page.locator("#expertCount").textContent()).isEqualTo("1");

        page.locator("#exp_name_1").fill("ศ.ดร.หนึ่ง ทดสอบ");
        page.locator("#btnAddExpert").click();
        page.locator("#exp_name_2").fill("ศ.ดร.สอง ทดสอบ");
        page.locator("#exp_major_2").fill("วิทยาการคอมพิวเตอร์");
        page.locator("#btnAddExpert").click();
        assertThat(visibleRows().count()).isEqualTo(3);
        assertThat(page.locator("#expertCount").textContent()).isEqualTo("3");

        page.locator("[data-expert-row='1'] [data-expert-remove]").click();

        assertThat(visibleRows().count()).isEqualTo(2);
        assertThat(page.locator("#exp_name_1").inputValue()).as("แถว 2 เลื่อนขึ้นมาเป็นแถว 1").isEqualTo("ศ.ดร.สอง ทดสอบ");
        assertThat(page.locator("#exp_major_1").inputValue()).isEqualTo("วิทยาการคอมพิวเตอร์");
        assertThat(page.locator("#exp_name_2").inputValue()).as("แถว 3 ที่ว่างเลื่อนขึ้นมาแทนแถว 2").isEmpty();
        assertThat(page.locator("#exp_name_3").inputValue()).as("แถวสุดท้ายถูกล้างและซ่อน").isEmpty();
    }

    @Test
    @DisplayName("เพิ่มได้ไม่เกิน 10 คน แล้วปุ่มเพิ่มหายไป")
    void addStopsAtTen() {
        open(null);
        for (int i = 0; i < 9; i++) {
            page.locator("#btnAddExpert").click();
        }
        assertThat(visibleRows().count()).isEqualTo(10);
        assertThat(page.locator("#btnAddExpert").isVisible()).isFalse();
    }

    private AcademicCommitteeMember expert(String first, String affiliation) {
        AcademicCommitteeMember m = new AcademicCommitteeMember();
        m.setTitle("ศ.ดร.");
        m.setFirstName(first);
        m.setLastName("ผู้ทรงคุณวุฒิ");
        m.setAffiliation(affiliation);
        m.setExpertiseField("วิทยาการคอมพิวเตอร์");
        m.setEmail("test." + Math.abs(first.hashCode()) + "@example.com");
        return committee.save(m);
    }

    @Test
    @DisplayName("ปุ่มเลือกจากฐานข้อมูล: ติ๊กหลายคนแล้วเพิ่ม → ลงแถวว่างถัดไป · คนที่อยู่ในตารางแล้วเลือกซ้ำไม่ได้")
    void pickFromTheDatabaseFillsTheNextRows() {
        AcademicCommitteeMember first = expert("เลือกหนึ่ง", "มหาวิทยาลัยขอนแก่น");
        AcademicCommitteeMember second = expert("เลือกสอง", "จุฬาลงกรณ์มหาวิทยาลัย");
        open(null);
        assertThat(page.locator(".expert-selector").count()).as("ไม่มีดรอปดาวน์ในแต่ละแถวแล้ว").isZero();

        page.locator("#btnPickExpert").click();
        Locator list = page.locator("#expertPickerList");
        list.locator("label", new Locator.LocatorOptions().setHasText(first.getFullName())).locator("input").check();
        list.locator("label", new Locator.LocatorOptions().setHasText(second.getFullName())).locator("input").check();
        page.locator("#expertPickerAdd").click();
        page.locator("#expertPickerModal").waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));

        assertThat(visibleRows().count()).isEqualTo(2);
        assertThat(page.locator("#exp_name_1").inputValue()).isEqualTo(first.getFullName());
        assertThat(page.locator("#exp_info_1").inputValue()).contains("มหาวิทยาลัยขอนแก่น");
        assertThat(page.locator("#exp_name_2").inputValue()).isEqualTo(second.getFullName());

        page.locator("#btnPickExpert").click();
        page.locator("#expertPickerModal").waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        Locator again = list.locator("label", new Locator.LocatorOptions().setHasText(first.getFullName()));
        assertThat(again.locator("[data-already]").isVisible()).isTrue();
        assertThat(again.locator("input").isDisabled()).as("คนที่อยู่ในตารางแล้วเลือกซ้ำไม่ได้").isTrue();
    }

    @Test
    @DisplayName("ประธานกรรมการเลือกจากรายชื่อในตาราง พิมพ์เองไม่ได้ · ลบคนนั้นออกจากตาราง ช่องประธานว่าง")
    void chairIsPickedFromTheTable() {
        open(null);
        assertThat(page.locator("input[name=committee_name]").getAttribute("type")).isEqualTo("hidden");

        page.locator("#exp_name_1").fill("ศ.ดร.หนึ่ง ทดสอบ");
        page.locator("#exp_name_1").dispatchEvent("change");
        page.locator("#btnAddExpert").click();
        page.locator("#exp_name_2").fill("ศ.ดร.สอง ทดสอบ");
        page.locator("#exp_name_2").dispatchEvent("change");

        page.locator("#chairPicker").selectOption("ศ.ดร.สอง ทดสอบ");
        assertThat(page.locator("#committeeName").inputValue()).isEqualTo("ศ.ดร.สอง ทดสอบ");

        page.locator("[data-expert-row='1'] [data-expert-remove]").click();
        assertThat(page.locator("#committeeName").inputValue())
                .as("แถวที่ลบคือแถว 1 — ประธาน (แถว 2) เลื่อนขึ้นมาแต่ยังอยู่ในตาราง").isEqualTo("ศ.ดร.สอง ทดสอบ");

        page.locator("#btnAddExpert").click();
        page.locator("[data-expert-row='1'] [data-expert-remove]").click();
        assertThat(page.locator("#committeeName").inputValue()).as("ประธานถูกลบออกจากตาราง").isEmpty();
        assertThat(page.locator("#chairPicker option").count()).as("เหลือแค่ตัวเลือกว่าง").isEqualTo(1);
    }

    @Test
    @DisplayName("เปิดเอกสารที่บันทึกไว้: แสดงถึงแถวสุดท้ายที่มีข้อมูล ไม่ซ่อนรายชื่อที่กรอกไว้")
    void savedRowsStayVisible() {
        open("{\"name1\":\"ศ.ดร.หนึ่ง ทดสอบ\",\"name3\":\"ศ.ดร.สาม ทดสอบ\"}");

        assertThat(visibleRows().count()).as("แถว 2 ว่างแต่อยู่ก่อนแถว 3 ที่มีข้อมูล").isEqualTo(3);
        assertThat(page.locator("#exp_name_3").inputValue()).isEqualTo("ศ.ดร.สาม ทดสอบ");
    }
}
