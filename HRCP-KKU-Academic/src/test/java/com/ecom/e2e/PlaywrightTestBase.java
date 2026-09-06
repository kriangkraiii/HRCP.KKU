package com.ecom.e2e;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.ecom.support.NoRealMailConfig;
import com.ecom.support.RecordingMailSender;
import com.ecom.support.RequiredTools;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;

/**
 * เบราว์เซอร์จริง บนพอร์ตจริง
 *
 * <p>ทุกอย่างที่เหลือในชุดทดสอบหยุดอยู่ที่ชั้น controller ซึ่งทิ้งชั้นที่แอปพลิเคชันนี้
 * ใช้ชีวิตอยู่จริงไว้โดยไม่มีอะไรตรวจ: modal เลือกงานวิจัยจาก Scopus, แถวที่งอกตามการกด,
 * ตัวบันทึกแบบร่างอัตโนมัติ, canvas วาดลายเซ็น, Content-Security-Policy ที่ห้าม inline handler
 * และเทมเพลตที่คอมไพล์ผ่านฉลุยแล้วไปพังตอนมีคนเปิดหน้า
 *
 * <p><b>ทำไมถึงเลิกใช้ Docker</b> — เดิมชั้นนี้ผูกกับ PostgreSQL ผ่าน Testcontainers
 * และมี {@code @EnabledIfDockerAvailable} กำกับ ผลคือมันข้ามตัวเองเงียบ ๆ ทุกครั้งที่
 * Docker ไม่ได้เปิด และเนื่องจาก {@code pom.xml} ยังกันมันออกจาก {@code mvn test}
 * ด้วย ในขณะที่ CI ไม่เคยส่ง {@code -Pe2e} เลย <b>ชั้นเบราว์เซอร์จึงไม่เคยรันจริง
 * แม้แต่ครั้งเดียว</b> ทั้งที่บั๊กที่รายงานเข้ามาเกือบทั้งหมดอยู่ในชั้นนี้
 *
 * <p>ตอนนี้ใช้ H2 ชุดเดียวกับที่ {@code AbstractFlowTest} ใช้ จึงรันใน {@code mvn test}
 * ได้ทุกครั้งโดยไม่ต้องมี Docker ส่วนเรื่องที่ต้องใช้ PostgreSQL จริง — partial index,
 * check constraint, การพับตัวพิมพ์ของชื่อ identifier — ยังมี
 * {@code MigrationOnPostgresTest} รับผิดชอบอยู่ตามเดิม
 *
 * <p>เมื่อ trace ถูกเขียนไว้ที่ {@code target/playwright/} เปิดดูทีละเฟรมได้ด้วย
 * {@code npx playwright show-trace <file>}
 */
@Tag("browser")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ NoRealMailConfig.class, TestDataFactory.class })
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:browserdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "fs.api.enabled=false",
        "fs.sync.on-startup=false",
        "cp.web.enabled=false",
        "cp.sync.on-startup=false",
        "app.alert.email.enabled=false",
        // ดู GAP-13 — ทางลัดบัญชีทดสอบต้องปิดไว้ ไม่อย่างนั้นการตรวจขอบเขต
        // การมองเห็นผลงานจะผ่านด้วยเหตุผลที่ผิด
        "app.user.email=__no_universal_access__@example.invalid",
        "app.upload.certificate-dir=${java.io.tmpdir}/hrcp-test-certificates"
})
public abstract class PlaywrightTestBase {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestDataFactory data;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSenderImpl mailSenderBean;

    /** The recording sender installed by {@link NoRealMailConfig}. */
    protected RecordingMailSender mail() {
        return (RecordingMailSender) mailSenderBean;
    }

    protected BrowserContext browserContext;
    protected Page page;

    /** ข้อผิดพลาดที่เบราว์เซอร์รายงานระหว่างเทสหนึ่งตัว */
    private final List<String> browserComplaints = new ArrayList<>();

    /** เทสที่ตั้งใจให้เกิดข้อผิดพลาด เรียกตัวนี้เพื่อยกเว้นตัวเอง */
    protected void toleratingBrowserErrors() {
        browserComplaints.clear();
        toleratingErrors = true;
    }

    private boolean toleratingErrors;

    /**
     * Playwright ดาวน์โหลดเบราว์เซอร์เองในการรันครั้งแรก ซึ่งต้องต่อเน็ตได้
     * เครื่องที่ออฟไลน์จึงข้ามชั้นนี้ไป ส่วน CI ตั้ง {@code -Dhrcp.tools.required=true}
     * ไว้ การข้ามจึงกลายเป็นความล้มเหลวที่นั่น
     */
    @BeforeAll
    static void launchBrowser() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
        } catch (Throwable t) {
            closeBrowser();
            RequiredTools.require(false, "เบราว์เซอร์ของ Playwright (" + t.getMessage() + ")");
        }
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        data.reset();
        mail().clear();
        browserComplaints.clear();
        toleratingErrors = false;

        browserContext = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(baseUrl())
                .setIgnoreHTTPSErrors(true));
        browserContext.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(true));
        page = browserContext.newPage();
        watchForBrowserErrors(page);
    }

    /**
     * ดักทุกอย่างที่เบราว์เซอร์บ่น แล้วทำให้มันเป็นความล้มเหลวของเทส
     *
     * <p><b>นี่คือส่วนที่ให้ผลมากที่สุดของชั้นนี้ทั้งชั้น</b> บั๊กหมวด "กดปุ่มแล้วไม่มีอะไรเกิดขึ้น"
     * เกือบทั้งหมดมีร่องรอยอยู่ใน console อยู่แล้ว — {@code TypeError} จากตัวแปรที่เป็น null,
     * สคริปต์ที่โหลดไม่สำเร็จ, การละเมิด Content-Security-Policy — แต่ไม่มีอะไรอ่านมัน
     * เพราะไม่เคยมีใครเปิดเบราว์เซอร์ตอนรันเทส
     *
     * <p>ผลคือเทสเบราว์เซอร์ทุกตัว ทั้งที่มีอยู่และที่จะเขียนใหม่ กลายเป็นเครื่องดักบั๊ก
     * ในตัวโดยที่คนเขียนไม่ต้องเพิ่ม assertion อะไรเลย
     */
    private void watchForBrowserErrors(Page target) {
        target.onConsoleMessage(message -> {
            if ("error".equals(message.type()) && !isKnownTranslateWidgetNoise(message.text())) {
                browserComplaints.add("console error: " + message.text());
            }
        });
        target.onPageError(error -> browserComplaints.add("JavaScript ตาย: " + error));

        target.onResponse(response -> {
            if (response.status() >= 400 && response.url().startsWith(baseUrl())) {
                browserComplaints.add("โหลด " + response.url() + " ไม่สำเร็จ (HTTP "
                        + response.status() + ")");
            }
        });
    }

    /**
     * เสียงรบกวนจาก widget แปลภาษาของ Google ที่ไม่ใช่ความผิดของโค้ดในโปรเจกต์
     *
     * <p>widget ตัวนี้ยัด inline style เข้ามาเองและโหลด stylesheet จาก
     * {@code www.gstatic.com} ซึ่ง {@code style-src} ของแอปไม่ได้อนุญาต ผลคือทุกหน้า
     * พ่น CSP violation ออกมาหลายสิบบรรทัด ถ้านับเป็นความล้มเหลวด้วย ด่านนี้จะ
     * ใช้งานไม่ได้เลยเพราะบั๊กจริงจะจมหายไปในกองเดียวกัน
     *
     * <p><b>ไม่ได้แปลว่าเรื่องนี้ไม่สำคัญ</b> — มันคือบั๊ก UI-07 ในรายงาน
     * {@code docs/reports/bug-report-2026-09-06.md}: ปุ่มเปลี่ยนภาษาแสดงผลไม่ถูกต้อง
     * ทุกหน้าเพราะ stylesheet ของมันถูกบล็อก การกรองที่นี่คือการแยก "เรื่องที่รู้แล้ว
     * และมีเจ้าของ" ออกจาก "เรื่องใหม่ที่ต้องรู้ทันที" ไม่ใช่การกวาดไว้ใต้พรม
     * เมื่อ UI-07 ถูกแก้แล้ว ให้ลบเมธอดนี้ทิ้ง
     */
    private static boolean isKnownTranslateWidgetNoise(String text) {
        return text.contains("gstatic.com")
                || text.contains("translate.googleapis.com") && text.contains("Applying inline style")
                || text.contains("Applying inline style violates");
    }

    @AfterEach
    void closePage(TestInfo info) {
        Path trace = Paths.get("target", "playwright",
                info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("trace") + ".zip");
        try {
            browserContext.tracing().stop(new Tracing.StopOptions().setPath(trace));
        } finally {
            browserContext.close();
        }

        if (!toleratingErrors && !browserComplaints.isEmpty()) {
            throw new AssertionError("เบราว์เซอร์รายงานข้อผิดพลาดระหว่างเทสนี้:\n  - "
                    + String.join("\n  - ", browserComplaints.stream().distinct().toList())
                    + "\n(เปิดดูทีละเฟรมได้ที่ " + trace + ")");
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Signs in through the real form.
     *
     * <p>Deliberately not a shortcut that injects a session: the login form,
     * its CSRF token and the success handler's redirect are all part of what
     * these tests are here to exercise.
     */
    protected void signIn(String email, String password) {
        page.navigate(baseUrl() + "/signin");
        page.fill("input[name='email']", email);
        page.fill("input[name='password']", password);
        page.click("button[type='submit']");
        page.waitForURL(url -> !url.contains("/signin"));
        completeTwoFactorIfAsked();
    }

    /**
     * Finishes the second factor when the account needs one.
     *
     * <p>Every {@code ROLE_ADMIN} account does, always — {@code
     * SignInService.requiresTwoFactor} enforces it for privileged accounts
     * regardless of the user's own toggle (ISO 27001 A.9). So an officer signing
     * in lands on {@code /2fa/verify} while a professor goes straight to their
     * dashboard, and a test that assumed one flow for both was left holding an
     * unauthenticated session.
     *
     * <p>The code is read out of the e-mail the application actually sent, not
     * out of the database: only a hash is stored. That makes this a real
     * exercise of the 2FA path, delivery included.
     */
    private void completeTwoFactorIfAsked() {
        if (!page.url().contains("/2fa")) {
            return;
        }

        String otp = awaitOneTimeCode();

        // Typed key by key rather than filled. The verify button stays disabled
        // until the page's own script has seen every digit, and it watches key
        // events — a programmatic fill sets the values but leaves the button
        // disabled, and the click then waits forever.
        // One digit per cell, addressed explicitly. Typing the whole code into
        // the first cell races the page's auto-advance: each keystroke has to
        // land after focus has moved on, and Playwright types faster than that.
        com.microsoft.playwright.Locator cells = page.locator("#otpInputs .otp-cell");
        for (int i = 0; i < otp.length() && i < cells.count(); i++) {
            cells.nth(i).pressSequentially(String.valueOf(otp.charAt(i)));
        }

        com.microsoft.playwright.assertions.PlaywrightAssertions
                .assertThat(page.locator("#verifyBtn")).isEnabled();
        page.click("#verifyBtn");
        page.waitForURL(url -> !url.contains("/2fa"));
    }

    /**
     * Pulls the one-time code out of the most recent captured message.
     *
     * <p>Matched as element <em>text</em> — {@code >12345678<} — rather than as
     * any run of digits. The HTML body is full of inline styles, and a plain
     * {@code \d{6,8}} happily returns the {@code 475569} out of a colour like
     * {@code #475569} long before it reaches the code.
     */
    private String awaitOneTimeCode() {
        java.util.regex.Pattern code = java.util.regex.Pattern.compile(">(\\d{8})<");
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            java.util.List<RecordingMailSender.Sent> sent = mail().captured();
            for (int i = sent.size() - 1; i >= 0; i--) {
                String body = sent.get(i).body();
                if (body == null) {
                    continue;
                }
                java.util.regex.Matcher m = code.matcher(body);
                if (m.find()) {
                    return m.group(1);
                }
            }
            page.waitForTimeout(100);
        }
        throw new AssertionError("ไม่ได้รับอีเมลรหัส OTP ภายใน 10 วินาที (จับอีเมลได้ "
                + mail().count() + " ฉบับ)");
    }

    /**
     * Signs out the way the sidebar button does — a POST carrying the CSRF token.
     *
     * <p>Navigating to {@code /logout} does nothing: Spring Security accepts
     * logout only as a POST while CSRF is on, so a GET left the previous user
     * signed in and the next sign-in landed somewhere unexpected. The layout's
     * own form is {@code <form method="post" action="/logout">}.
     */
    protected void signOut() {
        com.microsoft.playwright.Locator button =
                page.locator("form[action='/logout'] button[type='submit']");
        if (button.count() > 0) {
            button.first().click();
            page.waitForURL("**/signin**");
        } else {
            // No layout on this page (an error screen, say) — drop the session
            // rather than leaving the next sign-in to fight the old one.
            browserContext.clearCookies();
        }
    }
}
