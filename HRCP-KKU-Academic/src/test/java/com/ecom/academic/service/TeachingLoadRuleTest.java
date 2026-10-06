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
}
