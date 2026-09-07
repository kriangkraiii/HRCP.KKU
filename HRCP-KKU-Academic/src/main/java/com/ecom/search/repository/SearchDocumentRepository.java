package com.ecom.search.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.search.model.ExtractionState;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;

/**
 * Write-side access to the index.
 *
 * <p>Searching does not go through here. Ranked queries need trigram and
 * tsvector operators that JPQL cannot express, so they live in
 * {@code SearchDocumentQueryRepository} as SQL. This interface exists for the
 * indexer, the reconciler and the extraction worker.
 */
public interface SearchDocumentRepository extends JpaRepository<SearchDocument, Long> {

    Optional<SearchDocument> findByEntityTypeAndEntityIdAndDocPart(
            SearchEntityType entityType, Long entityId, String docPart);

    List<SearchDocument> findByEntityTypeAndEntityId(SearchEntityType entityType, Long entityId);

    long countByEntityType(SearchEntityType entityType);

    long countByEntityTypeAndDeletedFalse(SearchEntityType entityType);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByEntityTypeAndEntityId(SearchEntityType entityType, Long entityId);

    /**
     * Every source id this type currently has a row for.
     *
     * <p>The reconciler diffs this against the ids the source table still holds,
     * which is how rows left behind by bulk JPQL deletes, cascades and the sync
     * jobs — none of which raise JPA lifecycle events — are found and removed.
     */
    @Query("SELECT d.entityId FROM SearchDocument d WHERE d.entityType = :type")
    List<Long> findIndexedEntityIds(@Param("type") SearchEntityType type);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM SearchDocument d WHERE d.entityType = :type AND d.entityId IN :ids")
    int deleteOrphans(@Param("type") SearchEntityType type, @Param("ids") List<Long> ids);

    /**
     * The next files waiting to have their text read.
     *
     * <p>Ordered by id so the queue drains predictably and a poisonous file
     * cannot be picked repeatedly ahead of everything behind it.
     */
    @Query("SELECT d FROM SearchDocument d WHERE d.extractionState = :state ORDER BY d.id ASC")
    List<SearchDocument> findNextForExtraction(@Param("state") ExtractionState state,
            org.springframework.data.domain.Pageable pageable);

    long countByExtractionState(ExtractionState state);
}
