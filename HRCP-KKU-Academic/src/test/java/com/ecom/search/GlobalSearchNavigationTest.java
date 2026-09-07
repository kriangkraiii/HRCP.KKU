package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.UserDtls;
import com.ecom.service.GlobalSearchService;
import com.ecom.service.GlobalSearchService.SearchResultItem;
import com.ecom.support.AbstractFlowTest;

/**
 * Locks the keyword bug that made one menu answer almost every Thai query.
 *
 * <p>The position-request menu listed {@code "ศ"} among its keywords, and the
 * matcher behind it is {@code String.contains}. Thai writes without spaces, so a
 * single character is a substring of an enormous share of the language: ประกาศ,
 * การศึกษา and ศาสตราจารย์ all contain it. The menu therefore appeared under
 * almost anything anybody typed.
 *
 * <p>The minimum query length alone does not fix this — {@code "ประกาศ"} is six
 * characters and still contains {@code ศ}. Removing the bare character from the
 * keyword list is the fix; the length guard is a separate protection.
 *
 * <p>The database is left empty on purpose, so navigation entries are the only
 * thing that can come back and an assertion cannot pass by accident on a request
 * row that happened to match.
 */
class GlobalSearchNavigationTest extends AbstractFlowTest {

    private static final String POSITION_MENU = "ยื่นขอกำหนดตำแหน่งทางวิชาการ";

    @Autowired
    private GlobalSearchService globalSearchService;

    private UserDtls applicant;

    @BeforeEach
    void emptyDatabase() {
        data.reset();
        applicant = data.applicant();
    }

    private List<String> titlesFor(String typed) {
        return globalSearchService.search(typed, applicant).stream()
                .map(SearchResultItem::getTitle)
                .toList();
    }

    @Test
    @DisplayName("a single Thai character is not a query")
    void singleCharacterReturnsNothing() {
        assertThat(titlesFor("ศ")).isEmpty();
    }

    @Test
    @DisplayName("an ordinary word containing ศ does not surface the position menu")
    void unrelatedWordDoesNotMatch() {
        assertThat(titlesFor("ประกาศ"))
                .as("บั๊กเดิม: contains(\"ศ\") ทำให้เมนูขอตำแหน่งโผล่ใต้คำค้นนี้")
                .doesNotContain(POSITION_MENU);
    }

    @Test
    @DisplayName("an ordinary word containing the รศ pair does not surface it either")
    void abbreviationPairInsideAnOrdinaryWordDoesNotMatch() {
        // ก-า-ร-ศ-ึ-ก-ษ-า: ร ต่อด้วย ศ ติดกันพอดี คำนี้จึง contains "รศ"
        // เป็นเหตุผลที่คำย่อไม่มีจุดต้องเทียบแบบทั้งคำ ไม่ใช่ contains
        assertThat(titlesFor("การศึกษา"))
                .as("ไทยเขียนติดกัน คำย่อสองตัวอักษรจึงไปโผล่กลางคำธรรมดาได้")
                .doesNotContain(POSITION_MENU);
    }

    @Test
    @DisplayName("the abbreviations people actually type still work")
    void abbreviationsStillMatch() {
        assertThat(titlesFor("ผศ")).contains(POSITION_MENU);
        assertThat(titlesFor("รศ")).contains(POSITION_MENU);
        assertThat(titlesFor("ผศ.")).contains(POSITION_MENU);
        assertThat(titlesFor("รศ.")).contains(POSITION_MENU);
        assertThat(titlesFor("ศ.")).contains(POSITION_MENU);
    }

    @Test
    @DisplayName("the spelled-out title still works")
    void fullWordStillMatches() {
        assertThat(titlesFor("ศาสตราจารย์")).contains(POSITION_MENU);
        assertThat(titlesFor("ตำแหน่ง")).contains(POSITION_MENU);
    }

    @Test
    @DisplayName("blank and null are handled without reaching the database")
    void emptyInputIsSafe() {
        assertThat(titlesFor("")).isEmpty();
        assertThat(titlesFor("   ")).isEmpty();
        assertThat(globalSearchService.search(null, applicant)).isEmpty();
        assertThat(globalSearchService.search("ตำแหน่ง", null)).isEmpty();
    }
}
