package com.ecom.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which parts of the ก.พ.ว.มข.03 form actually offer "เลือกจาก Scopus".
 *
 * <p>This is the reported fault — "งานวิจัยตอนขอตำแหน่งยังไม่มีให้เลือกเลย" — and
 * it is not a bug in the picker. The picker works; it is simply absent from most
 * of the form, and entirely absent from the ผู้ช่วยศาสตราจารย์ section, which is
 * the section most applicants use. Someone applying for ผศ. sees no button
 * anywhere on the page and concludes the feature does not exist.
 *
 * <p>The check is a template scan rather than a browser test on purpose: it
 * covers every section at once, including the two that are hidden until the
 * applicant picks a target position, and it runs in milliseconds. The browser
 * test in {@code ScopusPickerE2ETest} then proves the buttons that do exist
 * actually work.
 *
 * <p>The first test records today's coverage so the gap is visible in a test
 * report and not only in a document; the disabled one states the target.
 */
@DisplayName("งานวิจัย: ความครอบคลุมของปุ่ม 'เลือกจาก Scopus' ในแบบ ก.พ.ว.มข.03")
class ScopusPickerCoverageTest {

    private static final Path FORM = Path.of(
            "src/main/resources/templates/academic/position/applicant/doc_form_1.html");

    /**
     * Every container on the form that collects the title of a piece of
     * academic work, and so ought to be fillable from Scopus.
     *
     * <p>Listed rather than derived: the form also has row containers for
     * education, teaching, administrative posts, speaking engagements and
     * research grants, none of which are publications. Deriving the distinction
     * from markup would be guesswork; naming it is a decision.
     */
    private static final List<String> CONTAINERS_THAT_TAKE_PUBLICATIONS = List.of(
            // ผู้ช่วยศาสตราจารย์
            "asstResearchRows", "asstOtherRows", "asstBookRows",
            // รองศาสตราจารย์
            "assocResearchRows", "assocOtherRows", "assocBookRows", "assocM3ResearchRows",
            // ศาสตราจารย์
            "profResearchRows", "profOtherRows", "profBookRows", "profM3ResearchRows");

    private static String source;

    @BeforeAll
    static void readForm() throws IOException {
        assertThat(FORM).as("ไม่พบไฟล์แบบฟอร์ม ก.พ.ว.มข.03").exists();
        source = Files.readString(FORM, StandardCharsets.UTF_8);
    }

    /** Container ids the form declares, e.g. {@code <div id="asstResearchRows">}. */
    private static Set<String> declaredContainers() {
        return matches(Pattern.compile("id=\"(\\w+Rows)\""));
    }

    /** Preset names a button actually invokes via {@code data-call-args}. */
    private static Set<String> presetsWiredToAButton() {
        return matches(Pattern.compile(
                "data-call=\"openScopusPreset\"\\s+data-call-args='\\[\"(\\w+)\"\\]'"));
    }

    /** The container each entry of {@code SCOPUS_PRESETS} targets. */
    private static Set<String> containersReachableFromAPreset() {
        Set<String> reachable = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(\\w+):\\s*\\{\\s*container:\\s*'(\\w+)'").matcher(source);
        Set<String> wired = presetsWiredToAButton();
        while (m.find()) {
            if (wired.contains(m.group(1))) {
                reachable.add(m.group(2));
            }
        }
        return reachable;
    }

    private static Set<String> matches(Pattern pattern) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = pattern.matcher(source);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("รายชื่อกล่องรับผลงานที่เทสนี้อ้างถึง มีอยู่จริงในแบบฟอร์มทุกตัว")
    void theContainersThisTestNamesAllExist() {
        assertThat(declaredContainers())
                .as("ถ้า id ในแบบฟอร์มถูกเปลี่ยนชื่อ เทสนี้ต้องรู้ตัว ไม่ใช่ผ่านไปเงียบ ๆ")
                .containsAll(CONTAINERS_THAT_TAKE_PUBLICATIONS);
    }

    @Test
    @DisplayName("GAP-10: ส่วน ผู้ช่วยศาสตราจารย์ ไม่มีปุ่มเลือกจาก Scopus แม้แต่ปุ่มเดียว")
    void assistantProfessorSectionHasNoPickerAtAll() {
        Set<String> reachable = containersReachableFromAPreset();

        assertThat(reachable)
                .as("""
                        ผู้ยื่นที่เลือกตำแหน่ง 'ผู้ช่วยศาสตราจารย์' จะเห็นเฉพาะ sectionAsst
                        ซึ่งไม่มีปุ่ม 'เลือกจาก Scopus' เลย จึงต้องพิมพ์ชื่อผลงานเองทั้งหมด
                        นี่คืออาการ 'ไม่มีงานวิจัยให้เลือก' ที่ผู้ใช้รายงาน
                        ถ้าเทสข้อนี้เริ่ม fail แปลว่า GAP-10 ถูกแก้แล้ว — ให้เปิดเทส
                        everySectionThatTakesPublicationsShouldOfferThePicker แทน""")
                .doesNotContain("asstResearchRows", "asstOtherRows", "asstBookRows");
    }

    @Test
    @DisplayName("GAP-10: กล่องรับผลงาน 11 กลุ่ม มีปุ่มเลือกจาก Scopus เพียง 3 กลุ่ม")
    void onlyThreeOfElevenSectionsOfferThePicker() {
        Set<String> reachable = containersReachableFromAPreset();

        assertThat(reachable)
                .as("ปุ่มที่มีอยู่จริงในวันนี้")
                .containsExactlyInAnyOrder(
                        "assocResearchRows", "assocM3ResearchRows", "profResearchRows");

        List<String> missing = CONTAINERS_THAT_TAKE_PUBLICATIONS.stream()
                .filter(c -> !reachable.contains(c))
                .toList();

        assertThat(missing)
                .as("กลุ่มที่ยังขาดปุ่ม — ผู้ยื่นต้องพิมพ์ชื่อผลงานเองทั้งหมด")
                .containsExactly("asstResearchRows", "asstOtherRows", "asstBookRows",
                        "assocOtherRows", "assocBookRows",
                        "profOtherRows", "profBookRows", "profM3ResearchRows");
    }

    @Test
    @DisplayName("ทุก preset ที่ประกาศไว้ ต้องมีปุ่มเรียกใช้จริง (ไม่มีโค้ดตาย)")
    void everyDeclaredPresetIsReachable() {
        Set<String> declared = matches(Pattern.compile("(\\w+):\\s*\\{\\s*container:\\s*'\\w+'"));

        assertThat(presetsWiredToAButton())
                .as("preset ที่ประกาศแต่ไม่มีปุ่มเรียก คือโค้ดที่ไม่มีใครใช้")
                .containsExactlyInAnyOrderElementsOf(declared);
    }

    @Test
    @DisplayName("ทุกปุ่มที่เรียก preset ต้องชี้ไปยัง preset ที่มีอยู่จริง (ไม่ชี้ไปที่ว่าง)")
    void everyButtonPointsAtARealPreset() {
        Set<String> declared = matches(Pattern.compile("(\\w+):\\s*\\{\\s*container:\\s*'\\w+'"));

        assertThat(declared)
                .as("""
                        ปุ่มที่ชี้ไป preset ที่ไม่มีอยู่จะ log error ใน console แล้วเงียบไป
                        ผู้ใช้จะเห็นแค่ปุ่มที่กดแล้วไม่เกิดอะไรขึ้น""")
                .containsAll(presetsWiredToAButton());
    }

    @Test
    @Disabled("GAP-10: ต้องเพิ่ม preset และปุ่มให้ครบทั้ง 11 กลุ่มก่อน")
    @DisplayName("GAP-10 (spec): ทุกกลุ่มที่รับผลงานต้องเลือกจาก Scopus ได้")
    void everySectionThatTakesPublicationsShouldOfferThePicker() {
        assertThat(containersReachableFromAPreset())
                .containsAll(CONTAINERS_THAT_TAKE_PUBLICATIONS);
    }
}
