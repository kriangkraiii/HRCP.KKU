package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.model.PositionRequest;
import com.ecom.model.UserDtls;

@DisplayName("เอกสาร ๘: ดึงข้อมูลจากเอกสารของผู้ยื่นมากรอกให้")
class Doc8AutoFillTest {

    private static final Map<String, String> DOC1 = Map.ofEntries(
            Map.entry("current_position", "ผู้ช่วยศาสตราจารย์"),
            Map.entry("method", "ปกติ"),
            Map.entry("major", "วิทยาการคอมพิวเตอร์"),
            Map.entry("major_code", "1234"),
            Map.entry("department", "สาขาวิชาวิทยาการคอมพิวเตอร์"),
            Map.entry("faculty", "วิทยาลัยการคอมพิวเตอร์"),
            Map.entry("lecturer_appointment_date", "๑ มิถุนายน พ.ศ. ๒๕๖๐"),
            Map.entry("years", "9"),
            Map.entry("months", "4"),
            Map.entry("assistant_department", "วิทยาการข้อมูล"),
            Map.entry("assistant_appointment_date", "๑ มกราคม พ.ศ. ๒๕๖๓"));

    private static PositionRequest request(String target) {
        UserDtls user = new UserDtls();
        user.setAcademicPosition("อาจารย์");
        PositionRequest request = new PositionRequest();
        request.setApplicant(user);
        request.setTargetPosition(target);
        return request;
    }

    private static EvaluationSummary evaluation() {
        return new EvaluationSummary(1L, "TE-001", "CP101", "การเขียนโปรแกรม", "2568", "1",
                "ชำนาญพิเศษ", "1 ม.ค. 2569", "1 ม.ค. 2571", null, null, null, null);
    }

    @Test
    void fillsApplicantAndThePositionHeldBeforeTheRequestedRank() {
        PositionRequest request = request("รองศาสตราจารย์");
        request.setCollegeResolutionDate(LocalDate.of(2026, 9, 15));

        Map<String, String> out = Doc8AutoFill.derive(request, DOC1, null, evaluation());

        assertEquals("ผู้ช่วยศาสตราจารย์", out.get("applicant_position"));
        assertEquals("รองศาสตราจารย์", out.get("target_position"));
        assertEquals("ปกติ", out.get("evaluation_method"));
        assertEquals("1234", out.get("major_code"));
        assertEquals("๑ มิถุนายน พ.ศ. ๒๕๖๐", out.get("date"));
        assertEquals("9", out.get("count_year"));
        assertEquals("4", out.get("count_month"));
        assertEquals("ผู้ช่วยศาสตราจารย์", out.get("current_position"));
        assertEquals("วิทยาการข้อมูล", out.get("current_major"));
        assertEquals("๑ มกราคม พ.ศ. ๒๕๖๓", out.get("currentpositiondate"));
        assertEquals("สาขาวิชาวิทยาการคอมพิวเตอร์", out.get("major_meet"));
        assertEquals("วิทยาลัยการคอมพิวเตอร์", out.get("department_meet"));
        assertEquals("๑๕ กันยายน พ.ศ. ๒๕๖๙", out.get("date_meet"));
        assertEquals("CP101", out.get("docsubject_code"));
        assertEquals("การเขียนโปรแกรม", out.get("subject_name"));
        assertEquals("ชำนาญพิเศษ", out.get("result_estimate"));
    }

    @Test
    void assistantProfessorRequestFallsBackToTheLecturerAppointment() {
        Map<String, String> doc1 = new java.util.HashMap<>(DOC1);
        doc1.remove("current_position");

        Map<String, String> out = Doc8AutoFill.derive(request("ผู้ช่วยศาสตราจารย์"), doc1, null, null);

        assertEquals("อาจารย์", out.get("applicant_position"));
        assertEquals("อาจารย์", out.get("current_position"));
        assertEquals("สาขาวิชาวิทยาการคอมพิวเตอร์", out.get("current_major"));
        assertEquals("๑ มิถุนายน พ.ศ. ๒๕๖๐", out.get("currentpositiondate"));
        assertFalse(out.containsKey("date_meet"));
        assertFalse(out.containsKey("docsubject_code"));
        assertFalse(out.containsKey("receivedrequest_date"));
    }

    @Test
    void receivedDateIsTheSubmissionDate() {
        PositionRequest request = request("ผู้ช่วยศาสตราจารย์");
        request.setSubmissionDate(java.time.LocalDateTime.of(2026, 10, 5, 9, 30));

        Map<String, String> out = Doc8AutoFill.derive(request, DOC1, null, null);

        assertEquals("๕ ตุลาคม พ.ศ. ๒๕๖๙", out.get("receivedrequest_date"));
    }

    @Test
    void authorRolesComeFromDoc6TickedOrNot() {
        Map<String, String> doc6 = Map.of(
                "des_research1", "งาน A", "chk_firstauthor1", "☑", "essen1", "☑",
                "des_research2", "งาน B", "chk_Corres2", "☑",
                "des_research4", "งาน D", "chk_Corres4", "☑",
                "des_research5", " ", "chk_firstauthor5", "☑");

        Map<String, String> out = Doc8AutoFill.derive(request("ผู้ช่วยศาสตราจารย์"), DOC1, doc6, null);

        // งานเรื่องที่ 1 = แถวหลัก (ไม่มีเลขต่อท้าย)
        assertEquals("☑", out.get("firstauthor"));
        assertEquals("☐", out.get("corresauthor"));
        assertEquals("☑", out.get("essencontributor"));
        // งานเรื่องที่ 2 = แถว 1
        assertEquals("☐", out.get("firstauthor1"));
        assertEquals("☑", out.get("corresauthor1"));
        // งานเรื่องที่ 4 = แถว 3 ซึ่งช่อง Corresponding สะกด corresautho3r ตามแม่แบบ
        assertEquals("☑", out.get("corresautho3r"));
        // ไม่มีชื่องาน ไม่นับเป็นแถว
        assertFalse(out.containsKey("firstauthor4"));
    }

    @Test
    void wholeResearchRowsAndTheirCountComeFromDoc6() {
        Map<String, String> doc6 = Map.of(
                "des_research1", "งาน A", "impactfacttor1", "2.5", "data1", "Scopus",
                "des_research2", "งาน B");

        Map<String, String> out = Doc8AutoFill.derive(request("ผู้ช่วยศาสตราจารย์"), DOC1, doc6, null);

        assertEquals("2", out.get("research_count"));
        assertEquals("งาน A", out.get("des_research"));
        assertEquals("2.5", out.get("impact_factor"));
        assertEquals("Scopus", out.get("database"));
        assertEquals("งาน B", out.get("research_des1"));
        // ผู้ยื่นเว้นว่างไว้ — ล็อกเป็นค่าว่าง ไม่ใช่ปล่อยให้กรอกเพิ่ม
        assertEquals("", out.get("impact_factor1"));
        assertEquals("", out.get("database1"));
    }

    @Test
    void articleCountStartsFromDoc4ButTheOfficerMayChangeIt() {
        Map<String, String> partOfDegree = Map.of("academic_paper_status", "is_part",
                "paper_title_1", "บทความ ก", "paper_title_2", "บทความ ข", "paper_title_3", " ");
        // เปลี่ยนเป็น "ไม่เป็นส่วนหนึ่ง" แล้ว ชื่อบทความยังค้างในช่องที่ซ่อน — ไม่นับ
        Map<String, String> notPart = Map.of("academic_paper_status", "not_part", "paper_title_1", "บทความ ก");

        assertEquals("2", Doc8AutoFill.defaults(partOfDegree).get("research2_count"));
        assertFalse(Doc8AutoFill.defaults(notPart).containsKey("research2_count"));
        assertFalse(Doc8AutoFill.defaults(null).containsKey("research2_count"));

        PositionRequest request = request("ผู้ช่วยศาสตราจารย์");
        com.ecom.academic.model.PositionDocument doc4 = new com.ecom.academic.model.PositionDocument();
        doc4.setDocumentType(4);
        doc4.setJsonData("{\"academic_paper_status\":\"is_part\",\"paper_title_1\":\"บทความ ก\"}");
        request.getDocuments().add(doc4);
        DocumentDataAutoFillHelper helper = new DocumentDataAutoFillHelper(null);

        assertEquals("1", helper.getPreFilledPositionDocData(request, 8, null).get("research2_count"));
        assertEquals("3", helper.getPreFilledPositionDocData(request, 8, "{\"research2_count\":\"3\"}")
                .get("research2_count"));
        assertFalse(helper.lockedFields(request, 8).containsKey("research2_count"));
    }

    @Test
    void booksForTheRequestedRankComeFromDoc1() {
        Map<String, String> doc1 = new java.util.HashMap<>(DOC1);
        doc1.put("assoc_book_working_1", "ตำรา ก");
        doc1.put("assoc_book_working_2", "ตำรา ข");
        doc1.put("asst_book_working_1", "ตำราตอนขอ ผศ.");

        Map<String, String> out = Doc8AutoFill.derive(request("รองศาสตราจารย์"), doc1, null, null);

        assertEquals("2", out.get("research1_count"));
        assertEquals("1. ตำรา ก\n2. ตำรา ข", out.get("research1_des"));
        assertFalse(out.containsKey("research2_count"));
        assertFalse(out.containsKey("research_count"));
    }

    @Test
    void recordedByIsTheOfficerFillingTheForm() {
        UserDtls officer = new UserDtls();
        officer.setTitle("นางสาว");
        officer.setFirstName("สมหญิง");
        officer.setLastName("สายตรวจการ");
        DocumentDataAutoFillHelper helper = new DocumentDataAutoFillHelper(null);

        assertEquals("นางสาวสมหญิง สายตรวจการ", Doc8AutoFill.officerName(officer));
        // ตั้งต้นเท่านั้น — เจ้าหน้าที่เลือกคนอื่นได้
        assertFalse(helper.lockedFields(request("ผู้ช่วยศาสตราจารย์"), 8).containsKey("name_admin"));
    }

    @Test
    void derivedFieldsAreLockedOverSavedValuesTheRestStayEditable() {
        DocumentDataAutoFillHelper helper = new DocumentDataAutoFillHelper(null);
        PositionRequest request = request("ผู้ช่วยศาสตราจารย์");
        request.getApplicant().setFirstName("สมชาย");
        request.getApplicant().setLastName("ใจดี");

        Map<String, String> data = helper.getPreFilledPositionDocData(request, 8,
                "{\"applicant_position\":\"อาจารย์ ดร.\",\"count_meet\":\"5/2569\",\"date\":\"\"}");
        Map<String, String> locked = helper.lockedFields(request, 8);

        assertEquals("อาจารย์", data.get("applicant_position"));
        assertEquals("สมชาย ใจดี", data.get("applicant_name"));
        assertEquals("5/2569", data.get("count_meet"));
        assertEquals("อาจารย์", locked.get("applicant_position"));
        assertFalse(locked.containsKey("count_meet"));
        assertFalse(data.containsKey("date"));
    }
}
