package com.ecom.academic.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.model.PositionRequest;
import com.ecom.model.UserDtls;
import com.ecom.util.ThaiDateUtil;

/**
 * ค่าตั้งต้นของเอกสารที่ 8 (แบบสรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ) จากข้อมูลที่ผู้ยื่นกรอกไว้แล้ว
 *
 * <p>เอกสารที่ 8 ใช้ชื่อช่องของตัวเอง (date, current_major, docsubject_code …) ความหมายตามข้อความใน
 * p2doc_8.docx เช่น {@code date} คือ "ได้รับการแต่งตั้งให้ดำรงตำแหน่งอาจารย์ เมื่อวันที่" ไม่ใช่วันที่ทำเอกสาร
 *
 * <p>ทุกค่าที่คืนไปล็อก — เจ้าหน้าที่แก้ไม่ได้ เซิร์ฟเวอร์เขียนทับตอนบันทึก ช่องที่ระบบไม่รู้ค่า (ไม่อยู่ในผลลัพธ์) กรอกเองได้
 */
public final class Doc8AutoFill {

    private Doc8AutoFill() {
    }

    /**
     * @param doc1       เอกสารที่ 1 (ก.พ.ว. มข. 03) หรือ null
     * @param evaluation ผลประเมินการสอนที่คำร้องอ้างถึง หรือ null
     */
    public static Map<String, String> derive(PositionRequest request, Map<String, String> doc1,
            EvaluationSummary evaluation) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> d1 = doc1 == null ? Map.of() : doc1;

        // ชื่อ - สกุล {applicant_name} {ตำแหน่งปัจจุบันของผู้ยื่น}
        UserDtls user = request == null ? null : request.getApplicant();
        String name = user == null || user.getFirstName() == null || user.getFirstName().isBlank() ? null
                : (user.getFirstName() + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        put(out, "applicant_name", firstNonBlank(name, d1.get("applicant_name")));
        String applicantPosition = firstNonBlank(d1.get("current_position"),
                request == null || request.getApplicant() == null ? null
                        : request.getApplicant().getAcademicPosition());
        put(out, "applicant_position", applicantPosition);

        String target = firstNonBlank(request == null ? null : request.getTargetPosition(),
                d1.get("target_position"));
        put(out, "target_position", target);
        put(out, "evaluation_method", firstNonBlank(request == null ? null : request.getEvaluationMethod(),
                d1.get("method")));
        put(out, "major", firstNonBlank(d1.get("major"), request == null ? null : request.getMajor()));
        put(out, "major_code", firstNonBlank(d1.get("major_code"), request == null ? null : request.getMajorCode()));

        // ได้รับการแต่งตั้งให้ดำรงตำแหน่งอาจารย์ เมื่อวันที่ ... อายุราชการ ... ปี ... เดือน
        put(out, "date", d1.get("lecturer_appointment_date"));
        put(out, "count_year", d1.get("years"));
        put(out, "count_month", d1.get("months"));

        // เคยได้รับการแต่งตั้งให้ดำรงตำแหน่ง ... ในสาขาวิชา ... เมื่อวันที่ — ตำแหน่งก่อนหน้าตำแหน่งที่ขอ
        String rank = target == null ? "" : target.trim();
        if ("รองศาสตราจารย์".equals(rank)) {
            out.put("current_position", "ผู้ช่วยศาสตราจารย์");
            put(out, "current_major", d1.get("assistant_department"));
            put(out, "currentpositiondate", d1.get("assistant_appointment_date"));
        } else if ("ศาสตราจารย์".equals(rank)) {
            out.put("current_position", "รองศาสตราจารย์");
            put(out, "current_major", d1.get("associate_department"));
            put(out, "currentpositiondate", d1.get("associate_appointment_date"));
        } else {
            put(out, "current_position", applicantPosition);
            put(out, "current_major", d1.get("department"));
            put(out, "currentpositiondate", d1.get("lecturer_appointment_date"));
        }

        // ผ่านการประชุมกรรมการประจำคณะฯ ... สังกัดสายวิชา ... คณะ ...
        put(out, "major_meet", d1.get("department"));
        put(out, "department_meet", d1.get("faculty"));
        if (request != null && request.getCollegeResolutionDate() != null) {
            out.put("date_meet", thaiDate(request.getCollegeResolutionDate()));
        }

        // เอกสารประกอบการประเมินผลการสอน รหัส ... รายวิชา ... / สรุปผลการประเมิน
        if (evaluation != null) {
            put(out, "docsubject_code", evaluation.courseCode());
            put(out, "subject_name", evaluation.courseName());
            put(out, "result_estimate", evaluation.resultLevel());
        }
        return out;
    }

    /** "๑ มกราคม พ.ศ. ๒๕๖๐" — ช่องในเอกสารมีคำว่า "เมื่อวันที่" นำหน้าอยู่แล้ว */
    static String thaiDate(LocalDate d) {
        return ThaiDateUtil.toThaiDigits(d.getDayOfMonth() + " " + Doc7AutoFill.THAI_MONTHS[d.getMonthValue() - 1]
                + " พ.ศ. " + (d.getYear() + 543));
    }

    private static void put(Map<String, String> out, String key, String value) {
        if (value != null && !value.isBlank()) {
            out.put(key, value.trim());
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
