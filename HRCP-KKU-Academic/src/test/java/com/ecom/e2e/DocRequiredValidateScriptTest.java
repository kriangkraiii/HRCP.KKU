package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * doc_required_validate.js บนเบราว์เซอร์จริง โดยไม่ต้องเปิดทั้งแอป
 *
 * <p>คอลัมน์ "เจ้าหน้าที่" ของเอกสารที่ 2 เฟส 1: ติ๊กแล้วหมายเหตุว่างได้ ไม่ติ๊กต้องเขียนหมายเหตุ
 * — กติกาเดียวกับ {@code DocumentCompleteness.missingAdminFields} ฝั่งเซิร์ฟเวอร์
 */
@Tag("browser")
@DisplayName("ตัวตรวจช่องที่ต้องกรอก: หมายเหตุของช่องติ๊กเจ้าหน้าที่")
class DocRequiredValidateScriptTest {

    private static Playwright playwright;
    private static Browser browser;
    private Page page;

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void open() {
        page = browser.newPage();
        page.setContent("""
                <form id="docForm">
                  <table><tr>
                    <td><input type="checkbox" id="c1" data-required-check data-note="text_1" data-label="ข้อ 1"></td>
                    <td><input type="text" name="text_1"></td>
                  </tr><tr>
                    <td><input type="checkbox" id="c2" data-required-check data-note="text_2" data-label="ข้อ 2"></td>
                    <td><input type="text" name="text_2"></td>
                  </tr></table>
                  <input type="text" name="other_field" value="กรอกแล้ว">
                  <button type="button" id="save">บันทึก</button>
                </form>
                """);
        page.addScriptTag(new Page.AddScriptTagOptions()
                .setPath(Path.of("src/main/resources/static/js/doc_required_validate.js")));
    }

    @AfterEach
    void close() {
        page.close();
    }

    private boolean check() {
        return (Boolean) page.evaluate(
                "() => DocRequiredFields.checkForm(document.getElementById('docForm'), document.getElementById('save'))");
    }

    @SuppressWarnings("unchecked")
    private List<String> flagged() {
        return (List<String>) page.evaluate(
                "() => Array.from(document.querySelectorAll('.is-invalid')).map(e => e.name || e.id)");
    }

    @Test
    @DisplayName("ติ๊กครบทุกแถว หมายเหตุว่าง — ผ่าน ไม่มีช่องแดง")
    void tickedRowsNeedNoRemark() {
        page.check("#c1");
        page.check("#c2");
        assertThat(check()).isTrue();
        assertThat(flagged()).isEmpty();
    }

    @Test
    @DisplayName("ไม่ติ๊กแต่มีหมายเหตุ — ผ่าน")
    void untickedRowWithRemarkPasses() {
        page.check("#c1");
        page.fill("[name=text_2]", "ไม่เห็นเอกสาร");
        assertThat(check()).isTrue();
    }

    @Test
    @DisplayName("ไม่ติ๊กและไม่มีหมายเหตุ — กั้นไว้ที่ช่องติ๊กของแถวนั้น ไม่ใช่ที่หมายเหตุของแถวที่ติ๊กแล้ว")
    void untickedRowWithoutRemarkIsFlagged() {
        page.check("#c1");
        assertThat(check()).isFalse();
        assertThat(flagged()).containsExactly("c2");
    }
}
