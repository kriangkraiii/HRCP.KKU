package com.ecom.academic.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ecom.academic.model.PositionRequest;
import com.ecom.model.UserDtls;
import com.ecom.util.ThaiDateUtil;

/**
 * ค่าตั้งต้นของเอกสารที่ 7 (แบบฟอร์มตรวจสอบคุณสมบัติ) จากข้อมูลที่ผู้ยื่นกรอกไว้แล้ว
 *
 * <p>เอกสารที่ 7 ใช้ชื่อช่องของตัวเอง (applicant_firstname, m_years, research_working_title_N …)
 * ไม่ตรงกับชื่อช่องกลางที่เอกสารอื่นใช้ร่วมกัน เจ้าหน้าที่จึงต้องพิมพ์ทุกอย่างซ้ำจากเอกสารที่ 1
 * ทั้งที่ระบบมีอยู่แล้ว คลาสนี้แปลงข้อมูลเหล่านั้นเป็นชื่อช่องของเอกสารที่ 7
 *
 * <p>ช่องติ๊กใช้ค่า {@link #CHECKED} เหมือนที่ฟอร์มส่งมา
 */
public final class Doc7AutoFill {

    public static final String CHECKED = "☑";
    public static final String UNCHECKED = "☐";

    /**
     * ค่าที่ดึงมาเป็นค่าตั้งต้นแต่ไม่ล็อก — เจ้าหน้าที่แก้ได้ และค่าที่บันทึกไว้ชนะ
     * <ul>
     * <li>เอกสารประกอบ: มีในระบบไม่ได้แปลว่าเอกสารจริงครบ เจ้าหน้าที่ต้องเอาติ๊กออกได้</li>
     * <li>จำนวนผลงานรวม: เจ้าหน้าที่อาจเพิ่มผลงานรับใช้สังคมหรือบทความที่ระบบไม่มีข้อมูล</li>
     * </ul>
     */
    public static final java.util.Set<String> DEFAULT_ONLY = java.util.Set.of(
            "is_req_03", "is_req_04", "is_req_05", "teaching_eval_1_set", "total_stories");

    private static final List<List<String>> SINGLE_CHOICE_GROUPS = List.of(
            List.of("is_req_asst", "is_req_assoc", "is_req_prof", "is_req_prof_2"),
            List.of("is_teach_asst", "is_teach_assoc", "is_teach_prof"),
            List.of("eval_skilled", "eval_highly_skilled", "eval_expert"));

    /** ช่องผลงานในเอกสารที่ 1 แยกตามตำแหน่งที่ขอ — ดู PositionRequestService.workCopies */
    static final Map<String, String> RANK_PREFIX = Map.of(
            "ผู้ช่วยศาสตราจารย์", "asst",
            "รองศาสตราจารย์", "assoc",
            "ศาสตราจารย์", "prof");

    private static final Pattern DOCTORAL = Pattern.compile(
            "(?i)(เอก|ph\\.?\\s*d|doctor|d\\.\\s*eng|ด\\.?$)");
    private static final Pattern MASTER = Pattern.compile(
            "(?i)(โท|master|^m\\.|^mba|ม\\.?$)");
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})\\s*[/.-]\\s*(\\d{4})");
    private static final Pattern NAMED_DATE = Pattern.compile("(\\d{1,2})\\s+(\\S+(?:\\s?\\S\\.)?)\\s+(\\d{4})");
    static final String[] THAI_MONTHS = { "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม",
            "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม" };
    private static final String[] THAI_MONTH_ABBR = { "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.",
            "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค." };

    private Doc7AutoFill() {
    }

    /**
     * @param doc1             เอกสารที่ 1 (ก.พ.ว. มข. 03) หรือ null
     * @param doc2             เอกสารที่ 2 (หนังสือแจ้งความประสงค์) หรือ null
     * @param doc4             เอกสารที่ 4 (บันทึกรับรองผลงาน วิทยานิพนธ์) หรือ null
     * @param evaluationLevel  ระดับผลประเมินการสอนที่คำร้องอ้างถึง เช่น "ชำนาญพิเศษ" หรือ null
     * @param hasEvaluation    คำร้องผูกกับผลประเมินการสอนไว้หรือไม่
     * @param asOf             วันที่นับระยะเวลาการปฏิบัติงานถึง (วันที่ยื่นคำร้อง)
     */
    public static Map<String, String> derive(PositionRequest request, Map<String, String> doc1,
            Map<String, String> doc2, Map<String, String> doc4, String evaluationLevel, boolean hasEvaluation,
            LocalDate asOf) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> d1 = doc1 == null ? Map.of() : doc1;
        Map<String, String> d2 = doc2 == null ? Map.of() : doc2;
        Map<String, String> d4 = doc4 == null ? Map.of() : doc4;

        // ข้อมูลผู้ขอ
        UserDtls user = request == null ? null : request.getApplicant();
        String first = user == null ? null : user.getFirstName();
        String last = user == null ? null : user.getLastName();
        if (isBlank(first) && !isBlank(d1.get("applicant_name"))) {
            String[] parts = d1.get("applicant_name").trim().split("\\s+", 2);
            first = parts[0];
            last = parts.length > 1 ? parts[1] : null;
        }
        put(out, "applicant_firstname", first);
        put(out, "applicant_lastname", last);
        put(out, "status", d2.get("status"));
        put(out, "department", d1.get("department"));
        put(out, "faculty", d1.get("faculty"));
        put(out, "major", firstNonBlank(d1.get("major"), request == null ? null : request.getMajor()));
        put(out, "major_code", firstNonBlank(d1.get("major_code"), request == null ? null : request.getMajorCode()));
        if (user != null) {
            put(out, "email", user.getEmail());
            put(out, "phone_mobile", user.getMobileNumber());
        }

        putServicePeriod(out, d1, asOf);
        // ชื่อวิทยานิพนธ์ที่ผู้ยื่นเอาติ๊กระดับนั้นออกแล้วยังค้างอยู่ในช่องที่ซ่อนไว้ — ไม่นับ
        if (!isBlank(d4.get("master_thesis"))) {
            put(out, "master_thesis_titles", d4.get("master_thesis_title"));
        }
        if (!isBlank(d4.get("doctoral_thesis"))) {
            put(out, "doctoral_thesis_titles", d4.get("doctoral_thesis_title"));
        }

        // ตำแหน่งที่ขอ
        String target = firstNonBlank(request == null ? null : request.getTargetPosition(),
                d1.get("target_position"));
        String prefix = target == null ? null : RANK_PREFIX.get(target.trim());
        if (prefix != null) {
            // ศ. แยก "วิธีที่ 1/2/พิเศษ" กับ "ได้รับเงินเดือนขั้นสูง" ไม่ได้จากคำร้อง — ให้เจ้าหน้าที่เลือกเอง
            if (!"prof".equals(prefix)) {
                out.put("is_req_" + prefix, CHECKED);
            }
            out.put("is_teach_" + prefix, CHECKED);
        }
        String levelField = levelField(evaluationLevel);
        if (levelField != null) {
            out.put(levelField, CHECKED);
        }

        // เอกสารประกอบที่อยู่ในระบบแล้ว
        if (!d1.isEmpty()) {
            out.put("is_req_03", CHECKED);
            // แบบประเมินผู้บังคับบัญชารวมเป็นส่วนที่ ๒ ของเอกสารที่ 1 แล้ว
            out.put("is_req_05", CHECKED);
        }
        if (!d4.isEmpty()) {
            out.put("is_req_04", CHECKED);
        }
        if (hasEvaluation) {
            out.put("teaching_eval_1_set", CHECKED);
        }

        // ผลงานที่ยื่นสำหรับตำแหน่งที่ขอ — เรียงเลขใหม่ให้ต่อเนื่องตามบล็อกของเอกสารที่ 7
        if (prefix != null) {
            int research = putWorks(out, rows(d1, prefix + "_research_working"), "research_working_title_");
            int books = putWorks(out, rows(d1, prefix + "_book_working"), "book_working_title_");
            int others = putWorks(out, rows(d1, prefix + "_other_working"), "other_working_title_");
            putCount(out, "research_and_articles", research);
            putCount(out, "textbooks_and_books", books);
            putCount(out, "other_academic_works", others);
            if (research + books + others > 0) {
                out.put("total_stories", String.valueOf(research + books + others));
            }
        }
        return out;
    }

    /**
     * ช่องที่ล็อกไว้จากค่าที่ดึงมา — เจ้าหน้าที่แก้ไม่ได้ เซิร์ฟเวอร์เขียนทับทุกครั้งที่บันทึก
     *
     * <p>กลุ่มที่ตอบได้ทางเดียว (ตำแหน่งที่ขอ ผลงานด้านการสอน ระดับการประเมิน) ล็อกทั้งกลุ่ม
     * ช่องอื่นในกลุ่มเป็น ☐ จะได้ติ๊กซ้อนไม่ได้ ช่องใน {@link #DEFAULT_ONLY} ไม่ล็อก
     */
    public static Map<String, String> locked(Map<String, String> derived) {
        Map<String, String> locked = new LinkedHashMap<>(derived);
        locked.keySet().removeAll(DEFAULT_ONLY);
        for (List<String> group : SINGLE_CHOICE_GROUPS) {
            if (group.stream().anyMatch(f -> CHECKED.equals(derived.get(f)))) {
                group.forEach(f -> locked.putIfAbsent(f, UNCHECKED));
            }
        }
        return locked;
    }

    /** ระดับผลประเมินการสอน → ช่องติ๊กระดับการประเมิน ("ชำนาญพิเศษ" ต้องเช็กก่อน "ชำนาญ") */
    static String levelField(String level) {
        if (isBlank(level)) {
            return null;
        }
        if (level.contains("เชี่ยวชาญ")) {
            return "eval_expert";
        }
        if (level.contains("ชำนาญพิเศษ")) {
            return "eval_highly_skilled";
        }
        if (level.contains("ชำนาญ")) {
            return "eval_skilled";
        }
        return null;
    }

    /** แถว {prefix}_N ที่มีค่า เรียงตาม N → {N: ค่า} */
    static Map<Integer, String> rows(Map<String, String> data, String prefix) {
        Pattern row = Pattern.compile("^" + Pattern.quote(prefix) + "_(\\d+)$");
        Map<Integer, String> found = new TreeMap<>();
        for (Map.Entry<String, String> e : data.entrySet()) {
            Matcher m = row.matcher(e.getKey());
            if (m.matches() && !isBlank(e.getValue())) {
                found.put(Integer.parseInt(m.group(1)), e.getValue().trim());
            }
        }
        return found;
    }

    private static int putWorks(Map<String, String> out, Map<Integer, String> works, String field) {
        List<String> titles = List.copyOf(works.values());
        for (int i = 0; i < titles.size(); i++) {
            out.put(field + (i + 1), titles.get(i));
        }
        return titles.size();
    }

    private static void putCount(Map<String, String> out, String field, int count) {
        if (count > 0) {
            out.put(field + "_chk", CHECKED);
            out.put(field, String.valueOf(count));
        }
    }

    /**
     * ระยะเวลาการปฏิบัติงาน (ปี เดือน วัน) ตามวุฒิสูงสุด นับจากวันที่ได้รับแต่งตั้งเป็นอาจารย์ถึง {@code asOf}
     *
     * <p>เอกสารที่ 1 เก็บแค่ปีที่สำเร็จการศึกษา นับได้แม่นเฉพาะเมื่อสำเร็จก่อนปีที่เป็นอาจารย์
     * ซึ่งทั้งช่วงที่เป็นอาจารย์ถือวุฒินั้นอยู่แล้ว กรณีอื่นวันเริ่มนับไม่แน่นอน ปล่อยให้เจ้าหน้าที่กรอกเอง
     */
    private static void putServicePeriod(Map<String, String> out, Map<String, String> doc1, LocalDate asOf) {
        LocalDate appointed = parseThaiDate(doc1.get("lecturer_appointment_date"));
        if (appointed == null || asOf == null || appointed.isAfter(asOf)) {
            return;
        }
        String field = null;
        Integer graduated = null;
        for (Map.Entry<Integer, String> row : rows(doc1, "education_degree").entrySet()) {
            Integer year = beYear(doc1.get("education_year_" + row.getKey()));
            if (DOCTORAL.matcher(row.getValue()).find()) {
                field = "d";
                graduated = year;
            } else if (MASTER.matcher(row.getValue()).find() && !"d".equals(field)) {
                field = "m";
                graduated = year;
            }
        }
        if (field == null || graduated == null || graduated >= appointed.getYear() + 543) {
            return;
        }
        Period period = Period.between(appointed, asOf);
        out.put(field + "_years", String.valueOf(period.getYears()));
        out.put(field + "_months", String.valueOf(period.getMonths()));
        out.put(field + "_days", String.valueOf(period.getDays()));
    }

    /** ปี พ.ศ. ในข้อความ — ผู้ยื่นบางคนกรอกเป็น ค.ศ. หรือเลขไทย */
    static Integer beYear(String raw) {
        Matcher m = YEAR.matcher(ThaiDateUtil.toArabicDigits(raw == null ? "" : raw));
        if (!m.find()) {
            return null;
        }
        int year = Integer.parseInt(m.group(1));
        return year < 2400 ? year + 543 : year;
    }

    /** "๑ มกราคม พ.ศ. ๒๕๖๐", "1 ม.ค. 2560" หรือ "01/01/2560" → LocalDate (ปีเป็น ค.ศ. ก็ได้) */
    static LocalDate parseThaiDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String text = ThaiDateUtil.toArabicDigits(raw).replace("พ.ศ.", " ").trim();
        Matcher numeric = NUMERIC_DATE.matcher(text);
        Matcher named = NAMED_DATE.matcher(text);
        int day;
        int month;
        String year;
        if (numeric.find()) {
            day = Integer.parseInt(numeric.group(1));
            month = Integer.parseInt(numeric.group(2));
            year = numeric.group(3);
        } else if (named.find()) {
            day = Integer.parseInt(named.group(1));
            month = monthOf(named.group(2));
            year = named.group(3);
        } else {
            return null;
        }
        Integer be = beYear(year);
        try {
            return month < 1 || be == null ? null : LocalDate.of(be - 543, month, day);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    private static int monthOf(String name) {
        for (int i = 0; i < THAI_MONTHS.length; i++) {
            if (name.startsWith(THAI_MONTHS[i]) || name.replace(" ", "").equals(THAI_MONTH_ABBR[i])) {
                return i + 1;
            }
        }
        return 0;
    }

    private static void put(Map<String, String> out, String key, String value) {
        if (!isBlank(value)) {
            out.put(key, value.trim());
        }
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
