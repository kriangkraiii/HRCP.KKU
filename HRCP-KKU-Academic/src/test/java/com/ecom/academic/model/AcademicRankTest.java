package com.ecom.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ลำดับตำแหน่งทางวิชาการ")
class AcademicRankTest {

    @Nested
    @DisplayName("อ่านตำแหน่งจากข้อความ")
    class Parsing {

        @Test
        @DisplayName("คำนำหน้าแบบย่อพร้อม ดร. ก็อ่านได้")
        void shortTitleWithDoctorate() {
            assertThat(AcademicRank.of("ผศ.ดร.")).isEqualTo(AcademicRank.ASSISTANT_PROFESSOR);
        }

        @Test
        @DisplayName("ชื่อตำแหน่งภาษาอังกฤษจาก directory ก็อ่านได้")
        void englishPositionName() {
            assertThat(AcademicRank.of("Associate Professor")).isEqualTo(AcademicRank.ASSOCIATE_PROFESSOR);
        }

        @Test
        @DisplayName("ศาสตราจารย์ ไม่ถูกอ่านเป็นผู้ช่วยหรือรอง")
        void fullProfessor() {
            assertThat(AcademicRank.of("ศาสตราจารย์")).isEqualTo(AcademicRank.PROFESSOR);
        }

        @Test
        @DisplayName("อาจารย์")
        void lecturer() {
            assertThat(AcademicRank.of("อาจารย์")).isEqualTo(AcademicRank.LECTURER);
        }

        @Test
        @DisplayName("ข้อความที่ไม่ได้ระบุตำแหน่งวิชาการ คืน null ไม่เดาเอาเอง")
        void unrecognisedTextIsNull() {
            assertThat(AcademicRank.of("พนักงานมหาวิทยาลัย")).isNull();
            assertThat(AcademicRank.of("   ")).isNull();
            assertThat(AcademicRank.of((String[]) null)).isNull();
        }

        @Test
        @DisplayName("หลายช่อง: ช่องที่ไม่เกี่ยวไม่บังช่องที่ใช่")
        void laterCandidateWins() {
            // SSO สายสนับสนุนส่ง positionName เป็นชื่องาน แล้วตำแหน่งวิชาการไปอยู่ levelName
            assertThat(AcademicRank.of("พนักงานมหาวิทยาลัย", "ผู้ช่วยศาสตราจารย์"))
                    .isEqualTo(AcademicRank.ASSISTANT_PROFESSOR);
        }

        @Test
        @DisplayName("หลายช่อง: ถ้าไม่มีช่องไหนเป็นตำแหน่งวิชาการเลย ต้องคืน null ไม่ใช่ช่องแรก")
        void noCandidateMatchesIsNull() {
            assertThat(AcademicRank.of("นักวิชาการคอมพิวเตอร์", "ชำนาญการ")).isNull();
            assertThat(AcademicRank.of()).isNull();
        }
    }

    @Nested
    @DisplayName("ชื่อตำแหน่งสองภาษา")
    class Labels {

        @Test
        @DisplayName("ทุกตำแหน่งมีคู่ไทย-อังกฤษครบ")
        void everyRankHasBothLabels() {
            assertThat(AcademicRank.LECTURER.englishLabel()).isEqualTo("Lecturer");
            assertThat(AcademicRank.ASSISTANT_PROFESSOR.englishLabel()).isEqualTo("Assistant Professor");
            assertThat(AcademicRank.ASSOCIATE_PROFESSOR.englishLabel()).isEqualTo("Associate Professor");
            assertThat(AcademicRank.PROFESSOR.englishLabel()).isEqualTo("Professor");
        }

        @Test
        @DisplayName("ชื่ออังกฤษอ่านกลับเป็นตำแหน่งเดิมได้ — แปลงไป-กลับไม่เพี้ยน")
        void englishLabelRoundTrips() {
            for (AcademicRank rank : AcademicRank.values()) {
                assertThat(AcademicRank.of(rank.englishLabel()))
                        .as("อ่าน %s กลับ", rank.englishLabel())
                        .isEqualTo(rank);
                assertThat(AcademicRank.of(rank.thaiLabel()))
                        .as("อ่าน %s กลับ", rank.thaiLabel())
                        .isEqualTo(rank);
            }
        }
    }

    @Nested
    @DisplayName("เปรียบเทียบลำดับ")
    class Ordering {

        @Test
        @DisplayName("รศ. สูงกว่า ผศ.")
        void associateIsAboveAssistant() {
            assertThat(AcademicRank.ASSOCIATE_PROFESSOR.isAbove(AcademicRank.ASSISTANT_PROFESSOR)).isTrue();
        }

        @Test
        @DisplayName("ตำแหน่งเดียวกัน ไม่นับว่าสูงกว่า")
        void sameRankIsNotAbove() {
            assertThat(AcademicRank.ASSISTANT_PROFESSOR.isAbove(AcademicRank.ASSISTANT_PROFESSOR)).isFalse();
        }

        @Test
        @DisplayName("อาจารย์ ไม่สูงกว่า ผศ.")
        void lecturerIsNotAboveAssistant() {
            assertThat(AcademicRank.LECTURER.isAbove(AcademicRank.ASSISTANT_PROFESSOR)).isFalse();
        }
    }

    @Test
    @DisplayName("ต้องใช้ผลประเมินการสอนเฉพาะการขอ ผศ. และ รศ.")
    void onlyAssistantAndAssociateNeedATeachingEvaluation() {
        assertThat(AcademicRank.ASSISTANT_PROFESSOR.requiresTeachingEvaluation()).isTrue();
        assertThat(AcademicRank.ASSOCIATE_PROFESSOR.requiresTeachingEvaluation()).isTrue();
        assertThat(AcademicRank.PROFESSOR.requiresTeachingEvaluation()).isFalse();
    }

    @Nested
    @DisplayName("อ่านตำแหน่งที่ขอจากเอกสารที่ 0 ของผลประเมินการสอน")
    class FromDocumentZero {

        @Test
        @DisplayName("chk1 = ผศ.")
        void chk1() {
            assertThat(AcademicRank.fromDoc1Checks(Map.of("chk1", "✓")))
                    .isEqualTo(AcademicRank.ASSISTANT_PROFESSOR);
        }

        @Test
        @DisplayName("chk2 = รศ.")
        void chk2() {
            assertThat(AcademicRank.fromDoc1Checks(Map.of("chk1", " ", "chk2", "✓")))
                    .isEqualTo(AcademicRank.ASSOCIATE_PROFESSOR);
        }

        @Test
        @DisplayName("chk3 = ศ. (ฟอร์มฝั่งแอดมินยังมี)")
        void chk3() {
            assertThat(AcademicRank.fromDoc1Checks(Map.of("chk3", "✓")))
                    .isEqualTo(AcademicRank.PROFESSOR);
        }

        @Test
        @DisplayName("ไม่ได้ติ๊กช่องไหน คืน null")
        void nothingTicked() {
            assertThat(AcademicRank.fromDoc1Checks(Map.of("chk1", " ", "chk2", ""))).isNull();
            assertThat(AcademicRank.fromDoc1Checks(Map.of())).isNull();
            assertThat(AcademicRank.fromDoc1Checks(null)).isNull();
        }
    }
}
