package com.ecom.academic.dto;

import java.time.LocalDateTime;

import com.ecom.academic.model.AcademicRank;

/**
 * What one teaching evaluation says, flattened into the handful of facts the
 * position flow actually needs.
 *
 * <p>The facts live scattered across two of the evaluation's documents —
 * document 0, where the applicant names the course and the academic year, and
 * document 8, the notification of the result — and three screens plus the
 * auto-fill engine all want the same subset. Reading the JSON in each of those
 * places would mean four copies of "which key holds the course code", so it is
 * assembled once by {@code AcademicRequestService.summarize}.
 *
 * @param currentPosition the applicant's position as written on document 1 when
 *                        they asked to be evaluated — the first document filled,
 *                        which the rank rule trusts over the profile
 * @param targetRank      the position this evaluation was for (document 1's
 *                        {@code chk1}/{@code chk2}); a position request built on it
 *                        may ask for this position only. Null when document 1
 *                        ticks nothing.
 */
public record EvaluationSummary(
        Long evaluationId,
        String requestCode,
        String courseCode,
        String courseName,
        String academicYear,
        String semester,
        String resultLevel,
        String evaluationDate,
        String expiryDate,
        LocalDateTime expiryAt,
        Long daysLeft,
        String currentPosition,
        AcademicRank targetRank) {

    /** "CP001101 — Introduction to CS", or whichever half is present. */
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
