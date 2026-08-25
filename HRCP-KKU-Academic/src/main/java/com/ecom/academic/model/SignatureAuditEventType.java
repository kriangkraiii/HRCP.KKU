package com.ecom.academic.model;

/**
 * What happened to a signing envelope.
 *
 * <p>The audit trail is the evidence that a signature was given knowingly:
 * under พ.ร.บ.ว่าด้วยธุรกรรมทางอิเล็กทรอนิกส์ ม.9 the method has to be
 * "เชื่อถือได้ตามสมควรแก่พฤติการณ์", and a record of who was asked, when they
 * opened the document, and when they consented is what makes that arguable
 * later. Events are only ever appended.
 */
public enum SignatureAuditEventType {

    CREATED("สร้างคำขอลงนาม", "fas fa-plus"),
    NOTIFIED("แจ้งเตือนผู้ลงนาม", "fas fa-bell"),
    REMINDED("เตือนซ้ำ", "fas fa-bell"),
    VIEWED("ผู้ลงนามเปิดดูเอกสาร", "fas fa-eye"),
    SIGNED("ลงนาม", "fas fa-signature"),
    DECLINED("ปฏิเสธการลงนาม", "fas fa-circle-xmark"),
    CANCELLED("ยกเลิกการเวียน", "fas fa-ban"),
    FORWARDED("ส่งเวียนลงนามต่อ", "fas fa-share"),
    EXTENSION_REQUESTED("ขอขยายเวลาลงนาม", "fas fa-clock-rotate-left"),
    DUE_EXTENDED("ขยายกำหนดเวลาลงนาม", "fas fa-calendar-plus"),
    COMPLETED("ลงนามครบทุกคน", "fas fa-circle-check"),
    VOIDED("ลายเซ็นเป็นโมฆะ", "fas fa-triangle-exclamation"),
    EXPIRED("เลยกำหนด", "fas fa-clock");

    private final String thaiLabel;
    private final String icon;

    SignatureAuditEventType(String thaiLabel, String icon) {
        this.thaiLabel = thaiLabel;
        this.icon = icon;
    }

    public String getThaiLabel() {
        return thaiLabel;
    }

    public String getIcon() {
        return icon;
    }
}
