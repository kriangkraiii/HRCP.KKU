package com.ecom.academic.model;

public enum StatusType {
    RECEIVED("รับคำร้อง", "blue", "fa-inbox"),
    COMMITTEE_ASSIGNED("แต่งตั้งอนุกรรมการ", "orange", "fa-users"),
    MEETING_SCHEDULED("นัดหมายวันประชุม", "purple", "fa-calendar"),
    RESULT_APPROVED("แจ้งผล - ผ่าน", "green", "fa-check-circle"),
    RESULT_REVISION("แจ้งผล - แก้ไข", "yellow", "fa-edit"),
    REJECTED("ไม่รับคำร้อง", "red", "fa-times-circle"),
    COMPLETED("เสร็จสิ้น", "dark-green", "fa-check");

    private final String displayName;
    private final String color;
    private final String iconClass;

    StatusType(String displayName, String color, String iconClass) {
        this.displayName = displayName;
        this.color = color;
        this.iconClass = iconClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getIconClass() {
        return iconClass;
    }
}
