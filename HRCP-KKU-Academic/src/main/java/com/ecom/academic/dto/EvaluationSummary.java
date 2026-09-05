package com.ecom.academic.dto;

import java.time.LocalDateTime;

import com.ecom.util.ThaiDateUtil;

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
 * @param courseKey identity for the reuse rule: which course, in which academic
 *                  year. See {@link #courseKey()} for why both halves matter.
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
        Long daysLeft) {

    /**
     * The pair that decides whether a position request may put this evaluation
     * forward: <b>course code + academic year</b>.
     *
     * <p>Not the course alone. A lecturer teaches the same course every year and
     * is evaluated on it again each time; those are separate results, and locking
     * on the code alone would retire a course after its first use and shut the
     * door on every later year's evaluation of it.
     *
     * <p>Not the evaluation's own id either. Two evaluations can name the same
     * course and year — a re-run, a duplicate filed by mistake — and a rule keyed
     * on the row id would let the second one through as if it were a different
     * course.
     *
     * <p>Both halves are normalised before comparing, because both are typed by
     * hand: "CP 123 456" and "cp123456" are one course, and "๒๕๖๘" and "2568" are
     * one year.
     *
     * <p>An evaluation whose document 8 and document 0 both fail to name a course
     * falls back to its own id, so that two such evaluations never collide into a
     * single empty key and lock each other out.
     */
    public String courseKey() {
        String code = normalise(courseCode);
        String year = normalise(academicYear);
        if (code.isEmpty()) {
            return "eval:" + evaluationId;
        }
        return code + "|" + year;
    }

    /**
     * Case, spacing, punctuation and Thai numerals removed.
     *
     * <p>Unicode-aware on purpose. Java's {@code \p{Alnum}} is ASCII-only, so a
     * course code or year written in Thai script would be stripped to nothing —
     * and an emptied code does not fail loudly, it falls through to the
     * {@code eval:id} branch and quietly stops locking anything.
     */
    private static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        return ThaiDateUtil.toArabicDigits(raw)
                .replaceAll("(?U)[^\\p{Alnum}]", "")
                .toUpperCase();
    }

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
