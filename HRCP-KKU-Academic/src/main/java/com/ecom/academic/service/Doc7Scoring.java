package com.ecom.academic.service;

import java.util.List;
import java.util.Map;

import com.ecom.academic.model.AcademicRank;
import com.ecom.util.ThaiDateUtil;

/**
 * คะแนนและผลสรุปของเอกสารที่ 7 (แบบประเมินผลการสอน) ที่คำนวณจากคะแนนรายส่วน
 *
 * <p>เดิมอยู่ใน {@code AcademicAdminController} และคำนวณเฉพาะตอนกด "บันทึก" เท่านั้น
 * แต่เจ้าหน้าที่ส่วนใหญ่กรอกคะแนนแล้วกด "ส่งเวียนลงนาม" เลย — ทางนั้นบันทึกผ่าน
 * auto-draft ซึ่งไม่เคยคำนวณ ซองลายเซ็นจึงแช่แข็งเอกสารที่ไม่มี {@code eval_result_level}
 * สถานะคำร้องไม่เลื่อนเป็น "ผ่าน" เมื่อประธานลงนามครบ และเอกสารที่ 8/9 ไม่รู้ระดับผลประเมิน
 */
public final class Doc7Scoring {

    private static final String[] SECTION_KEYS = { "sec_score_1", "sec_score_2", "sec_score_3", "sec_score_4" };

    /** คะแนนรวมต่ำสุดของระดับชำนาญ — ต่ำกว่านี้ไม่ผ่าน (ประกาศ มข. ฉบับที่ 1669/2569 ข้อ ๙.๓) */
    public static final double PASS_SCORE = 56;

    /** คะแนนรวมต่ำสุดของระดับชำนาญพิเศษ (ข้อ ๙.๓.๓ "๗๑ - ๘๕") */
    public static final double SPECIAL_EXPERT_SCORE = 71;

    /** คะแนนรวมต่ำสุดของระดับเชี่ยวชาญ (ข้อ ๙.๓.๔ "๘๖ - ๑๐๐") */
    public static final double EXPERT_SCORE = 86;

    /** ระดับผลการสอนเรียงจากต่ำไปสูง */
    private static final List<String> LEVELS = List.of("ไม่ผ่าน", "ชำนาญ", "ชำนาญพิเศษ", "เชี่ยวชาญ");

    /**
     * ระดับต่ำสุดที่ตำแหน่งที่ขอต้องได้ (ประกาศ มข. ฉบับที่ 1669/2569 ข้อ ๙.๔) — ผศ. ชำนาญ, รศ. ชำนาญพิเศษ
     * ตำแหน่งอื่นหรือไม่รู้ตำแหน่ง ขอแค่ไม่ต่ำกว่าชำนาญ
     */
    public static String minimumLevelFor(AcademicRank rank) {
        return rank == AcademicRank.ASSOCIATE_PROFESSOR ? "ชำนาญพิเศษ" : "ชำนาญ";
    }

    /**
     * ผลประเมินระดับนี้ต่ำกว่าเกณฑ์ของตำแหน่งที่ขอหรือไม่ — "ชำนาญ" ผ่านเกณฑ์ ผศ. แต่ต่ำกว่าเกณฑ์ รศ.
     * ตัดสินเฉพาะระดับที่รู้จัก ค่าที่ว่างหรือเขียนแบบอื่น (ข้อมูลเก่า) ไม่ถูกตัดสิทธิ์จากกติกานี้
     */
    public static boolean fallsShortOf(String level, AcademicRank rank) {
        int index = level == null ? -1 : LEVELS.indexOf(level.trim());
        return index >= 0 && index < LEVELS.indexOf(minimumLevelFor(rank));
    }

    private Doc7Scoring() {
    }

    /** กรอกคะแนนครบทั้งสี่ส่วนแล้วหรือยัง — แบบร่างที่ยังกรอกไม่ครบต้องไม่ถูกสรุปว่า "ไม่ผ่าน" */
    public static boolean hasAllSectionScores(Map<String, String> formData) {
        for (String key : SECTION_KEYS) {
            String v = formData.get(key);
            if (v == null || v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** คะแนนสูงสุดของแต่ละส่วน */
    public static final double MAX_SECTION_SCORE = 5;

    /**
     * บีบคะแนนรายส่วนให้อยู่ในช่วง 0–5 แล้วเขียนกลับ — ช่อง max="5" บนหน้าเว็บไม่กันการพิมพ์
     * และร่างอัตโนมัติไม่ผ่านการตรวจของเบราว์เซอร์ คะแนน 10 จึงเคยถูกบันทึกและนับเกิน 100
     * ช่องที่ว่างหรือไม่ใช่ตัวเลขปล่อยไว้ตามเดิม
     */
    public static void clampSectionScores(Map<String, String> formData) {
        for (String key : SECTION_KEYS) {
            String v = formData.get(key);
            if (v == null || v.isBlank()) {
                continue;
            }
            try {
                double score = Double.parseDouble(v.trim());
                if (score > MAX_SECTION_SCORE || score < 0) {
                    double clamped = Math.max(0, Math.min(MAX_SECTION_SCORE, score));
                    formData.put(key, clamped == Math.rint(clamped)
                            ? String.valueOf((long) clamped) : String.valueOf(clamped));
                }
            } catch (NumberFormatException e) {
                // ปล่อยไว้ — derive นับเป็น 0
            }
        }
    }

    /**
     * คะแนนรายส่วนมีทศนิยมได้ไม่เกิน 1 ตำแหน่ง — แบบฟอร์มตามประกาศ มข. ฉบับที่ 1669/2569
     * แบ่งระดับการประเมินเป็นช่วง ๐–๑, ๑.๑–๒, ๒.๑–๓, ๓.๑–๔, ๔.๑–๕ ค่าอย่าง 1.05 จึงไม่อยู่ในช่วงใดของแบบฟอร์ม
     *
     * @return ข้อความแจ้งเจ้าหน้าที่ หรือ {@code null} เมื่อถูกต้อง (ช่องว่างยังไม่นับ)
     */
    public static String scorePrecisionProblem(Map<String, String> formData) {
        for (String key : SECTION_KEYS) {
            String v = formData.get(key);
            if (v == null || v.isBlank()) {
                continue;
            }
            try {
                double tenths = Double.parseDouble(v.trim()) * 10;
                if (Math.abs(tenths - Math.rint(tenths)) > 1e-9) {
                    return "คะแนนรายส่วนกรอกได้ 0–5 ทศนิยมไม่เกิน 1 ตำแหน่ง (ส่วนที่ " + key.substring(key.length() - 1)
                            + " กรอกไว้ " + v.trim() + ")";
                }
            } catch (NumberFormatException e) {
                // ไม่ใช่ตัวเลข — derive นับเป็น 0 เหมือนเดิม
            }
        }
        return null;
    }

    /** เติมคะแนนถ่วงน้ำหนัก คะแนนรวม ช่องติ๊กผลสรุป และ {@code eval_result_level} ลงใน formData */
    public static void derive(Map<String, String> formData) {
        clampSectionScores(formData);
        // ค่าน้ำหนักแต่ละส่วน: ส่วนที่ 1=20, 2=30, 3=30, 4=20
        int[] weights = { 20, 30, 30, 20 };
        // คิดเป็นทศนิยมฐานสิบ ไม่ใช่ double — คะแนนรายส่วนอย่าง 1.8/4.8/4.1 รวมกันได้ 56 พอดี แต่ double
        // ได้ 55.99999999999999 ผลเลยเป็น "ไม่ผ่าน" ทั้งที่เอกสารพิมพ์ 56.00 (น้ำหนัก/5 เป็นจำนวนเต็ม ผลจึงตรงเป๊ะ)
        java.math.BigDecimal grandTotal = java.math.BigDecimal.ZERO;

        for (int sec = 1; sec <= 4; sec++) {
            // Admin กรอกคะแนนรวมต่อส่วน (1 ค่าต่อส่วน) ในช่อง sec_score_X
            double secScore = sectionScore(formData.get("sec_score_" + sec));

            // วิเคราะห์ว่าคะแนนตกอยู่ในช่วงไหน (5 ช่วง)
            // ช่วง: 0-1 = score_1, 1.01-2 = score_2, 2.01-3 = score_3, 3.01-4 = score_4,
            // 4.01-5 = score_5
            // placeholder ใน template DOCX: {{score11}}, {{score12}}, ..., {{score45}}
            for (int range = 1; range <= 5; range++) {
                String key = "score" + sec + range;
                boolean inRange = secScore > range - 1 && secScore <= range;
                // ช่วงที่ตรง → ใส่คะแนน, ช่วงอื่น → ว่าง
                formData.put(key, inRange ? String.valueOf(secScore) : "");
            }

            // สูตร: (คะแนน / 5) × ค่าน้ำหนัก
            java.math.BigDecimal weighted = java.math.BigDecimal.valueOf(secScore)
                    .multiply(java.math.BigDecimal.valueOf(weights[sec - 1]))
                    .divide(java.math.BigDecimal.valueOf(5));
            formData.put("score" + sec + "x", twoDecimals(weighted));
            grandTotal = grandTotal.add(weighted);
        }

        // คะแนนรวม — เก็บเป็นเลขอารบิกเสมอ เอกสารที่ 7 พิมพ์เป็นเลขไทยก็จริง แต่การแปลง
        // เป็นเรื่องของตอนสร้างไฟล์ (DocumentGenerationService ดูจากเทมเพลตเอง) ไม่ใช่ของ
        // ข้อมูลที่บันทึกไว้ ซึ่งต้องอ่านกลับมาใส่ฟอร์มและคำนวณต่อได้
        formData.put("scorex", twoDecimals(grandTotal));

        // สรุปผลการประเมิน: ติ้กช่องตามเกณฑ์ (ใช้คะแนนจริง ไม่ปัดขึ้น) — ช่องติ๊กกับระดับมาจากค่าเดียวกัน
        String level = evalLevelFromScore(grandTotal.toPlainString());
        formData.put("ch1", "ไม่ผ่าน".equals(level) ? "☑" : "☐");
        formData.put("ch2", "ชำนาญ".equals(level) ? "☑" : "☐");
        formData.put("ch3", "ชำนาญพิเศษ".equals(level) ? "☑" : "☐");
        formData.put("ch4", "เชี่ยวชาญ".equals(level) ? "☑" : "☐");

        // กำหนด eval_level จากผลคะแนนเพื่อส่งต่อไป doc8/doc9
        formData.put("eval_result_level", level);
    }

    /** คะแนนรายส่วนเป็นตัวเลข — ว่าง อ่านไม่ออก หรือไม่ใช่ตัวเลขจริง (NaN, Infinity) นับเป็น 0 */
    private static double sectionScore(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            double score = Double.parseDouble(raw.trim());
            return Double.isFinite(score) ? score : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String twoDecimals(java.math.BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * สรุประดับผลการประเมินจากคะแนนรวม (รับได้ทั้งเลขไทยและ Arabic)
     *
     * <p>ประกาศ มข. ฉบับที่ 1669/2569 ข้อ ๙.๓: ต่ำกว่า ๕๖ ไม่ผ่าน, ๕๖–๗๐ ชำนาญ, ๗๑–๘๕ ชำนาญพิเศษ,
     * ๘๖–๑๐๐ เชี่ยวชาญ — ทุกขอบใช้หลักเดียวกับ "ต่ำกว่า ๕๖ ไม่ผ่าน": ต้องถึงคะแนนต่ำสุดของช่วงจึงเข้าช่วงนั้น
     * คะแนนที่ตกระหว่างช่วง (เช่น 70.6) จึงยังอยู่ช่วงล่าง
     */
    /**
     * ระดับที่ควรได้จากคะแนนรวม เมื่อไม่ตรงกับระดับที่บันทึกไว้ — {@code null} เมื่อตรงกัน หรือข้อมูลไม่พอจะเทียบ
     *
     * <p>ก่อนแก้การคิดคะแนนเป็นทศนิยมฐานสิบ (BigDecimal) คะแนนรวม 56/71/86 พอดีอาจถูกบันทึกเป็นระดับต่ำกว่าหนึ่งขั้น
     * ทั้งที่เอกสารพิมพ์คะแนนนั้น — หน้าเอกสารที่ 7 ฝั่งเจ้าหน้าที่เตือนให้ตรวจ (docs/PLAN-bughunt-decisions.md ข้อ 2a)
     */
    public static String levelMismatch(Map<String, String> formData) {
        if (formData == null) {
            return null;
        }
        String stored = formData.get("eval_result_level");
        String expected = evalLevelFromScore(formData.get("scorex"));
        if (stored == null || stored.isBlank() || expected.isEmpty() || expected.equals(stored.trim())) {
            return null;
        }
        return expected;
    }

    public static String evalLevelFromScore(Object rawScore) {
        if (rawScore == null)
            return "";
        String s = ThaiDateUtil.toArabicDigits(rawScore.toString()).trim();
        if (s.isEmpty())
            return "";
        try {
            // ใช้คะแนนจริงตามช่วงเกณฑ์ ไม่ปัดเศษขึ้น
            double score = Double.parseDouble(s);
            if (!Double.isFinite(score))
                return "";
            if (score < PASS_SCORE)
                return "ไม่ผ่าน";
            if (score < SPECIAL_EXPERT_SCORE)
                return "ชำนาญ";
            if (score < EXPERT_SCORE)
                return "ชำนาญพิเศษ";
            return "เชี่ยวชาญ";
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
