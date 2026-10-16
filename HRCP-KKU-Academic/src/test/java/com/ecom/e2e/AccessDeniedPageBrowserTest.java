package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;

/**
 * POST ที่ถูกปฏิเสธต้องได้หน้า 403 ไม่ใช่ 405
 *
 * <p>Spring Security ส่งต่อ (forward) คำขอที่ถูกปฏิเสธไปที่ {@code /403} ด้วย method เดิม — เดิมหน้านั้นรับแค่ GET
 * POST ที่โทเค็น CSRF ไม่ผ่าน (ฟอร์มที่เปิดค้างไว้จน session หมด) หรือสิทธิ์ไม่พอ จึงจบที่ 405 Method Not Allowed
 * ซึ่งไม่บอกผู้ใช้ว่าเกิดอะไรขึ้น MockMvc ไม่ทำ forward จึงต้องทดสอบผ่านเซิร์ฟเวอร์จริง
 */
@DisplayName("POST ที่ถูกปฏิเสธได้หน้า 403")
class AccessDeniedPageBrowserTest extends PlaywrightTestBase {

    @Test
    @DisplayName("โทเค็น CSRF ไม่ถูกต้อง → 403 พร้อมหน้าแจ้ง ไม่ใช่ 405")
    void aRejectedPostGetsTheForbiddenPage() {
        toleratingBrowserErrors();
        data.applicant();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);

        APIResponse response = page.request().post(baseUrl() + "/user/feedback",
                RequestOptions.create().setForm(FormData.create()
                        .set("subject", "x").set("message", "y").set("_csrf", "not-the-token")));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("ผู้ยื่นส่ง POST เข้าหน้าของแอดมิน → 403 ไม่ใช่ 405")
    void aPostWithoutTheRoleGetsTheForbiddenPage() {
        toleratingBrowserErrors();
        data.applicant();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/academic/dashboard");
        String token = (String) page.evaluate("() => document.querySelector('meta[name=_csrf]').content");

        APIResponse response = page.request().post(baseUrl() + "/admin/updateSts",
                RequestOptions.create().setForm(FormData.create()
                        .set("status", "false").set("id", "1").set("type", "1").set("_csrf", token)));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("หน้า 404 และ 403 โหลด CSS ผ่าน CSP ได้ครบ แสดงข้อความภาษาไทย และปุ่มนำทางกลับแดชบอร์ดอย่างปลอดภัย")
    void errorPagesRenderStylesUnderCspAndNavigateSafely() {
        toleratingBrowserErrors();
        java.util.List<String> cspErrors = new java.util.ArrayList<>();
        page.onConsoleMessage(msg -> {
            if ("error".equals(msg.type()) && msg.text().contains("Content Security Policy")) {
                cspErrors.add(msg.text());
            }
        });

        data.applicant();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);

        // 1. เปิดหน้า 404 ที่ไม่มีอยู่จริง (ผ่าน ERROR dispatch)
        page.navigate(baseUrl() + "/user/non-existent-page-404-test");
        assertThat(page.locator(".error-code").textContent().trim()).isEqualTo("404");
        assertThat(page.locator(".error-message").textContent())
                .contains("หน้าที่ท่านกำลังค้นหา")
                .doesNotContain("Not Found");

        String borderRadius = (String) page.evaluate(
                "() => window.getComputedStyle(document.querySelector('.error-card')).borderRadius");
        assertThat(borderRadius).isEqualTo("24px");
        assertThat(cspErrors).isEmpty();

        // 2. กดปุ่ม 'กลับสู่หน้าหลัก' ขณะล็อกอินอยู่ ต้องพากลับแดชบอร์ดของผู้ใช้ ไม่ใช่เด้งไปหน้า /signin
        page.locator("a.btn-custom-primary").click();
        page.waitForURL("**/user/academic/dashboard**");
        assertThat(page.url()).contains("/user/academic/dashboard");

        // 3. เข้าหน้า /403 แล้วกดปุ่ม 'ย้อนกลับ' ต้องย้อนกลับมาหน้าแดชบอร์ดเดิมได้
        page.navigate(baseUrl() + "/admin/academic/dashboard");
        assertThat(page.locator(".error-code").textContent().trim()).isEqualTo("403");
        String borderRadius403 = (String) page.evaluate(
                "() => window.getComputedStyle(document.querySelector('.error-card')).borderRadius");
        assertThat(borderRadius403).isEqualTo("24px");
        assertThat(cspErrors).isEmpty();

        page.locator("#backBtn").click();
        page.waitForURL("**/user/academic/dashboard**");
        assertThat(page.url()).contains("/user/academic/dashboard");
    }

    @Test
    @DisplayName("สวิตช์เปลี่ยนภาษาแบบคลิกเดียว (TH ↔ EN) สลับสถานะได้ทันทีทั้งหน้าเข้าสู่ระบบและแถบด้านบน")
    void singleClickLanguageSwitchTogglesBetweenThAndEn() {
        java.nio.file.Path artifactDir = java.nio.file.Paths.get(
                "/Users/kriangkrai/.gemini/antigravity/brain/a1ddee9c-65bb-4f3e-8c66-a7f39f8a5409");

        // จำลองสคริปต์ Google Translate เพื่อให้ทดสอบการสลับ TH <-> EN ได้เสถียรโดยไม่พึ่งเครือข่ายภายนอก
        page.route("**/translate_a/element.js*", route -> {
            String mockScript = ""
                    + "window.google = { translate: { TranslateElement: function(opts, id) {"
                    + "  var root = document.getElementById(id);"
                    + "  if (root && !root.querySelector('select.goog-te-combo')) {"
                    + "    var sel = document.createElement('select');"
                    + "    sel.className = 'goog-te-combo';"
                    + "    sel.innerHTML = '<option value=\"th\">Thai</option><option value=\"en\">English</option>';"
                    + "    sel.addEventListener('change', function() {"
                    + "      if (sel.value === 'en') document.documentElement.classList.add('translated-ltr');"
                    + "      else document.documentElement.classList.remove('translated-ltr');"
                    + "      var tick = document.createElement('span');"
                    + "      tick.className = 'd-none';"
                    + "      document.body.appendChild(tick);"
                    + "    });"
                    + "    root.appendChild(sel);"
                    + "  }"
                    + "} } };"
                    + "if (typeof window.googleTranslateElementInit === 'function') window.googleTranslateElementInit();";
            route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/javascript")
                    .setBody(mockScript));
        });

        page.setViewportSize(1440, 900);

        // 1. ตรวจสอบสวิตช์ในหน้าเข้าสู่ระบบ (/signin)
        page.navigate(baseUrl() + "/signin");
        com.microsoft.playwright.Locator loginSwitch = page.locator("#langDropdownBtn");
        assertThat(loginSwitch.getAttribute("role")).isEqualTo("switch");
        assertThat(loginSwitch.getAttribute("data-active-lang")).isEqualTo("th");
        assertThat(loginSwitch.getAttribute("aria-checked")).isEqualTo("false");
        assertThat(page.locator("#langDropdownMenu").count()).isZero();

        if (java.nio.file.Files.isDirectory(artifactDir)) {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(artifactDir.resolve("lang_switch_login_th.png"))
                    .setClip(980, 0, 460, 120));
        }

        // คลิกครั้งเดียวเพื่อสลับเป็น EN บนหน้าเข้าสู่ระบบ
        loginSwitch.click();
        page.waitForCondition(() -> "en".equals(loginSwitch.getAttribute("data-active-lang"))
                && "false".equals(loginSwitch.getAttribute("aria-busy")));
        assertThat(loginSwitch.getAttribute("aria-checked")).isEqualTo("true");
        page.mouse().move(0, 0);
        page.waitForTimeout(350);

        if (java.nio.file.Files.isDirectory(artifactDir)) {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(artifactDir.resolve("lang_switch_login_en.png"))
                    .setClip(980, 0, 460, 120));
        }

        // คลิกอีกหนึ่งครั้งเพื่อสลับกลับเป็น TH
        loginSwitch.click();
        page.waitForCondition(() -> "th".equals(loginSwitch.getAttribute("data-active-lang"))
                && "false".equals(loginSwitch.getAttribute("aria-busy")));
        assertThat(loginSwitch.getAttribute("aria-checked")).isEqualTo("false");

        // 2. เข้าสู่ระบบและตรวจสอบสวิตช์บน Topbar (/user/academic/dashboard)
        data.applicant();
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/user/academic/dashboard");

        com.microsoft.playwright.Locator topbarSwitch = page.locator("#langDropdownBtn");
        assertThat(topbarSwitch.getAttribute("role")).isEqualTo("switch");
        assertThat(topbarSwitch.getAttribute("data-active-lang")).isEqualTo("th");

        if (java.nio.file.Files.isDirectory(artifactDir)) {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(artifactDir.resolve("lang_switch_topbar_light_th.png"))
                    .setClip(740, 0, 700, 72));
        }

        // คลิกครั้งเดียวสลับเป็น EN บน Topbar
        topbarSwitch.click();
        page.waitForCondition(() -> "en".equals(topbarSwitch.getAttribute("data-active-lang"))
                && "false".equals(topbarSwitch.getAttribute("aria-busy")));
        assertThat(topbarSwitch.getAttribute("aria-checked")).isEqualTo("true");
        page.mouse().move(0, 0);
        page.waitForTimeout(350);

        if (java.nio.file.Files.isDirectory(artifactDir)) {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(artifactDir.resolve("lang_switch_topbar_light_en.png"))
                    .setClip(740, 0, 700, 72));
        }

        // ตรวจสอบในโหมดมืด (Dark Mode)
        page.evaluate("() => document.documentElement.setAttribute('data-theme', 'dark')");
        page.waitForTimeout(200);
        if (java.nio.file.Files.isDirectory(artifactDir)) {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(artifactDir.resolve("lang_switch_topbar_dark_en.png"))
                    .setClip(740, 0, 700, 72));
        }

        // คลิกครั้งเดียวสลับกลับเป็น TH ในโหมดมืด
        topbarSwitch.click();
        page.waitForCondition(() -> "th".equals(topbarSwitch.getAttribute("data-active-lang"))
                && "false".equals(topbarSwitch.getAttribute("aria-busy")));
        page.mouse().move(0, 0);
        page.waitForTimeout(250);
        if (java.nio.file.Files.isDirectory(artifactDir)) {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(artifactDir.resolve("lang_switch_topbar_dark_th.png"))
                    .setClip(740, 0, 700, 72));
        }
    }
}
