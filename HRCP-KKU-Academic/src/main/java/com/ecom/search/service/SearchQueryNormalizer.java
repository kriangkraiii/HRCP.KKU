package com.ecom.search.service;

/**
 * Turns what somebody typed into the shapes the search queries need.
 *
 * <p>Two rules live here rather than in each caller, because getting either
 * wrong is invisible until someone hits it in production.
 *
 * <p><b>Patterns are built in Java, never in the query.</b> Writing
 * {@code CONCAT('%', :q, '%')} in JPQL looks tidier and is a trap on
 * PostgreSQL: with no search term the parameter is null, both operands of
 * {@code ||} are then untyped, and the server resolves the concatenation to
 * {@code bytea}, failing with <em>"function lower(bytea) does not exist"</em>
 * the moment anyone opens the screen without typing anything. The same note is
 * on {@code ScopusPublicationRepository}, which learned it the hard way.
 *
 * <p><b>A one-character query is not a query.</b> Thai has no word boundaries,
 * so a single character is a substring of an enormous share of the corpus —
 * {@code ศ} alone appears in ศาสตราจารย์, เอกสาร, ประกาศ and การศึกษา. Callers
 * get {@code null} back and are expected to return no results rather than
 * everything.
 */
public final class SearchQueryNormalizer {

    /**
     * Shortest query worth running. Two, not three: real Thai words are two
     * characters long ({@code ผศ}, {@code มข}), so three would refuse queries
     * people legitimately type.
     */
    public static final int MIN_QUERY_LENGTH = 2;

    private SearchQueryNormalizer() {
    }

    /**
     * Trims, lower-cases and collapses runs of whitespace.
     *
     * @return the cleaned term, or null when there is nothing worth searching for
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("\\s+", " ").toLowerCase();
        return cleaned.length() < MIN_QUERY_LENGTH ? null : cleaned;
    }

    /**
     * Builds a {@code %term%} pattern, already lower-cased so the column side is
     * the only thing the database has to fold.
     *
     * <p>{@code %}, {@code _} and {@code \} are escaped with a backslash, which
     * is the default LIKE escape character on both PostgreSQL and H2. Without
     * this, someone typing {@code %} matches every row and someone typing
     * {@code _} matches any character — surprising, and it turns a search box
     * into an accidental "select all".
     *
     * @return the pattern, or null when the term is too short to search on
     */
    public static String likePattern(String raw) {
        String cleaned = normalize(raw);
        if (cleaned == null) {
            return null;
        }
        return "%" + escapeLikeMetacharacters(cleaned) + "%";
    }

    /**
     * Builds a {@code term%} prefix pattern, for ranking an entry that starts
     * with what was typed above one that merely contains it.
     *
     * @return the pattern, or null when the term is too short to search on
     */
    public static String prefixPattern(String raw) {
        String cleaned = normalize(raw);
        if (cleaned == null) {
            return null;
        }
        return escapeLikeMetacharacters(cleaned) + "%";
    }

    private static String escapeLikeMetacharacters(String term) {
        return term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
