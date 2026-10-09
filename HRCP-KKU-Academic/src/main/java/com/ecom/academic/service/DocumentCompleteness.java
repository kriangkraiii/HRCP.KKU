package com.ecom.academic.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecom.academic.model.SignatureModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ช่องของผู้ยื่นที่ยังไม่ได้กรอกในเอกสารฉบับหนึ่ง
 *
 * <p>เดิมไม่มีการตรวจความครบถ้วนก่อนลงนามเลย เอกสารที่กรอกครึ่ง ๆ กลาง ๆ จึงถูกแช่
 * ({@code frozenJson}) แล้วเวียนให้คนอื่นลงนามได้ เฟส 2 หนักกว่านั้นอีกเพราะนิยาม
 * "กรอกครบ" คือ "มีแถวที่ไม่ใช่ร่าง" เท่านั้น กดบันทึกฟอร์มเปล่าก็ผ่าน
 *
 * <p>{@link DocumentFieldOwnership} รู้ว่าช่องไหนเป็นของใคร แต่ไม่รู้ว่าช่องไหน
 * "ต้องกรอก" คลาสนี้เติมส่วนที่ขาดด้วยกติกาสามข้อ:
 *
 * <ol>
 *   <li>ช่องของแอดมินและของผู้ลงนามไม่นับ — ผู้ยื่นกรอกไม่ได้อยู่แล้ว</li>
 *   <li>ช่องใน {@link #OPTIONAL_FIELDS} ไม่นับ — เป็นช่อง "ถ้ามี" ตามแบบฟอร์มจริง</li>
 *   <li>ช่องที่อยู่ในกลุ่มของตำแหน่งอื่นไม่นับ — ผู้ขอ รศ. ไม่ต้องกรอกส่วนของ ศ.
 *       ({@link #POSITION_RANK_GROUPS} ล้อกับส่วนที่ doc_form_1.html ซ่อน/แสดงตาม
 *       ตำแหน่งที่ขอ)</li>
 * </ol>
 *
 * <p>ที่เหลือถ้าค่าว่างคือยังกรอกไม่ครบ ช่องติ๊กไม่เคยว่างเพราะ {@code checkbox_defaults.js}
 * และ {@code auto_draft.js} เติม {@code "☐"} ให้ช่องที่ไม่ติ๊กเสมอ
 *
 * <p>ตรวจเฉพาะเอกสารของผู้ยื่น เอกสารของแอดมินเป็นงานของเจ้าหน้าที่ซึ่งมีจังหวะทำงาน
 * ของตัวเอง
 */
public final class DocumentCompleteness {

    private static final Logger log = LoggerFactory.getLogger(DocumentCompleteness.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocumentCompleteness() {
    }

    // =====================================================================
    // ช่องที่ผู้ยื่นไม่ต้องกรอก
    // =====================================================================

    /**
     * ช่อง "ถ้ามี" ของแต่ละเอกสาร — ไล่จากแบบฟอร์มจริงทีละใบ
     *
     * <p>เกณฑ์ที่ใช้: หัวข้อที่ขึ้นต้นว่า "อื่นๆ", ช่องที่ไม่ใช่ทุกคนจะมี (เช่น
     * วิทยานิพนธ์ปริญญาเอก) และช่องที่ผู้ยื่นอาจเป็นคนเดียวกับที่ระบุอยู่แล้ว
     */
    private static final Map<SignatureModule, Map<Integer, Set<String>>> OPTIONAL_FIELDS = Map.of(
            SignatureModule.ACADEMIC, Map.of(),
            SignatureModule.POSITION, Map.of(
                    1, Set.of(
                            // ๒.๕ ตำแหน่งอื่นๆ และ ๒.๖ วิทยากรนานาชาติ — มีเฉพาะบางคน
                            "other_position_no_1", "other_position_1",
                            "international_speaker_last_5_years_no_1",
                            "international_speaker_last_5_years_1",
                            // ๓.๒–๓.๕ งานวิจัย งานบริการวิชาการ งานบริหาร และงานอื่นๆ — ไม่ใช่ทุกคนจะมี
                            "reseach", "academic_service", "administration", "other",
                            // อนุสาขาวิชาไม่ใช่ทุกสาขาจะมี
                            "sub_major", "sub_major_code"),
                    4, Set.of(
                            // ไม่ใช่ทุกคนจบทั้งโทและเอก และเสนอขอด้วยบทความหรืองานวิจัยอย่างใดอย่างหนึ่ง
                            "master_thesis_title", "doctoral_thesis_title",
                            "academic_paper_additional_detail", "research_additional_detail",
                            "academic_paper_not_part_edu", "academic_paper_is_part_edu",
                            "research_not_part_edu", "research_is_part_edu",
                            "academic_paper_status", "research_status",
                            "paper_title_1", "research_title_1",
                            "academic_paper_count", "research_count"),
                    8, Set.of(
                            // เอกสารประกอบการประเมินผลการสอน ... เรื่อง (ถ้ามี)
                            "subject"),
                    9, Set.of(
                            // ช. อื่นๆ ในส่วนการมีส่วนร่วม
                            "role_des7",
                            // ช่องทางเผยแพร่ — ผลงานหนึ่งชิ้นไม่ได้ผ่านทุกช่องทาง
                            "des_journal", "des_ patent", "des_techreport",
                            "des_poster", "des_scholarship", "des_researchdd",
                            // ผู้ยื่นอาจเป็น first author / corresponding เองอยู่แล้ว
                            "firstauthor_name", "corres_name", "coauthor_count")));

    /**
     * กลุ่มที่เพิ่มแถวได้ — แถวแรกบังคับ แถวถัดไปกรอกเท่าที่มี
     *
     * <p>แถวที่สองเป็นต้นไปจะอยู่ใน JSON ก็ต่อเมื่อผู้ยื่นกดเพิ่มแถวเอง
     */
    private static final Map<SignatureModule, Map<Integer, Set<String>>> REPEATABLE_PREFIXES = Map.of(
            SignatureModule.ACADEMIC, Map.of(),
            SignatureModule.POSITION, Map.of(
                    1, Set.of("education_", "teaching_", "other_position_",
                            "international_speaker_last_5_years_"),
                    4, Set.of("paper_title_", "research_title_"),
                    6, Set.of("des_research", "impactfacttor", "data"),
                    // รายชื่อผู้ทรงคุณวุฒิ: สูงสุด 10 คน อย่างน้อย 1 คน
                    8, Set.of("name", "majorcom", "major_expert", "Information"),
                    9, Set.of("coauthor_name_")));

    /**
     * ส่วนของแบบ ก.พ.ว. มข. 03 ที่ใช้เฉพาะบางตำแหน่ง
     *
     * <p>ล้อกับที่ {@code doc_form_1.html} ซ่อน/แสดงตาม {@code target_position}:
     * ผลงานกรอกเฉพาะระดับที่ขอ ส่วนประวัติการดำรงตำแหน่งกรอกเฉพาะตำแหน่งที่เคยผ่านมาแล้ว
     */
    private static final List<RankGroup> POSITION_RANK_GROUPS = List.of(
            // ๔.๑ / ๔.๒ / ๔.๓ ผลงานทางวิชาการ — กรอกเฉพาะระดับที่เสนอขอ
            new RankGroup("asst_", Set.of("ผู้ช่วยศาสตราจารย์")),
            new RankGroup("assoc_", Set.of("รองศาสตราจารย์")),
            new RankGroup("prof_", Set.of("ศาสตราจารย์")),
            // ๒.๓ / ๒.๔ ประวัติการดำรงตำแหน่ง — กรอกเฉพาะตำแหน่งที่เคยดำรงมาก่อน
            new RankGroup("assistant_", Set.of("รองศาสตราจารย์", "ศาสตราจารย์")),
            new RankGroup("associate_", Set.of("ศาสตราจารย์")));

    private record RankGroup(String prefix, Set<String> appliesTo) {
    }

    /**
     * ช่องที่แบบฟอร์ม พ.ศ. 2569 ตัดออกไปแล้ว — ไม่นับว่า "ยังไม่กรอก" ไม่ว่าจะว่างหรือไม่
     *
     * <p>ฟอร์มบนเว็บเอาช่องพวกนี้ออกไปแล้ว แต่ร่างที่บันทึกไว้ก่อนหน้ายังมีคีย์ค้างอยู่ใน JSON
     * เพราะการบันทึกรวมค่าใหม่ทับค่าเดิม ไม่ได้ลบคีย์ที่ไม่ได้ส่งมา ถ้าคีย์ที่ค้างนั้นว่าง ด่านนี้จะ
     * ฟ้องว่ากรอกไม่ครบ ทั้งที่หน้าฟอร์มไม่มีช่องให้กรอกแล้ว ผู้ยื่นที่มีร่างเก่าจะส่งไปลงนามไม่ได้เลย
     *
     * <ul>
     *   <li>เอกสารที่ 1 — หัวข้อ "วิธีที่ ๓" ของ รศ./ศ. (Scopus, h-index, หัวหน้าโครงการวิจัย)</li>
     *   <li>เอกสารที่ 9 — กลุ่มที่ ๒–๓, ส่วนที่ ๒ การเผยแพร่ และ "ผู้มีส่วนสำคัญทางปัญญา"</li>
     * </ul>
     */
    private static final Map<Integer, Pattern> RETIRED_POSITION_FIELDS = Map.of(
            1, Pattern.compile("^(assoc|prof)_(h_index|scopus_\\w+|m3_\\w+|method3_\\w+|pi_\\w+)$"),
            9, Pattern.compile("^(chk_essen|group1_research|chkgroup[23]_.*|des_.*)$"));

    private static final Pattern TRAILING_INDEX = Pattern.compile("(\\d+)$");

    // =====================================================================
    // API
    // =====================================================================

    /**
     * ช่องสารบรรณที่ยังว่างในเอกสารฉบับนี้
     *
     * <p>คนละคำถามกับ {@link #missingApplicantFields} ซึ่งตอบว่า "ผู้ยื่นกรอกครบหรือยัง"
     * และตอบเฉพาะเอกสารของผู้ยื่น ส่วนตัวนี้ตอบว่า "สารบรรณออกเลขให้หรือยัง" ซึ่งเป็น
     * งานคนละคนคนละจังหวะ — เลขที่หนังสือและวันที่ออกให้ <em>หลัง</em> เอกสารลงนามครบแล้ว
     *
     * <p>ตราบใดที่ยังมีช่องว่างอยู่ เอกสารฉบับนั้นยังไม่เสร็จ แม้จะบันทึกและลงนามครบแล้ว
     * กล่องเอกสารในหน้าผู้ดูแลระบบจึงยังไม่ขึ้นเขียว
     *
     * @param json ข้อมูลฟอร์มที่บันทึกไว้ — {@code null} หรือว่างถือว่ายังไม่เคยกรอก
     * @return ชื่อช่องที่ยังว่าง — ว่างเปล่าเมื่อครบแล้ว หรือเมื่อเอกสารนี้ไม่มีช่องสารบรรณ
     */
    public static List<String> missingOfficeFields(SignatureModule module, int documentType,
            String json) {
        Set<String> keys = DocumentFieldOwnership.officeFields(module, documentType);
        if (keys.isEmpty()) {
            return List.of();
        }

        return missingOfficeValues(module, documentType, parse(json));
    }

    /** เหมือน {@link #missingOfficeFields} แต่อ่านจากค่าที่ส่งมากับฟอร์มโดยตรง */
    public static List<String> missingOfficeValues(SignatureModule module, int documentType,
            Map<String, String> data) {
        List<String> missing = new ArrayList<>();
        for (String key : DocumentFieldOwnership.officeFields(module, documentType)) {
            String value = data == null ? null : data.get(key);
            if (!isIssued(value)) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * สารบรรณออกเลขที่หนังสือและวันที่ครบแล้วหรือยัง — ครบแล้วเอกสารฉบับนั้นถือว่าเสร็จ แก้ไขไม่ได้อีก
     *
     * @return false เมื่อเอกสารนี้ไม่มีช่องสารบรรณเลย เพราะไม่มีอะไรให้ออก
     */
    public static boolean officeFieldsIssued(SignatureModule module, int documentType, String json) {
        return !DocumentFieldOwnership.officeFields(module, documentType).isEmpty()
                && missingOfficeFields(module, documentType, json).isEmpty();
    }

    private static final Pattern OFFICER_CHECK = Pattern.compile("^chk_off_(\\d+)$");
    private static final Pattern OFFICER_NOTE = Pattern.compile("^text_(\\d+)$");

    /**
     * ช่องของเจ้าหน้าที่ในเอกสารของผู้ยื่นที่ยังว่าง — ต้องกรอกครบก่อนส่งต่อให้ผู้ลงนามคนถัดไป
     *
     * <p>ไม่นับเลขที่หนังสือกับวันที่ (ออกให้หลังลงนามครบ) และไม่นับชื่อผู้ลงนาม
     * (ระบบเติมจากผู้ที่ลงนามจริง ดู {@code SignerNameResolver})
     *
     * <p>คอลัมน์ "เจ้าหน้าที่" ในเอกสารที่ 2 ของเฟส 1: แต่ละแถวต้องติ๊กว่าตรวจแล้ว หรือถ้าไม่ติ๊ก
     * ต้องเขียนหมายเหตุบอกเหตุผล — ติ๊กครบทุกแถวแล้วหมายเหตุว่างได้
     *
     * @return ชื่อช่องที่ยังว่าง — ว่างเปล่าเมื่อกรอกครบ หรือเอกสารนี้ไม่มีช่องของเจ้าหน้าที่
     */
    public static List<String> missingAdminFields(SignatureModule module, int documentType, String json) {
        Set<String> keys = new LinkedHashSet<>(DocumentFieldOwnership.adminFields(module, documentType));
        keys.removeAll(DocumentFieldOwnership.officeFields(module, documentType));
        SignatureAnchorRegistry.slotsOf(module, documentType)
                .forEach(slot -> keys.remove(slot.anchorPlaceholder()));
        if (keys.isEmpty()) {
            return List.of();
        }
        Map<String, String> data = parse(json);
        if (data == null) {
            data = Map.of();
        }
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            Matcher note = OFFICER_NOTE.matcher(key);
            if (note.matches() && keys.contains("chk_off_" + note.group(1))) {
                continue; // ตรวจคู่กับช่องติ๊กของแถวเดียวกัน
            }
            Matcher check = OFFICER_CHECK.matcher(key);
            if (check.matches()) {
                String remark = data.get("text_" + check.group(1));
                if (!isTicked(data.get(key)) && (remark == null || remark.isBlank())) {
                    missing.add(key);
                }
                continue;
            }
            String value = data.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * ช่องเลขที่หนังสือตั้งต้นด้วยรหัสหน่วยงาน (เช่น {@code "อว 660301.26.8/"}) ให้สารบรรณเติมเลขต่อท้าย
     * มีแต่รหัสหน่วยงานแปลว่ายังไม่ได้ออกเลข ไม่อย่างนั้นกดบันทึกเฉย ๆ ก็ปิดเอกสารไปทั้งที่ไม่มีเลข
     */
    private static boolean isIssued(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !value.strip().endsWith("/");
    }


    /** ชั่วโมงสอนประจำวิชาขั้นต่ำ เทียบหน่วยกิตระบบทวิภาค (ประกาศ มข. 1669/2569 ข้อ 7) */
    public static final int MIN_TEACHING_CREDITS = 3;

    /**
     * ประกาศ มข. 1669/2569 ข้อ 7: "ชั่วโมงสอนที่เทียบค่าไม่น้อยกว่า ๓ หน่วยกิต หรือ ๔๕ ชั่วโมงสอน ในระบบทวิภาคต่อปี"
     * — นับชั่วโมงที่ผู้ยื่นสอนเองในวิชานั้นต่อปีการศึกษา ไม่ใช่หน่วยกิตของวิชา (สอนร่วมได้ส่วนเดียว)
     */
    public static final int MIN_TEACHING_HOURS_PER_YEAR = 45;

    private static boolean meetsTeachingLoad(String credits) {
        return atLeast(credits, MIN_TEACHING_CREDITS);
    }

    private static boolean atLeast(String number, double min) {
        Double value = parseNumber(number);
        return value != null && value >= min;
    }

    private static Double parseNumber(String number) {
        try {
            return Double.parseDouble(com.ecom.util.ThaiDateUtil.toArabicDigits(number).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** งานสอนในเอกสารที่ 1 เฟส 2: {@code teaching_hours_per_week_N} */
    private static final Pattern TEACHING_HOURS = Pattern.compile("^teaching_hours_per_week_\\d+$");

    /**
     * ขอ ศ. ไม่ผ่านการประเมินการสอน ภาระสอนจึงไม่ได้ตรวจที่เฟส 1 — ข้อบังคับ 2569 ข้อ 16.3.2 ยังกำหนดให้มีชั่วโมงสอน
     * ประจำวิชาใดวิชาหนึ่งเทียบได้ไม่น้อยกว่า 3 หน่วยกิตระบบทวิภาค ตารางงานสอนเก็บเป็นชั่วโมง/สัปดาห์ จึงถือ 3 ชม./สัปดาห์
     * (1 หน่วยกิตทฤษฎี = 1 ชม./สัปดาห์) ตรวจเมื่อกรอกชั่วโมงแล้วอย่างน้อยหนึ่งแถว
     */
    private static List<String> professorTeachingLoadProblems(Map<String, String> data) {
        if (data == null || !"ศาสตราจารย์".equals(data.get("target_position"))) {
            return List.of();
        }
        boolean anyFilled = false;
        for (Map.Entry<String, String> e : data.entrySet()) {
            if (!TEACHING_HOURS.matcher(e.getKey()).matches() || e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            anyFilled = true;
            if (meetsTeachingLoad(e.getValue())) {
                return List.of();
            }
        }
        return anyFilled
                ? List.of("งานสอนต้องมีรายวิชาที่สอนไม่น้อยกว่า " + MIN_TEACHING_CREDITS
                        + " ชั่วโมง/สัปดาห์ (เทียบ 3 หน่วยกิตระบบทวิภาค) อย่างน้อย 1 วิชา ตามข้อบังคับ มข. พ.ศ. 2569 ข้อ 16.3.2")
                : List.of();
    }

    /** ภาค/ปีการศึกษา: ภาค 1–3 (3 = ภาคฤดูร้อน) ทับปี พ.ศ. สี่หลัก เช่น 1/2569 */
    public static final Pattern SEMESTER_YEAR = Pattern.compile("^[1-3]/\\d{4}$");

    /**
     * ช่องที่กรอกแล้วแต่รูปแบบผิด — ร่างบันทึกได้ตามปกติ (กำลังพิมพ์อยู่) แต่ส่งไปลงนามไม่ได้
     *
     * @return ข้อความบอกผู้ใช้ทีละช่อง ว่างแปลว่าถูกรูปแบบทุกช่อง
     */
    public static List<String> malformedFields(SignatureModule module, int documentType, String json) {
        if (module == SignatureModule.POSITION && documentType == 1) {
            return professorTeachingLoadProblems(parse(json));
        }
        if (module == SignatureModule.POSITION && PositionDocTypes.base(documentType) == PositionDocTypes.WORK_PARTICIPATION) {
            return applicantRoleConflict(parse(json));
        }
        if (module != SignatureModule.ACADEMIC || documentType != 1) {
            return List.of();
        }
        Map<String, String> data = parse(json);
        List<String> problems = new ArrayList<>();
        String year = data == null ? null : data.get("academic_year");
        if (year != null && !year.isBlank() && !SEMESTER_YEAR.matcher(year.trim()).matches()) {
            problems.add("ภาค/ปีการศึกษา ต้องกรอกเป็น ภาค/ปี เช่น 1/2569");
        }
        String courseCredits = data == null ? null : data.get("course_credits");
        Double courseCreditValue = courseCredits == null ? null : parseNumber(courseCredits);
        if (courseCredits != null && !courseCredits.isBlank() && (courseCreditValue == null || courseCreditValue <= 0)) {
            problems.add("หน่วยกิตของรายวิชาต้องเป็นตัวเลขมากกว่า 0");
        }
        String hours = data == null ? null : data.get("teaching_hours");
        String credits = data == null ? null : data.get("teaching_credits");
        if (hours != null && !hours.isBlank()) {
            if (!atLeast(hours, MIN_TEACHING_HOURS_PER_YEAR)) {
                problems.add("ชั่วโมงที่ท่านสอนเองในรายวิชานี้ต้องไม่น้อยกว่า " + MIN_TEACHING_HOURS_PER_YEAR
                        + " ชั่วโมงต่อปี (เทียบ " + MIN_TEACHING_CREDITS
                        + " หน่วยกิตระบบทวิภาค) ตามประกาศ มข. 1669/2569 ข้อ 7");
            }
        } else if (credits != null && !credits.isBlank() && !meetsTeachingLoad(credits)) {
            // ร่างที่บันทึกก่อนแยกช่อง — มีแต่ชั่วโมงสอนแบบเทียบหน่วยกิต
            problems.add("ชั่วโมงสอนในรายวิชาต้องไม่น้อยกว่า " + MIN_TEACHING_CREDITS
                    + " หน่วยกิต (45 ชั่วโมงสอน) ตามประกาศ มข. 1669/2569 ข้อ 7");
        }
        return problems;
    }

    /**
     * ช่องของผู้ยื่นที่ยังว่างในเอกสารฉบับนี้
     *
     * @param json           ข้อมูลฟอร์มที่บันทึกไว้ — {@code null} หรือว่างถือว่ายังไม่เคยกรอก
     * @param targetPosition ตำแหน่งที่เสนอขอ ใช้ตัดส่วนที่ไม่เกี่ยวออก
     * @return ชื่อช่องที่ยังว่าง เรียงตามลำดับในเอกสาร — ว่างแปลว่ากรอกครบแล้ว
     */
    public static List<String> missingApplicantFields(SignatureModule module, int documentType,
            String json, String targetPosition) {
        if (!DocumentFieldOwnership.isApplicantDocument(module, documentType)) {
            return List.of();
        }
        Map<String, String> data = parse(json);
        if (data == null) {
            return List.of();
        }

        Set<String> adminFields = DocumentFieldOwnership.adminFields(module, documentType);
        Set<String> signerFields = DocumentFieldOwnership.signerFields(module, documentType);

        boolean academicDoc1HasChoice = module == SignatureModule.ACADEMIC && documentType == 1;
        boolean academicDoc1Ticked = academicDoc1HasChoice &&
                (isTicked(data.get("chk1")) || isTicked(data.get("chk2")) || isTicked(data.get("chk3")));
        boolean academicDoc1MissingAdded = false;

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            if (adminFields.contains(key) || signerFields.contains(key)) {
                continue;
            }
            // รหัสบัญชีที่คู่กับช่องชื่อผู้ลงนาม (ตัวค้นหาชื่อ) — บังคับหรือไม่ตามช่องชื่อของมัน ไม่ใช่ช่องแยก
            if (key.endsWith(DocumentFieldOwnership.SIGNER_ID_SUFFIX)) {
                continue;
            }
            if (isOptional(module, documentType, key, targetPosition)
                    || isUnusedWorkDetail(module, documentType, key, data)) {
                continue;
            }
            if (academicDoc1HasChoice && ("chk1".equals(key) || "chk2".equals(key) || "chk3".equals(key))) {
                if (academicDoc1Ticked) {
                    continue;
                }
                if (!academicDoc1MissingAdded) {
                    missing.add("target_position_choice");
                    academicDoc1MissingAdded = true;
                }
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        if (module == SignatureModule.POSITION && PositionDocTypes.base(documentType) == PositionDocTypes.WORK_PARTICIPATION
                && APPLICANT_ROLE_FIELDS.stream().noneMatch(key -> isTicked(data.get(key)))) {
            missing.add(APPLICANT_ROLE);
        }
        return missing;
    }

    /**
     * เอกสารที่ 9 ข้อ ข. สถานะผู้ขอในผลงาน — ต้องติ๊กอย่างน้อยหนึ่งข้อ ผู้ขอเป็นผู้ประพันธ์อันดับแรก
     * และ/หรือบรรณกิจ หรือเป็นผู้นิพนธ์ร่วม อย่างใดอย่างหนึ่งเสมอ (เอกสารแนบท้ายข้อบังคับ มข. พ.ศ. 2569 ข้อ 3.1, 3.4)
     *
     * <p>ช่องที่ไม่ติ๊กถูกบันทึกเป็น "☐" ซึ่งไม่ว่าง ด่านช่องว่างข้างบนจึงจับไม่ได้ ต้องตรวจแยก
     */
    private static final List<String> APPLICANT_ROLE_FIELDS = List.of("chk_ firstauthor", "chk_corresp", "chk_coauthor");

    /**
     * ผู้นิพนธ์ร่วมเป็นพร้อมผู้ประพันธ์อันดับแรกหรือบรรณกิจไม่ได้ (เอกสารแนบท้ายข้อบังคับ ข้อ 3.1, 3.4)
     *
     * <p>แบบฟอร์มล็อกข้อที่ขัดกันไว้ แต่ล็อกด้วยหน้าตาเท่านั้น ร่างที่เติมด้วยเครื่องมือหรือส่งตรงจึงติ๊กครบได้
     * เอกสารที่ติ๊กขัดกันพิมพ์ออกมาเป็นผู้นิพนธ์ร่วมอย่างเดียว ({@code DocumentGenerationService}) ไม่ตรงกับที่ผู้ขอกรอก
     */
    private static List<String> applicantRoleConflict(Map<String, String> data) {
        if (data == null || !isTicked(data.get("chk_coauthor"))) {
            return List.of();
        }
        if (isTicked(data.get("chk_ firstauthor")) || isTicked(data.get("chk_corresp"))) {
            return List.of("ข. สถานะผู้ขอในผลงาน: ผู้นิพนธ์ร่วมเลือกคู่กับผู้ประพันธ์อันดับแรกหรือผู้ประพันธ์บรรณกิจไม่ได้"
                    + " — กรุณาเอาติ๊กข้อที่ไม่ใช่ออก");
        }
        return List.of();
    }

    /** ชื่อที่ส่งกลับไปเมื่อยังไม่ติ๊กสถานะ — doc_form_9.html ผูกป้ายกำกับไว้ด้วย data-missing-key */
    public static final String APPLICANT_ROLE = "applicant_role";

    /**
     * ชื่อช่องที่ผู้ยื่นต้องกรอกในเอกสารฉบับนี้ — ฝั่งเบราว์เซอร์ใช้บอกผู้ใช้ว่าขาดช่องไหน
     *
     * <p>อิงจากช่องที่เคยบันทึกไว้ใน {@code json} เพราะเซิร์ฟเวอร์ไม่รู้จักผังของฟอร์ม
     * ฝั่งเบราว์เซอร์จึงต้องตัดช่องที่ซ่อนอยู่ออกเองอีกชั้น
     */
    public static Set<String> requiredFields(SignatureModule module, int documentType,
            String json, String targetPosition) {
        if (!DocumentFieldOwnership.isApplicantDocument(module, documentType)) {
            return Set.of();
        }
        Map<String, String> data = parse(json);
        if (data == null) {
            return Set.of();
        }

        Set<String> adminFields = DocumentFieldOwnership.adminFields(module, documentType);
        Set<String> signerFields = DocumentFieldOwnership.signerFields(module, documentType);

        boolean academicDoc1HasChoice = module == SignatureModule.ACADEMIC && documentType == 1;
        boolean academicDoc1Ticked = academicDoc1HasChoice &&
                (isTicked(data.get("chk1")) || isTicked(data.get("chk2")) || isTicked(data.get("chk3")));

        Set<String> required = new LinkedHashSet<>();
        for (String key : data.keySet()) {
            if (adminFields.contains(key) || signerFields.contains(key)
                    || key.endsWith(DocumentFieldOwnership.SIGNER_ID_SUFFIX)) {
                continue;
            }
            if (academicDoc1HasChoice && ("chk1".equals(key) || "chk2".equals(key) || "chk3".equals(key))) {
                if (academicDoc1Ticked) {
                    if (isTicked(data.get(key))) {
                        required.add(key);
                    }
                    continue;
                }
            }
            if (!isOptional(module, documentType, key, targetPosition)
                    && !isUnusedWorkDetail(module, documentType, key, data)) {
                required.add(key);
            }
        }
        return required;
    }

    private static boolean isTicked(String val) {
        if (val == null) {
            return false;
        }
        String t = val.trim();
        // ☑ คือค่าของช่องติ๊กในฟอร์มเฟส 2 (เช่น เอกสารที่ 9) — ไม่ติ๊กบันทึกเป็น ☐
        return "✓".equals(t) || "✔".equals(t) || "☑".equals(t) || "on".equalsIgnoreCase(t);
    }

    /**
     * ช่อง "ถ้ามี" ของเอกสารฉบับนี้ — ฝั่งเบราว์เซอร์ใช้ตัดออกก่อนเตือนผู้ใช้
     *
     * <p>ส่งเฉพาะรายการที่คงที่ ส่วนช่องของระดับตำแหน่งอื่นไม่ต้องส่ง เพราะฟอร์มซ่อนไว้อยู่แล้ว
     * และตัวตรวจฝั่งเบราว์เซอร์ดูเฉพาะช่องที่มองเห็น
     */
    public static Set<String> optionalFields(SignatureModule module, int documentType) {
        return OPTIONAL_FIELDS.getOrDefault(module, Map.of()).getOrDefault(PositionDocTypes.base(documentType), Set.of());
    }

    /** คำนำหน้าชื่อช่องของตารางที่เพิ่มแถวได้ — แถวที่ ๒ ขึ้นไปไม่บังคับ */
    public static Set<String> repeatablePrefixes(SignatureModule module, int documentType) {
        return REPEATABLE_PREFIXES.getOrDefault(module, Map.of()).getOrDefault(PositionDocTypes.base(documentType), Set.of());
    }

    // =====================================================================
    // กติกา
    // =====================================================================

    private static final Pattern WORK_USED_DETAIL = Pattern.compile(
            "^((?:asst|assoc|prof)_used_(?:research|other|book))_(?:year|level)_(\\d+)$");

    /**
     * ปีและระดับคุณภาพของผลงานในเอกสารที่ 1 เฟส 2 มีความหมายเฉพาะผลงานที่ "เคยใช้" — เลือก
     * "ไม่เคยใช้" แล้วฟอร์มซ่อนและล้างสองช่องนี้ จึงไม่นับเป็นช่องที่ขาด
     */
    private static boolean isUnusedWorkDetail(SignatureModule module, int documentType, String key,
            Map<String, String> data) {
        if (module != SignatureModule.POSITION || documentType != 1) {
            return false;
        }
        Matcher m = WORK_USED_DETAIL.matcher(key);
        return m.matches() && !"used".equals(data.get(m.group(1) + "_" + m.group(2)));
    }

    /** รหัสผลงานที่ตัวเลือก Scopus แนบไว้ข้างชื่อผลงาน — ช่องซ่อน ว่างเมื่อผู้ยื่นพิมพ์ชื่อผลงานเอง */
    private static final Pattern SCOPUS_ID_FIELD = Pattern.compile("^.+_scopus_id_\\d+$");

    private static boolean isOptional(SignatureModule module, int documentType, String key,
            String targetPosition) {
        if (SCOPUS_ID_FIELD.matcher(key).matches()) {
            return true;
        }
        Set<String> optional = OPTIONAL_FIELDS
                .getOrDefault(module, Map.of())
                .getOrDefault(PositionDocTypes.base(documentType), Set.of());
        if (optional.contains(key)) {
            return true;
        }
        if (module == SignatureModule.POSITION && RETIRED_POSITION_FIELDS.containsKey(PositionDocTypes.base(documentType))
                && RETIRED_POSITION_FIELDS.get(PositionDocTypes.base(documentType)).matcher(key).matches()) {
            return true;
        }
        if (isExtraRepeatedRow(module, documentType, key)) {
            return true;
        }
        return isForAnotherRank(module, documentType, key, targetPosition);
    }

    /** แถวที่สองเป็นต้นไปของตารางที่เพิ่มแถวได้ */
    private static boolean isExtraRepeatedRow(SignatureModule module, int documentType, String key) {
        Set<String> prefixes = REPEATABLE_PREFIXES
                .getOrDefault(module, Map.of())
                .getOrDefault(PositionDocTypes.base(documentType), Set.of());
        for (String prefix : prefixes) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            Matcher m = TRAILING_INDEX.matcher(key);
            if (m.find()) {
                return !"1".equals(m.group(1));
            }
        }
        return false;
    }

    /** ช่องของระดับตำแหน่งที่คำร้องนี้ไม่ได้ขอ */
    private static boolean isForAnotherRank(SignatureModule module, int documentType, String key,
            String targetPosition) {
        if (module != SignatureModule.POSITION || documentType != 1) {
            return false;
        }
        for (RankGroup group : POSITION_RANK_GROUPS) {
            if (key.startsWith(group.prefix())) {
                // ไม่รู้ตำแหน่งที่ขอ = ตัดสินไม่ได้ ปล่อยผ่านดีกว่ากันคนที่กรอกถูกต้องไว้
                return targetPosition == null || !group.appliesTo().contains(targetPosition.trim());
            }
        }
        return false;
    }

    private static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("Could not read document data for completeness check: {}", e.toString());
            return null;
        }
    }
}
