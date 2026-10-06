package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentCompleteness;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;

/**
 * ปุ่มกรอกข้อมูลทดสอบ (โหมด dev) ต้องทำให้เอกสารผ่านด่านความครบถ้วนของเซิร์ฟเวอร์จริง
 *
 * <p>ตัวชี้วัดคือ {@link DocumentCompleteness#missingApplicantFields} ตัวเดียวกับที่กันการส่งลงนาม
 * ไม่ใช่แค่ "ช่องบนหน้าจอไม่ว่าง" — ถ้าค่าไปไม่ถึงฐานข้อมูล เครื่องมือนี้ก็ไม่ได้ช่วยใครทดสอบ
 */
@DisplayName("กรอกข้อมูลทดสอบ (dev) — เอกสารเฟส 2 ผ่านด่านความครบถ้วน")
class DevFormFillerBrowserTest extends PlaywrightTestBase {

    private static final String TARGET = "ผู้ช่วยศาสตราจารย์";

    @Autowired
    private PositionRequestService positionService;

    private final ObjectMapper mapper = new ObjectMapper();

    private PositionRequest draft;

    @BeforeEach
    void signedIn() {
        UserDtls applicant = data.applicant();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        // หลังล็อกอิน — แดชบอร์ดที่ล็อกอินพาไปลบร่างคำร้องที่ยังว่างทิ้ง
        draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null, TARGET);
    }

    private java.util.List<String> missing(int type) throws Exception {
        Map<String, String> saved = positionService.getLatestDocumentData(draft.getId(), type);
        assertThat(saved).as("เอกสารที่ %d ต้องถูกบันทึก", type).isNotNull();
        return DocumentCompleteness.missingApplicantFields(SignatureModule.POSITION, type,
                mapper.writeValueAsString(saved), TARGET);
    }

    @Test
    @DisplayName("ปุ่มในฟอร์มเอกสารที่ 1 — กรอกและบันทึกร่างจนไม่เหลือช่องที่ยังว่าง")
    void fillsOneForm() throws Exception {
        page.navigate(baseUrl() + "/user/position/request/" + draft.getId() + "/document/1");
        page.click(".dev-fill-btn");
        page.waitForSelector(".dev-fill-status[data-tone='ok']");

        assertThat(missing(1)).isEmpty();
        assertThat(positionService.getLatestDocumentData(draft.getId(), 1))
                .as("ช่องสารบรรณออกเลขหลังลงนาม ต้องไม่ถูกเติม")
                .doesNotContainKey("memo_no");
    }

    @Test
    @DisplayName("ปุ่มกรอกทุกเอกสารในหน้าคำร้อง — เอกสารของผู้ยื่นครบทุกฉบับ")
    void fillsEveryApplicantDocument() throws Exception {
        page.navigate(baseUrl() + "/user/position/request/" + draft.getId());
        page.click(".dev-fill-btn");
        page.waitForFunction("() => /เสร็จ/.test(document.querySelector('.dev-fill-status').textContent)",
                null, new Page.WaitForFunctionOptions().setTimeout(180_000));

        String status = page.textContent(".dev-fill-status");
        assertThat(status).as(page.textContent(".dev-fill-log")).contains("เสร็จทุกฉบับ");
        for (int type : PositionRequestService.APPLICANT_DOCS) {
            assertThat(missing(type)).as("เอกสารที่ %d", type).isEmpty();
        }
    }

    @Test
    @DisplayName("ฝั่งแอดมิน — เอกสารที่ 7 (ช่องมากที่สุด) กรอกและบันทึกร่างได้ ไม่เลื่อนสถานะคำร้อง")
    void fillsTheLargestAdminForm() {
        PositionRequest received = data.positionRequest(data.applicant(),
                PositionRequestStatus.DOCUMENT_RECEIVED, null, TARGET);
        data.admin();
        browserContext.clearCookies();
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);

        page.navigate(baseUrl() + "/admin/position/request/" + received.getId() + "/document/7");
        page.click(".dev-fill-btn");
        page.waitForSelector(".dev-fill-status[data-tone='ok']");

        assertThat(positionService.getLatestDocumentData(received.getId(), 7)).hasSizeGreaterThan(50);
        assertThat(positionService.findById(received.getId()).orElseThrow().getCurrentStatus())
                .as("บันทึกแค่ร่าง — สถานะต้องอยู่ที่เดิม")
                .isEqualTo(PositionRequestStatus.DOCUMENT_RECEIVED);
    }
}
