package com.ecom.util;

import java.util.Locale;

/**
 * Intelligent Academic Title & Position Normalizer.
 *
 * <p>Deduces and normalizes Thai academic titles (e.g. ผศ.ดร., รศ.ดร., ศ.ดร., ดร., อาจารย์)
 * and Thai/English academic positions from various sources (web directory sync, SSO, or manual input).
 */
public final class AcademicTitleResolver {

    private AcademicTitleResolver() {}

    /**
     * Resolves the best short Thai title from available candidates.
     *
     * @param candidates array of title/rank strings in order of preference
     * @return normalized short Thai title (e.g. "ผศ.ดร.", "รศ.ดร.", "ศ.ดร.", "อาจารย์", "นาย", etc.)
     */
    public static String resolveShortTitle(String... candidates) {
        if (candidates == null) {
            return null;
        }

        boolean hasDoctorate = false;
        String detectedRank = null;
        String plainPrefix = null;

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String lower = candidate.toLowerCase(Locale.ROOT).replace(" ", "").replace("\t", "");

            if (lower.contains("ดร") || lower.contains("ph.d") || lower.contains("phd") || lower.contains("dr.") || lower.equals("dr")) {
                hasDoctorate = true;
            }

            if (detectedRank == null) {
                if (lower.contains("รองศาสตราจารย์") || lower.contains("รศ.") || lower.contains("รศ")
                        || lower.contains("assoc")) {
                    detectedRank = "รศ.";
                } else if (lower.contains("ผู้ช่วยศาสตราจารย์") || lower.contains("ผศ.") || lower.contains("ผศ")
                        || lower.contains("asst") || lower.contains("assist")) {
                    detectedRank = "ผศ.";
                } else if ((lower.contains("ศาสตราจารย์") || lower.contains("prof"))
                        && !lower.contains("รอง") && !lower.contains("ผู้ช่วย") && !lower.contains("assoc") && !lower.contains("asst")) {
                    detectedRank = "ศ.";
                } else if (lower.contains("อาจารย์") || lower.contains("lecturer") || lower.contains("instructor")) {
                    detectedRank = "อาจารย์";
                }
            }

            if (plainPrefix == null) {
                if (lower.equals("นาย") || lower.equals("mr") || lower.equals("mr.")) plainPrefix = "นาย";
                else if (lower.equals("นาง") || lower.equals("mrs") || lower.equals("mrs.")) plainPrefix = "นาง";
                else if (lower.equals("นางสาว") || lower.equals("น.ส.") || lower.equals("ms") || lower.equals("miss")) plainPrefix = "นางสาว";
            }
        }

        if (detectedRank != null) {
            if (detectedRank.equals("ศ.")) return hasDoctorate ? "ศ.ดร." : "ศ.";
            if (detectedRank.equals("รศ.")) return hasDoctorate ? "รศ.ดร." : "รศ.";
            if (detectedRank.equals("ผศ.")) return hasDoctorate ? "ผศ.ดร." : "ผศ.";
            if (detectedRank.equals("อาจารย์")) return hasDoctorate ? "อ.ดร." : "อาจารย์";
        }

        if (hasDoctorate) {
            return "ดร.";
        }

        if (plainPrefix != null) {
            return plainPrefix;
        }

        // Fallback: First non-blank candidate
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    /**
     * Resolves the official Thai academic position (อาจารย์ / ผู้ช่วยศาสตราจารย์ / รองศาสตราจารย์ / ศาสตราจารย์).
     */
    public static String resolveThaiAcademicPosition(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String raw = candidate.trim().toLowerCase(Locale.ROOT).replace(" ", "");
            if (raw.contains("รองศาสตราจารย์") || raw.contains("รศ.") || raw.contains("รศ") || raw.contains("assoc")) {
                return "รองศาสตราจารย์";
            }
            if (raw.contains("ผู้ช่วยศาสตราจารย์") || raw.contains("ผศ.") || raw.contains("ผศ") || raw.contains("asst") || raw.contains("assist")) {
                return "ผู้ช่วยศาสตราจารย์";
            }
            if ((raw.contains("ศาสตราจารย์") || raw.contains("prof") || raw.equals("ศ.") || raw.equals("ศ"))
                    && !raw.contains("รอง") && !raw.contains("ผู้ช่วย") && !raw.contains("assoc") && !raw.contains("asst")) {
                return "ศาสตราจารย์";
            }
            if (raw.contains("อาจารย์") || raw.contains("lecturer") || raw.contains("instructor") || raw.equals("อ.") || raw.equals("อ")) {
                return "อาจารย์";
            }
        }

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
