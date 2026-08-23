package com.ecom.academic.model;

/**
 * Lifecycle of a signing envelope.
 *
 * <p>Follows the shape of {@link RequestStatus}: Thai label, icon and colour on
 * the enum itself, so views never have to translate a status by hand.
 */
public enum SignatureRequestStatus {

    IN_PROGRESS("รอลงนาม", "fas fa-hourglass-half", "warning"),
    COMPLETED("ลงนามครบแล้ว", "fas fa-circle-check", "success"),
    DECLINED("ถูกปฏิเสธการลงนาม", "fas fa-circle-xmark", "danger"),
    CANCELLED("ยกเลิกการเวียนลงนาม", "fas fa-ban", "secondary"),

    /**
     * The frozen content no longer matches what was signed.
     *
     * <p>Reached only when the document is altered behind the envelope's back.
     * Terminal and unrecoverable on purpose: a signature that was given for
     * different content is worthless, and the honest response is to void it and
     * make someone circulate the document again.
     */
    VOIDED("ลายเซ็นเป็นโมฆะ (เอกสารถูกแก้ไข)", "fas fa-triangle-exclamation", "danger"),

    EXPIRED("เลยกำหนดลงนาม", "fas fa-clock", "secondary");

    private final String thaiLabel;
    private final String icon;
    private final String color;

    SignatureRequestStatus(String thaiLabel, String icon, String color) {
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

    /** Whether the envelope can still accept a signature. */
    public boolean isOpen() {
        return this == IN_PROGRESS;
    }

    /** Whether the underlying document should stay locked against edits. */
    public boolean locksDocument() {
        return this == IN_PROGRESS || this == COMPLETED;
    }

    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }
}
