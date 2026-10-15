package com.ecom.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The "เลือกจากฐานข้อมูลผลงาน" button on position document 6 (รายการผลงานวิจัย).
 *
 * <p>{@link ScopusPickerCoverageTest} only reads document 1, so this button had no
 * test at all. Like that class this is a template scan: it checks the wiring the
 * picker depends on, which a browser test would only reach after creating a
 * request and walking to document 6.
 */
@DisplayName("งานวิจัย: ปุ่มเลือกจากฐานข้อมูลผลงาน ในเอกสารที่ 6")
class ScopusPickerDocSixTest {

    private static final Path FORM = Path.of(
            "src/main/resources/templates/academic/position/applicant/doc_form_6.html");

    private static String source;

    @BeforeAll
    static void readForm() throws IOException {
        assertThat(FORM).as("ไม่พบไฟล์เอกสารที่ 6").exists();
        source = Files.readString(FORM, StandardCharsets.UTF_8);
    }

    /** The {@code <button ...>...</button>} with the given id. */
    private static String button(String id) {
        Matcher m = Pattern.compile("<button[^>]*id=\"" + id + "\"[^>]*>.*?</button>", Pattern.DOTALL)
                .matcher(source);
        assertThat(m.find()).as("ไม่พบปุ่ม #" + id).isTrue();
        return m.group();
    }

    @Test
    @DisplayName("ชื่อปุ่มไม่ระบุว่าเป็น Scopus เพราะ picker ดึงผลงานจากหลายแหล่ง")
    void theLabelDoesNotClaimScopusOnly() {
        assertThat(button("btnPickResearch"))
                .as("""
                        picker แสดงผลงานจาก Scopus, OpenAlex, Crossref, DBLP, ThaiJO และ KKU IR
                        ถ้าปุ่มเขียนว่า (Scopus) ผู้ยื่นจะเข้าใจว่าผลงานจากแหล่งอื่นเลือกไม่ได้""")
                .contains("เลือกจากฐานข้อมูลผลงาน")
                .doesNotContain("Scopus");
    }

    @Test
    @DisplayName("สีปุ่มตรงกับเอกสารที่ 1: ปุ่มเลือกสีเขียว ปุ่มเพิ่มขอบน้ำเงิน")
    void theButtonsMatchDocumentOne() {
        assertThat(button("btnPickResearch")).contains("btn-success");
        assertThat(button("btnAddResearch")).contains("btn-outline-primary");
    }

    @Test
    @DisplayName("ปุ่มเปิด picker ให้เติมช่อง des_research{n} โดยไม่ผูกรหัสผลงาน")
    void theButtonOpensThePickerOnTheResearchRows() {
        assertThat(source)
                .contains("getElementById('btnPickResearch').addEventListener('click'")
                .contains("ScopusPicker.open(")
                .contains("titleName: n => 'des_research' + n")
                .as("ผลงานชุดเดียวกันผูกรหัสไว้ที่เอกสารที่ 1 แล้ว ผูกซ้ำที่นี่จะนับว่าใช้ผลงานสองครั้ง")
                .contains("trackIds: false");
    }

    @Test
    @DisplayName("โหลด scopus_picker.js ในหน้า ไม่งั้นกดปุ่มแล้วไม่เกิดอะไร")
    void thePickerScriptIsLoaded() {
        assertThat(source).contains("<script src=\"/js/scopus_picker.js\"></script>");
    }

    @Test
    @DisplayName("ช่องที่ picker เติม (des_research / data) มีอยู่จริงในแถวที่สร้าง")
    void theFieldsThePickerFillsExist() {
        assertThat(source)
                .contains("name=\"des_research${idx}\"")
                .contains("name=\"data${idx}\"")
                .contains("document.querySelector('[name=\"data' + n + '\"]')");
    }
}
