package com.ecom.academic.model;

import java.util.Map;

import com.ecom.util.AcademicTitleResolver;

/**
 * ตำแหน่งทางวิชาการ เรียงจากต่ำไปสูง — ลำดับการประกาศคือลำดับของตำแหน่งจริง
 *
 * <p>ชื่อตำแหน่งในระบบมาจากหลายทาง (directory, SSO, ช่องที่ผู้ยื่นพิมพ์เอง) และเขียนไม่เหมือนกัน
 * เช่น "ผศ.ดร.", "Assistant Professor", "ผู้ช่วยศาสตราจารย์" จึงต้องแปลงผ่าน
 * {@link AcademicTitleResolver} ก่อนเทียบทุกครั้ง
 */
public enum AcademicRank {

    LECTURER("อาจารย์"),
    ASSISTANT_PROFESSOR("ผู้ช่วยศาสตราจารย์"),
    ASSOCIATE_PROFESSOR("รองศาสตราจารย์"),
    PROFESSOR("ศาสตราจารย์");

    private static final String TICK = "✓";

    private final String thaiLabel;

    AcademicRank(String thaiLabel) {
        this.thaiLabel = thaiLabel;
    }

    public String thaiLabel() {
        return thaiLabel;
    }

    /**
     * @return ตำแหน่งที่ข้อความระบุ หรือ null ถ้าข้อความไม่ได้ระบุตำแหน่งวิชาการ — ไม่เดา
     */
    public static AcademicRank of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String resolved = AcademicTitleResolver.resolveThaiAcademicPosition(raw);
        for (AcademicRank rank : values()) {
            if (rank.thaiLabel.equals(resolved)) {
                return rank;
            }
        }
        return null;
    }

    /**
     * ตำแหน่งที่ขอ ตามช่องที่ติ๊กในเอกสารที่ 1 ของคำร้องประเมินการสอน
     * ({@code chk1} ผศ., {@code chk2} รศ., {@code chk3} ศ. — แบบเดียวกับที่ฝั่งแอดมินอ่าน)
     */
    public static AcademicRank fromDoc1Checks(Map<String, String> doc1) {
        if (doc1 == null) {
            return null;
        }
        if (TICK.equals(doc1.get("chk1"))) {
            return ASSISTANT_PROFESSOR;
        }
        if (TICK.equals(doc1.get("chk2"))) {
            return ASSOCIATE_PROFESSOR;
        }
        if (TICK.equals(doc1.get("chk3"))) {
            return PROFESSOR;
        }
        return null;
    }

    public boolean isAbove(AcademicRank other) {
        return other == null || compareTo(other) > 0;
    }

    /** ขอ ศ. ไม่ต้องผ่านการประเมินผลการสอน — Flow ข้อ 1-15 ใช้กับ ผศ. และ รศ. เท่านั้น */
    public boolean requiresTeachingEvaluation() {
        return this == ASSISTANT_PROFESSOR || this == ASSOCIATE_PROFESSOR;
    }
}
