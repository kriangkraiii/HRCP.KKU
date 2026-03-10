package com.ecom.academic.model;

public enum PositionRequestStatus {
    DRAFT("แบบร่าง", "fa-edit", "secondary"),
    SUBMITTED("ส่งคำร้องแล้ว", "fa-paper-plane", "primary"),
    UNDER_REVIEW("อยู่ระหว่างพิจารณา", "fa-search", "info"),
    APPROVED("อนุมัติ", "fa-check-circle", "success"),
    REJECTED("ไม่อนุมัติ", "fa-times-circle", "danger"),
    COMPLETED("เสร็จสิ้น", "fa-flag-checkered", "dark");

    private final String thaiLabel;
    private final String icon;
    private final String badgeClass;

    PositionRequestStatus(String thaiLabel, String icon, String badgeClass) {
        this.thaiLabel = thaiLabel;
        this.icon = icon;
        this.badgeClass = badgeClass;
    }

    public String getThaiLabel() {
        return thaiLabel;
    }

    public String getIcon() {
        return icon;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == COMPLETED;
    }

    public boolean isEditable() {
        return this == DRAFT;
    }
}
