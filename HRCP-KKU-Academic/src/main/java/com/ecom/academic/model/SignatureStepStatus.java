package com.ecom.academic.model;

/**
 * State of one person's turn to sign.
 *
 * <p>{@link #WAITING} versus {@link #ACTIVE} is what enforces "ตามลำดับขั้น":
 * only the active step accepts a signature, and a step becomes active only when
 * every step before it has been signed.
 */
public enum SignatureStepStatus {

    WAITING("รอคิว", "fas fa-clock", "secondary"),
    ACTIVE("ถึงคิวลงนาม", "fas fa-pen-nib", "warning"),
    SIGNED("ลงนามแล้ว", "fas fa-circle-check", "success"),
    DECLINED("ปฏิเสธการลงนาม", "fas fa-circle-xmark", "danger"),

    /** Never reached because the envelope ended first. */
    SKIPPED("ไม่ได้ลงนาม", "fas fa-minus", "secondary");

    private final String thaiLabel;
    private final String icon;
    private final String color;

    SignatureStepStatus(String thaiLabel, String icon, String color) {
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
