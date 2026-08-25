package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;

public interface SignatureStepRepository extends JpaRepository<SignatureStep, Long> {

    /**
     * Everything waiting on one person — the "รอลงนาม" inbox.
     *
     * <p>Only ACTIVE steps: a step further down a chain is not this person's
     * problem yet, and listing it would invite them to try to sign out of turn.
     */
    @Query("""
            SELECT s FROM SignatureStep s
            JOIN FETCH s.signatureRequest r
            WHERE s.signer.id = :userId AND s.status = :status
              AND r.status = com.ecom.academic.model.SignatureRequestStatus.IN_PROGRESS
            ORDER BY r.dueAt ASC NULLS LAST, r.createdAt ASC
            """)
    List<SignatureStep> findInbox(@Param("userId") Integer userId,
            @Param("status") SignatureStepStatus status);

    /** Count for the sidebar badge. */
    @Query("""
            SELECT COUNT(s) FROM SignatureStep s
            WHERE s.signer.id = :userId
              AND s.status = com.ecom.academic.model.SignatureStepStatus.ACTIVE
              AND s.signatureRequest.status = com.ecom.academic.model.SignatureRequestStatus.IN_PROGRESS
            """)
    long countPendingFor(@Param("userId") Integer userId);

    /** A step with its envelope and steps loaded, for the signing page. */
    @Query("""
            SELECT s FROM SignatureStep s
            JOIN FETCH s.signatureRequest
            WHERE s.id = :id
            """)
    Optional<SignatureStep> findByIdWithRequest(@Param("id") Long id);

    List<SignatureStep> findBySignatureRequestIdOrderByStepOrderAsc(Long signatureRequestId);

    /**
     * Every step of an envelope with the signer's account loaded.
     *
     * <p>Used when printing signer names onto the document: the name line of a
     * step that has not been signed yet still has to say who is expected to
     * sign it, and reading {@code signer} lazily would fail outside a session.
     */
    @Query("""
            SELECT s FROM SignatureStep s
            LEFT JOIN FETCH s.signer
            WHERE s.signatureRequest.id = :requestId
            ORDER BY s.stepOrder ASC
            """)
    List<SignatureStep> findStepsWithSigner(@Param("requestId") Long requestId);

    /** Steps already signed, whose images must be stamped into the document. */
    @Query("""
            SELECT s FROM SignatureStep s
            WHERE s.signatureRequest.id = :requestId
              AND s.status = com.ecom.academic.model.SignatureStepStatus.SIGNED
            ORDER BY s.stepOrder ASC
            """)
    List<SignatureStep> findSignedSteps(@Param("requestId") Long requestId);
}
