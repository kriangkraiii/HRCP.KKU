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

    // ประกาศ มข. 1669/2569 ข้อ ๙.๓: "ต่ำกว่า ๕๖ ไม่ผ่าน" และ "๕๖-๗๐ ชำนาญ" — ๕๖ พอดีผ่าน
    // ส่วนเศษระหว่างช่วงนับขึ้นไปช่วงบน ไม่ให้ตกช่องว่าง
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} → {1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "0, ไม่ผ่าน", "55.6, ไม่ผ่าน", "56, ชำนาญ", "56.4, ชำนาญ", "70, ชำนาญ",
            "70.5, ชำนาญพิเศษ", "85, ชำนาญพิเศษ", "85.8, เชี่ยวชาญ", "100, เชี่ยวชาญ"
    })
    @DisplayName("ระดับผลการสอนตามเกณฑ์คะแนนรวม")
    void levelFollowsTheScoreBands(String score, String level) {
        assertThat(Doc7Scoring.evalLevelFromScore(score)).isEqualTo(level);
    }

    @Test
    @DisplayName("คะแนนรายส่วนทศนิยมได้ 1 ตำแหน่ง ตามช่วง ๑.๑–๒ ของแบบฟอร์ม — 2 ตำแหน่งถูกปฏิเสธ ช่องว่างยังไม่นับ")
    void sectionScoresTakeOneDecimalPlace() {
        assertThat(Doc7Scoring.scorePrecisionProblem(scores("5", "3.0", "", "0"))).isNull();
        assertThat(Doc7Scoring.scorePrecisionProblem(scores("5", "3", "3.5", "4.1"))).isNull();
        assertThat(Doc7Scoring.scorePrecisionProblem(scores("5", "3", "3.55", "0")))
                .contains("ส่วนที่ 3").contains("3.55");
    }

    @Test
    @DisplayName("คะแนนรวม 56 (5/3/3/0) ติ๊กช่องชำนาญ")
    void fiftySixTicksExpert() {
        Map<String, String> m = scores("5", "3", "3", "0");

        Doc7Scoring.derive(m);

        assertThat(m).containsEntry("scorex", "56.00")
                .containsEntry("ch1", "☐")
                .containsEntry("ch2", "☑")
                .containsEntry("eval_result_level", "ชำนาญ");
    }

    @Test
    @DisplayName("คะแนนรายส่วนทศนิยมคิดน้ำหนักและช่วงระดับถูกต้อง")
    void decimalSectionScoresAreWeighted() {
        Map<String, String> m = scores("4.5", "3.2", "3.8", "4");

        Doc7Scoring.derive(m);

        // 4.5×4 + 3.2×6 + 3.8×6 + 4×4 = 18 + 19.2 + 22.8 + 16 = 76
        assertThat(m).containsEntry("scorex", "76.00")
                .containsEntry("score15", "4.5")
                .containsEntry("score24", "3.2")
                .containsEntry("eval_result_level", "ชำนาญพิเศษ");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} ขอ {1} → ต่ำกว่าเกณฑ์ {2}")
    @org.junit.jupiter.params.provider.CsvSource({
            "ชำนาญ, ASSISTANT_PROFESSOR, false", "ชำนาญ, ASSOCIATE_PROFESSOR, true",
            "ชำนาญพิเศษ, ASSOCIATE_PROFESSOR, false", "เชี่ยวชาญ, ASSOCIATE_PROFESSOR, false",
            "ไม่ผ่าน, ASSISTANT_PROFESSOR, true", "'', ASSOCIATE_PROFESSOR, false", "ดี, ASSOCIATE_PROFESSOR, false"
    })
    @DisplayName("ระดับผลต้องถึงเกณฑ์ของตำแหน่งที่ขอ (ข้อ ๙.๔) — ค่าที่ไม่รู้จักไม่ถูกตัดสิทธิ์")
    void levelMustMeetTheRequestedRank(String level, com.ecom.academic.model.AcademicRank rank, boolean short_) {
        assertThat(Doc7Scoring.fallsShortOf(level, rank)).isEqualTo(short_);
    }
}
