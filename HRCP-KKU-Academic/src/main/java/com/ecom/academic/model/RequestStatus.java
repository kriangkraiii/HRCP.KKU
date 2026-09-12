package com.ecom.academic.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * สถานะคำร้องประเมินผลการสอน — ข้อ 1-15 ของ Flow การขอกำหนดตำแหน่งทางวิชาการ
 *
 * <p>เรียงตามลำดับที่เกิดขึ้นจริงในกระบวนการ ไม่ใช่ลำดับที่เผอิญเขียนไว้ก่อนหลัง
 * แต่โค้ดที่ใช้งานต้องถามจาก {@link #allowedNext()} เท่านั้น ห้ามเทียบด้วย
 * {@code ordinal()} — ลำดับการประกาศเป็นเรื่องของการอ่านโค้ด ไม่ใช่กติกาของกระบวนการ
 */
public enum RequestStatus {

    DRAFT("แบบร่าง", "fa-file-pen", "#757575"),

    /** ข้อ 1 — หน่วยสารบรรณรับเรื่อง */
    RECEIVED("รับคำร้อง", "fa-file-signature", "#1565c0"),

    /** ข้อ 3-4 — คณบดีเสนอรายชื่ออนุกรรมการ 3 คน และลงนามคำสั่งแต่งตั้ง */
    SUB_COMMITTEE_APPOINTED("แต่งตั้งอนุกรรมการ", "fa-users-cog", "#e65100"),

    /** ข้อ 6-7 — นัดหมายวันประชุมและจองห้อง */
    MEETING_SCHEDULED("นัดหมายวันประชุม", "fa-calendar-check", "#7b1fa2"),

    /** ข้อ 8 — มติที่ประชุมคณะอนุกรรมการ: ผ่าน */
    COMPLETED_PASS("แจ้งผล - ผ่าน", "fa-check-circle", "#2e7d32"),

    /** ข้อ 8/12 — มติที่ประชุมคณะอนุกรรมการ: ให้แก้ไข */
    COMPLETED_REVISE("แจ้งผล - แก้ไข", "fa-exclamation-circle", "#f57f17"),

    /** ข้อ 13 — ผู้ขอกำหนดตำแหน่งส่งเอกสารที่แก้แล้วกลับมา */
    REVISION_SUBMITTED("ส่งเอกสารแก้ไขแล้ว", "fa-rotate-left", "#ef6c00"),

    /** ข้อ 9-10 — คณะกรรมการประจำวิทยาลัยฯ ประชุมรับรองผลประเมินการสอน */
    COLLEGE_ENDORSED("รับรองผลโดยกรรมการประจำวิทยาลัยฯ", "fa-landmark", "#00695c"),

    /** ข้อ 11 — แจ้งผลให้ผู้ขอกำหนดตำแหน่งทราบ กระบวนการเฟส 1 จบ */
    COMPLETED("เสร็จสิ้น", "fa-flag-checkered", "#1b5e20"),

    /** ข้อ 8 — มติที่ประชุมคณะอนุกรรมการ: ไม่ผ่าน */
    COMPLETED_FAIL("แจ้งผล - ไม่ผ่าน", "fa-times-circle", "#d32f2f");

    private final String thaiLabel;
    private final String icon;
    private final String color;

    RequestStatus(String thaiLabel, String icon, String color) {
        this.thaiLabel = thaiLabel;
        this.icon = icon;
        this.color = color;
    }

    public String getThaiLabel() {
        return thaiLabel;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    /**
     * สถานะที่เปลี่ยนไปได้จากสถานะนี้ ตามลำดับใน Flow ข้อ 1-15
     *
     * <p>เดิม {@code updateStatus} เป็นเพียง setter ที่มีประวัติ — กระโดดจาก
     * "รับคำร้อง" ไป "เสร็จสิ้น" ข้ามการแต่งตั้งอนุกรรมการ การประชุม และการรับรอง
     * ของกรรมการประจำวิทยาลัยฯ ได้ทั้งหมด และดึงคำร้องที่ปิดไปแล้วกลับมาก็ได้ (GAP-30)
     *
     * <p>ไม่มีสถานะปฏิเสธ — ใน Flow ทุกคำตอบ "NO" คือการตีกลับให้แก้ ข้อ 2 คณบดีไม่เห็นชอบ
     * ส่งคำร้องกลับเป็น {@code DRAFT} ให้ผู้ยื่นแก้แล้วยื่นใหม่ (ต้องมีเหตุผล) ส่วนหลังแต่งตั้ง
     * อนุกรรมการแล้ว การตีกลับคือวงจร {@code COMPLETED_REVISE} ของข้อ 12-15
     */
    public Set<RequestStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(RECEIVED);
            // ข้อ 2 — คณบดีไม่เห็นชอบ: ส่งคืนให้ผู้ยื่นแก้แล้วยื่นใหม่ (กลับไปข้อ 1)
            case RECEIVED -> EnumSet.of(SUB_COMMITTEE_APPOINTED, DRAFT);
            case SUB_COMMITTEE_APPOINTED -> EnumSet.of(MEETING_SCHEDULED);
            case MEETING_SCHEDULED ->
                EnumSet.of(COMPLETED_PASS, COMPLETED_REVISE, COMPLETED_FAIL);
            // ข้อ 9-10 — ผลจากอนุกรรมการต้องผ่านการรับรองของกรรมการประจำวิทยาลัยฯ ก่อนแจ้งผล
            case COMPLETED_PASS -> EnumSet.of(COLLEGE_ENDORSED);
            // ข้อ 12-15 — วงจรแก้ไข: ผู้ยื่นส่งกลับ แล้ววนเข้าที่ประชุมอนุกรรมการอีกรอบ
            case COMPLETED_REVISE -> EnumSet.of(REVISION_SUBMITTED);
            case REVISION_SUBMITTED -> EnumSet.of(MEETING_SCHEDULED);
            case COLLEGE_ENDORSED -> EnumSet.of(COMPLETED);
            case COMPLETED, COMPLETED_FAIL -> EnumSet.noneOf(RequestStatus.class);
        };
    }

    /** เปลี่ยนจากสถานะนี้ไปเป็น {@code target} ได้หรือไม่ */
    public boolean canMoveTo(RequestStatus target) {
        return target != null && allowedNext().contains(target);
    }

    /**
     * สถานะที่แสดงใน progress tracker — เส้นทางหลักตาม flow ไม่รวมกิ่งที่แยกออกไป
     *
     * <p>เดิมแสดงเพียง 3 ขั้นจากกระบวนการจริง 15 ขั้น ผู้ยื่นจึงมองไม่เห็นว่า
     * ยังเหลืออะไรอีกบ้าง (GAP-33)
     */
    public static RequestStatus[] getProgressSteps() {
        return new RequestStatus[] { RECEIVED, SUB_COMMITTEE_APPOINTED, MEETING_SCHEDULED,
                COMPLETED_PASS, COLLEGE_ENDORSED, COMPLETED };
    }

    /**
     * ตำแหน่งบนแถบความคืบหน้า (0-based) หรือ -1 เมื่อยังไม่เริ่ม
     *
     * <p>แทนการเทียบ {@code ordinal()} ซึ่งผูกกับลำดับการประกาศ ไม่ใช่ลำดับของ
     * กระบวนการ — สลับบรรทัดใน enum ทีเดียวแถบความคืบหน้าก็เพี้ยนเงียบ ๆ (GAP-32)
     * สถานะที่แตกกิ่งออกไปจะถูกแมปกลับมาที่จุดที่แตกออก
     */
    public int progressIndex() {
        RequestStatus onMainPath = switch (this) {
            case DRAFT -> null;
            case COMPLETED_REVISE, REVISION_SUBMITTED, COMPLETED_FAIL -> MEETING_SCHEDULED;
            default -> this;
        };
        if (onMainPath == null) {
            return -1;
        }
        RequestStatus[] steps = getProgressSteps();
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == onMainPath) {
                return i;
            }
        }
        return -1;
    }

    /** สถานะที่ถือว่าเสร็จสิ้นแล้ว (ยื่นคำร้องใหม่ได้) */
    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_FAIL;
    }

    /** สถานะ draft - ยังไม่ส่งคำร้อง */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * ผลประเมินในสถานะนี้ใช้ยื่นขอกำหนดตำแหน่งได้หรือไม่
     *
     * <p>ข้อ 11 บอกว่าเมื่อทราบผลแล้วให้ยื่นขอกำหนดตำแหน่งต่อได้ — ผลที่ประกาศแล้ว
     * จึงใช้ได้ ไม่ว่างานเอกสารเบื้องหลังจะถูกปิดเป็น COMPLETED แล้วหรือยัง
     */
    public boolean carriesAPassedResult() {
        return this == COMPLETED_PASS || this == COLLEGE_ENDORSED || this == COMPLETED;
    }
}
