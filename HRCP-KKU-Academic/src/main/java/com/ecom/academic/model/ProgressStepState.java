package com.ecom.academic.model;

/**
 * สถานะของแต่ละขั้นบนแถบความคืบหน้า — ให้หน้าผู้ยื่น แดชบอร์ด และหน้าแอดมิน
 * ใช้กติกาเดียวกัน แทนการคำนวณซ้ำในแต่ละเทมเพลต
 *
 * <p>{@code timelineClass} ใช้กับ {@code .timeline-v2} (หน้าผู้ยื่น)
 * ส่วน {@code detailClass} ใช้กับ {@code .detail-progress-steps} (หน้าแอดมิน)
 */
public enum ProgressStepState {

    /** ยังไม่ถึง */
    PENDING("", "", null),

    /** ผ่านไปแล้ว */
    DONE("t-completed", "dp-done", null),

    /** ขั้นที่กำลังดำเนินการ */
    CURRENT("t-active", "dp-active", null),

    /** แจ้งผล: ให้แก้ไข */
    REVISE("t-revise", "dp-revise", "fa-circle-exclamation"),

    /** แจ้งผล: ไม่ผ่าน */
    REJECTED("t-rejected", "dp-rejected", "fa-circle-xmark"),

    /** ขั้นสุดท้าย เมื่อกระบวนการจบแล้ว */
    FINAL("t-final", "dp-done dp-final", null);

    private final String timelineClass;
    private final String detailClass;
    private final String outcomeIcon;

    ProgressStepState(String timelineClass, String detailClass, String outcomeIcon) {
        this.timelineClass = timelineClass;
        this.detailClass = detailClass;
        this.outcomeIcon = outcomeIcon;
    }

    public String getTimelineClass() {
        return timelineClass;
    }

    public String getDetailClass() {
        return detailClass;
    }

    /** ไอคอนของขั้นนี้ — ผลแก้ไข/ไม่ผ่านแทนไอคอนของขั้น, ขั้นที่ยังไม่ถึงไม่มีไอคอน */
    public String iconFor(RequestStatus step) {
        if (this == PENDING) {
            return null;
        }
        return outcomeIcon != null ? outcomeIcon : step.getIcon();
    }

    /** เส้นที่ต่อไปยังขั้นถัดไปเป็นสีเขียวหรือไม่ */
    public boolean isReached() {
        return this == DONE || this == FINAL;
    }
}
