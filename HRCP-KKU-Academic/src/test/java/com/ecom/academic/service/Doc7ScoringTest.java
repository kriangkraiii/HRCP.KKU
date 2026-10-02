package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("เอกสารที่ 7 — คะแนนรายส่วนอยู่ในช่วง 0–5")
class Doc7ScoringTest {

    private static Map<String, String> scores(String s1, String s2, String s3, String s4) {
        Map<String, String> m = new HashMap<>();
        m.put("sec_score_1", s1);
        m.put("sec_score_2", s2);
        m.put("sec_score_3", s3);
        m.put("sec_score_4", s4);
        return m;
    }

    @Test
    @DisplayName("คะแนนเกิน 5 ถูกบีบเป็น 5 ติดลบเป็น 0 ค่าในช่วงไม่เปลี่ยน")
    void outOfRangeScoresAreClamped() {
        Map<String, String> m = scores("10", "-2", "4.5", "");

        Doc7Scoring.clampSectionScores(m);

        assertThat(m).containsEntry("sec_score_1", "5")
                .containsEntry("sec_score_2", "0")
                .containsEntry("sec_score_3", "4.5")
                .containsEntry("sec_score_4", "");
    }

    @Test
    @DisplayName("คะแนนรวมไม่เกิน 100 แม้กรอกเกิน 5 ทุกส่วน")
    void theTotalNeverPassesAHundred() {
        Map<String, String> m = scores("10", "10", "10", "10");

        Doc7Scoring.derive(m);

        assertThat(m).containsEntry("scorex", "100.00")
                .containsEntry("score15", "5.0")
                .containsEntry("eval_result_level", "เชี่ยวชาญ");
    }
}
