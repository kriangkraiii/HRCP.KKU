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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which parts of the ก.พ.ว.มข.03 form actually offer "เลือกจาก Scopus".
 *
 * <p>This was the reported fault — "งานวิจัยตอนขอตำแหน่งยังไม่มีให้เลือกเลย" — and it
 * was never a bug in the picker. The picker worked; it was simply absent from
 * most of the form, and entirely absent from the ผู้ช่วยศาสตราจารย์ section, which
 * is the section most applicants use. Someone applying for ผศ. saw no button
 * anywhere on the page and concluded the feature did not exist.
 *
 * <p>The check is a template scan rather than a browser test on purpose: it
 * covers every section at once, including the two that are hidden until the
 * applicant picks a target position, and it runs in milliseconds. The browser
 * test in {@code ScopusPickerE2ETest} then proves the buttons that do exist
 * actually work.
 *
 * <p>Every group is covered now (GAP-10, fixed). What the checks below are for
 * is the next person who adds a group to the form: a new container with no
 * preset, or a preset no button reaches, fails here rather than being discovered
 * by an applicant who cannot find their research.
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
    @DisplayName("ส่วน ผู้ช่วยศาสตราจารย์ ต้องมีปุ่มเลือกจาก Scopus ครบทั้งสามกลุ่ม")
    void assistantProfessorSectionOffersThePicker() {
        assertThat(containersReachableFromAPreset())
                .as("""
                        นี่คือส่วนที่ผู้ยื่นส่วนใหญ่ใช้ และเป็นส่วนที่เคยไม่มีปุ่มเลยสักปุ่ม
                        จนผู้ใช้เข้าใจว่าระบบไม่มีให้เลือกงานวิจัย (GAP-10)""")
                .contains("asstResearchRows", "asstOtherRows", "asstBookRows");
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

    /**
     * The picker attaches a hidden publication id beside each citation it writes,
     * and that id is what ties the request to the work (GAP-11). Those fields are
     * built by the picker, not declared in this template, so a form reopened from
     * saved data came back without them — and the next save, read faithfully as
     * "this form cites nothing by id", dropped the links. The work then counted
     * as never submitted and could be put forward again.
     */
    @Test
    @DisplayName("เปิดฟอร์มที่บันทึกไว้ ต้องคืนค่า id ของผลงานที่ picker แนบไว้ด้วย")
    void reopeningTheFormRestoresThePublicationIds() {
        assertThat(source)
                .as("""
                        ลูปเติมค่ากลับเข้าฟอร์มข้ามคีย์ที่ไม่มี input รองรับ ซึ่งรวมถึง
                        _scopus_id_ ที่ picker สร้างขึ้นเอง — ต้องสร้างช่องซ่อนคืนให้ด้วย
                        ไม่งั้นการบันทึกครั้งถัดไปจะลบการผูกผลงานทิ้งเงียบ ๆ""")
                .contains("_scopus_id_")
                .contains("ScopusPicker.rememberId");
    }

    @Test
    @DisplayName("GAP-10: ทุกกลุ่มที่รับผลงานต้องเลือกจาก Scopus ได้")
    void everySectionThatTakesPublicationsOffersThePicker() {
        assertThat(containersReachableFromAPreset())
                .as("กลุ่มที่ขาดปุ่ม แปลว่าผู้ยื่นต้องพิมพ์ชื่อผลงานเองทั้งหมดในกลุ่มนั้น")
                .containsAll(CONTAINERS_THAT_TAKE_PUBLICATIONS);
    }
}
