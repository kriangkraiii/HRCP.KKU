package com.ecom.model;

public enum NotificationType {
    ACADEMIC_NEW_REQUEST("คำร้องขอรับการประเมินใหม่", "fas fa-clipboard-check text-primary"),
    ACADEMIC_STATUS_UPDATE("อัปเดตสถานะการประเมิน", "fas fa-sync-alt text-info"),
    POSITION_NEW_REQUEST("คำร้องขอตำแหน่งใหม่", "fas fa-university text-primary"),
    POSITION_STATUS_UPDATE("อัปเดตสถานะขอตำแหน่ง", "fas fa-sync-alt text-info"),
    EXPIRY_WARNING("แจ้งเตือนใกล้หมดอายุ", "fas fa-exclamation-triangle text-danger"),
    SYSTEM("การแจ้งเตือนจากระบบ", "fas fa-bell text-secondary"),
    LIKE("liked your post", "fas fa-thumbs-up text-primary"),
    COMMENT("commented on your post", "fas fa-comment text-info"),
    ORDER("order update", "fas fa-shopping-cart text-success");

    private final String thaiLabel;
    private final String iconClass;

    NotificationType(String thaiLabel) {
        this(thaiLabel, "fas fa-bell text-primary");
    }

    NotificationType(String thaiLabel, String iconClass) {
        this.thaiLabel = thaiLabel;
        this.iconClass = iconClass;
    }

    public String getThaiLabel() {
        return thaiLabel;
    }

    public String getIconClass() {
        return iconClass;
    }

    public String getDefaultMessage() {
        return thaiLabel;
    }
}