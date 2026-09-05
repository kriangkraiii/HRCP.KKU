package com.ecom.academic.model;

public enum PositionRequestStatus {
    DRAFT("แบบร่าง", "fa-file-pen", "#757575"),
    DOCUMENT_RECEIVED("รับคำร้อง", "fa-file-signature", "#1565c0"),
    DOCUMENT_VERIFICATION("ตรวจสอบความถูกต้อง/ครบถ้วน", "fa-clipboard-check", "#0277bd"),
    SCREENING_COMMITTEE("เสนอวาระกลั่นกรองฯ", "fa-users-cog", "#e65100"),
    REVISION_REQUESTED("ส่งแก้ไข", "fa-circle-exclamation", "#f57f17"),
    REJECTED("ไม่รับคำร้อง", "fa-circle-xmark", "#c62828"),
    SCREENING_APPROVED("รับรองมติกลั่นกรองฯ", "fa-stamp", "#2e7d32"),
    COLLEGE_COMMITTEE("เสนอวาระคณะกรรมการวิทยาลัยฯ", "fa-landmark", "#7b1fa2"),
    COLLEGE_APPROVED("รับรองมติคณะกรรมการวิทยาลัยฯ", "fa-file-circle-check", "#1b5e20"),
    SENT_TO_HR("ส่งออกกองทรัพยากรบุคคล มข.", "fa-paper-plane", "#004d40");

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

    /** Bootstrap badge class derived from color */
    public String getBadgeClass() {
        switch (this) {
            case DRAFT:
                return "secondary";
            case REVISION_REQUESTED:
                return "warning";
            case REJECTED:
                return "danger";
            case SENT_TO_HR:
                return "dark";
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
                SENT_TO_HR
        };
    }

    /**
     * สถานะที่เปลี่ยนไปได้จากสถานะนี้ ตามลำดับใน Flow ข้อ 16-31
     *
     * <p>เดิม {@code updateStatus} รับสถานะปลายทางอะไรก็ได้จากต้นทางอะไรก็ได้
     * กระโดดจาก "รับคำร้อง" ไป "ส่งออกกองทรัพยากรบุคคล" ข้ามทั้งการกลั่นกรองและ
     * คณะกรรมการประจำวิทยาลัยฯ ได้ (GAP-30)
     *
     * <p>{@code REJECTED} ออกได้จากทุกสถานะที่ยังไม่ปิด และ
     * {@code REVISION_REQUESTED} ออกได้จากทุกจุดที่มีการพิจารณา แล้วกลับเข้า
     * ขั้นตรวจสอบเอกสารอีกครั้ง
     */
    public java.util.Set<PositionRequestStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> java.util.EnumSet.of(DOCUMENT_RECEIVED);
            case DOCUMENT_RECEIVED -> java.util.EnumSet.of(DOCUMENT_VERIFICATION, REJECTED);
            case DOCUMENT_VERIFICATION ->
                java.util.EnumSet.of(SCREENING_COMMITTEE, REVISION_REQUESTED, REJECTED);
            case SCREENING_COMMITTEE ->
                java.util.EnumSet.of(SCREENING_APPROVED, REVISION_REQUESTED, REJECTED);
            case SCREENING_APPROVED -> java.util.EnumSet.of(COLLEGE_COMMITTEE, REJECTED);
            case COLLEGE_COMMITTEE ->
                java.util.EnumSet.of(COLLEGE_APPROVED, REVISION_REQUESTED, REJECTED);
            case COLLEGE_APPROVED -> java.util.EnumSet.of(SENT_TO_HR, REJECTED);
            // ผู้ยื่นแก้แล้วส่งกลับ เข้าสู่การตรวจสอบเอกสารอีกรอบ
            case REVISION_REQUESTED -> java.util.EnumSet.of(DOCUMENT_VERIFICATION, REJECTED);
            case SENT_TO_HR, REJECTED -> java.util.EnumSet.noneOf(PositionRequestStatus.class);
        };
    }

    /** เปลี่ยนจากสถานะนี้ไปเป็น {@code target} ได้หรือไม่ */
    public boolean canMoveTo(PositionRequestStatus target) {
        return target != null && allowedNext().contains(target);
    }

    /**
     * ตำแหน่งบนแถบความคืบหน้า (0-based) หรือ -1 เมื่อยังไม่เริ่ม/ถูกปฏิเสธ
     * แทนการเทียบ {@code ordinal()} — ดูเหตุผลใน {@code RequestStatus} (GAP-32)
     */
    public int progressIndex() {
        PositionRequestStatus onMainPath = switch (this) {
            case DRAFT, REJECTED -> null;
            // ส่งแก้ไขคือการถอยกลับมาที่ขั้นตรวจสอบเอกสาร
            case REVISION_REQUESTED -> DOCUMENT_VERIFICATION;
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

    /** Terminal status — process is finished */
    public boolean isTerminal() {
        return this == SENT_TO_HR || this == REJECTED;
    }

    /** Draft — not yet submitted */
    public boolean isDraft() {
        return this == DRAFT;
    }

    public boolean isEditable() {
        return this == DRAFT;
    }
}
