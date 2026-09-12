package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRank;
import com.ecom.model.UserDtls;

@DisplayName("กติกาตำแหน่งที่ขอต้องสูงกว่าตำแหน่งปัจจุบัน")
class AcademicRankPolicyTest {

    private static UserDtls withProfilePosition(String position) {
        UserDtls user = new UserDtls();
        user.setAcademicPosition(position);
        return user;
    }

    @Nested
    @DisplayName("ตำแหน่งปัจจุบันมาจากไหน")
    class CurrentRank {

        @Test
        @DisplayName("เอกสารที่กรอกไว้ชนะโปรไฟล์ — โปรไฟล์อาจเปลี่ยนไปหลังกรอกเอกสาร")
        void theDocumentWinsOverTheProfile() {
            assertThat(AcademicRankPolicy.currentRank(withProfilePosition("ผู้ช่วยศาสตราจารย์"), "อาจารย์"))
                    .isEqualTo(AcademicRank.LECTURER);
        }

        @Test
        @DisplayName("เอกสารแรกที่อ่านได้เป็นตัวตัดสิน")
        void theFirstReadableDocumentDecides() {
            assertThat(AcademicRankPolicy.currentRank(withProfilePosition(null),
                    null, "รองศาสตราจารย์", "ผู้ช่วยศาสตราจารย์"))
                    .isEqualTo(AcademicRank.ASSOCIATE_PROFESSOR);
        }

        @Test
        @DisplayName("ยังไม่มีเอกสาร — ใช้โปรไฟล์")
        void noDocumentFallsBackToTheProfile() {
            assertThat(AcademicRankPolicy.currentRank(withProfilePosition("ผศ.ดร."), null, "  "))
                    .isEqualTo(AcademicRank.ASSISTANT_PROFESSOR);
        }

        @Test
        @DisplayName("เอกสารที่ไม่ได้ระบุตำแหน่งวิชาการ ไม่ลบล้างโปรไฟล์")
        void aDocumentThatNamesNoRankDoesNotOverrideTheProfile() {
            assertThat(AcademicRankPolicy.currentRank(withProfilePosition("ผู้ช่วยศาสตราจารย์"),
                    "พนักงานมหาวิทยาลัย"))
                    .as("พิมพ์ข้อความอื่นลงช่องตำแหน่งต้องไม่ทำให้ ผศ. ขอ ผศ. ซ้ำได้")
                    .isEqualTo(AcademicRank.ASSISTANT_PROFESSOR);
        }

        @Test
        @DisplayName("ไม่มีที่ไหนระบุตำแหน่งวิชาการ — ถือว่าเป็นอาจารย์")
        void nothingNamesARankMeansLecturer() {
            assertThat(AcademicRankPolicy.currentRank(withProfilePosition(null))).isEqualTo(AcademicRank.LECTURER);
            assertThat(AcademicRankPolicy.currentRank(null, "พนักงานมหาวิทยาลัย")).isEqualTo(AcademicRank.LECTURER);
        }
    }

    @Nested
    @DisplayName("ตำแหน่งที่ขอ")
    class Violation {

        @Test
        @DisplayName("ผศ. ขอ ผศ. ซ้ำ — ไม่ได้")
        void theSameRankIsRefused() {
            assertThat(AcademicRankPolicy.rankViolation(
                    AcademicRank.ASSISTANT_PROFESSOR, AcademicRank.ASSISTANT_PROFESSOR))
                    .hasValueSatisfying(message -> assertThat(message).contains("ผู้ช่วยศาสตราจารย์"));
        }

        @Test
        @DisplayName("รศ. ขอ ผศ. — ไม่ได้")
        void aLowerRankIsRefused() {
            assertThat(AcademicRankPolicy.rankViolation(
                    AcademicRank.ASSOCIATE_PROFESSOR, AcademicRank.ASSISTANT_PROFESSOR)).isPresent();
        }

        @Test
        @DisplayName("อาจารย์ ขอ รศ. ข้ามขั้น — ได้")
        void skippingARankIsAllowed() {
            assertThat(AcademicRankPolicy.rankViolation(
                    AcademicRank.LECTURER, AcademicRank.ASSOCIATE_PROFESSOR)).isEmpty();
        }

        @Test
        @DisplayName("ผศ. ขอ รศ. — ได้")
        void theNextRankIsAllowed() {
            assertThat(AcademicRankPolicy.rankViolation(
                    AcademicRank.ASSISTANT_PROFESSOR, AcademicRank.ASSOCIATE_PROFESSOR)).isEmpty();
        }

        @Test
        @DisplayName("ยังไม่รู้ว่าขอตำแหน่งอะไร — ยังไม่มีอะไรให้ตรวจ")
        void noTargetYetIsNotAViolation() {
            assertThat(AcademicRankPolicy.rankViolation(AcademicRank.PROFESSOR, null)).isEmpty();
        }
    }
}
