package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * The guard must wrap the sender Boot actually builds. As a plain
 * {@code @Configuration} it was evaluated before Boot's mail setup, found no
 * sender, and was skipped — so every service got the raw, unfiltered sender.
 */
@DisplayName("ตัวกรองอีเมลถูกติดตั้งจริงในแอป")
class GuardedMailSenderWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TestAccountRegistry.class, () -> new TestAccountRegistry(""))
            .withConfiguration(AutoConfigurations.of(
                    MailSenderAutoConfiguration.class, GuardedMailSenderConfig.class))
            .withPropertyValues("spring.mail.host=relay.example.invalid", "spring.mail.port=587");

    /** โหมด dev ตั้งค่า static ใน EmailTemplateHelper — คืนค่าไม่ให้รั่วไปเทสต์อื่นใน JVM เดียวกัน */
    @org.junit.jupiter.api.AfterEach
    void resetDevCapture() {
        com.ecom.util.EmailTemplateHelper.setDevCapture(false);
    }

    @Test
    @DisplayName("production — ห่อด้วยตัวกรอง และไม่แตะ SMTP ที่ตั้งไว้")
    void productionWrapsWithoutTouchingSmtp() {
        runner.withPropertyValues("app.auth.mode=production").run(ctx -> {
            assertThat(ctx.getBean(JavaMailSender.class)).isInstanceOf(GuardedJavaMailSender.class);
            JavaMailSenderImpl impl = ctx.getBean(JavaMailSenderImpl.class);
            assertThat(impl.getHost()).isEqualTo("relay.example.invalid");
            assertThat(impl.getPort()).isEqualTo(587);
        });
    }

    @Test
    @DisplayName("dev — ห่อด้วยตัวกรอง และส่งทุกฉบับไป Mailpit ในเครื่อง")
    void devRetargetsToMailpit() {
        runner.withPropertyValues("app.auth.mode=dev").run(ctx -> {
            assertThat(ctx.getBean(JavaMailSender.class)).isInstanceOf(GuardedJavaMailSender.class);
            JavaMailSenderImpl impl = ctx.getBean(JavaMailSenderImpl.class);
            assertThat(impl.getHost()).isEqualTo("localhost");
            assertThat(impl.getPort()).isEqualTo(1025);
        });
    }

    /**
     * แต่ละบริการตรวจ isTestEmail เองก่อนถึงตัวส่ง — ถ้าโหมด dev ยังข้ามบัญชีทดสอบ
     * แจ้งสถานะ แจ้งลงนาม และแจ้งแก้ไขของบัญชี test.* จะไม่ถึง Mailpit เลย
     */
    @Test
    @DisplayName("dev — บริการส่งอีเมลไม่ข้ามบัญชีทดสอบ ส่วน production ยังข้าม")
    void devStopsServicesSkippingTestAccounts() {
        runner.withPropertyValues("app.auth.mode=dev").run(ctx -> assertThat(
                com.ecom.util.EmailTemplateHelper.isTestEmail("test.applicant@kku.ac.th")).isFalse());
        runner.withPropertyValues("app.auth.mode=production").run(ctx -> assertThat(
                com.ecom.util.EmailTemplateHelper.isTestEmail("test.applicant@kku.ac.th")).isTrue());
    }
}
