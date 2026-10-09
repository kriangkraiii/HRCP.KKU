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

        Map<String, String> out = Doc8AutoFill.derive(request, DOC1, evaluation());

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

        Map<String, String> out = Doc8AutoFill.derive(request("ผู้ช่วยศาสตราจารย์"), doc1, null);

        assertEquals("อาจารย์", out.get("applicant_position"));
        assertEquals("อาจารย์", out.get("current_position"));
        assertEquals("สาขาวิชาวิทยาการคอมพิวเตอร์", out.get("current_major"));
        assertEquals("๑ มิถุนายน พ.ศ. ๒๕๖๐", out.get("currentpositiondate"));
        assertFalse(out.containsKey("date_meet"));
        assertFalse(out.containsKey("docsubject_code"));
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
