package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureModule;

@DisplayName("ชั่วโมงสอนประจำวิชาไม่น้อยกว่า 3 หน่วยกิต — ประกาศ มข. 1669/2569 ข้อ 7")
class TeachingLoadRuleTest {

    private static java.util.List<String> problems(String json) {
        return DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 1, json);
    }

    @Test
    @DisplayName("3 หน่วยกิตขึ้นไปผ่าน ต่ำกว่านั้นส่งลงนามไม่ได้ ช่องที่ยังว่างไม่นับ (ด่านช่องว่างอยู่ที่อื่น)")
    void creditsMustReachThree() {
        assertThat(problems("{\"academic_year\":\"1/2569\",\"teaching_credits\":\"3\"}")).isEmpty();
        assertThat(problems("{\"teaching_credits\":\"๔.๕\"}")).isEmpty();
        assertThat(problems("{\"teaching_credits\":\"2\"}")).singleElement().asString().contains("3 หน่วยกิต");
        assertThat(problems("{\"teaching_credits\":\"\"}")).isEmpty();
    }

    private static java.util.List<String> positionProblems(String json) {
        return DocumentCompleteness.malformedFields(SignatureModule.POSITION, 1, json);
    }

    @Test
    @DisplayName("ขอ ศ. ไม่ผ่านเฟส 1 — งานสอนในเอกสารที่ 1 เฟส 2 ต้องมีวิชาที่สอน 3 ชม./สัปดาห์ขึ้นไป (ข้อบังคับ 2569 ข้อ 16.3.2)")
    void aProfessorRequestNeedsOneCourseOfThreeHoursAWeek() {
        String prof = "\"target_position\":\"ศาสตราจารย์\"";
        assertThat(positionProblems("{" + prof + ",\"teaching_hours_per_week_1\":\"1.5\","
                + "\"teaching_hours_per_week_2\":\"3\"}")).isEmpty();
        assertThat(positionProblems("{" + prof + ",\"teaching_hours_per_week_1\":\"1.5\","
                + "\"teaching_hours_per_week_2\":\"2\"}"))
                .singleElement().asString().contains("16.3.2");
        assertThat(positionProblems("{" + prof + ",\"teaching_hours_per_week_1\":\"\"}"))
                .as("ยังไม่ได้กรอก — ด่านช่องว่างอยู่ที่อื่น").isEmpty();
        assertThat(positionProblems("{\"target_position\":\"รองศาสตราจารย์\",\"teaching_hours_per_week_1\":\"1\"}"))
                .as("ผศ./รศ. ตรวจภาระสอนแล้วที่เอกสารที่ 1 ของการประเมินการสอน").isEmpty();
    }
}
