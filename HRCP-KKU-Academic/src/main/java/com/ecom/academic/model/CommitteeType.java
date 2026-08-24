package com.ecom.academic.model;

/**
 * Types of committees and external assessors.
 */
public enum CommitteeType {

    TEACHING_EVALUATION("กรรมการประเมินผลการสอน", "fas fa-chalkboard-user", "info"),
    POSITION_SCREENING("คณะกรรมการกลั่นกรองผลงานทางวิชาการ", "fas fa-filter-circle-dollar", "primary"),
    EXTERNAL_READER("ผู้ทรงคุณวุฒิประเมินผลงานทางวิชาการ (Reader)", "fas fa-book-open-reader", "success"),
    OTHER("กรรมการผู้ทรงคุณวุฒิอื่นๆ", "fas fa-user-tie", "secondary");

    private final String thaiLabel;
    private final String icon;
    private final String color;

    CommitteeType(String thaiLabel, String icon, String color) {
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
}
