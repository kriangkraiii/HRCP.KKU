package com.ecom.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for the workflow suite.
 *
 * <p><b>Why one base class rather than per-test properties.</b> Spring caches an
 * application context per distinct configuration, and this application takes a
 * few seconds to start. The tests already in the repository each declare their
 * own {@code @TestPropertySource} with a uniquely named H2 database, so every
 * one of them pays for a fresh context. Everything extending this class shares a
 * single set of properties and therefore a single context.
 *
 * <p><b>Why H2 here and PostgreSQL elsewhere.</b> These tests are about workflow
 * rules — who may do what, in which order, and what the system does in response.
 * None of that is dialect-sensitive, and requiring a Docker daemon to run the
 * everyday build would be a poor trade. The places where the database engine
 * genuinely matters get real PostgreSQL instead: {@code MigrationOnPostgresTest}
 * for the migrations and the {@code *E2ETest} suite for the full journey.
 *
 * <p><b>Mail.</b> {@link NoRealMailConfig} is imported here rather than on each
 * test, so a test cannot forget it and open an SMTP connection.
 */
@SpringBootTest
@Import({ NoRealMailConfig.class, TestDataFactory.class })
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:flowdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false",
        // NoRealMailConfig replaces Boot's `mailSender` bean by name, which is
        // the only way to take the live transport out of the context without
        // also removing the guard that wraps it.
        "spring.main.allow-bean-definition-overriding=true",
        // Nothing in this suite may reach the campus systems.
        "fs.api.enabled=false",
        "fs.sync.on-startup=false",
        "cp.web.enabled=false",
        "cp.sync.on-startup=false",
        "app.alert.email.enabled=false",
        // Index writes run on the calling thread, so a test can assert on the
        // index row straight after the save instead of racing the executor.
        // This belongs here rather than in the search tests: overriding it in a
        // subclass would fork the Spring context and cost the whole suite
        // another boot, which is the thing this base class exists to avoid.
        "app.search.async=false",
        // The universal-access shortcut is off unless a test asks for it: with a
        // real address here every publication-scoping assertion would pass for
        // the wrong reason. See GAP-13 in docs/GAP-REPORT-flow-vs-implementation.md.
        "app.user.email=__no_universal_access__@example.invalid",
        // .p12 files that TestDataFactory installs are written to disk. Left at
        // the default they land in the repository's own uploads/ directory and
        // accumulate there, one per test that signs anything.
        "app.upload.certificate-dir=${java.io.tmpdir}/hrcp-test-certificates"
})
public abstract class AbstractFlowTest {

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected TestDataFactory data;

    /** The recording sender installed by {@link NoRealMailConfig}. */
    @Autowired
    private JavaMailSenderImpl mailSenderBean;

    protected MockMvc mvc;

    protected RecordingMailSender mail() {
        return (RecordingMailSender) mailSenderBean;
    }

    /**
     * Asserts a POST was accepted and acted on, not bounced.
     *
     * <p>Worth its own helper because the obvious assertion is a trap. A POST
     * that fails CSRF is redirected to {@code /signin?expired=true} by this
     * application's authentication entry point — {@code CsrfFilter} runs before
     * authentication, so the context is still anonymous when it denies. That is
     * a 3xx, so {@code status().is3xxRedirection()} passes and the test carries
     * on believing the form was submitted. Every assertion after it then checks
     * the effects of a request that never happened.
     *
     * @param expectedPrefix where a successful post should send the browser
     */
    protected void expectAccepted(org.springframework.test.web.servlet.ResultActions result,
            String expectedPrefix) throws Exception {
        String redirect = result.andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        org.assertj.core.api.Assertions.assertThat(redirect)
                .as("ถูกเด้งไปหน้าเข้าสู่ระบบ แปลว่า POST ไม่ผ่าน (ลืม .with(csrf()) หรือสิทธิ์ไม่พอ)")
                .doesNotStartWith("/signin");
        org.assertj.core.api.Assertions.assertThat(redirect)
                .as("ปลายทางหลัง POST ไม่ตรงกับที่คาด")
                .startsWith(expectedPrefix);
    }

    /**
     * Waits for a condition that a background thread will satisfy.
     *
     * <p>Almost every notification in this system is raised from an
     * {@code @Async} method, so asserting immediately after the call under test
     * races the executor and produces a test that passes on a fast machine and
     * fails in CI. Polling rather than sleeping keeps the common case quick.
     *
     * @param description what is being waited for, used in the failure message
     */
    protected void awaitCondition(String description, java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + description, e);
            }
        }
        throw new AssertionError("รอ 5 วินาทีแล้วยังไม่เกิด: " + description);
    }

    /** Waits until at least {@code expected} messages have been captured. */
    protected void awaitMailCount(int expected) {
        awaitCondition("อีเมลอย่างน้อย " + expected + " ฉบับ (จับได้จริง " + mail().count() + ")",
                () -> mail().count() >= expected);
    }

    /**
     * Gives a background thread a chance to do something we expect it NOT to do.
     *
     * <p>Asserting "no mail was sent" immediately after the call proves nothing
     * — the executor may simply not have run yet. There is no event to wait for
     * in the negative case, so a short settle is the honest option.
     */
    protected void settle() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void setUpFlowTest() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        // The context is shared, so state left by the previous test would
        // otherwise leak into this one — including, for the security context,
        // into GuardedJavaMailSender's "was this a test actor" decision.
        data.reset();
        mail().clear();
        SecurityContextHolder.clearContext();
    }
}
