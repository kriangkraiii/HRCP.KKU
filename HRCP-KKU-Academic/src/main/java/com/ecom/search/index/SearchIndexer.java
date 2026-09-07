package com.ecom.search.index;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;

/**
 * Keeps one source row's index entry up to date.
 *
 * <p>Only coordination lives here; the two halves it coordinates are separate
 * beans for reasons that each cost a bug to learn:
 *
 * <ul>
 * <li>{@link SearchDocumentLoader} reads the source in a <b>read-only</b>
 * transaction, so Hibernate cannot flush the entities it loads back over
 * concurrent edits.</li>
 * <li>{@link SearchDocumentWriter} does the writing in a transaction of its own.
 * It is a separate bean because {@code @Transactional} is applied by a proxy and
 * a call between methods of one bean bypasses it entirely.</li>
 * </ul>
 *
 * <p>This class is therefore not transactional itself — an outer transaction
 * would swallow both of theirs and undo the separation.
 *
 * <p><b>An unknown id is a delete, not an error.</b> The listener only knows a
 * type and an id; if the row has gone by the time the indexer looks, the right
 * response is to drop the index entry.
 */
@Service
public class SearchIndexer {

    private final SearchDocumentLoader loader;
    private final SearchDocumentWriter writer;

    public SearchIndexer(SearchDocumentLoader loader, SearchDocumentWriter writer) {
        this.loader = loader;
        this.writer = writer;
    }

    /**
     * Rebuilds the index entry for one source row.
     *
     * @return true when something was written; false when nothing changed or
     *         there was nothing to do
     */
    public boolean reindex(SearchEntityType type, Long entityId) {
        if (type == null || entityId == null) {
            return false;
        }

        SearchDocument built = loader.load(type, entityId);
        if (built != null) {
            return upsertHandlingCollision(built);
        }
        // A type nobody builds must not have its rows deleted as if the source
        // had vanished — something else may be maintaining them.
        return loader.canLoad(type) && writer.remove(type, entityId);
    }

    /**
     * Writes, and copes with another writer having got there first.
     *
     * <p>Both calls cross a bean boundary on purpose. The failed insert leaves
     * its transaction unusable — Hibernate refuses to continue a session after a
     * constraint violation — so the retry has to be a genuinely new transaction,
     * and only a call through the proxy gets one. Retrying inside
     * {@code SearchDocumentWriter} instead produced "Entry for instance … has a
     * null identifier", which is that rule being enforced.
     */
    private boolean upsertHandlingCollision(SearchDocument built) {
        try {
            return writer.upsert(built);
        } catch (DataIntegrityViolationException e) {
            return writer.retryAsUpdate(built);
        }
    }

    /**
     * Writes a document that has no source table behind it.
     *
     * <p>Navigation entries are the only such case: they come from a static
     * catalogue in Java rather than a row somewhere, so nothing can raise a
     * lifecycle event for them and there is no id to look up.
     */
    public boolean upsertPrebuilt(SearchDocument document) {
        return writer.upsert(document);
    }

    /** Drops every entry for a source row. */
    public boolean remove(SearchEntityType type, Long entityId) {
        return writer.remove(type, entityId);
    }
}
