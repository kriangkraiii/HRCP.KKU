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

    // ระเบียบเขียน "ต่ำกว่า ๕๖ ไม่ผ่าน" และ "๕๗-๗๐ ชำนาญ" — ๕๖ พอดีไม่อยู่ในช่วงใด จึงถือว่าไม่ผ่าน
    // ส่วนเศษระหว่างช่วงนับขึ้นไปช่วงบน ไม่ให้ตกช่องว่าง
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} → {1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "0, ไม่ผ่าน", "56, ไม่ผ่าน", "56.4, ชำนาญ", "57, ชำนาญ", "70, ชำนาญ",
            "70.5, ชำนาญพิเศษ", "85, ชำนาญพิเศษ", "85.8, เชี่ยวชาญ", "100, เชี่ยวชาญ"
    })
    @DisplayName("ระดับผลการสอนตามเกณฑ์คะแนนรวม")
    void levelFollowsTheScoreBands(String score, String level) {
        assertThat(Doc7Scoring.evalLevelFromScore(score)).isEqualTo(level);
    }

    @Test
    @DisplayName("คะแนนรายส่วนต้องเป็นจำนวนเต็ม — ทศนิยมถูกปฏิเสธ ช่องว่างยังไม่นับ")
    void sectionScoresMustBeWhole() {
        assertThat(Doc7Scoring.nonWholeScoreProblem(scores("5", "3.0", "", "0"))).isNull();
        assertThat(Doc7Scoring.nonWholeScoreProblem(scores("5", "3", "3.5", "0")))
                .contains("ส่วนที่ 3").contains("3.5");
    }

    @Test
    @DisplayName("คะแนนรวม 56 (5/3/3/0) ติ๊กช่องไม่ผ่าน")
    void fiftySixTicksFail() {
        Map<String, String> m = scores("5", "3", "3", "0");

        Doc7Scoring.derive(m);

        assertThat(m).containsEntry("scorex", "56.00")
                .containsEntry("ch1", "☑")
                .containsEntry("ch2", "☐")
                .containsEntry("eval_result_level", "ไม่ผ่าน");
    }
}
