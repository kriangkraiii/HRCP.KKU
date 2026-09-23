package com.ecom.academic.service;

import java.util.Map;

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

    /** เติมคะแนนถ่วงน้ำหนัก คะแนนรวม ช่องติ๊กผลสรุป และ {@code eval_result_level} ลงใน formData */
    public static void derive(Map<String, String> formData) {
        // ค่าน้ำหนักแต่ละส่วน: ส่วนที่ 1=20, 2=30, 3=30, 4=20
        int[] weights = { 20, 30, 30, 20 };
        double grandTotal = 0;

        for (int sec = 1; sec <= 4; sec++) {
            // Admin กรอกคะแนนรวมต่อส่วน (1 ค่าต่อส่วน) ในช่อง sec_score_X
            String secScoreVal = formData.getOrDefault("sec_score_" + sec, "0");
            double secScore = 0;
            try {
                secScore = Double.parseDouble(secScoreVal);
            } catch (NumberFormatException e) {
                secScore = 0;
            }

            // วิเคราะห์ว่าคะแนนตกอยู่ในช่วงไหน (5 ช่วง)
            // ช่วง: 0-1 = score_1, 1.01-2 = score_2, 2.01-3 = score_3, 3.01-4 = score_4,
            // 4.01-5 = score_5
            // placeholder ใน template DOCX: {{score11}}, {{score12}}, ..., {{score45}}
            for (int range = 1; range <= 5; range++) {
                String key = "score" + sec + range;
                boolean inRange = false;
                if (range == 1)
                    inRange = (secScore > 0 && secScore <= 1);
                else if (range == 2)
                    inRange = (secScore > 1 && secScore <= 2);
                else if (range == 3)
                    inRange = (secScore > 2 && secScore <= 3);
                else if (range == 4)
                    inRange = (secScore > 3 && secScore <= 4);
                else if (range == 5)
                    inRange = (secScore > 4 && secScore <= 5);
                // ช่วงที่ตรง → ใส่คะแนน, ช่วงอื่น → ว่าง
                formData.put(key, inRange ? String.valueOf(secScore) : "");
            }

            // สูตร: (คะแนน / 5) × ค่าน้ำหนัก
            double weighted = (secScore / 5.0) * weights[sec - 1];
            formData.put("score" + sec + "x", "%.2f".formatted(weighted));
            grandTotal += weighted;
        }

        // คะแนนรวม — เก็บเป็นเลขอารบิกเสมอ เอกสารที่ 7 พิมพ์เป็นเลขไทยก็จริง แต่การแปลง
        // เป็นเรื่องของตอนสร้างไฟล์ (DocumentGenerationService ดูจากเทมเพลตเอง) ไม่ใช่ของ
        // ข้อมูลที่บันทึกไว้ ซึ่งต้องอ่านกลับมาใส่ฟอร์มและคำนวณต่อได้
        formData.put("scorex", "%.2f".formatted(grandTotal));

        // สรุปผลการประเมิน: ติ้กช่องตามเกณฑ์ (ใช้คะแนนจริง ไม่ปัดขึ้น)
        formData.put("ch1", grandTotal <= 56 ? "☑" : "☐");
        formData.put("ch2", (grandTotal > 56 && grandTotal <= 70) ? "☑" : "☐");
        formData.put("ch3", (grandTotal > 70 && grandTotal <= 85) ? "☑" : "☐");
        formData.put("ch4", grandTotal > 85 ? "☑" : "☐");

        // กำหนด eval_level จากผลคะแนนเพื่อส่งต่อไป doc8/doc9
        formData.put("eval_result_level", evalLevelFromScore(grandTotal));
    }

    /** สรุประดับผลการประเมินจากคะแนนรวม (รับได้ทั้งเลขไทยและ Arabic) */
    public static String evalLevelFromScore(Object rawScore) {
        if (rawScore == null)
            return "";
        String s = ThaiDateUtil.toArabicDigits(rawScore.toString()).trim();
        if (s.isEmpty())
            return "";
        try {
            // ใช้คะแนนจริงตามช่วงเกณฑ์ ไม่ปัดเศษขึ้น
            double score = Double.parseDouble(s);
            if (score <= 56)
                return "ไม่ผ่าน";
            if (score <= 70)
                return "ชำนาญ";
            if (score <= 85)
                return "ชำนาญพิเศษ";
            return "เชี่ยวชาญ";
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
