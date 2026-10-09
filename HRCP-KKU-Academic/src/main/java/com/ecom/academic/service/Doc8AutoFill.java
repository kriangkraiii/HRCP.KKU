package com.ecom.academic.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern DOC6_WORK = Pattern.compile("^des_research(\\d+)$");

    private Doc8AutoFill() {
    }

    /**
     * @param doc1       เอกสารที่ 1 (ก.พ.ว. มข. 03) หรือ null
     * @param doc6       เอกสารที่ 6 (การมีส่วนร่วมในผลงาน) หรือ null
     * @param evaluation ผลประเมินการสอนที่คำร้องอ้างถึง หรือ null
     */
    public static Map<String, String> derive(PositionRequest request, Map<String, String> doc1,
            Map<String, String> doc6, EvaluationSummary evaluation) {
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

        // กองทรัพยากรบุคคลรับเรื่อง วันที่ — วันที่ผู้ยื่นส่งคำร้อง
        if (request != null && request.getSubmissionDate() != null) {
            out.put("receivedrequest_date", thaiDate(request.getSubmissionDate().toLocalDate()));
        }

        putResearchRows(out, doc6);
        putBooks(out, d1, target);

        // เอกสารประกอบการประเมินผลการสอน รหัส ... รายวิชา ... / สรุปผลการประเมิน
        if (evaluation != null) {
            put(out, "docsubject_code", evaluation.courseCode());
            put(out, "subject_name", evaluation.courseName());
            put(out, "result_estimate", evaluation.resultLevel());
        }
        return out;
    }

    /**
     * ตารางผลงานวิจัยทั้งแถวจากเอกสารที่ 6: ชื่อผลงาน สถานะผู้ขอ (First / Corresponding / Co-author)
     * Impact factor และฐานข้อมูล — ช่องว่างก็เป็นค่าที่ผู้ยื่นให้มา จึงส่งไปเป็น "" ให้ล็อกด้วย
     *
     * <p>ผลงานเรื่องที่ N ของเอกสารที่ 6 คือแถวที่ N-1 ของตารางเอกสารที่ 8 — ชื่อช่องตาม
     * {@code getFieldNames} ในหน้า doc_form_8 (แถวแรกไม่มีเลขต่อท้าย แถว 3 ของ Corresponding สะกด corresautho3r)
     */
    private static void putResearchRows(Map<String, String> out, Map<String, String> doc6) {
        if (doc6 == null) {
            return;
        }
        int works = 0;
        for (Map.Entry<String, String> e : doc6.entrySet()) {
            Matcher m = DOC6_WORK.matcher(e.getKey());
            if (!m.matches() || e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            works++;
            int n = Integer.parseInt(m.group(1));
            String row = n == 1 ? "" : String.valueOf(n - 1);
            out.put(n == 1 ? "des_research" : "research_des" + row, e.getValue().trim());
            out.put("firstauthor" + row, tick(doc6.get("chk_firstauthor" + n)));
            out.put(n == 4 ? "corresautho3r" : "corresauthor" + row, tick(doc6.get("chk_Corres" + n)));
            out.put("essencontributor" + row, tick(doc6.get("essen" + n)));
            out.put("impact_factor" + row, orEmpty(doc6.get("impactfacttor" + n)));
            out.put("database" + row, orEmpty(doc6.get("data" + n)));
        }
        if (works > 0) {
            out.put("research_count", String.valueOf(works));
        }
    }

    /** ตำรา จำนวน ... เรื่อง — ตำราหรือหนังสือที่ผู้ยื่นเสนอสำหรับตำแหน่งที่ขอในเอกสารที่ 1 (ข้อ ๔.๑.๓) */
    private static void putBooks(Map<String, String> out, Map<String, String> doc1, String target) {
        String prefix = target == null ? null : Doc7AutoFill.RANK_PREFIX.get(target.trim());
        if (prefix == null) {
            return;
        }
        java.util.List<String> books = java.util.List.copyOf(
                Doc7AutoFill.rows(doc1, prefix + "_book_working").values());
        if (books.isEmpty()) {
            return;
        }
        out.put("research1_count", String.valueOf(books.size()));
        StringBuilder titles = new StringBuilder();
        for (int i = 0; i < books.size(); i++) {
            titles.append(i == 0 ? "" : "\n").append(books.size() == 1 ? "" : (i + 1) + ". ").append(books.get(i));
        }
        out.put("research1_des", titles.toString());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String tick(String value) {
        return Doc7AutoFill.CHECKED.equals(value) ? Doc7AutoFill.CHECKED : Doc7AutoFill.UNCHECKED;
    }

    /**
     * ค่าตั้งต้นที่ไม่ล็อก — ค่าที่เจ้าหน้าที่บันทึกไว้ชนะ
     *
     * <p>จำนวนบทความทางวิชาการ: ผู้ยื่นใส่รายชื่อบทความในเอกสารที่ 4 ได้เฉพาะบทความที่เป็นส่วนหนึ่งของการศึกษา
     * เพื่อรับปริญญา บทความอื่นไม่มีที่กรอก ตัวเลขนี้จึงอาจน้อยกว่าจริง เจ้าหน้าที่ต้องเพิ่มได้
     *
     * @param doc4 เอกสารที่ 4 (บันทึกรับรองผลงานทางวิชาการ) หรือ null
     */
    public static Map<String, String> defaults(Map<String, String> doc4) {
        Map<String, String> out = new LinkedHashMap<>();
        // ชื่อบทความที่ผู้ยื่นเปลี่ยนเป็น "ไม่เป็นส่วนหนึ่ง" แล้วยังค้างอยู่ในช่องที่ซ่อนไว้ — ไม่นับ
        if (doc4 != null && "is_part".equals(doc4.get("academic_paper_status"))) {
            int papers = Doc7AutoFill.rows(doc4, "paper_title").size();
            if (papers > 0) {
                out.put("research2_count", String.valueOf(papers));
            }
        }
        return out;
    }

    /** "นางสาวสมหญิง สายตรวจการ" — ชื่อเจ้าหน้าที่ผู้บันทึกในช่องลงชื่อท้ายเอกสาร */
    public static String officerName(UserDtls officer) {
        if (officer == null) {
            return null;
        }
        if (officer.getFirstName() == null || officer.getFirstName().isBlank()) {
            return officer.getName();
        }
        String title = officer.getTitle() == null ? "" : officer.getTitle().trim();
        String last = officer.getLastName() == null ? "" : officer.getLastName().trim();
        return (title + officer.getFirstName().trim() + " " + last).trim();
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
