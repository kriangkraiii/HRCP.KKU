package com.ecom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Proves the real {@code application.properties} resolves the e-sign settings.
 *
 * <p>Reads the main file directly rather than through {@code @SpringBootTest}:
 * the test classpath carries its own {@code application.properties} which
 * replaces it by name, so a normal context test would never see these keys.
 * Every {@code @Value} in the code has a default, which means a typo in the real
 * file would otherwise go unnoticed — the app would quietly run on defaults.
 */
@DisplayName("การตั้งค่า e-Sign ใน application.properties")
class EsignConfigResolutionTest {

    /**
     * The real file, by path.
     *
     * <p>Not via the classpath: {@code src/test/resources/application.properties}
     * shadows it there, so a classpath lookup silently reads the test config and
     * every assertion below would pass on the wrong file.
     */
    private static final FileSystemResource MAIN_CONFIG =
            new FileSystemResource("src/main/resources/application.properties");

    private StandardEnvironment mainProperties() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new ResourcePropertySource(
                MAIN_CONFIG));
        return env;
    }

    @Test
    @DisplayName("ทุกค่า resolve ได้ รวมถึง placeholder ซ้อนของ signature-dir")
    void allKeysResolve() throws IOException {
        StandardEnvironment env = mainProperties();

        String baseUrl = env.getProperty("app.esign.base-url");
        String signDir = env.getProperty("app.upload.signature-dir");

        System.out.println("PROBE_BASEURL: " + baseUrl);
        System.out.println("PROBE_SIGDIR: " + signDir);
        System.out.println("PROBE_DAYS: " + env.getProperty("app.esign.reminder-after-days"));
        System.out.println("PROBE_CRON: " + env.getProperty("app.esign.reminder-cron"));
        System.out.println("PROBE_SECRET: '" + env.getProperty("app.esign.hmac-secret") + "'");

        // Works out of the box on a developer machine, over the app's own https port.
        assertThat(baseUrl).isEqualTo("https://localhost:8081");

        // The nested ${app.upload.dir} default must expand, not survive literally.
        assertThat(signDir).doesNotContain("${").endsWith("signatures");

        assertThat(env.getProperty("app.esign.reminder-after-days")).isEqualTo("3");
        assertThat(env.getProperty("app.esign.reminder-cron")).isEqualTo("0 30 8 * * *");

        // The signing key is configured (in the file, which is gitignored — see
        // the next test). Without it signatures are recorded unsealed.
        assertThat(env.getProperty("app.esign.hmac-secret"))
                .as("ต้องตั้งกุญแจผนึกหลักฐาน ไม่งั้นลายเซ็นจะไม่ถูกผนึก")
                .isNotBlank()
                .hasSizeGreaterThanOrEqualTo(32);
    }

    /**
     * The signing key lives in {@code application.properties}, which is only safe
     * while that file stays out of version control.
     *
     * <p>A key in the repository is a key anyone with repo access can forge
     * signature evidence with, which would defeat the whole point of sealing. The
     * file is currently covered by {@code *.properties} in the module's
     * .gitignore — this asserts that has not been narrowed or removed.
     */
    @Test
    @DisplayName("application.properties ต้องถูก gitignore เพราะมีกุญแจอยู่ในไฟล์")
    void theConfigFileIsNotVersionControlled() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "git", "check-ignore", "src/main/resources/application.properties");
        pb.directory(new java.io.File("."));
        Process process = pb.start();
        int exit = process.waitFor();

        assertThat(exit)
                .as("application.properties มีกุญแจ HMAC อยู่ จึงต้องถูก .gitignore เสมอ "
                        + "(git check-ignore ต้องคืนค่า 0)")
                .isZero();
    }
}
