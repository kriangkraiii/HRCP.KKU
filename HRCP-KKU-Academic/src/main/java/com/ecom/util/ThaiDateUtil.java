package com.ecom.util;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component("thaiDateUtil")
public class ThaiDateUtil {

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
