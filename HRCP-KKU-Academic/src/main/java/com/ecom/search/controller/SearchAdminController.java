package com.ecom.search.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecom.search.index.SearchReconciler;
import com.ecom.search.index.SearchSourceCatalog;
import com.ecom.search.model.ExtractionState;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.repository.SearchDocumentRepository;

/**
 * Lets an administrator see whether the index is healthy, and rebuild it.
 *
 * <p>Two JSON endpoints rather than a page. The index is normally invisible —
 * it fills itself on first boot and repairs itself nightly — so the only
 * questions worth answering are "does it have the rows it should?" and "rebuild
 * it now, I changed something". A screen for that would be more to maintain than
 * it is worth until somebody asks for one.
 *
 * <p>Reindexing is the documented follow-up to changing a URL template in
 * {@link SearchEntityType}: the resolved links are written into rows at index
 * time, so old rows keep pointing at the old path until they are rewritten.
 */
@Controller
@RequestMapping("/admin/search")
@PreAuthorize("hasRole('ADMIN')")
public class SearchAdminController {

    private final SearchDocumentRepository index;
    private final SearchSourceCatalog catalog;
    private final SearchReconciler reconciler;

    public SearchAdminController(SearchDocumentRepository index,
            SearchSourceCatalog catalog,
            SearchReconciler reconciler) {
        this.index = index;
        this.catalog = catalog;
        this.reconciler = reconciler;
    }

    /**
     * Row counts per type, next to what the source tables hold.
     *
     * <p>Showing both is the point: a count on its own says nothing, whereas
     * "1 240 indexed, 1 240 in the table" says the index is complete and
     * "0 indexed, 1 240 in the table" says the backfill never ran.
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> perType = new LinkedHashMap<>();
        for (SearchEntityType type : catalog.indexedTypes()) {
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("indexed", index.countByEntityType(type));
            counts.put("visible", index.countByEntityTypeAndDeletedFalse(type));
            counts.put("inSource", catalog.sourceIds(type).size());
            perType.put(type.name(), counts);
        }

        Map<String, Object> extraction = new LinkedHashMap<>();
        for (ExtractionState state : ExtractionState.values()) {
            long count = index.countByExtractionState(state);
            if (count > 0) {
                extraction.put(state.name(), count);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", index.count());
        body.put("byType", perType);
        body.put("fileExtractionQueue", extraction);
        body.put("lastReconcileAt", reconciler.getLastRunAt());
        body.put("lastReconcile", reconciler.getLastRunSummary());
        return ResponseEntity.ok(body);
    }

    /**
     * Rebuilds everything, synchronously.
     *
     * <p>Deliberately not asynchronous: an administrator who triggers this wants
     * to know it finished and what it did. At this data volume the pass is
     * seconds, and the alternative — returning immediately and making them poll
     * the status endpoint — is worse for the one person who ever calls it.
     */
    @PostMapping("/reindex")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reindex() {
        String summary = reconciler.reconcileAll();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("summary", summary);
        body.put("total", index.count());
        return ResponseEntity.ok(body);
    }
}
