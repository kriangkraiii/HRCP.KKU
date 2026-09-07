package com.ecom.search.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ecom.model.UserDtls;
import com.ecom.search.dto.SearchQuery;
import com.ecom.search.dto.SearchResponse;
import com.ecom.search.service.SearchQueryNormalizer;
import com.ecom.search.service.SearchService;
import com.ecom.service.UserService;

/**
 * The full results page behind the omnibox.
 *
 * <p>The dropdown answers "take me straight there"; this answers "show me
 * everything, and let me narrow it down". Same index, same scoping, same
 * ranking — only the presentation and the filters differ.
 *
 * <p><b>No role gate, deliberately.</b> {@code /search} matches neither
 * {@code /admin/**} nor {@code /user/**} in {@code SecurityConfig}, so it falls
 * to {@code .anyRequest().authenticated()} and both roles reach the same
 * mapping. What each of them sees is decided inside the query, by the visibility
 * predicate, which is the one place that decision should live — a second gate
 * here could only ever disagree with it.
 */
@Controller
public class SearchPageController {

    /**
     * Fixed page size. Offered as a parameter it would only ever be used to
     * pull the whole index out in one request.
     */
    private static final int PAGE_SIZE = 20;

    private final SearchService searchService;
    private final UserService userService;

    public SearchPageController(SearchService searchService, UserService userService) {
        this.searchService = searchService;
        this.userService = userService;
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "types", required = false) List<String> types,
            @RequestParam(name = "statuses", required = false) List<String> statuses,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Principal principal,
            Model model) {

        UserDtls user = principal == null ? null : userService.getUserByEmail(principal.getName());
        String term = SearchQueryNormalizer.normalize(q);

        SearchQuery query = new SearchQuery(term, types, statuses,
                from == null ? null : from.atStartOfDay(),
                // Inclusive of the whole end day: a filter of 1–3 September that
                // dropped everything after midnight on the 3rd would be read as
                // a bug by anyone using it.
                to == null ? null : to.atTime(LocalTime.MAX),
                parseSort(sort), Math.max(page, 0), PAGE_SIZE);

        SearchResponse response = (user == null || term == null)
                ? SearchResponse.empty(term)
                : searchService.search(query, user);

        model.addAttribute("result", response);
        // Echoed back so the form stays filled in after a submit.
        model.addAttribute("q", q);
        model.addAttribute("selectedTypes", query.types());
        model.addAttribute("selectedStatuses", query.statuses());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("sort", query.sort().name());
        model.addAttribute("tooShort", q != null && !q.isBlank() && term == null);
        model.addAttribute("minQueryLength", SearchQueryNormalizer.MIN_QUERY_LENGTH);
        model.addAttribute("filterQuery", filterQuery(q, types, statuses, from, to, query.sort().name()));

        return "search";
    }

    private static SearchQuery.SearchSort parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return SearchQuery.SearchSort.RELEVANCE;
        }
        try {
            return SearchQuery.SearchSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // A hand-edited URL should fall back, not 500.
            return SearchQuery.SearchSort.RELEVANCE;
        }
    }

    /**
     * The current filters as a query-string tail for the pager links.
     *
     * <p>Copied in shape from {@code PublicationAdminPageController}, and for
     * the reason its javadoc gives: without it, paging past the first page
     * silently drops whatever was filtered — the classic way a search screen
     * lies about its own results.
     */
    private String filterQuery(String q, List<String> types, List<String> statuses,
            LocalDate from, LocalDate to, String sort) {
        StringBuilder sb = new StringBuilder();
        append(sb, "q", q);
        if (types != null) {
            types.forEach(type -> append(sb, "types", type));
        }
        if (statuses != null) {
            statuses.forEach(status -> append(sb, "statuses", status));
        }
        append(sb, "from", from == null ? null : from.toString());
        append(sb, "to", to == null ? null : to.toString());
        append(sb, "sort", sort);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append('&').append(key).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}
