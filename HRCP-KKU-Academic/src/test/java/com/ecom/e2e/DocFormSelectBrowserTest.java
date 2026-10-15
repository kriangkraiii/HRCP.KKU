package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * ตัวเลือกในฟอร์มเอกสาร (select_to_datalist.js): ช่องที่ต้องเป็นค่าในรายการยังเป็น dropdown
 * มีแต่คำนำหน้าชื่อที่พิมพ์ค่านอกรายการได้
 *
 * <p>เดิมสคริปต์เปลี่ยน {@code <select>} ทุกตัวในฟอร์มเป็นช่องพิมพ์ได้ — ตำแหน่งที่ขอ วิธีการ สถานะ
 * จึงพิมพ์อะไรก็ได้ ทั้งที่กติกาฝั่งเซิร์ฟเวอร์อ่านได้เฉพาะค่าในรายการ
 */
@DisplayName("E2E: ฟอร์มเอกสาร — dropdown เลือกจากรายการเท่านั้น ยกเว้นคำนำหน้าชื่อ")
class DocFormSelectBrowserTest extends PlaywrightTestBase {

    private static final String OLD_FREE_TEXT = "ตำแหน่งที่เคยพิมพ์เอง";

    @Test
    @DisplayName("ตำแหน่งที่เสนอขอเป็น dropdown · คำนำหน้าพิมพ์เองได้ · ค่าเก่าที่ไม่อยู่ในรายการไม่หาย")
    void onlyTheTitleAcceptsFreeText() {
        UserDtls applicant = data.applicant();
        PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        data.positionDocument(draft, 2, "{\"title\":\"ผศ.ดร.\",\"request_position\":\"" + OLD_FREE_TEXT + "\"}");
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);

        page.navigate(baseUrl() + "/user/position/request/" + draft.getId() + "/document/2");
        page.waitForSelector("[name=request_position]",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));

        assertThat(page.evalOnSelector("[name=request_position]", "el => el.tagName"))
                .as("ตำแหน่งที่เสนอขอต้องเลือกจากรายการ").isEqualTo("SELECT");
        assertThat(page.evalOnSelector("[data-academic-title]", "el => el.tagName"))
                .as("คำนำหน้าชื่อพิมพ์ค่าที่ไม่มีในรายการได้").isEqualTo("INPUT");
        assertThat(page.locator("[name=request_position] option[value='" + OLD_FREE_TEXT + "']").count())
                .as("ค่าที่บันทึกไว้แต่ไม่อยู่ในรายการต้องยังเห็น ไม่หายเงียบ ๆ ตอนเปิดฟอร์ม").isEqualTo(1);
    }
}
