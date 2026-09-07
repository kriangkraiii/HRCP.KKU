package com.ecom.search.dto;

import java.util.List;

/**
 * A page of results, plus what the page around it needs to render.
 *
 * @param query        the term as typed
 * @param hits         this page of results
 * @param total        how many hits there are in all
 * @param page         zero-based page number
 * @param size         page size used
 * @param typeFacets   counts per entity type
 * @param statusFacets counts per status
 * @param tookMs       how long the search took, for the results header
 * @param approximate  true when nothing matched exactly and these are the
 *                     typo-tolerant fallback — the page says so rather than
 *                     presenting near-misses as if they were hits
 */
public record SearchResponse(
        String query,
        List<SearchHit> hits,
        long total,
        int page,
        int size,
        List<SearchFacet> typeFacets,
        List<SearchFacet> statusFacets,
        long tookMs,
        boolean approximate) {

    public static SearchResponse empty(String query) {
        return new SearchResponse(query, List.of(), 0, 0, 0, List.of(), List.of(), 0, false);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    public boolean hasMore() {
        return (long) (page + 1) * size < total;
    }
}
