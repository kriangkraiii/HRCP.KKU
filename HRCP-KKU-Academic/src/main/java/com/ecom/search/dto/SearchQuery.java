package com.ecom.search.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What was asked for: the term, the filters, and which page of it.
 *
 * <p>Carries no notion of who is asking. That is {@link SearchPrincipal}, kept
 * separate so it is impossible to build a query object that quietly widens its
 * own visibility — the scoping comes from the authenticated user at the service
 * boundary, never from anything a request parameter can shape.
 *
 * @param q         the normalised search term
 * @param types     entity types to restrict to; empty means all
 * @param statuses  status values to restrict to; empty means all
 * @param from      earliest {@code occurred_at}, or null
 * @param to        latest {@code occurred_at}, or null
 * @param sort      how to order results
 * @param page      zero-based page number
 * @param size      page size, already clamped
 */
public record SearchQuery(
        String q,
        List<String> types,
        List<String> statuses,
        LocalDateTime from,
        LocalDateTime to,
        SearchSort sort,
        int page,
        int size) {

    /** Ordering options offered on the results page. */
    public enum SearchSort {
        RELEVANCE, NEWEST, OLDEST, TITLE
    }

    public SearchQuery {
        types = types == null ? List.of() : List.copyOf(types);
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        sort = sort == null ? SearchSort.RELEVANCE : sort;
    }

    /** A plain term with no filters, for the omnibox. */
    public static SearchQuery of(String q, int size) {
        return new SearchQuery(q, List.of(), List.of(), null, null,
                SearchSort.RELEVANCE, 0, size);
    }

    public boolean hasTypeFilter() {
        return !types.isEmpty();
    }

    public boolean hasStatusFilter() {
        return !statuses.isEmpty();
    }

    public int offset() {
        return Math.max(page, 0) * size;
    }

    /** The same query with the type filter dropped, for counting type facets. */
    public SearchQuery withoutTypeFilter() {
        return new SearchQuery(q, List.of(), statuses, from, to, sort, page, size);
    }

    /** The same query with the status filter dropped, for counting status facets. */
    public SearchQuery withoutStatusFilter() {
        return new SearchQuery(q, types, List.of(), from, to, sort, page, size);
    }
}
