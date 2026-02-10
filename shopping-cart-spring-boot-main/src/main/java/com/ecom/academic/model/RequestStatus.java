package com.ecom.academic.model;

public enum RequestStatus {
    RECEIVED("รับคำร้อง"),
    SUB_COMMITTEE_APPOINTED("แต่งตั้งอนุกรรมการ"),
    MEETING_SCHEDULED("นัดหมายวันประชุม"),
    COMPLETED_PASS("แจ้งผล - ผ่าน"),
    COMPLETED_REVISE("แจ้งผล - แก้ไข"),
    REJECTED("ไม่รับคำร้อง");

    private final String thaiLabel;

    RequestStatus(String thaiLabel) {
        this.thaiLabel = thaiLabel;
    }

    public String getThaiLabel() {
        return thaiLabel;
    }
}
