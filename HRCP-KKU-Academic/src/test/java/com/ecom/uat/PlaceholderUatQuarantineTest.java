package com.ecom.uat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stops the placeholder UAT suite from growing, and from being mistaken for
 * coverage.
 *
 * <p>The ten {@code UAT_*} classes in this package contain 238 test methods
 * whose entire body is {@code assertTrue(true, "…")}. They execute no
 * application code at all. Reported as "238 passing UAT cases" they look like
 * the strongest part of the build; in fact they are the weakest, because they
 * cannot fail.
 *
 * <p>They are left in place rather than deleted because
 * {@code UAT_Test_Cases.docx} — already submitted — is written against their
 * case numbers, and throwing away someone's submitted deliverable is not a call
 * this suite should make on its own. What this test does is hold the line: the
 * count may go down as cases are replaced by real tests, never up.
 *
 * <p>The real coverage for these areas now lives in {@code com.ecom.flow},
 * {@code com.ecom.academic}, {@code com.ecom.research} and
 * {@code com.ecom.notification}. See {@code src/test/java/README.md}.
 */
@DisplayName("UAT ชุดเดิม: กันไม่ให้เทสหลอกเพิ่มขึ้นอีก")
class PlaceholderUatQuarantineTest {

    private static final Path UAT_PACKAGE = Path.of("src/test/java/com/ecom/uat");

    /**
     * The count as found on 31 August 2569, per file.
     *
     * <p>Written out rather than totalled so that replacing one file's cases
     * with real tests shows up as that file's number falling, and cannot be
     * masked by another file's rising.
     */
    private static final Map<String, Integer> PLACEHOLDERS_WHEN_MEASURED = new LinkedHashMap<>();
    static {
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_AcademicRequestWorkflowTest.java", 46);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_SecurityAccessControlTest.java", 35);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_AdminManagementTest.java", 34);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_AcademicApplicantTest.java", 27);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_AuthenticationTest.java", 23);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_DocumentGenerationTest.java", 18);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_PositionRequestTest.java", 18);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_FileManagementTest.java", 16);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_StaffMemberTest.java", 11);
        PLACEHOLDERS_WHEN_MEASURED.put("UAT_UserProfileTest.java", 10);
    }

    @Test
    @DisplayName("จำนวนเทสหลอกต้องไม่เพิ่มขึ้นจากเดิมในทุกไฟล์")
    void placeholderCountNeverGrows() throws IOException {
        Map<String, Integer> current = countPlaceholdersPerFile();

        assertThat(current.keySet())
                .as("มีไฟล์ UAT ใหม่ที่เทสนี้ยังไม่รู้จัก — ถ้าเป็นเทสจริงให้ย้ายออกจากแพ็กเกจ uat")
                .isSubsetOf(PLACEHOLDERS_WHEN_MEASURED.keySet());

        current.forEach((file, count) -> assertThat(count)
                .as("""
                        %s มีเทสหลอก (assertTrue(true)) เพิ่มขึ้น

                        เทสแบบนี้ไม่เรียกโค้ดของระบบเลยและไม่มีทาง fail จึงไม่ใช่ความครอบคลุม
                        ถ้าต้องการเพิ่มเคส ให้เขียนเทสจริงใน com.ecom.flow / academic /
                        research / notification แทน — ดู src/test/java/README.md""".formatted(file))
                .isLessThanOrEqualTo(PLACEHOLDERS_WHEN_MEASURED.get(file)));
    }

    @Test
    @DisplayName("เทสหลอกต้องอยู่แต่ในแพ็กเกจ uat เท่านั้น ห้ามลามไปที่อื่น")
    void thePatternDoesNotSpreadElsewhere() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            var offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(UAT_PACKAGE))
                    .filter(p -> !p.endsWith("PlaceholderUatQuarantineTest.java"))
                    .filter(p -> countPlaceholders(p) > 0)
                    .toList();

            assertThat(offenders)
                    .as("พบเทสที่ assert ค่าคงที่นอกแพ็กเกจ uat — เทสแบบนี้ไม่มีทาง fail")
                    .isEmpty();
        }
    }

    private Map<String, Integer> countPlaceholdersPerFile() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(UAT_PACKAGE)) {
            files.filter(p -> p.getFileName().toString().startsWith("UAT_"))
                    .forEach(p -> counts.put(p.getFileName().toString(), countPlaceholders(p)));
        }
        return counts;
    }

    private static int countPlaceholders(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            int count = 0;
            int at = 0;
            while ((at = source.indexOf("assertTrue(true", at)) >= 0) {
                count++;
                at += "assertTrue(true".length();
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }
}
