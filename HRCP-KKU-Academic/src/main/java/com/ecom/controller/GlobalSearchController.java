package com.ecom.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecom.model.UserDtls;
import com.ecom.search.dto.SearchResponse;
import com.ecom.search.service.SearchService;
import com.ecom.service.GlobalSearchService;
import com.ecom.service.GlobalSearchService.SearchResultItem;
import com.ecom.service.UserService;

/**
 * Backs the Ctrl/⌘-K omnibox.
 *
 * <p>The response shape is unchanged from the version that fanned out across a
 * dozen repositories: {@code query}, {@code results}, {@code total}, and on each
 * result the seven fields {@code global-search.js} renders. That is what let the
 * engine underneath be replaced without touching the front end, and
 * {@code GlobalSearchApiTest} holds the shape in place.
 *
 * <p>{@code app.search.enabled=false} switches back to the old service. It is a
 * way out if the index turns out to be wrong in production, where the deploy is
 * a Windows Service restart and rolling back a jar is not quick — not a
 * permanent option. The old service goes once the new one has run a release.
 */
@Controller
public class GlobalSearchController {

    /** Enough to fill the dropdown; the rest are on the results page. */
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 50;

    private final SearchService searchService;
    private final GlobalSearchService legacySearchService;
    private final UserService userService;
    private final boolean useIndexedSearch;

    public GlobalSearchController(SearchService searchService,
            GlobalSearchService legacySearchService,
            UserService userService,
            @Value("${app.search.enabled:true}") boolean useIndexedSearch) {
        this.searchService = searchService;
        this.legacySearchService = legacySearchService;
        this.userService = userService;
        this.useIndexedSearch = useIndexedSearch;
    }

    @GetMapping("/api/global-search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            Principal principal) {

        Map<String, Object> resp = new LinkedHashMap<>();

        // Anonymous callers are normally stopped by Spring Security long before
        // this — /api/** falls through to .anyRequest().authenticated() and is
        // redirected to /signin. This branch is for the case that gets past it:
        // a session that is authenticated but whose user row has gone.
        if (principal == null) {
            resp.put("query", query);
            resp.put("results", Collections.emptyList());
            resp.put("total", 0);
            return ResponseEntity.status(401).body(resp);
        }

        UserDtls user = userService.getUserByEmail(principal.getName());
        if (user == null) {
            resp.put("query", query);
            resp.put("results", Collections.emptyList());
            resp.put("total", 0);
            return ResponseEntity.status(401).body(resp);
        }

        if (!useIndexedSearch) {
            List<SearchResultItem> legacy = legacySearchService.search(query, user);
            resp.put("query", query);
            resp.put("results", legacy);
            resp.put("total", legacy.size());
            return ResponseEntity.ok(resp);
        }

        SearchResponse result = searchService.quickSearch(query, user, clampLimit(limit));

        resp.put("query", query);
        resp.put("results", result.hits());
        // Kept as the number of items returned, which is what the old contract
        // meant and what the dropdown counts.
        resp.put("total", result.hits().size());
        resp.put("totalMatches", result.total());
        resp.put("hasMore", result.hasMore());
        resp.put("approximate", result.approximate());
        resp.put("tookMs", result.tookMs());
        return ResponseEntity.ok(resp);
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }
}
