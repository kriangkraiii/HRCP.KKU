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
