package com.ecom.util;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component("thaiDateUtil")
public class ThaiDateUtil {

    /** Thai digits ๐-๙, in the order they sit in Unicode. */
    private static final String THAI_DIGITS = "๐๑๒๓๔๕๖๗๘๙";

    /**
     * Rewrites Thai digits as Arabic ones, leaving everything else alone.
     *
     * <p>Documents in this system are typed by hand and the same year is written
     * both ways — "๒๕๖๘" on one form, "2568" on the next. Anything that compares
     * or parses those strings has to agree on one shape first, so there is one
     * converter here rather than a copy beside each caller.
     *
     * @return the converted text, or null when given null
     */
    public static String toArabicDigits(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int thai = THAI_DIGITS.indexOf(c);
            out.append(thai >= 0 ? (char) ('0' + thai) : c);
        }
        return out.toString();
    }

    /**
     * Rewrites Arabic digits as Thai ones, leaving everything else alone.
     *
     * <p>The inverse of {@link #toArabicDigits(String)}, and needed for the same
     * reason read in the other direction: several of the {@code .docx} templates
     * are typeset entirely in Thai numerals, so a value filled into one of them
     * has to match or the page reads as two documents stitched together.
     *
     * @return the converted text, or null when given null
     */
    public static String toThaiDigits(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= '0' && c <= '9' ? THAI_DIGITS.charAt(c - '0') : c);
        }
        return out.toString();
    }

    private static final String[] THAI_MONTHS = { "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม",
            "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม" };
    private static final String[] THAI_WEEKDAYS = { "จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์",
            "อาทิตย์" };

    /** LocalDate → "วันจันทร์ที่ 5 ตุลาคม 2569" แบบที่เขียนในหนังสือนัดประชุม */
    public static String fullDate(LocalDate d) {
        if (d == null) return "-";
        return "วัน" + THAI_WEEKDAYS[d.getDayOfWeek().getValue() - 1] + "ที่ " + d.getDayOfMonth() + " "
                + THAI_MONTHS[d.getMonthValue() - 1] + " " + (d.getYear() + 543);
    }

    /** LocalDateTime → "dd/MM/2569 HH:mm" */
    public String format(LocalDateTime dt) {
        if (dt == null) return "-";
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/")) + (dt.getYear() + 543)
             + dt.format(DateTimeFormatter.ofPattern(" HH:mm"));
    }

    /** LocalDateTime → "dd/MM/2569 HH:mm:ss" */
    public String formatWithSeconds(LocalDateTime dt) {
        if (dt == null) return "-";
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/")) + (dt.getYear() + 543)
             + dt.format(DateTimeFormatter.ofPattern(" HH:mm:ss"));
    }

    /** LocalDateTime → "dd/MM/2569" (date only) */
    public String formatDateOnly(LocalDateTime dt) {
        if (dt == null) return "-";
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/")) + (dt.getYear() + 543);
    }

    /** LocalDate → "dd/MM/2569" */
    public String formatDate(LocalDate d) {
        if (d == null) return "-";
        return d.format(DateTimeFormatter.ofPattern("dd/MM/")) + (d.getYear() + 543);
    }
}
