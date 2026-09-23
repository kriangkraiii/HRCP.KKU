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
                            // ๓.๕ งานอื่นๆ ที่เกี่ยวข้อง
                            "other",
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
                    5, Set.of("sub_major", "sub_major_code"),
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

        Map<String, String> data = parse(json);
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            String value = data == null ? null : data.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        return missing;
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
            if (isOptional(module, documentType, key, targetPosition)) {
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
        return missing;
    }

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
            if (adminFields.contains(key) || signerFields.contains(key)) {
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
            if (!isOptional(module, documentType, key, targetPosition)) {
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
        return "✓".equals(t) || "✔".equals(t) || "on".equalsIgnoreCase(t);
    }

    /**
     * ช่อง "ถ้ามี" ของเอกสารฉบับนี้ — ฝั่งเบราว์เซอร์ใช้ตัดออกก่อนเตือนผู้ใช้
     *
     * <p>ส่งเฉพาะรายการที่คงที่ ส่วนช่องของระดับตำแหน่งอื่นไม่ต้องส่ง เพราะฟอร์มซ่อนไว้อยู่แล้ว
     * และตัวตรวจฝั่งเบราว์เซอร์ดูเฉพาะช่องที่มองเห็น
     */
    public static Set<String> optionalFields(SignatureModule module, int documentType) {
        return OPTIONAL_FIELDS.getOrDefault(module, Map.of()).getOrDefault(documentType, Set.of());
    }

    /** คำนำหน้าชื่อช่องของตารางที่เพิ่มแถวได้ — แถวที่ ๒ ขึ้นไปไม่บังคับ */
    public static Set<String> repeatablePrefixes(SignatureModule module, int documentType) {
        return REPEATABLE_PREFIXES.getOrDefault(module, Map.of()).getOrDefault(documentType, Set.of());
    }

    // =====================================================================
    // กติกา
    // =====================================================================

    private static boolean isOptional(SignatureModule module, int documentType, String key,
            String targetPosition) {
        Set<String> optional = OPTIONAL_FIELDS
                .getOrDefault(module, Map.of())
                .getOrDefault(documentType, Set.of());
        if (optional.contains(key)) {
            return true;
        }
        if (module == SignatureModule.POSITION && RETIRED_POSITION_FIELDS.containsKey(documentType)
                && RETIRED_POSITION_FIELDS.get(documentType).matcher(key).matches()) {
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
                .getOrDefault(documentType, Set.of());
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
