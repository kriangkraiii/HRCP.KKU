package com.ecom.academic.model;

/**
 * How a stored signature was produced.
 *
 * <p>All three end up as the same artifact — a transparent PNG — so nothing
 * downstream has to branch on this. It is kept because it is part of the
 * evidence record: "typed their name" and "drew their signature" are different
 * acts, and an audit years later should be able to tell them apart.
 */
public enum SignatureKind {

    DRAW("วาด", "fas fa-pen-nib"),
    UPLOAD("อัปโหลดรูปภาพ", "fas fa-image"),
    TYPE("ข้อความ", "fas fa-keyboard");

    private final String thaiLabel;
    private final String iconClass;

    SignatureKind(String thaiLabel, String iconClass) {
        this.thaiLabel = thaiLabel;
        this.iconClass = iconClass;
    }

    public String getThaiLabel() {
        return thaiLabel;
    }

    public String getIconClass() {
        return iconClass;
    }

    /** Parses a client-supplied value, defaulting to {@link #DRAW} when unrecognised. */
    public static SignatureKind fromValue(String value) {
        if (value == null) {
            return DRAW;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DRAW;
        }
    }
}
