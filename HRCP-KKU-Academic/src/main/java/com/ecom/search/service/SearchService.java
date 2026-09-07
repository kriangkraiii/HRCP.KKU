package com.ecom.search.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ecom.model.UserDtls;
import com.ecom.search.dto.SearchFacet;
import com.ecom.search.dto.SearchHit;
import com.ecom.search.dto.SearchPrincipal;
import com.ecom.search.dto.SearchQuery;
import com.ecom.search.dto.SearchResponse;
import com.ecom.search.repository.SearchDocumentQueryRepository;

/**
 * The only way into the search.
 *
 * <p>Takes an authenticated {@link UserDtls} and derives the scope from it
 * itself. No caller passes in a visibility, an owner id or an admin flag, so
 * there is no combination of request parameters that can widen what somebody
 * sees — the same discipline {@code ScopusQueryService} keeps for publications.
 */
@Service
public class SearchService {

    /** Above this a request is asking for a data export, not a search. */
    private static final int MAX_PAGE_SIZE = 100;

    private final SearchDocumentQueryRepository repository;

    public SearchService(SearchDocumentQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * The omnibox: one short page of the most relevant hits.
     *
     * <p><b>One query, not four.</b> This runs on every keystroke past the
     * debounce, so it deliberately does not do what {@link #search} does. There
     * is no {@code COUNT} — it asks for one row more than it will show and
     * infers "there are more" from getting it — and no facet queries, because a
     * dropdown has no facets to draw. The exact-match query is the only
     * statement a normal search costs; the typo-tolerant one runs only when that
     * returns nothing, which is the rare case and also the one where the user is
     * already waiting to be told there is no match.
     */
    public SearchResponse quickSearch(String rawQuery, UserDtls user, int limit) {
        long startedAt = System.nanoTime();

        String term = SearchQueryNormalizer.normalize(rawQuery);
        if (term == null) {
            return SearchResponse.empty(term);
        }

        SearchPrincipal principal = SearchPrincipal.of(user);
        int size = clampSize(limit);
        // One extra row is how "hasMore" is answered without a second statement.
        SearchQuery probe = SearchQuery.of(term, size + 1);

        boolean approximate = false;
        List<SearchHit> found = repository.hits(probe, principal, false);
        if (found.isEmpty()) {
            found = repository.hits(probe, principal, true);
            approximate = !found.isEmpty();
        }

        boolean hasMore = found.size() > size;
        List<SearchHit> hits = hasMore ? found.subList(0, size) : found;

        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
        // total is the number shown plus a marker that more exist; the omnibox
        // only ever needs "is there a 'see all' row to draw".
        return new SearchResponse(term, hits, hasMore ? hits.size() + 1L : hits.size(),
                0, size, List.of(), List.of(), tookMs, approximate);
    }

    /**
     * The results page: filters, facets and paging.
     *
     * <p>Runs the exact-match query first and only falls back to the
     * typo-tolerant one when it finds nothing. That ordering is not just about
     * result quality — the fuzzy predicate cannot use the trigram index, so
     * folding it into the main query with an {@code OR} would turn every search
     * into a sequential scan. Reaching for it only on a miss keeps the common
     * path fully indexed.
     */
    public SearchResponse search(SearchQuery query, UserDtls user) {
        long startedAt = System.nanoTime();

        if (query == null || query.q() == null) {
            return SearchResponse.empty(query == null ? null : query.q());
        }

        SearchPrincipal principal = SearchPrincipal.of(user);
        SearchQuery clamped = clamp(query);

        boolean approximate = false;
        long total = repository.count(clamped, principal, false);
        if (total == 0) {
            long fuzzyTotal = repository.count(clamped, principal, true);
            if (fuzzyTotal > 0) {
                approximate = true;
                total = fuzzyTotal;
            }
        }

        if (total == 0) {
            return SearchResponse.empty(clamped.q());
        }

        List<SearchHit> hits = repository.hits(clamped, principal, approximate);
        List<SearchFacet> typeFacets = repository.typeFacets(clamped, principal, approximate);
        List<SearchFacet> statusFacets = repository.statusFacets(clamped, principal, approximate);

        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new SearchResponse(clamped.q(), hits, total, clamped.page(), clamped.size(),
                typeFacets, statusFacets, tookMs, approximate);
    }

    private SearchQuery clamp(SearchQuery query) {
        return new SearchQuery(query.q(), query.types(), query.statuses(),
                query.from(), query.to(), query.sort(),
                Math.max(query.page(), 0), clampSize(query.size()));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
