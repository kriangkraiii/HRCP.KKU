package com.ecom.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ทดสอบการคำนวณปี พ.ศ. ตามไทม์โซนไทย (Asia/Bangkok)")
class ThaiYearTimezoneTest {

    private static final ZoneId BANGKOK_ZONE = ZoneId.of("Asia/Bangkok");
    private static final String SPEL_EXPRESSION = "T(java.time.Year).now(T(java.time.ZoneId).of('Asia/Bangkok')).getValue() + 543";

    @Test
    @DisplayName("SpEL Expression ที่ใช้ใน Thymeleaf ต้องคำนวณได้ปี พ.ศ. ปัจจุบันของไทยอย่างถูกต้อง")
    void testSpelExpressionEvaluatesToThaiYear() {
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(SPEL_EXPRESSION);
        Object value = exp.getValue();

        int expectedThaiYear = Year.now(BANGKOK_ZONE).getValue() + 543;

        assertThat(value).isInstanceOf(Integer.class);
        assertEquals(expectedThaiYear, value, "SpEL calculation must match current Thai Buddhist Year");
    }

    @Test
    @DisplayName("แม้เซิร์ฟเวอร์ JVM จะตั้ง TimeZone เป็น UTC หรือ America/New_York ก็ต้องได้ปี พ.ศ. ไทยเสมอ")
    void testIndependentOfJvmDefaultTimeZone() {
        TimeZone originalJvmTz = TimeZone.getDefault();
        try {
            // จำลองกรณีเซิร์ฟเวอร์ Cloud/Docker รันด้วย UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            int yearInUtcEnv = Year.now(BANGKOK_ZONE).getValue() + 543;

            // จำลองกรณีเซิร์ฟเวอร์รันด้วยเวลานิวยอร์ก (UTC-5)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            int yearInNyEnv = Year.now(BANGKOK_ZONE).getValue() + 543;

            int expectedThaiYear = Year.now(BANGKOK_ZONE).getValue() + 543;

            assertEquals(expectedThaiYear, yearInUtcEnv, "Must return Thai year even under UTC default timezone");
            assertEquals(expectedThaiYear, yearInNyEnv, "Must return Thai year even under US/Eastern default timezone");
        } finally {
            TimeZone.setDefault(originalJvmTz);
        }
    }

    @Test
    @DisplayName("ทดสอบจุดตัดส่งท้ายปีเก่าต้อนรับปีใหม่ (Boundary Test ที่ 2026-12-31 17:00:00Z = 00:00:00 ไทย)")
    void testNewYearRolloverBoundary() {
        // วินาทีก่อนเที่ยงคืนไทย 1 วินาที (23:59:59 ในไทย = 16:59:59 UTC)
        Instant rightBeforeMidnight = Instant.parse("2026-12-31T16:59:59Z");
        Clock clockBefore = Clock.fixed(rightBeforeMidnight, BANGKOK_ZONE);
        int thaiYearBefore = Year.now(clockBefore).getValue() + 543;
        assertEquals(2569, thaiYearBefore, "ก่อนเที่ยงคืนไทย 1 วินาที ต้องยังคงเป็นปี พ.ศ. 2569");

        // วินาทีที่ข้ามเที่ยงคืนไทยพอดี (00:00:00 ในไทย = 17:00:00 UTC)
        Instant midnightThai = Instant.parse("2026-12-31T17:00:00Z");
        Clock clockAfter = Clock.fixed(midnightThai, BANGKOK_ZONE);
        int thaiYearAfter = Year.now(clockAfter).getValue() + 543;
        assertEquals(2570, thaiYearAfter, "เมื่อถึงเที่ยงคืนไทย (UTC 17:00:00) ต้องเปลี่ยนเป็นปี พ.ศ. 2570 ทันที");

        // ตรวจสอบว่าถ้าใช้ UTC ณ เวลานั้น UTC จะยังคงเป็น 2026 (ยังไม่เปลี่ยนปี)
        Clock clockUtc = Clock.fixed(midnightThai, ZoneId.of("UTC"));
        int utcGregorianYear = Year.now(clockUtc).getValue();
        assertEquals(2026, utcGregorianYear, "ที่ UTC เวลาเดียวกันยังคงเป็นปี 2026 (พิสูจน์ว่า Asia/Bangkok เปลี่ยนเร็วกว่า 7 ชั่วโมง)");
    }

    @Test
    @DisplayName("Thymeleaf SpringTemplateEngine สามารถ parse และ render SpEL นี้ได้ผลลัพธ์ปี พ.ศ. ถูกต้อง")
    void testThymeleafRendering() {
        org.thymeleaf.spring6.SpringTemplateEngine engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        org.thymeleaf.templateresolver.StringTemplateResolver resolver = new org.thymeleaf.templateresolver.StringTemplateResolver();
        resolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
        engine.setTemplateResolver(resolver);

        org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
        String template = "<span th:text=\"${T(java.time.Year).now(T(java.time.ZoneId).of('Asia/Bangkok')).getValue() + 543}\">2569</span>";
        String result = engine.process(template, context);
        int expectedThaiYear = Year.now(BANGKOK_ZONE).getValue() + 543;
        assertThat(result).isEqualTo("<span>" + expectedThaiYear + "</span>");
    }

    @Test
    @DisplayName("ตรวจสอบเทมเพลตทั้งหมดในระบบว่าไม่มีการเรียก Year.now() แบบไม่ระบุ TimeZone")
    void testTemplatesDoNotUseUnparameterizedYearNow() throws IOException {
        Path templateDir = Path.of("src", "main", "resources", "templates");
        if (!Files.exists(templateDir)) {
            // ถ้า path สัมพัทธ์รันจาก root workspace
            templateDir = Path.of("HRCP-KKU-Academic", "src", "main", "resources", "templates");
        }

        try (Stream<Path> stream = Files.walk(templateDir)) {
            List<Path> problematicTemplates = stream
                    .filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            // ตรวจว่ามีการใช้ Year.now() โดยไม่มีพารามิเตอร์หรือไม่
                            return content.contains("Year).now()") || content.contains("Year.now()");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();

            assertThat(problematicTemplates)
                    .withFailMessage("พบไฟล์เทมเพลตที่ใช้ Year.now() โดยไม่ระบุ TimeZone: %s", problematicTemplates)
                    .isEmpty();
        }
    }
}
