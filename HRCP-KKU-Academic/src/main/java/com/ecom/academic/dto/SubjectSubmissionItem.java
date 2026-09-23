package com.ecom.academic.dto;

/**
 * Teaching evaluation subject submission item for yearly analytics.
 */
public record SubjectSubmissionItem(
        Long requestId,
        String requestCode,
        String courseCode,
        String courseName,
        String academicYear,
        String semester,
        String applicantName,
        String statusLabel,
        String statusColor,
        String passDate,
        String expiryDate,
        String resultLevel) {

    public String courseLabel() {
        boolean hasCode = courseCode != null && !courseCode.isBlank();
        boolean hasName = courseName != null && !courseName.isBlank();
        if (hasCode && hasName) {
            return courseCode + " — " + courseName;
        }
        if (hasCode) {
            return courseCode;
        }
        return hasName ? courseName : "ไม่ระบุรายวิชา";
    }
}
