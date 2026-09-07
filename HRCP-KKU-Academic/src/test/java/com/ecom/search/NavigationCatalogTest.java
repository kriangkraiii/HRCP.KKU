package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.UserDtls;
import com.ecom.search.dto.SearchHit;
import com.ecom.search.service.SearchService;
import com.ecom.support.AbstractFlowTest;

/**
 * The menus, as the new engine sees them.
 *
 * <p>{@code GlobalSearchNavigationTest} covers the same ground for the legacy
 * service, which is still the fallback behind {@code app.search.enabled}. This
 * is the other half: the same questions asked of the index, which is what
 * actually answers them now.
 *
 * <p>The bug worth keeping dead is the one that started this work — the
 * position menu listed {@code "ศ"} among its keywords and was matched with
 * {@code contains}, so it answered nearly every Thai query. As index rows the
 * mistake is structurally out of reach: matching goes through the ranked query,
 * which enforces the minimum length and scores a menu against real records
 * instead of always placing it first.
 */
class NavigationCatalogTest extends AbstractFlowTest {

    private static final String POSITION_MENU = "ยื่นขอกำหนดตำแหน่งทางวิชาการ";
    private static final String REGULATIONS_MENU = "คลังเอกสาร / ข้อบังคับ";

    @Autowired
    private SearchService searchService;

    private UserDtls applicant;

    @BeforeEach
    void seed() {
        data.reset();
        applicant = data.applicant();
    }

    private List<String> titlesFor(String term) {
        return searchService.quickSearch(term, applicant, 50).hits().stream()
                .map(SearchHit::title)
                .toList();
    }

    @Test
    @DisplayName("อักษรไทยตัวเดียวไม่ใช่คำค้น")
    void singleCharacterFindsNothing() {
        assertThat(titlesFor("ศ")).isEmpty();
    }

    @Test
    @DisplayName("คำธรรมดาที่มี ศ อยู่ ไม่ทำให้เมนูขอตำแหน่งโผล่")
    void ordinaryWordsDoNotSurfaceThePositionMenu() {
        assertThat(titlesFor("การศึกษา"))
                .as("การศึกษา มี ร ต่อ ศ ติดกัน จึง contains \"รศ\" — เป็นเหตุผลที่คำย่อ "
                        + "ต้องอยู่ในรูปที่ยาวพอ ไม่ใช่สองตัวอักษรลอย ๆ")
                .doesNotContain(POSITION_MENU);
    }

    @Test
    @DisplayName("ประกาศ ต้องพาไปคลังเอกสาร ไม่ใช่เมนูขอตำแหน่ง")
    void aWordGoesToTheMenuThatActuallyMeansIt() {
        List<String> titles = titlesFor("ประกาศ");

        assertThat(titles)
                .as("บั๊กเดิมทำให้เมนูขอตำแหน่งโผล่ใต้คำนี้")
                .doesNotContain(POSITION_MENU);
        assertThat(titles)
                .as("และคำนี้ควรเจอเมนูที่มันหมายถึงจริง ๆ")
                .contains(REGULATIONS_MENU);
    }

    @Test
    @DisplayName("คำที่คนพิมพ์จริงยังค้นเมนูขอตำแหน่งเจอ")
    void realQueriesStillFindIt() {
        assertThat(titlesFor("ผศ")).contains(POSITION_MENU);
        assertThat(titlesFor("รศ.")).contains(POSITION_MENU);
        assertThat(titlesFor("ศาสตราจารย์")).contains(POSITION_MENU);
        assertThat(titlesFor("ตำแหน่ง")).contains(POSITION_MENU);
        assertThat(titlesFor("position")).contains(POSITION_MENU);
    }

    @Test
    @DisplayName("เมนูไม่กลบผลลัพธ์จริง")
    void menusDoNotDrownOutRealRecords() {
        var request = data.evaluation(applicant, com.ecom.academic.model.RequestStatus.RECEIVED);

        assertThat(titlesFor(request.getRequestCode()))
                .as("ค้นด้วยเลขคำร้อง ต้องได้คำร้อง ไม่ใช่เมนู — เมนูเป็นแถวธรรมดาที่ถูกจัดอันดับ "
                        + "ไม่ใช่ผลลัพธ์ที่แทรกไว้ข้างหน้าเสมอ")
                .isNotEmpty()
                .doesNotContain(POSITION_MENU);
    }
}
