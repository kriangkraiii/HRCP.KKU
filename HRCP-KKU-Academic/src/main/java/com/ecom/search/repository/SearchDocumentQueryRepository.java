package com.ecom.search.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ecom.search.dto.SearchFacet;
import com.ecom.search.dto.SearchHit;
import com.ecom.search.dto.SearchPrincipal;
import com.ecom.search.dto.SearchQuery;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.service.SearchQueryNormalizer;

/**
 * Runs the search.
 *
 * <p>Raw SQL rather than JPQL because the matching and ranking need trigram and
 * full-text operators that JPA cannot express, and because loading entities to
 * render a list of links would be waste — the index row already holds everything
 * a result needs.
 *
 * <p>One class for both dialects; see {@link SearchSqlBuilder} for why that
 * matters.
 */
public class SearchDocumentQueryRepository {

    /**
     * How close a fuzzy match has to be to count.
     *
     * <p>0.45 sits below a one-character Thai typo, which measures 0.5 against a
     * realistic phrase, and above the noise floor of unrelated text.
     */
    private static final double FUZZY_THRESHOLD = 0.45;

    private final NamedParameterJdbcTemplate jdbc;
    private final SearchSqlBuilder sql;

    public SearchDocumentQueryRepository(NamedParameterJdbcTemplate jdbc, SearchSqlBuilder sql) {
        this.jdbc = jdbc;
        this.sql = sql;
    }

    public SearchSqlBuilder.Flavor flavor() {
        return sql.getFlavor();
    }

    public List<SearchHit> hits(SearchQuery query, SearchPrincipal principal, boolean fuzzy) {
        return jdbc.query(sql.hits(query, fuzzy), params(query, principal, fuzzy),
                (rs, rowNum) -> toHit(rs, query.q()));
    }

    public long count(SearchQuery query, SearchPrincipal principal, boolean fuzzy) {
        Long total = jdbc.queryForObject(sql.count(query, fuzzy),
                params(query, principal, fuzzy), Long.class);
        return total == null ? 0 : total;
    }

    public List<SearchFacet> typeFacets(SearchQuery query, SearchPrincipal principal, boolean fuzzy) {
        return jdbc.query(sql.typeFacets(query, fuzzy),
                params(query.withoutTypeFilter(), principal, fuzzy),
                (rs, rowNum) -> {
                    String key = rs.getString("facet_key");
                    return new SearchFacet(key, typeLabel(key), rs.getLong("facet_count"),
                            query.types().contains(key));
                });
    }

    public List<SearchFacet> statusFacets(SearchQuery query, SearchPrincipal principal, boolean fuzzy) {
        return jdbc.query(sql.statusFacets(query, fuzzy),
                params(query.withoutStatusFilter(), principal, fuzzy),
                (rs, rowNum) -> {
                    String key = rs.getString("facet_key");
                    return new SearchFacet(key, key, rs.getLong("facet_count"),
                            query.statuses().contains(key));
                });
    }

    /**
     * Binds everything either flavour might reference.
     *
     * <p>Extra entries are harmless — the template binds only the names that
     * appear in the statement — so one parameter source serves both dialects and
     * every statement.
     */
    private MapSqlParameterSource params(SearchQuery query, SearchPrincipal principal, boolean fuzzy) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("q", query.q())
                .addValue("pattern", SearchQueryNormalizer.likePattern(query.q()))
                .addValue("prefixPattern", SearchQueryNormalizer.prefixPattern(query.q()))
                .addValue("userId", principal.userId())
                .addValue("isAdmin", principal.adminFlag())
                .addValue("threshold", FUZZY_THRESHOLD)
                .addValue("limit", query.size())
                .addValue("offset", query.offset());

        if (query.hasTypeFilter()) {
            p.addValue("types", query.types());
        }
        if (query.hasStatusFilter()) {
            p.addValue("statuses", query.statuses());
        }
        if (query.from() != null) {
            p.addValue("from", query.from());
        }
        if (query.to() != null) {
            p.addValue("to", query.to());
        }
        return p;
    }

    private static SearchHit toHit(ResultSet rs, String term) throws SQLException {
        var occurredAt = rs.getTimestamp("occurred_at");
        return new SearchHit(
                rs.getString("category"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("target_url"),
                rs.getString("icon"),
                rs.getString("badge"),
                rs.getString("badge_class"),
                rs.getString("entity_type"),
                rs.getLong("entity_id"),
                snippet(rs.getString("body"), term),
                rs.getBoolean("is_external"),
                occurredAt == null ? null : occurredAt.toLocalDateTime(),
                rs.getDouble("score"));
    }

    /**
     * A window of the body around the first match.
     *
     * <p>Built in Java rather than with {@code ts_headline}: that function is
     * PostgreSQL-only and, on Thai, useless — the parser sees a whole phrase as
     * one token, so it would return the entire field or nothing.
     */
    private static String snippet(String body, String term) {
        if (body == null || body.isBlank() || term == null) {
            return null;
        }
        int at = body.toLowerCase().indexOf(term.toLowerCase());
        if (at < 0) {
            return body.length() <= 120 ? body : body.substring(0, 120) + "…";
        }
        int start = Math.max(0, at - 60);
        int end = Math.min(body.length(), at + term.length() + 60);
        return (start > 0 ? "…" : "") + body.substring(start, end) + (end < body.length() ? "…" : "");
    }

    /** Thai label for a facet key, falling back to the raw value. */
    private static String typeLabel(String entityTypeName) {
        try {
            return SearchEntityType.valueOf(entityTypeName).getCategory();
        } catch (IllegalArgumentException e) {
            // A type removed from the enum but still present in old rows.
            return entityTypeName;
        }
    }

    /** Exposed for the ranking test, which drives the builder directly. */
    public static List<String> columnNames() {
        return new ArrayList<>(List.of("entity_type", "entity_id", "title", "subtitle", "body",
                "category", "icon", "badge", "badge_class", "is_external", "status",
                "occurred_at", "target_url", "score"));
    }
}
