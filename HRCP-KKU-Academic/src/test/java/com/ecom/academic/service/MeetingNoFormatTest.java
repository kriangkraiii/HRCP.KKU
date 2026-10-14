package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ecom.academic.model.SignatureModule;

@DisplayName("เอกสารที่ 8 — ครั้งที่/ปีของการประชุมต้องเป็น ครั้งที่/ปี เช่น 1/2569")
class MeetingNoFormatTest {

    private static String doc8(String meetingNo) {
        return "{\"meeting_no\":\"" + meetingNo + "\"}";
    }

    @ParameterizedTest
    @ValueSource(strings = { "1/2569", "12/2568", "100/2569", " 3/2569 " })
    @DisplayName("ครั้งที่ 1–3 หลัก ทับปีสี่หลัก ส่งลงนามได้")
    void validValuesPass(String meetingNo) {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 8, doc8(meetingNo))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "2569", "1/69", "1-2569", "ครั้งที่ 1/2569", "1/2569/2", "1000/2569", "/2569" })
    @DisplayName("รูปแบบอื่นส่งลงนามไม่ได้")
    void otherFormatsAreRefused(String meetingNo) {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 8, doc8(meetingNo)))
                .containsExactly("ครั้งที่/ปีของการประชุม ต้องกรอกเป็น ครั้งที่/ปี พ.ศ. เช่น 1/2569");
    }

    @Test
    @DisplayName("ยังไม่กรอก — เป็นเรื่องของช่องที่ขาด ไม่ใช่รูปแบบผิด; เอกสารอื่นไม่ตรวจ")
    void blankAndOtherDocumentsAreNotFormatErrors() {
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 8, doc8(""))).isEmpty();
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 8, "{}")).isEmpty();
        assertThat(DocumentCompleteness.malformedFields(SignatureModule.ACADEMIC, 9, doc8("2569"))).isEmpty();
    }
}
