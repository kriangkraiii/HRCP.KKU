package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ThaiDateUtil — การแปลงเลขไทยกับเลขอารบิก")
class ThaiDigitsTest {

    @Nested
    @DisplayName("อารบิก → ไทย")
    class ToThai {

        @Test
        @DisplayName("แปลงทุกหลักได้ครบ ๐ ถึง ๙")
        void convertsEveryDigit() {
            assertThat(ThaiDateUtil.toThaiDigits("0123456789")).isEqualTo("๐๑๒๓๔๕๖๗๘๙");
        }

        @Test
        @DisplayName("ตัวอักษรไทยและเครื่องหมายอยู่เหมือนเดิม")
        void leavesEverythingElseAlone() {
            assertThat(ThaiDateUtil.toThaiDigits("อว 660301.26.3/13"))
                    .isEqualTo("อว ๖๖๐๓๐๑.๒๖.๓/๑๓");
        }

        @Test
        @DisplayName("null คืน null ไม่ใช่โยน exception")
        void handlesNull() {
            assertThat(ThaiDateUtil.toThaiDigits(null)).isNull();
        }

        @Test
        @DisplayName("สตริงที่ไม่มีตัวเลขคืนค่าเดิม")
        void keepsTextWithoutDigits() {
            assertThat(ThaiDateUtil.toThaiDigits("ผู้ช่วยศาสตราจารย์")).isEqualTo("ผู้ช่วยศาสตราจารย์");
        }

        @Test
        @DisplayName("เลขไทยที่ปนมาอยู่แล้วไม่ถูกแปลงซ้ำ")
        void leavesThaiDigitsAlone() {
            assertThat(ThaiDateUtil.toThaiDigits("๑/2569")).isEqualTo("๑/๒๕๖๙");
        }
    }

    @Nested
    @DisplayName("ไทย → อารบิก")
    class ToArabic {

        @Test
        @DisplayName("แปลงกลับได้ตรงกับต้นฉบับ")
        void roundTrips() {
            String original = "เลขที่ อว 660301.26.3/13 ลงวันที่ 21 กันยายน 2569";
            assertThat(ThaiDateUtil.toArabicDigits(ThaiDateUtil.toThaiDigits(original)))
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("null คืน null")
        void handlesNull() {
            assertThat(ThaiDateUtil.toArabicDigits(null)).isNull();
        }
    }
}
