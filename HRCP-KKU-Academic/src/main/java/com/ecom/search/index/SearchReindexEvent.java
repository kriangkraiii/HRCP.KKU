package com.ecom.search.index;

import com.ecom.search.model.SearchEntityType;

/**
 * "This row changed; the index should catch up."
 *
 * <p><b>Carries identifiers, never the entity.</b> The handler runs on another
 * thread, after the originating transaction has committed and its persistence
 * context is gone. An entity reference here would be detached, and touching any
 * lazy association on it — {@code applicant}, {@code recipient}, {@code request}
 * — would throw. The handler re-reads what it needs in a session of its own.
 *
 * @param type    which source table
 * @param id      the source row's id
 * @param removed true when the row was deleted, so the index entry should go
 */
public record SearchReindexEvent(SearchEntityType type, Long id, boolean removed) {

    public static SearchReindexEvent changed(SearchEntityType type, Long id) {
        return new SearchReindexEvent(type, id, false);
    }

    public static SearchReindexEvent removed(SearchEntityType type, Long id) {
        return new SearchReindexEvent(type, id, true);
    }
}
