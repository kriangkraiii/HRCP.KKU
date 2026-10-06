package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;

/**
 * คำนำหน้าที่เก็บไว้แบบมี ดร. ต้องไปตกที่ตัวเลือกตำแหน่งวิชาการในฟอร์มเฟส 2
 *
 * <p>ฟอร์มเฟส 2 เติมค่าด้วยสคริปต์ฝั่งเบราว์เซอร์ (ข้อมูลที่บันทึกไว้ และโปรไฟล์ผู้ใช้)
 * เทส MockMvc จึงเห็นแค่รายการตัวเลือก ไม่เห็นค่าที่ถูกเลือกจริง
 */
@DisplayName("คำนำหน้าในฟอร์มขอตำแหน่ง — ผศ.ดร. แสดงเป็น ผู้ช่วยศาสตราจารย์ (เหมือนเฟส 1)")
class PositionTitleSelectBrowserTest extends PlaywrightTestBase {

    private UserDtls applicant;

    @BeforeEach
    void signedIn() {
        applicant = data.applicant();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
    }

    private String selectedTitle(PositionRequest request, int type) {
        page.navigate(baseUrl() + "/user/position/request/" + request.getId() + "/document/" + type);
        // select_to_datalist.js เปลี่ยน <select> เป็น <input> + <datalist> — ช่องคำนำหน้าจึงเป็น
        // element ใดก็ได้ที่ติดเครื่องหมายไว้ และรอแค่ให้อยู่บนหน้า
        page.waitForSelector("[data-academic-title]",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));
        // สคริปต์เติมค่าจากโปรไฟล์หน่วงไว้เล็กน้อยหลังโหลดหน้า
        page.waitForFunction("() => document.querySelector('[data-academic-title]').value !== ''");
        return (String) page.evalOnSelector("[data-academic-title]", "el => el.value");
    }

    @Test
    @DisplayName("ข้อมูลที่บันทึกไว้เป็น ผศ.ดร. — เลือก ผู้ช่วยศาสตราจารย์")
    void aSavedTitleWithTheDoctorateLandsOnTheRank() {
        PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        data.positionDocument(draft, 2, "{\"title\":\"ผศ.ดร.\",\"applicant_name\":\"สมชาย ใจดี\"}");

        assertThat(selectedTitle(draft, 2)).isEqualTo("ผู้ช่วยศาสตราจารย์");
    }

    @Test
    @DisplayName("โปรไฟล์เป็น รศ.ดร. และยังไม่ได้กรอก — เลือก รองศาสตราจารย์ และไม่เติมตัวเลือกที่มี ดร.")
    void aProfileTitleWithTheDoctorateLandsOnTheRank() {
        applicant.setTitle("รศ.ดร.");
        data.saveUser(applicant);
        PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

        assertThat(selectedTitle(draft, 3)).isEqualTo("รองศาสตราจารย์");
        // รายการให้เลือก — <datalist> ที่ช่องชี้ไป (ถอด list ออกแล้วเก็บ id ไว้ที่ data-combo-list)
        // หรือตัว <select> เองถ้ายังไม่ถูกเปลี่ยน
        String options = (String) page.evalOnSelector("[data-academic-title]",
                "el => { const l = el.list || document.getElementById(el.dataset.comboList || '');"
                        + " return l ? l.innerHTML : el.innerHTML; }");
        assertThat(options).doesNotContain("ดร.");
    }
}
