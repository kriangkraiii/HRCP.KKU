package com.ecom.academic.model;

public enum RequestStatus {
    DRAFT("แบบร่าง", "fa-pencil-alt", "#9e9e9e"),
    RECEIVED("รับคำร้อง", "fa-inbox", "#1565c0"),
    SUB_COMMITTEE_APPOINTED("แต่งตั้งอนุกรรมการ", "fa-users-cog", "#e65100"),
    MEETING_SCHEDULED("นัดหมายวันประชุม", "fa-calendar-check", "#7b1fa2"),
    COMPLETED_PASS("แจ้งผล - ผ่าน", "fa-check-circle", "#2e7d32"),
    COMPLETED_REVISE("แจ้งผล - แก้ไข", "fa-exclamation-circle", "#f57f17"),
    REJECTED("ไม่รับคำร้อง", "fa-times-circle", "#c62828"),
    COMPLETED("เสร็จสิ้น", "fa-flag-checkered", "#1b5e20");

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
     * สถานะที่แสดงใน progress tracker (ไม่แสดงสถานะที่เป็น branch)
     * แสดงเฉพาะสถานะที่เกิดขึ้นจริงตาม flow
     */
    public static RequestStatus[] getProgressSteps() {
        return new RequestStatus[]{RECEIVED, SUB_COMMITTEE_APPOINTED, MEETING_SCHEDULED};
    }

    /** สถานะที่ถือว่าเสร็จสิ้นแล้ว (ยื่นคำร้องใหม่ได้) */
    public boolean isTerminal() {
        return this == REJECTED || this == COMPLETED;
    }

    /** สถานะ draft - ยังไม่ส่งคำร้อง */
    public boolean isDraft() {
        return this == DRAFT;
    }
}
