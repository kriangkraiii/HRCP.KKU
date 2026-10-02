package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ecom.academic.model.SignatureModule;

@DisplayName("เอกสารที่ 1 — ภาค/ปีการศึกษาต้องเป็น ภาค/ปี เช่น 1/2569")
class SemesterYearFormatTest {

    private static String doc1(String year) {
        return "{\"academic_year\":\"" + year + "\"}";
    }

    @ParameterizedTest
    @ValueSource(strings = { "1/2569", "2/2568", "3/2569", " 1/2569 " })
    @DisplayName("ภาค 1–3 ทับปีสี่หลัก ส่งลงนามได้")
    void validValuesPass(String year) {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 1, doc1(year))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "2569", "4/2569", "1/69", "1-2569", "ภาค 1/2569", "1/2569/2" })
    @DisplayName("รูปแบบอื่นส่งลงนามไม่ได้")
    void otherFormatsAreRefused(String year) {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 1, doc1(year)))
                .containsExactly("ภาค/ปีการศึกษา ต้องกรอกเป็น ภาค/ปี เช่น 1/2569");
    }

    @Test
    @DisplayName("ยังไม่กรอก — เป็นเรื่องของช่องที่ขาด ไม่ใช่รูปแบบผิด; เอกสารอื่นไม่ตรวจ")
    void blankAndOtherDocumentsAreNotFormatErrors() {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 1, doc1(""))).isEmpty();
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 9, doc1("2569"))).isEmpty();
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.POSITION, 1, doc1("2569"))).isEmpty();
    }
}
