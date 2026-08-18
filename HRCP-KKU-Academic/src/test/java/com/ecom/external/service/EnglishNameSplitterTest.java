package com.ecom.external.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ecom.external.service.EnglishNameSplitter.Parts;

/**
 * The FS directory only gives us a combined English name, so the split into
 * first/last is derived locally. It is a heuristic, and these tests pin down
 * exactly which shapes it promises to handle — including the ones where it
 * deliberately errs towards keeping data rather than guessing.
 */
class EnglishNameSplitterTest {

    @Nested
    @DisplayName("ชื่อรูปแบบปกติ")
    class OrdinaryNames {

        @Test
        @DisplayName("ชื่อสองคำแยกเป็นชื่อและนามสกุล")
        void twoTokensSplitCleanly() {
            Parts parts = EnglishNameSplitter.split("Somchai Jaidee");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isEqualTo("Jaidee");
        }

        @Test
        @DisplayName("ชื่อกลางถูกผนวกเข้ากับนามสกุล ไม่ถูกทิ้ง")
        void middleNameIsKeptWithTheLastName() {
            Parts parts = EnglishNameSplitter.split("Somchai Chai Jaidee");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName())
                    .as("dropping the middle token would silently lose part of the name")
                    .isEqualTo("Chai Jaidee");
        }

        @Test
        @DisplayName("นามสกุลหลายคำอยู่ครบ")
        void multiWordFamilyNameSurvives() {
            Parts parts = EnglishNameSplitter.split("Maria van der Berg");

            assertThat(parts.firstName()).isEqualTo("Maria");
            assertThat(parts.lastName()).isEqualTo("van der Berg");
        }

        @Test
        @DisplayName("ยัติภังค์ไม่ถูกตัด")
        void hyphenatedNameIsNotSplit() {
            Parts parts = EnglishNameSplitter.split("Somchai Jai-dee");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isEqualTo("Jai-dee");
        }

        @Test
        @DisplayName("token เดียวถือเป็นชื่อ ไม่เดานามสกุล")
        void singleTokenBecomesFirstNameOnly() {
            Parts parts = EnglishNameSplitter.split("Somchai");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isNull();
        }
    }

    @Nested
    @DisplayName("คำนำหน้าที่ปนมากับ name_en")
    class LeadingTitles {

        @ParameterizedTest(name = "\"{0}\" -> Somchai / Jaidee")
        @ValueSource(strings = {
                "Dr. Somchai Jaidee",
                "dr Somchai Jaidee",
                "Mr. Somchai Jaidee",
                "Prof. Somchai Jaidee",
                "Professor Somchai Jaidee",
                "Lecturer Somchai Jaidee",
                "Asst. Prof. Somchai Jaidee",
                "Assoc. Prof. Dr. Somchai Jaidee",
                "Assistant Professor Somchai Jaidee"
        })
        void titlesAreStripped(String nameEn) {
            Parts parts = EnglishNameSplitter.split(nameEn);

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isEqualTo("Jaidee");
        }

        @Test
        @DisplayName("คำที่เป็นคำนำหน้าแต่เป็น token สุดท้าย ต้องไม่ถูกตัดทิ้ง")
        void aTrailingTitleWordIsTreatedAsAName() {
            // Someone actually named "Prof" would otherwise be erased entirely.
            Parts parts = EnglishNameSplitter.split("Prof");

            assertThat(parts.firstName()).isEqualTo("Prof");
            assertThat(parts.lastName()).isNull();
        }
    }

    @Nested
    @DisplayName("รูปแบบนามสกุลขึ้นก่อน")
    class CommaForm {

        @Test
        @DisplayName("\"Jaidee, Somchai\" อ่านเป็นนามสกุลก่อน")
        void commaMeansFamilyNameFirst() {
            Parts parts = EnglishNameSplitter.split("Jaidee, Somchai");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isEqualTo("Jaidee");
        }

        @Test
        @DisplayName("มีคำนำหน้าร่วมกับคอมมา")
        void titleIsStrippedAroundTheComma() {
            Parts parts = EnglishNameSplitter.split("Jaidee, Dr. Somchai");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isEqualTo("Jaidee");
        }

        @Test
        @DisplayName("คอมมาลอยไม่ทำให้ข้อมูลหาย")
        void danglingCommaFallsBackToOrdinaryParsing() {
            Parts parts = EnglishNameSplitter.split("Somchai Jaidee,");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isEqualTo("Jaidee");
        }
    }

    @Nested
    @DisplayName("ค่าว่างและช่องว่าง")
    class EdgeCases {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("null/ว่าง คืน null ทั้งคู่ ไม่ throw")
        void blankInputYieldsNothing(String nameEn) {
            Parts parts = EnglishNameSplitter.split(nameEn);

            assertThat(parts.firstName()).isNull();
            assertThat(parts.lastName()).isNull();
        }

        @Test
        @DisplayName("ช่องว่างเกินถูกยุบ")
        void extraWhitespaceIsCollapsed() {
            Parts parts = EnglishNameSplitter.split("   Somchai    Jaidee   ");

            assertThat(parts.firstName()).isEqualTo("Somchai");
            assertThat(parts.lastName()).isEqualTo("Jaidee");
        }

        @Test
        @DisplayName("มีแต่คำนำหน้าล้วน ต้องไม่คืนค่าว่างเปล่าแบบพัง")
        void titlesOnlyKeepsTheLastToken() {
            Parts parts = EnglishNameSplitter.split("Dr. Prof.");

            assertThat(parts.firstName()).isEqualTo("Prof.");
            assertThat(parts.lastName()).isNull();
        }
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1} / {2}")
    @CsvSource(nullValues = "NULL", value = {
            "Somchai Jaidee,          Somchai, Jaidee",
            "Nattaporn Srisai,        Nattaporn, Srisai",
            "Dr. Anan Wong,           Anan, Wong",
            "Anan,                    Anan, NULL",
            "'Wong, Anan',            Anan, Wong"
    })
    @DisplayName("ตารางสรุปรูปแบบที่รองรับ")
    void supportedShapes(String input, String expectedFirst, String expectedLast) {
        Parts parts = EnglishNameSplitter.split(input);

        assertThat(parts.firstName()).isEqualTo(expectedFirst);
        assertThat(parts.lastName()).isEqualTo(expectedLast);
    }
}
