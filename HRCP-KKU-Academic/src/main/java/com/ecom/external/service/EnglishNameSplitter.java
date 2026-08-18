package com.ecom.external.service;

import java.util.Locale;
import java.util.Set;

/**
 * Splits the single English name string the upstream directory provides into a
 * first and last name.
 *
 * <p>
 * The FS directory returns English names as one combined {@code name_en} field
 * ("Somchai Jaidee") — there is no separate given/family name upstream. Every
 * local screen that wants them apart has to derive them, so the derivation
 * lives here rather than being re-invented per caller.
 *
 * <p>
 * <b>This is a heuristic and it will be wrong for some people.</b> A name is not
 * reliably parseable: multi-word family names, patronymics, and people who write
 * family name first all defeat any rule you pick. The split is a starting value
 * that an administrator can correct by hand, not an authoritative record. Where
 * the exact strings matter, prefer the untouched {@code name_en} the directory
 * gave us.
 *
 * <p>
 * Deliberate choices:
 * <ul>
 * <li>Everything after the first token becomes the last name. Dropping a middle
 * name would silently lose part of someone's name; keeping it attached is
 * recoverable.</li>
 * <li>Leading titles are stripped, because some upstream records prepend
 * {@code prefix_position_en} into {@code name_en} ("Asst. Prof. Somchai
 * Jaidee").</li>
 * <li>A comma is treated as "family name first" ("Jaidee, Somchai"), the one
 * convention common enough to be worth honouring.</li>
 * </ul>
 */
public final class EnglishNameSplitter {

    /**
     * The split result. Either component may be {@code null} when the input did
     * not carry it; callers should treat {@code null} as "unknown", not "empty".
     */
    public record Parts(String firstName, String lastName) {

        private static final Parts EMPTY = new Parts(null, null);
    }

    /**
     * Titles that turn up glued to the front of {@code name_en}. Compared after
     * lower-casing and after stripping a trailing dot, so "Prof." and "prof"
     * both match.
     */
    private static final Set<String> LEADING_TITLES = Set.of(
            "dr", "mr", "mrs", "ms", "miss",
            "prof", "professor",
            "asst", "assist", "assistant",
            "assoc", "associate",
            "lecturer");

    private EnglishNameSplitter() {
        // utility
    }

    /**
     * @param nameEn the upstream combined English name; may be {@code null}
     * @return the derived parts, never {@code null} itself
     */
    public static Parts split(String nameEn) {
        if (nameEn == null || nameEn.isBlank()) {
            return Parts.EMPTY;
        }

        String cleaned = nameEn.trim().replaceAll("\\s+", " ");

        // "Jaidee, Somchai" — family name first. Handled before title stripping
        // so that a title on either side still gets removed below.
        int comma = cleaned.indexOf(',');
        if (comma >= 0) {
            String beforeComma = stripLeadingTitles(cleaned.substring(0, comma).trim());
            String afterComma = stripLeadingTitles(cleaned.substring(comma + 1).trim());
            if (!beforeComma.isEmpty() && !afterComma.isEmpty()) {
                return new Parts(afterComma, beforeComma);
            }
            // A dangling comma tells us nothing; fall through and treat the
            // remainder as an ordinary name.
            cleaned = (beforeComma + " " + afterComma).trim();
        }

        cleaned = stripLeadingTitles(cleaned);
        if (cleaned.isEmpty()) {
            return Parts.EMPTY;
        }

        String[] tokens = cleaned.split(" ");
        if (tokens.length == 1) {
            return new Parts(tokens[0], null);
        }

        String first = tokens[0];
        String last = String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
        return new Parts(first, last);
    }

    /**
     * Removes any run of leading titles. Loops because they stack —
     * "Asst. Prof. Dr. Somchai" carries three.
     */
    private static String stripLeadingTitles(String value) {
        String remaining = value;
        while (true) {
            int space = remaining.indexOf(' ');
            if (space < 0) {
                // Never strip the final token: a lone "Prof" is more likely to be
                // someone's actual name than a title with nothing to qualify.
                return remaining;
            }
            String head = remaining.substring(0, space);
            String normalized = head.toLowerCase(Locale.ROOT).replace(".", "");
            if (!LEADING_TITLES.contains(normalized)) {
                return remaining;
            }
            remaining = remaining.substring(space + 1).trim();
        }
    }
}
