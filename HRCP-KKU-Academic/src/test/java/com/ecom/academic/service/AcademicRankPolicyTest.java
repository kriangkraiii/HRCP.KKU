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

    @Nested
    @DisplayName("วิธีพิเศษ — ข้อบังคับ มข. พ.ศ. 2569 ข้อ 18")
    class SpecialMethod {

        @Test
        @DisplayName("ข้ามขั้นต้องใช้วิธีพิเศษ: อาจารย์ → รศ./ศ., ผศ. → ศ.")
        void skippingARankRequiresTheSpecialMethod() {
            assertThat(AcademicRankPolicy.requiresSpecialMethod(
                    AcademicRank.LECTURER, AcademicRank.ASSOCIATE_PROFESSOR)).isTrue();
            assertThat(AcademicRankPolicy.requiresSpecialMethod(
                    AcademicRank.LECTURER, AcademicRank.PROFESSOR)).isTrue();
            assertThat(AcademicRankPolicy.requiresSpecialMethod(
                    AcademicRank.ASSISTANT_PROFESSOR, AcademicRank.PROFESSOR)).isTrue();
        }

        @Test
        @DisplayName("ขอตำแหน่งถัดไป ระบบไม่บังคับ — ผู้ยื่นเลือกวิธีพิเศษเองได้เมื่อระยะเวลาไม่ครบหรือขอต่างสาขา")
        void theNextRankLeavesTheChoiceToTheApplicant() {
            assertThat(AcademicRankPolicy.requiresSpecialMethod(
                    AcademicRank.LECTURER, AcademicRank.ASSISTANT_PROFESSOR)).isFalse();
            assertThat(AcademicRankPolicy.requiresSpecialMethod(
                    AcademicRank.ASSISTANT_PROFESSOR, AcademicRank.ASSOCIATE_PROFESSOR)).isFalse();
            assertThat(AcademicRankPolicy.requiresSpecialMethod(
                    AcademicRank.ASSOCIATE_PROFESSOR, AcademicRank.PROFESSOR)).isFalse();
            assertThat(AcademicRankPolicy.requiresSpecialMethod(AcademicRank.LECTURER, null)).isFalse();
        }

        @Test
        @DisplayName("รศ./ศ. วิธีพิเศษ เสนอผลงานวิธีที่ 2 ไม่ได้ (ข้อ 18.2, 18.3)")
        void theSpecialMethodTakesOnlyWorksMethodOne() {
            assertThat(AcademicRankPolicy.worksMethodViolation("พิเศษ", AcademicRank.ASSOCIATE_PROFESSOR, "2"))
                    .hasValueSatisfying(message -> assertThat(message).contains("18.2"));
            assertThat(AcademicRankPolicy.worksMethodViolation("พิเศษ", AcademicRank.PROFESSOR, "2"))
                    .hasValueSatisfying(message -> assertThat(message).contains("18.3"));
            assertThat(AcademicRankPolicy.worksMethodViolation("พิเศษ", AcademicRank.ASSOCIATE_PROFESSOR, "1")).isEmpty();
        }

        @Test
        @DisplayName("เอกสารที่ 1 พิมพ์ \"(โดยวิธีปกติ วิธีที่ ๒)\" — วิธีพิเศษและ ผศ. ไม่ต่อท้าย")
        void documentOnePrintsTheWorksMethod() {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>(java.util.Map.of(
                    "method", "ปกติ", "target_position", "รองศาสตราจารย์", "works_method", "2"));
            DocumentGenerationService.normalizePhase2(1, placeholders);
            assertThat(placeholders).containsEntry("method", "ปกติ วิธีที่ ๒");

            assertThat(AcademicRankPolicy.printedMethod("พิเศษ", AcademicRank.PROFESSOR, "1")).isEqualTo("พิเศษ");
            assertThat(AcademicRankPolicy.printedMethod("ปกติ", AcademicRank.ASSISTANT_PROFESSOR, "1")).isEqualTo("ปกติ");
            assertThat(AcademicRankPolicy.printedMethod("ปกติ", AcademicRank.PROFESSOR, null)).isEqualTo("ปกติ");
        }

        @Test
        @DisplayName("วิธีปกติ, ขอ ผศ. หรือยังไม่เลือกวิธีเสนอผลงาน — ไม่ใช่ข้อผิด")
        void otherCombinationsAreFine() {
            assertThat(AcademicRankPolicy.worksMethodViolation("ปกติ", AcademicRank.ASSOCIATE_PROFESSOR, "2")).isEmpty();
            assertThat(AcademicRankPolicy.worksMethodViolation("พิเศษ", AcademicRank.ASSISTANT_PROFESSOR, "2")).isEmpty();
            assertThat(AcademicRankPolicy.worksMethodViolation("พิเศษ", AcademicRank.PROFESSOR, "")).isEmpty();
        }
    }

    @Nested
    @DisplayName("ศาสตราจารย์ขอตำแหน่งต่อไม่ได้ — สูงสุดแล้ว")
    class HighestRank {

        @Test
        @DisplayName("โปรไฟล์เป็น ศ. — ถือว่าสูงสุดแล้ว")
        void aProfessorProfileHoldsTheHighestRank() {
            assertThat(AcademicRankPolicy.holdsHighestRank(withProfilePosition("ศ.ดร."))).isTrue();
        }

        @Test
        @DisplayName("โปรไฟล์เป็น ศ. แต่ผลประเมินเก่าเขียนว่า ผศ. — ยังถือว่าสูงสุด")
        void anOldEvaluationDoesNotHideAProfessorProfile() {
            assertThat(AcademicRankPolicy.holdsHighestRank(withProfilePosition("ศาสตราจารย์"),
                    "ผู้ช่วยศาสตราจารย์"))
                    .as("ได้ ศ. หลังประเมินการสอน ต้องเอาผลประเมินเก่ามาขอตำแหน่งต่อไม่ได้")
                    .isTrue();
        }

        @Test
        @DisplayName("เอกสารระบุ ศ. — ถือว่าสูงสุดแล้ว แม้โปรไฟล์ยังไม่ sync")
        void aDocumentNamingProfessorHoldsTheHighestRank() {
            assertThat(AcademicRankPolicy.holdsHighestRank(withProfilePosition(null), "ศาสตราจารย์")).isTrue();
        }

        @Test
        @DisplayName("รศ. ยังขอ ศ. ได้")
        void anAssociateProfessorDoesNot() {
            assertThat(AcademicRankPolicy.holdsHighestRank(withProfilePosition("รองศาสตราจารย์"))).isFalse();
            assertThat(AcademicRankPolicy.holdsHighestRank(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("ต้องใช้ผลประเมินการสอนไหม")
    class TeachingEvaluation {

        @Test
        @DisplayName("ขอ ผศ. หรือ รศ. — ต้องใช้เสมอ")
        void assistantAndAssociateAlwaysNeedOne() {
            for (AcademicRank current : new AcademicRank[] { AcademicRank.LECTURER,
                    AcademicRank.ASSISTANT_PROFESSOR }) {
                assertThat(AcademicRankPolicy.requiresTeachingEvaluation(current,
                        AcademicRank.ASSOCIATE_PROFESSOR)).isTrue();
            }
            assertThat(AcademicRankPolicy.requiresTeachingEvaluation(AcademicRank.LECTURER,
                    AcademicRank.ASSISTANT_PROFESSOR)).isTrue();
        }

        @Test
        @DisplayName("รศ. ขอ ศ. (วิธีปกติ) — ไม่ต้องใช้")
        void anAssociateProfessorApplyingForProfessorDoesNot() {
            assertThat(AcademicRankPolicy.requiresTeachingEvaluation(AcademicRank.ASSOCIATE_PROFESSOR,
                    AcademicRank.PROFESSOR)).isFalse();
        }

        @Test
        @DisplayName("อาจารย์ หรือ ผศ. ขอ ศ. (วิธีพิเศษ) — ไม่ต้องใช้ (1669/2569 ข้อ 6, ข้อบังคับ 2569 ข้อ 18.3)")
        void skippingToProfessorDoesNot() {
            assertThat(AcademicRankPolicy.requiresTeachingEvaluation(AcademicRank.ASSISTANT_PROFESSOR,
                    AcademicRank.PROFESSOR)).isFalse();
            assertThat(AcademicRankPolicy.requiresTeachingEvaluation(AcademicRank.LECTURER,
                    AcademicRank.PROFESSOR)).isFalse();
        }

        @Test
        @DisplayName("ยังไม่รู้ว่าขอตำแหน่งอะไร — ยังตัดสินไม่ได้ จึงไม่บังคับ")
        void noTargetYetDoesNot() {
            assertThat(AcademicRankPolicy.requiresTeachingEvaluation(AcademicRank.LECTURER, null)).isFalse();
        }
    }
}
