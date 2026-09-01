package com.ecom.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import com.ecom.support.RecordingMailSender;

/**
 * The suite's own guard rail: no test run may e-mail a real person.
 *
 * <p>Three things have to hold, and each has its own failure mode:
 *
 * <ol>
 *   <li>The recorder must actually reject a real-looking address, or the other
 *       two checks are worthless.</li>
 *   <li>{@code SendAllTestEmailsTest} — which connects to Gmail and sends to a
 *       real inbox — must stay disabled. It is one deleted annotation away from
 *       running in CI.</li>
 *   <li>No <em>other</em> test may quietly build its own live sender.</li>
 * </ol>
 *
 * <p>These are cheap, run with no Spring context, and fail with an explanation
 * rather than a stack trace, because whoever trips them will not be expecting to.
 */
@DisplayName("ตาข่ายนิรภัย: ห้ามเทสส่งอีเมลถึงคนจริง")
class MailSafetyNetTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");

    @Nested
    @DisplayName("ตัวบันทึกอีเมลของชุดทดสอบ")
    class RecorderTests {

        @Test
        @DisplayName("จับข้อความไว้แทนการส่ง — ไม่เปิดการเชื่อมต่อ SMTP เลย")
        void recordsInsteadOfSending() {
            RecordingMailSender sender = new RecordingMailSender();
            // Deliberately points at a port nothing is listening on: if the
            // recorder ever fell through to a real transport, this would throw.
            sender.setHost("127.0.0.1");
            sender.setPort(1);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("somchai@example.invalid");
            message.setSubject("แจ้งผลการประเมิน");
            message.setText("เนื้อความทดสอบ");

            assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();

            assertThat(sender.count()).isEqualTo(1);
            assertThat(sender.captured().get(0).wentTo("somchai@example.invalid")).isTrue();
            assertThat(sender.captured().get(0).subjectContains("แจ้งผลการประเมิน")).isTrue();
        }

        @Test
        @DisplayName("ที่อยู่นอกโดเมนสงวน — ต้อง fail ทันที ไม่ใช่บันทึกเงียบ ๆ")
        void rejectsRealAddresses() {
            RecordingMailSender sender = new RecordingMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("kriangkrai.p@kkumail.com");
            message.setSubject("ไม่ควรถูกบันทึก");
            message.setText("-");

            assertThatThrownBy(() -> sender.send(message))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("kriangkrai.p@kkumail.com")
                    .hasMessageContaining("example.invalid");
        }

        @Test
        @DisplayName("อ่านที่อยู่ออกจากรูปแบบ \"ชื่อ <อีเมล>\" ได้ถูกต้อง")
        void rejectsRealAddressBehindDisplayName() {
            RecordingMailSender sender = new RecordingMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("Somchai Jaidee <somchai@kku.ac.th>");
            message.setSubject("-");
            message.setText("-");

            assertThatThrownBy(() -> sender.send(message)).isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("บัญชีทดสอบในตัวของระบบ (user@user.com) ยังใช้ได้")
        void allowsBuiltInTestAccounts() {
            RecordingMailSender sender = new RecordingMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("user@user.com");
            message.setSubject("-");
            message.setText("-");

            assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();
            assertThat(sender.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("เทสที่ยิง SMTP จริงต้องปิดไว้เสมอ")
    class LiveSenderTests {

        /**
         * {@code SendAllTestEmailsTest} logs into Gmail with credentials
         * committed to this repository and mails a real address. Its
         * {@code @Disabled} is the only thing standing between a routine
         * "let's run everything" and a live send, so that annotation is
         * asserted rather than trusted.
         */
        @Test
        @DisplayName("SendAllTestEmailsTest ต้องมี @Disabled กำกับอยู่")
        void liveSmtpTestStaysDisabled() throws IOException {
            Path file = TEST_SOURCES.resolve("com/ecom/util/SendAllTestEmailsTest.java");
            assertThat(file).as("ไฟล์เทส SMTP จริงหายไป — ถ้าลบทิ้งแล้วให้ลบเทสข้อนี้ด้วย").exists();

            String source = Files.readString(file, StandardCharsets.UTF_8);
            String beforeClass = source.substring(0, source.indexOf("class SendAllTestEmailsTest"));

            assertThat(beforeClass)
                    .as("""
                            SendAllTestEmailsTest เชื่อมต่อ smtp.gmail.com และส่งอีเมลถึงที่อยู่จริง
                            ถ้าเอา @Disabled ออก การรัน `mvn test` จะส่งอีเมลออกไปจริง""")
                    .contains("@Disabled");
        }

        @Test
        @DisplayName("ไม่มีเทสอื่นสร้าง JavaMailSenderImpl ชี้ไปเซิร์ฟเวอร์ SMTP ภายนอก")
        void noOtherTestBuildsALiveSender() throws IOException {
            List<Path> offenders;
            try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
                offenders = files
                        .filter(p -> p.toString().endsWith(".java"))
                        // The two files that are allowed to mention a mail host:
                        // the disabled live test, and this guard itself.
                        .filter(p -> !p.endsWith("SendAllTestEmailsTest.java"))
                        .filter(p -> !p.endsWith("MailSafetyNetTest.java"))
                        .filter(MailSafetyNetTest::mentionsExternalSmtpHost)
                        .toList();
            }

            assertThat(offenders)
                    .as("""
                            เทสเหล่านี้อ้างถึงเซิร์ฟเวอร์ SMTP ภายนอก ซึ่งแปลว่าอาจส่งอีเมลจริงออกไป
                            ให้ใช้ RecordingMailSender ผ่าน AbstractFlowTest แทน""")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("ห้าม commit รหัสผ่านอีเมลลงรีโป (GAP-05)")
    class CredentialTests {

        /**
         * A Gmail app password used to sit in {@code application.properties} as
         * the default for {@code spring.mail.password}, and again in
         * {@code SendAllTestEmailsTest}. A credential in a committed file is a
         * leaked credential: it is in every clone and in the history of each.
         *
         * <p>Both are now environment-only, and this is what stops the next
         * person from putting one back "just while I test something".
         */
        @Test
        @DisplayName("ไฟล์ properties ต้องไม่มีรหัสผ่านหรือบัญชีจริงเป็นค่าเริ่มต้น")
        void noMailCredentialIsCommitted() throws IOException {
            for (Path file : List.of(Path.of("src/main/resources/application.properties"),
                    Path.of("src/test/resources/application.properties"))) {
                if (!Files.exists(file)) {
                    continue;
                }
                assertThat(credentialDefaultsIn(file))
                        .as("""
                                %s ตั้งค่าเริ่มต้นของบัญชี/รหัสผ่านอีเมลไว้ในไฟล์ — ค่านั้นถูก commit
                                ลง git แปลว่าหลุดไปแล้ว ให้เว้นว่างไว้แล้วส่งผ่าน environment เท่านั้น
                                เช่น spring.mail.password=${EMAIL_PASSWORD:}""", file)
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("เทส SMTP จริงต้องอ่านรหัสผ่านจาก environment ไม่ใช่จากตัวไฟล์")
        void theLiveTestCarriesNoPasswordOfItsOwn() throws IOException {
            String source = Files.readString(
                    TEST_SOURCES.resolve("com/ecom/util/SendAllTestEmailsTest.java"),
                    StandardCharsets.UTF_8);

            assertThat(source)
                    .as("รหัสผ่านต้องมาจาก environment เท่านั้น")
                    .doesNotContain("setPassword(\"")
                    .contains("EMAIL_PASSWORD");
        }
    }

    /**
     * Lines like {@code spring.mail.password=${EMAIL_PASSWORD:something}} — a
     * placeholder whose fallback is a real value rather than empty.
     */
    private static List<String> credentialDefaultsIn(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.startsWith("#"))
                .filter(line -> line.startsWith("spring.mail.password")
                        || line.startsWith("spring.mail.username"))
                .filter(line -> !line.matches(".*:}\\s*$"))   // ${VAR:} — empty fallback, fine
                .filter(line -> line.contains(":"))
                .filter(line -> !line.matches("^[^=]+=\\$\\{[A-Z_]+}$"))  // ${VAR} — no fallback, fine
                .toList();
    }

    private static boolean mentionsExternalSmtpHost(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            return source.contains("smtp.gmail.com")
                    || source.contains("smtp.office365.com")
                    || source.contains("setHost(\"smtp.");
        } catch (IOException e) {
            return false;
        }
    }
}
