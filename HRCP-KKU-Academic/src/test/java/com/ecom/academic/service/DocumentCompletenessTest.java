package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureModule;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("ความครบถ้วนของเอกสารก่อนลงนาม")
class DocumentCompletenessTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String json(Map<String, String> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** แบบ ก.พ.ว. มข. 03 ที่กรอกส่วนกลางครบ และเติมส่วนของตำแหน่งตามที่ระบุ */
    private static Map<String, String> positionDoc1(String rankPrefix) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("target_position", "รองศาสตราจารย์");
        data.put("title", "ผศ.ดร.");
        data.put("applicant_name", "สมชาย ใจดี");
        data.put("major", "วิทยาการคอมพิวเตอร์");
        data.put("major_code", "0001");
        data.put("department", "วิทยาการคอมพิวเตอร์");
        data.put("faculty", "วิทยาศาสตร์");
        data.put("university", "มหาวิทยาลัยขอนแก่น");
        data.put("method", "วิธีที่ ๑");
        data.put("birth_date", "๑ มกราคม ๒๕๒๐");
        data.put("age", "๔๕");
        data.put("current_position", "ผู้ช่วยศาสตราจารย์");
        data.put("current_salary", "๕๐,๐๐๐");
        data.put("lecturer_appointment_date", "๑ มิถุนายน ๒๕๕๐");
        data.put("years", "๑๕");
        data.put("months", "๓");
        data.put("reseach", "งานวิจัย");
        data.put("academic_service", "งานบริการวิชาการ");
        data.put("administration", "งานบริหาร");

        // ส่วนของทุกระดับถูกส่งมาหมดเสมอ เพราะส่วนที่ซ่อนก็ยัง submit
        for (String prefix : List.of("asst_", "assoc_", "prof_")) {
            data.put(prefix + "used_research", "");
            data.put(prefix + "used_book", "");
        }
        // ประวัติการดำรงตำแหน่ง
        data.put("assistant_method", "");
        data.put("associate_method", "");

        if (rankPrefix != null) {
            data.put(rankPrefix + "used_research", "๓ เรื่อง");
            data.put(rankPrefix + "used_book", "๑ เล่ม");
        }
        return data;
    }

    @Nested
    @DisplayName("ส่วนของตำแหน่งที่ไม่ได้ขอ")
    class RankSections {

        @Test
        @DisplayName("ขอ รศ. แล้วปล่อยช่องของ ศ. ว่าง ถือว่ากรอกครบ")
        void associateApplicantNeedNotFillProfessorFields() {
            Map<String, String> data = positionDoc1("assoc_");
            data.put("assistant_method", "วิธีที่ ๑"); // เคยเป็น ผศ. มาก่อน จึงต้องกรอก

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), "รองศาสตราจารย์");

            assertThat(missing)
                    .as("ช่องของ ผศ./ศ. ไม่เกี่ยวกับคำขอ รศ. จึงต้องไม่ถูกนับว่าขาด")
                    .isEmpty();
        }

        @Test
        @DisplayName("ขอ รศ. แล้วเว้นช่องของ รศ. เอง ถือว่ายังไม่ครบ")
        void associateApplicantMustFillAssociateFields() {
            Map<String, String> data = positionDoc1("assoc_");
            data.put("assistant_method", "วิธีที่ ๑");
            data.put("assoc_used_book", "");

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), "รองศาสตราจารย์");

            assertThat(missing).containsExactly("assoc_used_book");
        }

        @Test
        @DisplayName("ขอ ศ. ต้องกรอกส่วนของ ศ. และประวัติการเป็น ผศ./รศ.")
        void professorApplicantMustFillProfessorFields() {
            Map<String, String> data = positionDoc1(null);

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), "ศาสตราจารย์");

            assertThat(missing).contains("prof_used_research", "prof_used_book",
                    "assistant_method", "associate_method");
            assertThat(missing)
                    .as("ส่วนของ ผศ./รศ. ในข้อ ๔ ไม่ใช่ของคำขอ ศ.")
                    .doesNotContain("asst_used_research", "assoc_used_research");
        }

        @Test
        @DisplayName("ขอ ผศ. ไม่ต้องกรอกประวัติการเคยเป็น ผศ./รศ.")
        void assistantApplicantHasNoPriorAppointments() {
            Map<String, String> data = positionDoc1("asst_");

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), "ผู้ช่วยศาสตราจารย์");

            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("ไม่รู้ตำแหน่งที่ขอ — ไม่บังคับส่วนของระดับใดเลย ดีกว่ากันคนที่กรอกถูก")
        void unknownTargetPositionDoesNotBlock() {
            Map<String, String> data = positionDoc1(null);

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), null);

            assertThat(missing).isEmpty();
        }
    }

    @Nested
    @DisplayName("ช่องที่ไม่บังคับ")
    class OptionalFields {

        @Test
        @DisplayName("ช่อง 'อื่นๆ' และอนุสาขาที่เว้นไว้ ไม่นับว่าขาด")
        void optionalFieldsAreSkipped() {
            Map<String, String> data = positionDoc1("assoc_");
            data.put("assistant_method", "วิธีที่ ๑");
            data.put("other", "");
            data.put("other_position_1", "");
            data.put("sub_major", "");
            data.put("international_speaker_last_5_years_1", "");

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), "รองศาสตราจารย์");

            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("ตารางที่เพิ่มแถวได้ — แถวแรกบังคับ แถวถัดไปไม่บังคับ")
        void onlyTheFirstRepeatedRowIsRequired() {
            Map<String, String> data = positionDoc1("assoc_");
            data.put("assistant_method", "วิธีที่ ๑");
            data.put("education_degree_1", "");
            data.put("education_degree_2", "");

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), "รองศาสตราจารย์");

            assertThat(missing).containsExactly("education_degree_1");
        }

        @Test
        @DisplayName("เอกสารที่ 4 (กำหนดตำแหน่ง) เสนอขอเฉพาะงานวิจัย ไม่กรอกบทความ ไม่ถือว่าขาด")
        void positionDoc4ResearchOnlyDoesNotBlock() {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("title", "ผศ.ดร.");
            data.put("applicant_name", "สมชาย ใจดี");
            data.put("current_position", "ผู้ช่วยศาสตราจารย์");
            data.put("affiliation", "วิทยาลัยการคอมพิวเตอร์");
            data.put("request_position", "รองศาสตราจารย์");
            data.put("academic_paper_status", "");
            data.put("academic_paper_not_part_edu", "");
            data.put("academic_paper_is_part_edu", "");
            data.put("paper_title_1", "");
            data.put("academic_paper_count", "0");
            data.put("academic_paper_additional_detail", "");
            data.put("research_status", "not_part");
            data.put("research_not_part_edu", "✔");
            data.put("research_is_part_edu", "");
            data.put("research_count", "1");
            data.put("research_title_1", "AI in Education");
            data.put("research_additional_detail", "");

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 4, json(data), "รองศาสตราจารย์");

            assertThat(missing).isEmpty();
        }
    }

    @Nested
    @DisplayName("ช่องที่ผู้ยื่นแก้ไม่ได้")
    class FieldsTheApplicantCannotEdit {

        @Test
        @DisplayName("เลขที่หนังสือเป็นของแอดมิน เว้นว่างก็ยังลงนามได้")
        void adminOwnedFieldsDoNotBlockTheApplicant() {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("date", "๑ มีนาคม ๒๕๖๙");
            data.put("title", "ผศ.ดร.");
            data.put("applicant_name", "สมชาย ใจดี");
            data.put("current_position", "ผู้ช่วยศาสตราจารย์");
            data.put("employee_type", "พนักงานมหาวิทยาลัย");
            data.put("course_code", "SC101");
            data.put("course_name", "วิทยาการคอมพิวเตอร์เบื้องต้น");
            data.put("academic_year", "1/2569");
            data.put("chk1", "✓");
            data.put("chk2", "☐");
            data.put("memo_no", ""); // ช่องของแอดมิน

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.ACADEMIC, 1, json(data), null);

            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("เอกสารของแอดมินไม่ถูกตรวจด้วยกติกาของผู้ยื่น")
        void adminDocumentsAreNotChecked() {
            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.ACADEMIC, 3, json(Map.of("anything", "")), null);

            assertThat(missing).isEmpty();
        }
    }

    @Nested
    @DisplayName("ตัวเลือกตำแหน่งที่ขอในเอกสารที่ 1 (ประเมินการสอน)")
    class AcademicDoc1ChoiceFields {

        @Test
        @DisplayName("เลือก ผศ. แล้วอีกช่องเว้นว่าง (empty string) ต้องถือว่ากรอกครบ")
        void assistantSelectedWithEmptyAssociateIsComplete() {
            Map<String, String> data = academicDoc1Base();
            data.put("chk1", "✓");
            data.put("chk2", ""); // เบราว์เซอร์ส่งค่าว่างเพราะไม่ได้เลือก

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.ACADEMIC, 1, json(data), null);

            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("เลือก รศ. แล้วอีกช่องเว้นว่าง (empty string) ต้องถือว่ากรอกครบ")
        void associateSelectedWithEmptyAssistantIsComplete() {
            Map<String, String> data = academicDoc1Base();
            data.put("chk1", "");
            data.put("chk2", "✓");

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.ACADEMIC, 1, json(data), null);

            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("ไม่เลือกทั้งสองช่อง ต้องแจ้งเตือนขาดตัวเลือกตำแหน่ง")
        void neitherSelectedReportsMissing() {
            Map<String, String> data = academicDoc1Base();
            data.put("chk1", "");
            data.put("chk2", "");

            List<String> missing = DocumentCompleteness.missingApplicantFields(
                    SignatureModule.ACADEMIC, 1, json(data), null);

            assertThat(missing).containsExactly("target_position_choice");
        }

        private Map<String, String> academicDoc1Base() {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("title", "ผศ.ดร.");
            data.put("applicant_name", "สมชาย ใจดี");
            data.put("current_position", "ผู้ช่วยศาสตราจารย์");
            data.put("employee_type", "พนักงานมหาวิทยาลัย");
            data.put("course_code", "CP101");
            data.put("course_name", "Computer Programming");
            data.put("academic_year", "1/2569");
            return data;
        }
    }

    @Nested
    @DisplayName("ช่องที่แบบฟอร์ม พ.ศ. 2569 ตัดออก — ร่างเก่าที่ยังมีคีย์ค้างอยู่")
    class RetiredFields {

        /**
         * ฟอร์มบนเว็บเอาช่องพวกนี้ออกแล้ว แต่ร่างที่บันทึกก่อนหน้ายังมีคีย์ว่างค้างใน JSON
         * ถ้าด่านนี้นับว่าขาด ผู้ยื่นจะส่งไปลงนามไม่ได้ และไม่มีช่องให้กรอกเพื่อแก้เลย
         */
        @Test
        @DisplayName("เอกสารที่ 1: หัวข้อวิธีที่ ๓ ที่ค้างว่างอยู่ ไม่นับว่าขาด")
        void methodThreeLeftoversDoNotBlock() {
            Map<String, String> data = positionDoc1("assoc_");
            data.put("assistant_method", "วิธีที่ ๑");
            for (String key : List.of("assoc_h_index", "assoc_scopus_stories_count",
                    "assoc_scopus_citation_count", "assoc_method3_research_1",
                    "assoc_method3_research_no_1", "assoc_m3_quartile_1", "assoc_m3_first_1",
                    "assoc_pi_project_1", "assoc_pi_project_no_1", "assoc_pi_source_1")) {
                data.put(key, "");
            }

            assertThat(DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, json(data), "รองศาสตราจารย์"))
                    .isEmpty();
        }

        @Test
        @DisplayName("เอกสารที่ 9: กลุ่ม ๒–๓ ส่วนที่ ๒ และผู้มีส่วนสำคัญทางปัญญาที่ค้างว่างอยู่ ไม่นับว่าขาด")
        void documentNineLeftoversDoNotBlock() {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("title_name", "ผลงานวิจัย");
            data.put("role_des1", "ริเริ่ม");
            data.put("applicant_name", "สมชาย ใจดี");
            for (String key : List.of("chk_essen", "group1_research", "chkgroup2_1", "chkgroup3_book",
                    "des_journal", "des_ patent", "des_poster")) {
                data.put(key, "");
            }

            assertThat(DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 9, json(data), null))
                    .isEmpty();
        }

        @Test
        @DisplayName("ช่องที่ยังใช้อยู่ ถ้าว่างก็ยังต้องนับว่าขาดเหมือนเดิม")
        void liveFieldsStillCount() {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("title_name", "");
            data.put("des_journal", "");

            assertThat(DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 9, json(data), null))
                    .containsExactly("title_name");
        }
    }

    @Nested
    @DisplayName("ข้อมูลที่อ่านไม่ได้")
    class BadInput {

        @Test
        @DisplayName("ยังไม่เคยบันทึก — ปล่อยให้ด่าน frozenJson ว่างเป็นคนตอบ")
        void blankJsonYieldsNothing() {
            assertThat(DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, null, "รองศาสตราจารย์")).isEmpty();
            assertThat(DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, "   ", "รองศาสตราจารย์")).isEmpty();
        }

        @Test
        @DisplayName("JSON เสีย — ไม่โยน exception และไม่กันการลงนาม")
        void brokenJsonDoesNotThrow() {
            assertThat(DocumentCompleteness.missingApplicantFields(
                    SignatureModule.POSITION, 1, "{not json", "รองศาสตราจารย์")).isEmpty();
        }
    }

    @Test
    @DisplayName("requiredFields คืนเฉพาะช่องที่ผู้ยื่นต้องกรอกจริง")
    void requiredFieldsExcludesOptionalAndOtherRanks() {
        Map<String, String> data = positionDoc1("assoc_");

        var required = DocumentCompleteness.requiredFields(
                SignatureModule.POSITION, 1, json(data), "รองศาสตราจารย์");

        assertThat(required).contains("applicant_name", "assoc_used_research", "assistant_method");
        assertThat(required).doesNotContain("prof_used_research", "asst_used_research",
                "sub_major", "other");
    }
}
