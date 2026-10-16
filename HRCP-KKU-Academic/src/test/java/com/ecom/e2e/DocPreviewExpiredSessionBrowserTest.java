package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;

/**
 * session หมดระหว่างเปิดฟอร์ม แล้วกดดูตัวอย่างเอกสาร — ต้องบอกว่าหมดเวลาเข้าสู่ระบบ
 *
 * <p>server redirect คำขอที่ไม่มี session ไปหน้าเข้าสู่ระบบ fetch ตามไปเองได้หน้า login สถานะ 200
 * เดิมหน้า login จึงไปโผล่ในกรอบตัวอย่าง (และปุ่ม Word ได้ไฟล์ .docx ที่ข้างในเป็น HTML) โดยไม่บอกอะไร
 */
@DisplayName("ดูตัวอย่างเอกสารหลัง session หมด บอกให้เข้าสู่ระบบใหม่")
class DocPreviewExpiredSessionBrowserTest extends PlaywrightTestBase {

    @Test
    @DisplayName("กดดูตัวอย่าง — ขึ้นข้อความหมดเวลาเข้าสู่ระบบ ไม่ใช่หน้า login ในกรอบ")
    void anExpiredSessionIsExplainedInThePreview() {
        toleratingBrowserErrors();
        UserDtls applicant = data.applicant();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        AcademicRequest evaluation = data.evaluationFor(applicant, AcademicRank.ASSISTANT_PROFESSOR, "อาจารย์");
        PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                evaluation, "ผู้ช่วยศาสตราจารย์");

        page.navigate(baseUrl() + "/user/position/request/" + draft.getId() + "/document/1");
        page.waitForSelector("input[name='major']");
        // ลบเฉพาะคุกกี้ session — คุกกี้ CSRF อยู่ต่อ เหมือน session หมดอายุจริง
        var keep = page.context().cookies().stream()
                .filter(c -> !c.name.equals("HRCPSID") && !c.name.equals("JSESSIONID"))
                .toList();
        page.context().clearCookies();
        page.context().addCookies(keep);

        page.locator(".floating-doc-btn-preview").click();

        Locator error = page.locator(".docx-preview-overlay .u-fs-0-85rem-c-999");
        error.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        assertThat(error.textContent()).contains("หมดเวลาเข้าสู่ระบบ");
    }
}
