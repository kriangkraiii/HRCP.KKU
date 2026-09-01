package com.ecom.external.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.ScopusQueryService;

/**
 * Faculty-wide view of the publications mirrored from Scopus.
 *
 * <p>The applicant-facing API is owner-scoped by construction — every finder it
 * uses takes the professor's own id. Reading across everyone is a different
 * thing, needed for reviewing a promotion request against the rest of the
 * faculty, and it is confined here behind {@code ROLE_ADMIN}.
 *
 * <p>Read-only on purpose. The rows are a mirror of an upstream system; editing
 * one here would be overwritten by the next sync, so the page offers filters and
 * a link out to Scopus rather than an edit button that quietly does nothing.
 */
@Controller
@RequestMapping("/admin/publications")
@PreAuthorize("hasRole('ADMIN')")
public class PublicationAdminPageController {

    private static final int PAGE_SIZE = 25;

    private final ScopusQueryService scopusQuery;
    private final ScopusPublicationRepository publicationRepo;
    private final FsFacultyRepository facultyRepo;

    public PublicationAdminPageController(ScopusQueryService scopusQuery,
            ScopusPublicationRepository publicationRepo,
            FsFacultyRepository facultyRepo) {
        this.scopusQuery = scopusQuery;
        this.publicationRepo = publicationRepo;
        this.facultyRepo = facultyRepo;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long fsUserId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Page<ScopusQueryService.AdminPublication> results =
                scopusQuery.adminSearchWithOwner(fsUserId, year, year, q, source, page, PAGE_SIZE);

        model.addAttribute("publications", results);
        model.addAttribute("authorNames", authorNames());
        model.addAttribute("authorOptions", authorOptions());
        model.addAttribute("years", publicationRepo.findDistinctYears());
        model.addAttribute("sourceCounts", sourceCounts());

        // Echoed back so the filters stay filled in and the pager keeps them.
        model.addAttribute("q", q);
        model.addAttribute("selectedFsUserId", fsUserId);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedSource", source);
        model.addAttribute("filterQuery", filterQuery(q, fsUserId, year, source));

        model.addAttribute("totalPublications", publicationRepo.count());
        model.addAttribute("totalCitations", publicationRepo.sumAllCitations());
        model.addAttribute("authorsWithPublications", publicationRepo.countAuthorsWithPublications());

        return "admin/publications";
    }

    /**
     * The reading view for one publication.
     *
     * <p>A thousand-word abstract inside a table cell is technically visible and
     * practically unreadable, so it gets a page with room for it.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        var publication = scopusQuery.findForAdmin(id).orElse(null);
        if (publication == null) {
            return "redirect:/admin/publications";
        }

        model.addAttribute("row", publication);
        model.addAttribute("raw", scopusQuery.rawById(id).orElse(null));
        model.addAttribute("ownerName",
                authorNames().getOrDefault(publication.fsUserId(), "รหัส " + publication.fsUserId()));
        return "admin/publication_detail";
    }

    /**
     * Names for every professor who owns a row on this page.
     *
     * <p>A publication carries only the upstream id of its owner, and a table of
     * numbers is unreadable. The whole directory is 45 rows, so resolving the
     * names in one pass costs less than joining per row.
     */
    private Map<Long, String> authorNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (FsFaculty f : facultyRepo.findAll()) {
            if (f.getFsUserId() != null) {
                names.put(f.getFsUserId(), displayName(f));
            }
        }
        return names;
    }

    /** The filter dropdown: only professors who actually have publications. */
    private Map<Long, String> authorOptions() {
        Set<Long> withPublications = Set.copyOf(publicationRepo.findDistinctAuthorIds());
        return authorNames().entrySet().stream()
                .filter(e -> withPublications.contains(e.getKey()))
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String displayName(FsFaculty f) {
        String prefix = f.getPositionTitle() != null ? f.getPositionTitle() + " " : "";
        String first = f.getFirstName() != null ? f.getFirstName() : "";
        String last = f.getLastName() != null ? f.getLastName() : "";
        String name = (prefix + first + " " + last).trim();
        return name.isEmpty() ? "รหัส " + f.getFsUserId() : name;
    }

    /**
     * The current filters as a query string for the pager links.
     *
     * <p>Without it, paging past the first page silently drops whatever was
     * filtered — the classic way a search screen lies about its results.
     */
    /**
     * How many rows came from each source, most first.
     *
     * <p>Publications used to arrive from Scopus alone. They now also come from
     * Crossref, OpenAlex, DBLP, ThaiJO and the KKU repository, into this same
     * table — so a row's origin is something this page has to be able to show and
     * filter by, rather than a fact buried in a column nobody renders.
     */
    private Map<String, Long> sourceCounts() {
        return publicationRepo.countGroupByDataSource().stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? (String) row[0] : "UNKNOWN",
                        row -> (Long) row[1],
                        Long::sum,
                        LinkedHashMap::new))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String filterQuery(String q, Long fsUserId, Integer year, String source) {
        StringBuilder sb = new StringBuilder();
        if (q != null && !q.isBlank()) {
            sb.append("&q=").append(java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (fsUserId != null) {
            sb.append("&fsUserId=").append(fsUserId);
        }
        if (year != null) {
            sb.append("&year=").append(year);
        }
        if (source != null && !source.isBlank()) {
            sb.append("&source=").append(java.net.URLEncoder.encode(source, java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
