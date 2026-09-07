package com.ecom.search.index;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ecom.search.model.SearchEntityType;
import com.ecom.search.repository.SearchDocumentRepository;

/**
 * Repairs whatever the live indexing missed.
 *
 * <p>Entity listeners catch every write that goes through JPA, which is most of
 * them — but not all. Bulk JPQL updates and deletes bypass the persistence
 * context entirely and raise no lifecycle callbacks; cascade deletes remove
 * children without visiting them; and a listener that threw, or an index write
 * that failed while the database was briefly unavailable, leaves the index
 * quietly behind. None of those announce themselves.
 *
 * <p>So this runs nightly and makes the index match the tables again: rebuild
 * what is missing or stale, delete what no longer exists. It is deliberately
 * dumb and complete rather than clever and partial — the whole point is that it
 * does not depend on having been told what changed.
 *
 * <p>02:20 keeps it clear of {@code EvaluationExpiryScheduler} (08:00) and
 * {@code SignatureReminderScheduler} (08:30).
 */
@Service
public class SearchReconciler {

    private static final Logger log = LoggerFactory.getLogger(SearchReconciler.class);

    private final SearchDocumentRepository index;
    private final SearchSourceCatalog catalog;
    private final SearchIndexer indexer;
    private final com.ecom.search.service.NavigationCatalog navigationCatalog;
    private final boolean enabled;

    private volatile LocalDateTime lastRunAt;
    private volatile String lastRunSummary = "ยังไม่เคยรัน";

    public SearchReconciler(SearchDocumentRepository index,
            SearchSourceCatalog catalog,
            SearchIndexer indexer,
            com.ecom.search.service.NavigationCatalog navigationCatalog,
            @Value("${app.search.enabled:true}") boolean enabled) {
        this.index = index;
        this.catalog = catalog;
        this.indexer = indexer;
        this.navigationCatalog = navigationCatalog;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${app.search.reconcile-cron:0 20 2 * * *}")
    public void scheduledReconcile() {
        if (!enabled) {
            return;
        }
        reconcileAll();
    }

    /**
     * Brings every indexed type back in line with its source table.
     *
     * @return a short human-readable summary, also kept for the status endpoint
     */
    public String reconcileAll() {
        long startedAt = System.nanoTime();
        int written = 0;
        int removed = 0;
        List<String> perType = new ArrayList<>();

        // Menus first: they come from a static catalogue rather than a table, so
        // nothing else in the system will ever write them.
        int navWritten = seedNavigation();
        written += navWritten;
        if (navWritten > 0) {
            perType.add("NAVIGATION +%d".formatted(navWritten));
        }

        for (SearchEntityType type : catalog.indexedTypes()) {
            Result result = reconcile(type);
            written += result.written();
            removed += result.removed();
            if (result.written() > 0 || result.removed() > 0) {
                perType.add("%s +%d/-%d".formatted(type, result.written(), result.removed()));
            }
        }

        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
        lastRunAt = LocalDateTime.now();
        lastRunSummary = "เขียน %d, ลบ %d, ใช้เวลา %d ms%s".formatted(
                written, removed, took.toMillis(),
                perType.isEmpty() ? "" : " — " + String.join(", ", perType));

        log.info("Search reconcile: {}", lastRunSummary);
        return lastRunSummary;
    }

    /**
     * Writes the menu entries.
     *
     * <p>Cheap enough to redo on every pass — a couple of dozen rows that only
     * change when the catalogue in Java changes — and doing it here means a
     * renamed menu reaches the index without a deployment step of its own.
     */
    public int seedNavigation() {
        int written = 0;
        for (var document : navigationCatalog.documents()) {
            try {
                if (indexer.upsertPrebuilt(document)) {
                    written++;
                }
            } catch (Exception e) {
                log.warn("seed เมนู '{}' ไม่สำเร็จ: {}", document.getTitle(), e.toString());
            }
        }
        return written;
    }

    /**
     * One type.
     *
     * <p>Each row is indexed in its own transaction, so a single unreadable row
     * — a document whose {@code json_data} is corrupt, a request whose applicant
     * has been deleted out from under it — costs one row rather than aborting
     * the whole night's pass.
     */
    public Result reconcile(SearchEntityType type) {
        if (!catalog.isIndexed(type)) {
            return new Result(0, 0);
        }

        List<Long> sourceIds = catalog.sourceIds(type);
        int written = 0;
        for (Long id : sourceIds) {
            try {
                if (indexer.reindex(type, id)) {
                    written++;
                }
            } catch (Exception e) {
                log.warn("reconcile {} #{} ไม่สำเร็จ: {}", type, id, e.toString());
            }
        }

        int removed = removeOrphans(type, sourceIds);
        return new Result(written, removed);
    }

    /**
     * Deletes index rows whose source row is gone.
     *
     * <p>This is the half that catches bulk deletes and cascades. Without it an
     * orphan stays in the index forever and shows up in results as a link to a
     * page that 404s — which reads to a user as the system being broken rather
     * than the record being gone.
     */
    private int removeOrphans(SearchEntityType type, List<Long> sourceIds) {
        Set<Long> alive = new HashSet<>(sourceIds);
        List<Long> orphaned = index.findIndexedEntityIds(type).stream()
                .filter(id -> !alive.contains(id))
                .distinct()
                .toList();

        if (orphaned.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        // Chunked: a where-in with tens of thousands of ids is its own problem.
        for (int from = 0; from < orphaned.size(); from += 500) {
            List<Long> chunk = orphaned.subList(from, Math.min(from + 500, orphaned.size()));
            deleted += index.deleteOrphans(type, chunk);
        }
        return deleted;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public String getLastRunSummary() {
        return lastRunSummary;
    }

    /** How much a pass changed. */
    public record Result(int written, int removed) {
    }
}
