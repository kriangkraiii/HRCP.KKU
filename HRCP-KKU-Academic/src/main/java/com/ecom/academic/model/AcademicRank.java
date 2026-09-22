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

    LECTURER("อาจารย์", "Lecturer"),
    ASSISTANT_PROFESSOR("ผู้ช่วยศาสตราจารย์", "Assistant Professor"),
    ASSOCIATE_PROFESSOR("รองศาสตราจารย์", "Associate Professor"),
    PROFESSOR("ศาสตราจารย์", "Professor");

    private static final String TICK = "✓";

    private final String thaiLabel;
    private final String englishLabel;

    AcademicRank(String thaiLabel, String englishLabel) {
        this.thaiLabel = thaiLabel;
        this.englishLabel = englishLabel;
    }

    public String thaiLabel() {
        return thaiLabel;
    }

    /**
     * ชื่อตำแหน่งภาษาอังกฤษอย่างเป็นทางการ — ที่เดียวในระบบที่จับคู่ไทยกับอังกฤษ
     *
     * <p>ต้นทางทุกทางให้มาแต่ภาษาไทย: {@code fs_faculty.position_en} มีบ้างไม่มีบ้าง
     * ส่วน SSO ของ มข. ไม่ส่งตำแหน่งวิชาการภาษาอังกฤษมาเลย ({@code titleEng} คือ
     * คำนำหน้า Mr./Mrs. ไม่ใช่ตำแหน่ง) ค่าฝั่งอังกฤษจึงต้อง derive จากตรงนี้เสมอ
     */
    public String englishLabel() {
        return englishLabel;
    }

    /**
     * รับได้หลายช่องเพื่อลองตามลำดับ สำหรับต้นทางที่ไม่รู้แน่ว่าตำแหน่งวิชาการจะมาอยู่ช่องไหน
     * — เช่น SSO ที่สายวิชาการได้จาก {@code positionName} แต่บางสายไปโผล่ที่ {@code levelName}
     *
     * <p>{@link AcademicTitleResolver#resolveThaiAcademicPosition} วนหา candidate ตัวแรกที่
     * เข้าเค้าตำแหน่งวิชาการให้อยู่แล้ว ช่องที่ไม่เกี่ยวอย่าง "พนักงานมหาวิทยาลัย" จึงไม่บัง
     * ตัวที่ใช่ และถ้าไม่มีช่องไหนเข้าเค้าเลย มันจะคืน candidate ตัวแรกมาดื้อ ๆ ซึ่งการเทียบ
     * กับ {@link #thaiLabel} ข้างล่างกรองทิ้งอีกชั้น
     *
     * @return ตำแหน่งที่ข้อความระบุ หรือ null ถ้าไม่มีช่องไหนระบุตำแหน่งวิชาการ — ไม่เดา
     */
    public static AcademicRank of(String... raw) {
        if (raw == null || raw.length == 0) {
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
