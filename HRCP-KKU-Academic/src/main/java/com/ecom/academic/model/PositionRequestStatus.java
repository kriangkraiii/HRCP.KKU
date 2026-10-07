package com.ecom.academic.model;

public enum PositionRequestStatus {
    DRAFT("แบบร่าง", "fa-file-pen", "#757575"),
    DOCUMENT_RECEIVED("รับคำร้อง", "fa-file-signature", "#1565c0"),
    DOCUMENT_VERIFICATION("ตรวจสอบความถูกต้อง/ครบถ้วน", "fa-clipboard-check", "#0277bd"),
    SCREENING_COMMITTEE("เสนอวาระกลั่นกรองฯ", "fa-users-cog", "#e65100"),
    REVISION_REQUESTED("ส่งแก้ไข", "fa-circle-exclamation", "#f57f17"),
    SCREENING_APPROVED("รับรองมติกลั่นกรองฯ", "fa-stamp", "#2e7d32"),
    COLLEGE_COMMITTEE("เสนอวาระคณะกรรมการวิทยาลัยฯ", "fa-landmark", "#7b1fa2"),
    COLLEGE_APPROVED("รับรองมติคณะกรรมการวิทยาลัยฯ", "fa-file-circle-check", "#1b5e20"),
    SENT_TO_HR("ส่งออกกองทรัพยากรบุคคล มข.", "fa-paper-plane", "#004d40"),
    /** สภามหาวิทยาลัยมีมติกำหนดตำแหน่ง — จบกระบวนการ */
    COUNCIL_APPROVED("สภามหาวิทยาลัยอนุมัติ", "fa-award", "#1b5e20"),
    /** สภามหาวิทยาลัยมีมติไม่กำหนดตำแหน่ง — ขอทบทวนได้ไม่เกิน 2 ครั้ง ภายใน 90 วันนับจากวันรับทราบมติ (ข้อบังคับ 2569 ข้อ 35) */
    COUNCIL_REJECTED("สภามหาวิทยาลัยไม่อนุมัติ", "fa-circle-xmark", "#c62828"),
    /** ยื่นขอทบทวนผลการพิจารณาแล้ว รอ ก.พ.ว. และสภามหาวิทยาลัยพิจารณา */
    APPEAL_SUBMITTED("ยื่นขอทบทวนผลการพิจารณา", "fa-scale-balanced", "#6a1b9a");

    private final String thaiLabel;
    private final String icon;
    private final String color;

    PositionRequestStatus(String thaiLabel, String icon, String color) {
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
            case DOCUMENT_VERIFICATION -> "ตรวจสอบเอกสาร";
            case SCREENING_COMMITTEE -> "เสนอกลั่นกรองฯ";
            case REVISION_REQUESTED -> "รอผู้ยื่นแก้ไข";
            case SCREENING_APPROVED -> "รับรองมติกลั่นกรองฯ";
            case COLLEGE_COMMITTEE -> "เสนอกรรมการวิทยาลัยฯ";
            case COLLEGE_APPROVED -> "รับรองมติวิทยาลัยฯ";
            case SENT_TO_HR -> "ส่งกองทรัพยากรบุคคลแล้ว";
            case COUNCIL_APPROVED -> "สภาฯ อนุมัติ";
            case COUNCIL_REJECTED -> "สภาฯ ไม่อนุมัติ";
            case APPEAL_SUBMITTED -> "ขอทบทวนผล";
            default -> thaiLabel;
        };
    }

    /** แถบสถานะหน้าแอดมิน: สถานะที่แอดมินต้องทำขั้นต่อไป เรียงตาม flow */
    public static java.util.List<PositionRequestStatus> awaitingAdmin() {
        return java.util.List.of(DOCUMENT_RECEIVED, DOCUMENT_VERIFICATION, SCREENING_COMMITTEE,
                SCREENING_APPROVED, COLLEGE_COMMITTEE, COLLEGE_APPROVED);
    }

    /** แถบสถานะหน้าแอดมิน: ลูกอยู่ที่ผู้ยื่น */
    public static java.util.List<PositionRequestStatus> awaitingApplicant() {
        return java.util.List.of(REVISION_REQUESTED);
    }

    /** แถบสถานะหน้าแอดมิน: ปิดแล้วในฝั่งวิทยาลัย — ส่งออกแล้ว รวมถึงผลจากสภาและการขอทบทวน */
    public static java.util.List<PositionRequestStatus> closed() {
        return java.util.List.of(SENT_TO_HR, COUNCIL_APPROVED, COUNCIL_REJECTED, APPEAL_SUBMITTED);
    }

    /** Bootstrap badge class derived from color */
    public String getBadgeClass() {
        switch (this) {
            case DRAFT:
                return "secondary";
            case REVISION_REQUESTED:
                return "warning";
            case SENT_TO_HR:
                return "dark";
            case COUNCIL_APPROVED:
                return "success";
            case COUNCIL_REJECTED:
                return "danger";
            default:
                return "primary";
        }
    }

    /** Progress steps for the tracker (main flow, no branches) */
    public static PositionRequestStatus[] getProgressSteps() {
        return new PositionRequestStatus[] {
                DOCUMENT_RECEIVED,
                DOCUMENT_VERIFICATION,
                SCREENING_COMMITTEE,
                SCREENING_APPROVED,
                COLLEGE_COMMITTEE,
                COLLEGE_APPROVED,
                SENT_TO_HR,
                COUNCIL_APPROVED
        };
    }

    /**
     * สถานะที่เปลี่ยนไปได้จากสถานะนี้ ตามลำดับใน Flow ข้อ 16-31
     *
     * <p>เดิม {@code updateStatus} รับสถานะปลายทางอะไรก็ได้จากต้นทางอะไรก็ได้
     * กระโดดจาก "รับคำร้อง" ไป "ส่งออกกองทรัพยากรบุคคล" ข้ามทั้งการกลั่นกรองและ
     * คณะกรรมการประจำวิทยาลัยฯ ได้ (GAP-30)
     *
     * <p>ไม่มีสถานะปฏิเสธ — ใน Flow ข้อ 22 และ 27 คำตอบ "NO" คือการตีกลับให้แก้
     * {@code REVISION_REQUESTED} ออกได้จากทุกจุดที่มีการพิจารณา แล้วกลับเข้า
     * ขั้นตรวจสอบเอกสารอีกครั้ง
     */
    public java.util.Set<PositionRequestStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> java.util.EnumSet.of(DOCUMENT_RECEIVED);
            case DOCUMENT_RECEIVED -> java.util.EnumSet.of(DOCUMENT_VERIFICATION);
            case DOCUMENT_VERIFICATION ->
                java.util.EnumSet.of(SCREENING_COMMITTEE, REVISION_REQUESTED);
            case SCREENING_COMMITTEE ->
                java.util.EnumSet.of(SCREENING_APPROVED, REVISION_REQUESTED);
            case SCREENING_APPROVED -> java.util.EnumSet.of(COLLEGE_COMMITTEE);
            case COLLEGE_COMMITTEE ->
                java.util.EnumSet.of(COLLEGE_APPROVED, REVISION_REQUESTED);
            case COLLEGE_APPROVED -> java.util.EnumSet.of(SENT_TO_HR);
            // ผู้ยื่นแก้แล้วส่งกลับ เข้าสู่การตรวจสอบเอกสารอีกรอบ
            case REVISION_REQUESTED -> java.util.EnumSet.of(DOCUMENT_VERIFICATION);
            // หลังส่งออก: บันทึกมติสภามหาวิทยาลัย และการขอทบทวน (ข้อบังคับ 2569 ข้อ 35)
            // จำนวนครั้งและกำหนด 90 วันตรวจที่ PositionRequestService เพราะต้องดูประวัติ
            case SENT_TO_HR, APPEAL_SUBMITTED -> java.util.EnumSet.of(COUNCIL_APPROVED, COUNCIL_REJECTED);
            case COUNCIL_REJECTED -> java.util.EnumSet.of(APPEAL_SUBMITTED);
            case COUNCIL_APPROVED -> java.util.EnumSet.noneOf(PositionRequestStatus.class);
        };
    }

    /** เปลี่ยนจากสถานะนี้ไปเป็น {@code target} ได้หรือไม่ */
    public boolean canMoveTo(PositionRequestStatus target) {
        return target != null && allowedNext().contains(target);
    }

    /**
     * ตำแหน่งบนแถบความคืบหน้า (0-based) หรือ -1 เมื่อยังไม่เริ่ม
     * แทนการเทียบ {@code ordinal()} — ดูเหตุผลใน {@code RequestStatus} (GAP-32)
     */
    public int progressIndex() {
        PositionRequestStatus onMainPath = switch (this) {
            case DRAFT -> null;
            // ส่งแก้ไขคือการถอยกลับมาที่ขั้นตรวจสอบเอกสาร
            case REVISION_REQUESTED -> DOCUMENT_VERIFICATION;
            // ยังไม่ได้รับการกำหนดตำแหน่ง — ค้างอยู่ที่ขั้นส่งออก รอผลสภาฯ / ผลการทบทวน
            case COUNCIL_REJECTED, APPEAL_SUBMITTED -> SENT_TO_HR;
            default -> this;
        };
        if (onMainPath == null) {
            return -1;
        }
        PositionRequestStatus[] steps = getProgressSteps();
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == onMainPath) {
                return i;
            }
        }
        return -1;
    }

    /**
     * จบงานฝั่งวิทยาลัยแล้ว — ส่งออกกองทรัพยากรบุคคลไปแล้ว ขั้นหลังจากนั้น (มติสภา การขอทบทวน)
     * เป็นการบันทึกตามผลของมหาวิทยาลัย ผู้ยื่นจึงเริ่มคำร้องใหม่ได้ตั้งแต่ส่งออก เหมือนเดิม
     */
    public boolean isTerminal() {
        return this == SENT_TO_HR || this == COUNCIL_APPROVED || this == COUNCIL_REJECTED
                || this == APPEAL_SUBMITTED;
    }

    /** สภามหาวิทยาลัยมีมติแล้ว (อนุมัติหรือไม่อนุมัติ) และไม่ได้ขอทบทวน */
    public boolean isCouncilDecided() {
        return this == COUNCIL_APPROVED || this == COUNCIL_REJECTED;
    }

    /**
     * ผลงานในคำร้องสถานะนี้ไม่ถูกจองไว้ — แบบร่างยังไม่ได้ยื่น ส่วนคำร้องที่สภามีมติแล้ว
     * นำผลงานมาใช้ยื่นใหม่ได้ตามข้อบังคับ มข. พ.ศ. 2569 ข้อ 34 โดยต้องระบุว่า "เคยใช้"
     */
    public boolean releasesWorks() {
        return this == DRAFT || isCouncilDecided();
    }

    /** สถานะทั้งหมดที่ {@link #releasesWorks()} */
    public static java.util.Set<PositionRequestStatus> worksReleasing() {
        java.util.Set<PositionRequestStatus> set = java.util.EnumSet.noneOf(PositionRequestStatus.class);
        for (PositionRequestStatus s : values()) {
            if (s.releasesWorks()) {
                set.add(s);
            }
        }
        return set;
    }

    /** Draft — not yet submitted */
    public boolean isDraft() {
        return this == DRAFT;
    }

    public boolean isEditable() {
        return this == DRAFT;
    }
}
