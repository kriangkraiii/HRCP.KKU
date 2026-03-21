package com.ecom.academic.model;

public enum PositionRequestStatus {
    DRAFT("แบบร่าง", "fa-edit", "#9e9e9e"),
    DOCUMENT_RECEIVED("รับคำร้อง", "fa-inbox", "#1565c0"),
    DOCUMENT_VERIFICATION("ตรวจสอบความถูกต้อง/ครบถ้วน", "fa-search", "#0277bd"),
    SCREENING_COMMITTEE("เสนอวาระกลั่นกรองฯ", "fa-users-cog", "#e65100"),
    REVISION_REQUESTED("ส่งแก้ไข", "fa-exclamation-circle", "#f57f17"),
    REJECTED("ไม่รับคำร้อง", "fa-times-circle", "#c62828"),
    SCREENING_APPROVED("รับรองมติกลั่นกรองฯ", "fa-check-double", "#2e7d32"),
    COLLEGE_COMMITTEE("เสนอวาระคณะกรรมการวิทยาลัยฯ", "fa-landmark", "#7b1fa2"),
    COLLEGE_APPROVED("รับรองมติคณะกรรมการวิทยาลัยฯ", "fa-certificate", "#1b5e20"),
    SENT_TO_HR("ส่งออกกองทรัพยากรบุคคล มข.", "fa-flag-checkered", "#004d40");

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
