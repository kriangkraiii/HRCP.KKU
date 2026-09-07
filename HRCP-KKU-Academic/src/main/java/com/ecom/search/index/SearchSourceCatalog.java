package com.ecom.search.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ecom.search.model.SearchEntityType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Answers "which rows should the index contain for this type?"
 *
 * <p>Ids are read with one JPQL projection per type rather than by loading
 * entities: the reconciler only needs to know what exists, and pulling every
 * request, document and user into memory to look at its primary key would make
 * the nightly pass far more expensive than the work it is checking.
 *
 * <p>The entity names live here in one map instead of a listing method on ten
 * repositories. A type that is not listed is one the indexer does not build, and
 * the reconciler leaves its rows alone rather than deleting them as orphans.
 */
@Component
public class SearchSourceCatalog {

    private static final Map<SearchEntityType, String> ENTITY_NAMES = new LinkedHashMap<>();

    static {
        ENTITY_NAMES.put(SearchEntityType.ACADEMIC_REQUEST, "AcademicRequest");
        ENTITY_NAMES.put(SearchEntityType.ACADEMIC_DOCUMENT, "AcademicDocument");
        ENTITY_NAMES.put(SearchEntityType.POSITION_REQUEST, "PositionRequest");
        ENTITY_NAMES.put(SearchEntityType.POSITION_DOCUMENT, "PositionDocument");
        ENTITY_NAMES.put(SearchEntityType.COMMITTEE_MEMBER, "AcademicCommitteeMember");
        ENTITY_NAMES.put(SearchEntityType.STAFF_MEMBER, "StaffMember");
        ENTITY_NAMES.put(SearchEntityType.SYSTEM_USER, "UserDtls");
        ENTITY_NAMES.put(SearchEntityType.NOTIFICATION, "Notification");
        ENTITY_NAMES.put(SearchEntityType.ADMIN_FILE, "AdminFile");
        ENTITY_NAMES.put(SearchEntityType.REGULATION_DOC, "KkuRegulationDoc");
        ENTITY_NAMES.put(SearchEntityType.ACADEMIC_ATTACHMENT, "AcademicAttachment");
        ENTITY_NAMES.put(SearchEntityType.POSITION_ATTACHMENT, "PositionAttachment");
        ENTITY_NAMES.put(SearchEntityType.STATUS_HISTORY, "RequestStatusHistory");
        ENTITY_NAMES.put(SearchEntityType.SIGNATURE_REQUEST, "SignatureRequest");
        ENTITY_NAMES.put(SearchEntityType.PUBLICATION, "ScopusPublication");
    }

    @PersistenceContext
    private EntityManager entityManager;

    /** Every type the indexer knows how to build, in a stable order. */
    public List<SearchEntityType> indexedTypes() {
        return List.copyOf(ENTITY_NAMES.keySet());
    }

    public boolean isIndexed(SearchEntityType type) {
        return ENTITY_NAMES.containsKey(type);
    }

    /**
     * Every id the source table currently holds.
     *
     * <p>Returned as {@code Long} regardless of how the entity declares its key
     * — {@code UserDtls} uses {@code Integer} — so callers have one type to
     * work with and the index's own {@code entity_id} column stays a bigint.
     */
    public List<Long> sourceIds(SearchEntityType type) {
        String entityName = ENTITY_NAMES.get(type);
        if (entityName == null) {
            return List.of();
        }
        List<?> raw = entityManager
                .createQuery("SELECT e.id FROM " + entityName + " e")
                .getResultList();

        List<Long> ids = new ArrayList<>(raw.size());
        for (Object value : raw) {
            if (value instanceof Number n) {
                ids.add(n.longValue());
            }
        }
        return ids;
    }
}
