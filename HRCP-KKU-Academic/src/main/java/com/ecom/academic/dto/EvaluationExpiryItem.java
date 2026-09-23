package com.ecom.academic.dto;

import java.time.LocalDateTime;

/**
 * Teaching evaluation expiry status item for the admin dashboard analytics.
 * Tracks validity, days left, and whether the evaluation has been used in a position request.
 */
public record EvaluationExpiryItem(
        Long requestId,
        String requestCode,
        String applicantName,
        String courseCode,
        String courseName,
        String academicYear,
        String semester,
        String evaluationDate,
        String expiryDateFormatted,
        LocalDateTime expiryAt,
        Long daysLeft,
        String expiryCategory, // "EXPIRED", "EXPIRING_SOON_30", "EXPIRING_SOON_90", "EXPIRING_SOON_180", "ACTIVE"
        boolean hasPositionRequest,
        String targetRank,
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

    public boolean isExpired() {
        return daysLeft != null && daysLeft < 0;
    }

    public boolean isExpiringWithin90Days() {
        return daysLeft != null && daysLeft >= 0 && daysLeft <= 90;
    }

    public boolean isExpiringWithin30Days() {
        return daysLeft != null && daysLeft >= 0 && daysLeft <= 30;
    }
}
