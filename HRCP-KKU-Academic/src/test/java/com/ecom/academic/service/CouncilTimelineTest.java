package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("วันที่สภามหาวิทยาลัยรับเรื่อง — ผลงานที่ยังรอตีพิมพ์ (ประกาศ มข. 1670/2569 ข้อ 6 (6))")
class CouncilTimelineTest {

    private static final String TICK = "☑";

    @Test
    @DisplayName("งานวิจัยที่มีแต่หนังสือตอบรับ และบทความที่รอตีพิมพ์ นับเป็นผลงานที่ยังไม่ได้ตีพิมพ์")
    void acceptedButUnpublishedWorksAreCounted() {
        Map<String, String> doc7 = Map.of(
                "pending_letter_1", TICK,
                "article_is_pending_1", TICK,
                "article_accept_letter_2", TICK);

        assertThat(CouncilTimeline.unpublishedAcceptedWorks(doc7)).isEqualTo(3);
    }

    @Test
    @DisplayName("ตีพิมพ์แล้วแม้จะติ๊กหนังสือตอบรับไว้ด้วย ไม่นับ — ตำราและหนังสือไม่อยู่ในข้อนี้")
    void publishedWorksAndBooksAreNotCounted() {
        Map<String, String> doc7 = Map.of(
                "pending_letter_1", TICK, "is_published_1", TICK,
                "article_accept_letter_1", TICK, "article_is_published_1", TICK,
                "book_is_pending_1", TICK, "book_accept_letter_1", TICK,
                "pending_letter_2", "");

        assertThat(CouncilTimeline.unpublishedAcceptedWorks(doc7)).isZero();
        assertThat(CouncilTimeline.unpublishedAcceptedWorks(null)).isZero();
    }
}
