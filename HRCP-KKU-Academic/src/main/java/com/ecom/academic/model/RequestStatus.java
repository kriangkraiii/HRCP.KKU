package com.ecom.academic.model;

import java.util.EnumSet;
import java.util.List;
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
    MEETING_SCHEDULED("นัดหมายคณะอนุกรรมการ", "fa-calendar-check", "#7b1fa2"),

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

    /**
     * ข้อ 8 — มติที่ประชุมคณะอนุกรรมการ: ไม่ผ่าน ยังต้องให้กรรมการประจำวิทยาลัยฯ รับรองก่อนแจ้งผล
     * (ประกาศ มข. ฉบับที่ 1669/2569 ข้อ 10.3 และแผนภาพขั้นที่ 4 — รับรองทั้งผลที่ผ่านและไม่ผ่าน)
     */
    SUBCOMMITTEE_FAIL("มติอนุกรรมการ - ไม่ผ่าน", "fa-times-circle", "#c62828"),

    /** ข้อ 9-10 — กรรมการประจำวิทยาลัยฯ รับรองผลที่ไม่ผ่านแล้ว รอแจ้งผลภายใน 7 วันทำการ */
    COLLEGE_ENDORSED_FAIL("รับรองผล (ไม่ผ่าน) โดยกรรมการประจำวิทยาลัยฯ", "fa-landmark", "#b71c1c"),

    /** ข้อ 11 — แจ้งผลไม่ผ่านให้ผู้ขอทราบอย่างเป็นทางการ เริ่มนับกำหนดขอทบทวน */
    COMPLETED_FAIL("แจ้งผล - ไม่ผ่าน", "fa-times-circle", "#d32f2f"),

    /**
     * ผู้ยื่นขอทบทวนผลที่ไม่ผ่านต่อหัวหน้าส่วนงาน ภายใน 30 วันทำการนับจากวันที่ทราบผล
     * (ประกาศ มข. ฉบับที่ 1669/2569 ข้อ 10.3) — หัวหน้าส่วนงานรับทบทวน (นัดประชุมรอบใหม่) หรือยืนผลเดิม
     */
    APPEAL_SUBMITTED("ขอทบทวนผลการประเมิน", "fa-scale-balanced", "#6a1b9a");

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

    /** ป้ายสั้นสำหรับชิปบนแถบสถานะหน้าแอดมิน — ป้ายเต็มยาวจนตัดหลายบรรทัด */
    public String getShortLabel() {
        return switch (this) {
            case MEETING_SCHEDULED -> "นัดหมายอนุกรรมการ";
            case COMPLETED_PASS -> "ผ่าน · รอรับรอง";
            case COMPLETED_REVISE -> "รอผู้ยื่นแก้ไข";
            case REVISION_SUBMITTED -> "ส่งแก้ไขแล้ว";
            case COLLEGE_ENDORSED -> "รับรองโดยวิทยาลัย";
            case SUBCOMMITTEE_FAIL -> "ไม่ผ่าน · รอรับรอง";
            case COLLEGE_ENDORSED_FAIL -> "ไม่ผ่าน · รอแจ้งผล";
            case COMPLETED_FAIL -> "ไม่ผ่าน";
            case APPEAL_SUBMITTED -> "ขอทบทวนผล";
            default -> thaiLabel;
        };
    }

    /**
     * ป้ายบนแถบความคืบหน้า — ขั้นแจ้งผลเป็นกลาง ผลจริง (ผ่าน/แก้ไข/ไม่ผ่าน) แสดงด้วยสีและไอคอน
     * ไม่เช่นนั้นคำร้องที่ไม่ผ่านจะมีขั้น "แจ้งผล - ผ่าน" ค้างอยู่บนเส้นทาง
     */
    public String getStepLabel() {
        return this == COMPLETED_PASS ? "แจ้งผล" : thaiLabel;
    }

    /**
     * แถบสถานะหน้าแอดมิน: สถานะที่แอดมินต้องทำขั้นต่อไป เรียงตามเส้นทางหลัก
     * แล้วตามด้วยวงจรแก้ไข — แบ่งกลุ่มตามว่าใครถือลูกอยู่ ไม่ใช่ลำดับใน enum
     */
    public static List<RequestStatus> awaitingAdmin() {
        return List.of(RECEIVED, SUB_COMMITTEE_APPOINTED, MEETING_SCHEDULED,
                COMPLETED_PASS, COLLEGE_ENDORSED, SUBCOMMITTEE_FAIL, COLLEGE_ENDORSED_FAIL, REVISION_SUBMITTED,
                APPEAL_SUBMITTED);
    }

    /** แถบสถานะหน้าแอดมิน: ลูกอยู่ที่ผู้ยื่น (ข้อ 12-13) */
    public static List<RequestStatus> awaitingApplicant() {
        return List.of(COMPLETED_REVISE);
    }

    /** แถบสถานะหน้าแอดมิน: ปิดแล้ว */
    public static List<RequestStatus> closed() {
        return List.of(COMPLETED, COMPLETED_FAIL);
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
                EnumSet.of(COMPLETED_PASS, COMPLETED_REVISE, SUBCOMMITTEE_FAIL);
            // ข้อ 9-10 — ผลจากอนุกรรมการต้องผ่านการรับรองของกรรมการประจำวิทยาลัยฯ ก่อนแจ้งผล
            case COMPLETED_PASS -> EnumSet.of(COLLEGE_ENDORSED);
            // ข้อ 12-15 — วงจรแก้ไข: ผู้ยื่นส่งกลับ แล้ววนเข้าที่ประชุมอนุกรรมการอีกรอบ
            case COMPLETED_REVISE -> EnumSet.of(REVISION_SUBMITTED);
            case REVISION_SUBMITTED -> EnumSet.of(MEETING_SCHEDULED);
            case COLLEGE_ENDORSED -> EnumSet.of(COMPLETED);
            // 1669/2569 ข้อ 10.3 — ผลไม่ผ่านก็ต้องผ่านการรับรองของกรรมการประจำวิทยาลัยฯ ก่อนแจ้งผล
            case SUBCOMMITTEE_FAIL -> EnumSet.of(COLLEGE_ENDORSED_FAIL);
            case COLLEGE_ENDORSED_FAIL -> EnumSet.of(COMPLETED_FAIL);
            // 1669/2569 ข้อ 10.3 — ไม่ผ่านแล้วขอทบทวนได้ (ครั้งเดียว ภายใน 30 วันทำการ ตรวจที่ service)
            case COMPLETED_FAIL -> EnumSet.of(APPEAL_SUBMITTED);
            // หัวหน้าส่วนงานรับทบทวน → ประชุมอนุกรรมการรอบใหม่ หรือยืนผลเดิม
            case APPEAL_SUBMITTED -> EnumSet.of(MEETING_SCHEDULED, COMPLETED_FAIL);
            case COMPLETED -> EnumSet.noneOf(RequestStatus.class);
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
            case COMPLETED_REVISE, REVISION_SUBMITTED, SUBCOMMITTEE_FAIL, COLLEGE_ENDORSED_FAIL, COMPLETED_FAIL,
                    APPEAL_SUBMITTED -> MEETING_SCHEDULED;
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

    /**
     * ขั้นที่กำลังดำเนินการบนแถบความคืบหน้า หรือ -1 เมื่อไม่มี (แบบร่าง / เสร็จสิ้น)
     *
     * <p>ชื่อสถานะคือเหตุการณ์ที่เกิดไปแล้ว — "นัดหมายคณะอนุกรรมการ" แปลว่านัดแล้ว
     * ขั้นของสถานะปัจจุบันจึงเสร็จแล้ว ส่วนที่กำลังดำเนินการคือขั้นถัดไป
     */
    public int focusIndex() {
        if (this == DRAFT || this == COMPLETED) {
            return -1;
        }
        return progressIndex() + 1;
    }

    /** สถานะของขั้นที่ {@code index} บนแถบความคืบหน้า เมื่อคำร้องอยู่ในสถานะนี้ */
    public ProgressStepState stepStateAt(int index) {
        int reached = progressIndex();
        if (index <= reached) {
            return this == COMPLETED && index == getProgressSteps().length - 1
                    ? ProgressStepState.FINAL : ProgressStepState.DONE;
        }
        if (index != focusIndex()) {
            return ProgressStepState.PENDING;
        }
        return switch (this) {
            case COMPLETED_REVISE, REVISION_SUBMITTED, APPEAL_SUBMITTED -> ProgressStepState.REVISE;
            case SUBCOMMITTEE_FAIL, COLLEGE_ENDORSED_FAIL, COMPLETED_FAIL -> ProgressStepState.REJECTED;
            default -> ProgressStepState.CURRENT;
        };
    }

    /** คำอธิบายใต้ขั้นที่กำลังดำเนินการ ({@link #focusIndex()}) */
    public String getStepHint() {
        return switch (this) {
            case DRAFT, COMPLETED -> null;
            case MEETING_SCHEDULED -> "รอผลการประชุม";
            case COMPLETED_REVISE -> "รอผู้ยื่นแก้ไข";
            case REVISION_SUBMITTED -> "ส่งแก้ไขแล้ว · รอพิจารณารอบใหม่";
            case SUBCOMMITTEE_FAIL -> "ไม่ผ่าน · รอกรรมการประจำวิทยาลัยฯ รับรอง";
            case COLLEGE_ENDORSED_FAIL -> "ไม่ผ่าน · รับรองแล้ว รอแจ้งผล";
            case COMPLETED_FAIL -> "ไม่ผ่าน";
            case APPEAL_SUBMITTED -> "ขอทบทวนผล · รอหัวหน้าส่วนงานพิจารณา";
            default -> "กำลังดำเนินการ";
        };
    }

    /** สถานะที่ถือว่าเสร็จสิ้นแล้ว (ยื่นคำร้องใหม่ได้) */
    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_FAIL;
    }

    /** ผลการประเมินไม่ผ่าน ไม่ว่าจะรอรับรอง รอแจ้งผล หรือแจ้งผลแล้ว */
    public boolean carriesAFailedResult() {
        return this == SUBCOMMITTEE_FAIL || this == COLLEGE_ENDORSED_FAIL || this == COMPLETED_FAIL;
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
