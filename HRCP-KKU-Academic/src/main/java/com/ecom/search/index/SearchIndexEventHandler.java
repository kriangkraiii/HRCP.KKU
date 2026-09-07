package com.ecom.search.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Applies a change to the index, after the change that caused it has committed.
 *
 * <p><b>AFTER_COMMIT, not during.</b> Indexing inside the originating
 * transaction would let an index failure roll back a submitted request, and
 * would index rows that the caller then rolled back. After commit, the worst a
 * failure can do is leave the index a little behind — which the nightly
 * reconcile repairs.
 *
 * <p><b>Everything is caught.</b> A synchronous {@code AFTER_COMMIT} listener
 * that throws propagates to the caller, so an unhandled exception here would
 * turn a successful submission into a 500 in someone's browser after their work
 * was already saved.
 */
@Component
public class SearchIndexEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexEventHandler.class);

    private final SearchIndexer indexer;
    private final boolean enabled;

    public SearchIndexEventHandler(SearchIndexer indexer,
            @Value("${app.search.enabled:true}") boolean enabled) {
        this.indexer = indexer;
        this.enabled = enabled;
    }

    @Async("searchIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReindex(SearchReindexEvent event) {
        if (!enabled) {
            return;
        }
        try {
            if (event.removed()) {
                indexer.remove(event.type(), event.id());
            } else {
                indexer.reindex(event.type(), event.id());
            }
        } catch (Exception e) {
            log.warn("อัปเดต index ไม่สำเร็จสำหรับ {} #{}: {}",
                    event.type(), event.id(), e.toString());
        }
    }
}
