package com.ecom.e2e;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecom.support.NoRealMailConfig;
import com.ecom.support.RecordingMailSender;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;

/**
 * A real browser against a real PostgreSQL, on a real port.
 *
 * <p>Everything else in the suite stops at the controller. That leaves the layer
 * this application actually lives in untested: the Scopus picker modal, the rows
 * that grow on demand, the auto-draft listener, the signature canvas, the
 * Content-Security-Policy that forbids inline handlers, and Thymeleaf templates
 * that compile perfectly and only fail when somebody opens the page. Those are
 * where the reported faults are.
 *
 * <p><b>PostgreSQL, not H2.</b> Production runs PostgreSQL and builds its schema
 * with Flyway, while the ordinary test profile builds it from the entities on
 * H2. Anything that depends on the database being the real one — a partial
 * index, a check constraint, an enum column, case folding on identifiers — is
 * invisible everywhere except here.
 *
 * <p><b>Skips instead of failing when Docker is down.</b>
 * {@code @EnabledIfDockerAvailable} means a developer without Docker Desktop
 * running gets a skipped test with a reason, not a wall of connection errors.
 *
 * <p>On failure a Playwright trace is written to {@code target/playwright/} —
 * open it with {@code npx playwright show-trace <file>} to step through the run
 * frame by frame.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ NoRealMailConfig.class, TestDataFactory.class })
public abstract class PlaywrightTestBase {

    // NOTE: @EnabledIfDockerAvailable belongs on each concrete subclass, not
    // here. Its condition resolves the *test class* from the extension context,
    // and on an abstract base there is none — the whole class errors out with
    // "required test class is not present" instead of skipping.


    /**
     * One container for every E2E class in the run.
     *
     * <p>Started once and never stopped: Ryuk removes it when the JVM exits.
     * Starting a database per class would add roughly ten seconds each time and
     * buy nothing, because {@link TestDataFactory#reset()} already isolates the
     * tests from one another.
     */
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("hrcp_e2e")
                    .withUsername("hrcp")
                    .withPassword("hrcp");

    static {
        if (dockerIsAvailable()) {
            POSTGRES.start();
        }
    }

    private static boolean dockerIsAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES != null && POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:playwrightfallbackdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
        }
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // The migrations get their own dedicated test against this same image;
        // running them here as well would collide with ddl-auto building the
        // schema from the entities, which is what the running application does.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("server.ssl.enabled", () -> "false");
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
        registry.add("fs.api.enabled", () -> "false");
        registry.add("cp.web.enabled", () -> "false");
        registry.add("cp.sync.on-startup", () -> "false");
        registry.add("app.alert.email.enabled", () -> "false");
        registry.add("app.user.email", () -> "__no_universal_access__@example.invalid");
    }

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

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
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
        browserContext = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(baseUrl())
                .setIgnoreHTTPSErrors(true));
        browserContext.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(true));
        page = browserContext.newPage();
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
