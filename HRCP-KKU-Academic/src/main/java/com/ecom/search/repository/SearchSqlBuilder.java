package com.ecom.search.repository;

import java.util.ArrayList;
import java.util.List;

import com.ecom.search.dto.SearchQuery;

/**
 * Builds the search SQL, in the two dialects this application runs on.
 *
 * <p><b>Why one builder and not two repositories.</b> The visibility predicate
 * is a security control. Written twice — once for PostgreSQL, once for H2 — the
 * two copies drift, and the scoping test that runs on H2 then proves something
 * about code production never executes. Here the scope clause, the filters, the
 * grouping and the paging are written once; only three fragments differ, and
 * each is a scoring term that changes ranking rather than who can see what.
 *
 * <p>It is a pure string builder with no Spring dependencies, so the ranking
 * test can drive it against a real PostgreSQL with nothing but a JDBC
 * connection.
 */
public class SearchSqlBuilder {

    /** Which dialect to emit. */
    public enum Flavor {
        /** Production. Trigram similarity and full-text ranking available. */
        POSTGRES,
        /** H2 in the test suite. No pg_trgm, no tsvector; those terms score zero. */
        PORTABLE
    }

    private final Flavor flavor;

    public SearchSqlBuilder(Flavor flavor) {
        this.flavor = flavor;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    // ------------------------------------------------------------------
    // The three fragments that differ
    // ------------------------------------------------------------------

    /**
     * The concatenated, lower-cased text to match against.
     *
     * <p>On PostgreSQL this is the stored generated column, which is what the
     * GIN trigram index is built on — matching against anything else would
     * silently disable the index. H2 has no generated column, so it rebuilds
     * the same expression inline.
     */
    String haystack() {
        return flavor == Flavor.POSTGRES
                ? "d.search_text"
                : "lower(coalesce(d.title,'') || ' ' || coalesce(d.keywords,'') || ' ' "
                        + "|| coalesce(d.subtitle,'') || ' ' || coalesce(d.body,''))";
    }

    /**
     * Trigram similarity of the query against the best matching extent of the
     * text.
     *
     * <p>{@code word_similarity}, never {@code similarity}: the latter
     * normalises over the union of both trigram sets, so a short query against a
     * multi-kilobyte body scores near zero however well it matches. Measured on
     * real data, "ประเมิน" against "คำร้องขอประเมินผลการสอน" scores 0.625 one way
     * and 0.185 the other.
     */
    private String similarity(String target) {
        return flavor == Flavor.POSTGRES
                ? "word_similarity(:q, " + target + ")"
                : "0.0";
    }

    private String lexicalRank() {
        return flavor == Flavor.POSTGRES
                ? "ts_rank_cd(d.tsv, plainto_tsquery('simple', :q))"
                : "0.0";
    }

    /**
     * Bounded recency bonus, reciprocal in months.
     *
     * <p>Bounded on purpose: a recent but irrelevant row must never outrank an
     * older exact match, so this contributes at most 0.4 against a title hit's
     * 3.0.
     */
    private String recency() {
        return flavor == Flavor.POSTGRES
                ? "0.4 / (1 + extract(epoch FROM (now() - coalesce(d.occurred_at, d.indexed_at))) / 2592000.0)"
                : "0.0";
    }

    // ------------------------------------------------------------------
    // Shared: the parts that must never differ between dialects
    // ------------------------------------------------------------------

    /**
     * The entire authorisation check.
     *
     * <p>Deliberately part of the same statement as the matching, so there is no
     * arrangement of filters, sorts or pages that can return a row the caller
     * may not see. {@code isAdmin} binds as an integer rather than a boolean
     * because H2 and PostgreSQL disagree about boolean parameters inside
     * {@code CASE}.
     */
    public String scopeClause() {
        return """
                d.is_deleted = FALSE
                  AND ( d.visibility = 'PUBLIC'
                     OR (d.owner_user_id = :userId AND d.visibility IN ('OWNER','OWNER_OR_ADMIN'))
                     OR (:isAdmin = 1 AND d.visibility IN ('ADMIN','OWNER_OR_ADMIN')) )""";
    }

    /**
     * Stage one: a substring hit, or a full-text hit for the English in mixed
     * text. Both are index-backed, so the planner can bitmap-or them.
     */
    private String matchClause() {
        if (flavor == Flavor.POSTGRES) {
            return "(" + haystack() + " LIKE :pattern"
                    + " OR d.tsv @@ plainto_tsquery('simple', :q))";
        }
        return "(" + haystack() + " LIKE :pattern)";
    }

    /**
     * Stage two, run only when stage one found nothing.
     *
     * <p>Kept out of the main {@code OR} on purpose. Only the {@code <%}
     * operator can use the trigram index, and it reads its cut-off from a
     * session GUC rather than an argument — which would mean a {@code SET LOCAL}
     * on every request. The function form takes a threshold but cannot use the
     * index, and one unindexable branch inside an {@code OR} forces a sequential
     * scan over the whole table. Running it only on a miss keeps the common path
     * fully indexed and turns the fallback into a "did you mean" for free.
     */
    private String fuzzyClause() {
        return flavor == Flavor.POSTGRES
                // Titles and identifiers only — never the body. Measured on
                // 50 000 rows: against the whole document this predicate takes
                // 4.5 s, against title + keywords 342 ms. The index cannot help
                // either way (the function form is not indexable, and the
                // operator form was planned as a sequential scan too), so the
                // only lever is how much text it reads.
                //
                // It is also the better answer, not just the faster one: typo
                // tolerance belongs on names, titles and request codes. Nobody
                // types a misspelled fragment of form filler and expects a hit.
                ? "word_similarity(:q, lower(coalesce(d.title,'') || ' ' "
                        + "|| coalesce(d.keywords,''))) >= :threshold"
                // H2 has no trigram support, so there is no second chance there.
                : "1 = 0";
    }

    /**
     * Relevance.
     *
     * <p>Field importance is in the coefficients; the importance of a whole
     * category is in {@code d.weight}, which is a column — so tuning
     * notifications down or requests up is a data change, not an edit here.
     *
     * <p><b>Every term here is paid for on each matching row, not just the ones
     * returned</b> — the sort needs all of them. A term that is cheap on one row
     * is not cheap on fifty thousand. Measured at that size, on a query broad
     * enough to match nearly everything:
     *
     * <pre>
     *   with word_similarity over the whole document   5 477 ms
     *   without it                                       805 ms
     *   without ts_rank_cd as well                       367 ms
     *   a specific query, full scoring                      3 ms
     * </pre>
     *
     * <p>So similarity is computed against the title alone. Running it over the
     * concatenated document cost 4.7 seconds and bought almost nothing: a body
     * that matches already scores through the {@code LIKE} term on the first
     * line. {@code ts_rank_cd} stays — it is what orders English results
     * sensibly, and 440 ms in the worst case, at ten times the real corpus, is a
     * fair price for it.
     */
    private String scoreExpression() {
        return """
                ( 1.0 * (CASE WHEN %s LIKE :pattern THEN 1 ELSE 0 END)
                + 3.0 * (CASE WHEN lower(d.title) = :q THEN 1 ELSE 0 END)
                + 2.0 * (CASE WHEN lower(d.title) LIKE :prefixPattern THEN 1 ELSE 0 END)
                + 1.5 * %s
                + 1.2 * (CASE WHEN lower(coalesce(d.keywords,'')) LIKE :pattern THEN 1 ELSE 0 END)
                + 0.8 * %s
                + %s
                ) * d.weight""".formatted(
                haystack(),
                similarity("lower(coalesce(d.title,''))"),
                lexicalRank(),
                recency());
    }

    /**
     * Applicants and administrators reach the same record by different routes,
     * so the row carries both and the caller's role picks one.
     */
    private String urlExpression() {
        return "CASE WHEN :isAdmin = 1 AND d.admin_url IS NOT NULL THEN d.admin_url ELSE d.url END";
    }

    // ------------------------------------------------------------------
    // Statements
    // ------------------------------------------------------------------

    /** One page of ranked hits. */
    public String hits(SearchQuery query, boolean fuzzy) {
        return """
                SELECT d.id, d.entity_type, d.entity_id, d.title, d.subtitle, d.body,
                       d.category, d.icon, d.badge, d.badge_class, d.is_external,
                       d.status, d.occurred_at,
                       %s AS target_url,
                       %s AS score
                FROM search_document d
                WHERE %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset"""
                .formatted(urlExpression(), scoreExpression(), whereBody(query, fuzzy),
                        orderBy(query));
    }

    /** How many hits there are in total, for the pager. */
    public String count(SearchQuery query, boolean fuzzy) {
        return "SELECT count(*) FROM search_document d WHERE " + whereBody(query, fuzzy);
    }

    /**
     * Counts per entity type.
     *
     * <p>The type filter is dropped from this one so the facet list keeps
     * showing the other types after one is ticked — otherwise selecting a facet
     * hides every alternative and there is no way back except the reset button.
     */
    public String typeFacets(SearchQuery query, boolean fuzzy) {
        SearchQuery withoutTypes = query.withoutTypeFilter();
        return "SELECT d.entity_type AS facet_key, count(*) AS facet_count "
                + "FROM search_document d WHERE " + whereBody(withoutTypes, fuzzy)
                + " GROUP BY d.entity_type ORDER BY facet_count DESC";
    }

    /** Counts per status, with the status filter dropped for the same reason. */
    public String statusFacets(SearchQuery query, boolean fuzzy) {
        SearchQuery withoutStatuses = query.withoutStatusFilter();
        return "SELECT d.status AS facet_key, count(*) AS facet_count "
                + "FROM search_document d WHERE " + whereBody(withoutStatuses, fuzzy)
                + " AND d.status IS NOT NULL GROUP BY d.status ORDER BY facet_count DESC";
    }

    /**
     * Ordering, always ending in the row id.
     *
     * <p>The id tiebreak is not decoration. Page two is a separate statement, so
     * without a total order rows with equal scores can appear on both pages or
     * on neither — the classic way a paged result set loses records without
     * anyone noticing.
     *
     * <p>The sort value comes from an enum, never from the request string, so
     * this cannot become an injection point however the parameter arrives.
     */
    private String orderBy(SearchQuery query) {
        return switch (query.sort()) {
            case NEWEST -> "d.occurred_at DESC NULLS LAST, d.id DESC";
            case OLDEST -> "d.occurred_at ASC NULLS LAST, d.id ASC";
            case TITLE -> "lower(d.title) ASC, d.id ASC";
            case RELEVANCE -> "score DESC, d.occurred_at DESC NULLS LAST, d.id DESC";
        };
    }

    /**
     * Scope, then match, then the user's filters.
     *
     * <p>Filter clauses are appended only when they are actually set. The
     * alternative idiom — {@code (:types IS NULL OR ...)} — cannot express an
     * empty {@code IN} list, and an empty list in a bound {@code IN} is a syntax
     * error rather than a no-op.
     */
    private String whereBody(SearchQuery query, boolean fuzzy) {
        List<String> clauses = new ArrayList<>();
        clauses.add(scopeClause());
        clauses.add(fuzzy ? fuzzyClause() : matchClause());

        if (query.hasTypeFilter()) {
            clauses.add("d.entity_type IN (:types)");
        }
        if (query.hasStatusFilter()) {
            clauses.add("d.status IN (:statuses)");
        }
        if (query.from() != null) {
            clauses.add("d.occurred_at >= :from");
        }
        if (query.to() != null) {
            clauses.add("d.occurred_at <= :to");
        }
        return String.join("\n  AND ", clauses);
    }
}
