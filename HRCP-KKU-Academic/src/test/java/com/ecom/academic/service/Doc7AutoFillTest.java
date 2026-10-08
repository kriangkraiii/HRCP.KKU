package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequest;
import com.ecom.model.UserDtls;

@DisplayName("เอกสาร ๗: ดึงข้อมูลจากเอกสารของผู้ยื่นมากรอกให้")
class Doc7AutoFillTest {

    private static final String TICK = Doc7AutoFill.CHECKED;
    private static final LocalDate SUBMITTED = LocalDate.of(2026, 10, 8);

    private static PositionRequest request(String target) {
        UserDtls user = new UserDtls();
        user.setFirstName("สมชาย");
        user.setLastName("ใจดี");
        user.setEmail("somchai@kku.ac.th");
        user.setMobileNumber("0812345678");
        PositionRequest request = new PositionRequest();
        request.setApplicant(user);
        request.setTargetPosition(target);
        request.setMajor("วิทยาการคอมพิวเตอร์");
        return request;
    }

    @Test
    void fillsApplicantEducationAndWorksForTheRequestedRank() {
        Map<String, String> doc1 = Map.ofEntries(
                Map.entry("department", "สาขาวิชาวิทยาการคอมพิวเตอร์"),
                Map.entry("major_code", "1234"),
                Map.entry("lecturer_appointment_date", "๑ มิถุนายน พ.ศ. ๒๕๖๐"),
                Map.entry("education_degree_1", "วท.บ."),
                Map.entry("education_year_1", "2550"),
                Map.entry("education_degree_2", "วท.ม."),
                Map.entry("education_year_2", "2553"),
                Map.entry("education_degree_3", "Ph.D."),
                Map.entry("education_year_3", "2015"),
                Map.entry("assoc_research_working_1", "งานวิจัย A"),
                Map.entry("assoc_research_working_3", "งานวิจัย B"),
                Map.entry("assoc_research_working_4", " "),
                Map.entry("assoc_book_working_1", "ตำรา C"),
                Map.entry("asst_research_working_1", "งานที่ใช้ขอ ผศ. ไปแล้ว"));
        Map<String, String> doc2 = Map.of("status", "ข้าราชการ");
        Map<String, String> doc4 = Map.of("master_thesis", "✔", "master_thesis_title", "วิทยานิพนธ์โท",
                "doctoral_thesis_title", "ค้างจากตอนที่เคยติ๊กไว้");

        Map<String, String> out = Doc7AutoFill.derive(request("รองศาสตราจารย์"), doc1, doc2, doc4,
                "ชำนาญพิเศษ", true, SUBMITTED);

        assertEquals("สมชาย", out.get("applicant_firstname"));
        assertEquals("ใจดี", out.get("applicant_lastname"));
        assertEquals("ข้าราชการ", out.get("status"));
        assertEquals("วิทยาการคอมพิวเตอร์", out.get("major"));
        assertEquals("1234", out.get("major_code"));
        assertEquals("0812345678", out.get("phone_mobile"));
        // จบ ป.เอก ปี 2015 (= 2558) ก่อนเป็นอาจารย์ 1 มิ.ย. 2560 — นับเต็มช่วงตามวุฒิสูงสุด
        assertEquals("9", out.get("d_years"));
        assertEquals("4", out.get("d_months"));
        assertEquals("7", out.get("d_days"));
        assertFalse(out.containsKey("m_years"), "นับเฉพาะวุฒิสูงสุด");
        assertEquals("วิทยานิพนธ์โท", out.get("master_thesis_titles"));
        assertFalse(out.containsKey("doctoral_thesis_titles"), "ไม่ได้ติ๊กระดับปริญญาเอกในเอกสารที่ 4");

        assertEquals(TICK, out.get("is_req_assoc"));
        assertEquals(TICK, out.get("is_teach_assoc"));
        assertNull(out.get("is_req_asst"));
        assertEquals(TICK, out.get("eval_highly_skilled"));
        assertEquals(TICK, out.get("is_req_03"));
        assertEquals(TICK, out.get("is_req_04"));
        assertEquals(TICK, out.get("teaching_eval_1_set"));

        assertEquals("งานวิจัย A", out.get("research_working_title_1"));
        assertEquals("งานวิจัย B", out.get("research_working_title_2"), "เลขแถวต้องเรียงต่อกันใหม่");
        assertFalse(out.containsKey("research_working_title_3"));
        assertEquals("2", out.get("research_and_articles"));
        assertEquals("ตำรา C", out.get("book_working_title_1"));
        assertEquals("1", out.get("textbooks_and_books"));
        assertEquals("3", out.get("total_stories"));
    }

    @Test
    void worksWithNothingButTheRequest() {
        Map<String, String> out = Doc7AutoFill.derive(request("ผู้ช่วยศาสตราจารย์"), null, null, null, null, false, SUBMITTED);

        assertEquals("สมชาย", out.get("applicant_firstname"));
        assertEquals(TICK, out.get("is_req_asst"));
        assertFalse(out.containsKey("is_req_03"));
        assertFalse(out.containsKey("total_stories"));
    }

    @Test
    void levelPrefersTheLongerMatch() {
        assertEquals("eval_skilled", Doc7AutoFill.levelField("ชำนาญ"));
        assertEquals("eval_highly_skilled", Doc7AutoFill.levelField("ชำนาญพิเศษ"));
        assertEquals("eval_expert", Doc7AutoFill.levelField("เชี่ยวชาญ"));
        assertNull(Doc7AutoFill.levelField(""));
    }

    @Test
    void leavesServicePeriodBlankWhenTheDegreeCameAfterAppointment() {
        Map<String, String> doc1 = Map.of(
                "lecturer_appointment_date", "1 มิถุนายน 2560",
                "education_degree_1", "ปร.ด.",
                "education_year_1", "2563");

        Map<String, String> out = Doc7AutoFill.derive(request("ผู้ช่วยศาสตราจารย์"), doc1, null, null, null, false,
                SUBMITTED);

        assertFalse(out.containsKey("d_years"), "รู้แค่ปีที่จบ วันเริ่มนับไม่แน่นอน");
        assertFalse(out.containsKey("m_years"));
    }

    @Test
    void parsesTheDateFormatsApplicantsType() {
        assertEquals(LocalDate.of(2017, 6, 1), Doc7AutoFill.parseThaiDate("๑ มิถุนายน พ.ศ. ๒๕๖๐"));
        assertEquals(LocalDate.of(2017, 6, 1), Doc7AutoFill.parseThaiDate("1 มิ.ย. 2560"));
        assertEquals(LocalDate.of(2017, 6, 1), Doc7AutoFill.parseThaiDate("01/06/2560"));
        assertEquals(LocalDate.of(2017, 6, 1), Doc7AutoFill.parseThaiDate("1 มิถุนายน 2017"));
        assertNull(Doc7AutoFill.parseThaiDate("ไม่ทราบ"));
    }
}
