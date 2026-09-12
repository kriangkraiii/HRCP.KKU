package com.ecom.academic.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.PositionRequestPublication;
import com.ecom.academic.model.PositionRequestStatus;

public interface PositionRequestPublicationRepository
        extends JpaRepository<PositionRequestPublication, Long> {

    List<PositionRequestPublication> findByRequestIdAndDocumentType(Long requestId, Integer documentType);

    List<PositionRequestPublication> findByRequestId(Long requestId);

    @Modifying
    @Transactional
    void deleteByRequestIdAndDocumentType(Long requestId, Integer documentType);

    /**
     * The publications this applicant has already spent.
     *
     * <p>Scoped to one applicant on purpose, not queried faculty-wide. The link
     * rows come from a form, and a form can be tampered with: someone could
     * submit another professor's publication id and, on a global query, make that
     * professor's own work vanish from their picker. Scoping the answer to the
     * asker means a forged id can only ever cost the person who forged it.
     *
     * @param free the statuses that do not consume anything — only {@code DRAFT},
     *             because nothing has been submitted yet
     */
    @Query("""
            SELECT l.publicationId FROM PositionRequestPublication l
            WHERE l.request.applicant.id = :applicantId
              AND l.request.currentStatus NOT IN :free
            """)
    List<Long> findSpentPublicationIds(@Param("applicantId") Integer applicantId,
            @Param("free") Collection<PositionRequestStatus> free);
}
