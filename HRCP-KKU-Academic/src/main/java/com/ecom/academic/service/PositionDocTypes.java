package com.ecom.academic.service;

/**
 * เลข document type ของเอกสารเฟส 2 ที่แยกฉบับตามผลงาน
 *
 * <p>เอกสารที่ 9 (แบบแสดงหลักฐานการมีส่วนร่วมในผลงานทางวิชาการ) ต้องยื่นแยกทุกผลงาน และผู้ประพันธ์
 * อันดับแรกกับผู้ประพันธ์บรรณกิจของแต่ละเรื่องลงนามรับรองฉบับของเรื่องนั้น (เอกสารแนบท้ายข้อบังคับ มข.
 * พ.ศ. 2569 ข้อ 3.3–3.4) แต่ละฉบับจึงมี type ของตัวเอง {@code 900 + N} โดย N คือเลขแถวของงานวิจัย
 * ในเอกสารที่ 1 ({@code asst_research_working_N} ฯลฯ) — ระบบลงนาม การล็อก และการส่งกลับแก้ ซึ่งผูกกับ
 * (module, request, documentType) ทำงานกับแต่ละฉบับแยกกันได้ทันที ส่วนที่อ่านเทมเพลตหรือกติกาของเอกสาร
 * ให้ใช้ {@link #base(int)} กลับไปเป็น 9
 */
public final class PositionDocTypes {

    /** แบบแสดงหลักฐานการมีส่วนร่วมในผลงานทางวิชาการ (กลุ่มที่ 1 งานวิจัย) */
    public static final int WORK_PARTICIPATION = 9;

    private static final int COPY_BASE = 900;
    private static final int MAX_COPY = 99;

    private PositionDocTypes() {
    }

    /** ฉบับของผลงานลำดับที่ {@code n} ในเอกสารที่ 1 */
    public static int copyType(int n) {
        if (n < 1 || n > MAX_COPY) {
            throw new IllegalArgumentException("work number out of range: " + n);
        }
        return COPY_BASE + n;
    }

    /** type นี้เป็นฉบับแยกตามผลงานของเอกสารที่ 9 หรือไม่ */
    public static boolean isWorkCopy(int type) {
        return type > COPY_BASE && type <= COPY_BASE + MAX_COPY;
    }

    /** เลขแถวของผลงานในเอกสารที่ 1 — 0 เมื่อไม่ใช่ฉบับแยก */
    public static int copyNo(int type) {
        return isWorkCopy(type) ? type - COPY_BASE : 0;
    }

    /** type ของเทมเพลตและกติกาที่ฉบับนี้ใช้ — ฉบับแยกคืน 9 นอกนั้นคืนตัวเอง */
    public static int base(int type) {
        return isWorkCopy(type) ? WORK_PARTICIPATION : type;
    }
}
