package com.ecom.academic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequestStatus;
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

    /**
     * What one person has signed — the "ประวัติการลงนาม" tab.
     *
     * <p>{@code envelopeStatus} picks the view: COMPLETED for rounds that
     * collected every signature, IN_PROGRESS for rounds still waiting on
     * someone after this person. A round that was declined, cancelled or voided
     * is never asked for: its signatures are not on any document anyone can
     * verify, so listing it would point at nothing.
     *
     * <p>{@code searching} is a flag rather than a null check on {@code pattern}:
     * PostgreSQL cannot type a bare {@code ? IS NULL}. {@code modules} are the
     * request types whose Thai label matches the search, worked out by the caller.
     */
    @Query(value = """
            SELECT s FROM SignatureStep s
            JOIN FETCH s.signatureRequest r
            WHERE s.signer.id = :userId
              AND s.status = com.ecom.academic.model.SignatureStepStatus.SIGNED
              AND r.status = :envelopeStatus
              AND (:searching = false
                   OR LOWER(r.documentLabel) LIKE :pattern ESCAPE '\\'
                   OR LOWER(s.roleLabel) LIKE :pattern ESCAPE '\\'
                   OR LOWER(r.verificationCode) LIKE :pattern ESCAPE '\\'
                   OR r.module IN :modules)
            ORDER BY s.signedAt DESC, s.id DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM SignatureStep s
            JOIN s.signatureRequest r
            WHERE s.signer.id = :userId
              AND s.status = com.ecom.academic.model.SignatureStepStatus.SIGNED
              AND r.status = :envelopeStatus
              AND (:searching = false
                   OR LOWER(r.documentLabel) LIKE :pattern ESCAPE '\\'
                   OR LOWER(s.roleLabel) LIKE :pattern ESCAPE '\\'
                   OR LOWER(r.verificationCode) LIKE :pattern ESCAPE '\\'
                   OR r.module IN :modules)
            """)
    Page<SignatureStep> findSignedHistory(@Param("userId") Integer userId,
            @Param("envelopeStatus") SignatureRequestStatus envelopeStatus,
            @Param("searching") boolean searching,
            @Param("pattern") String pattern,
            @Param("modules") Collection<SignatureModule> modules,
            Pageable pageable);

    /** Size of each "ประวัติการลงนาม" view, for the filter buttons. */
    @Query("""
            SELECT COUNT(s) FROM SignatureStep s
            WHERE s.signer.id = :userId
              AND s.status = com.ecom.academic.model.SignatureStepStatus.SIGNED
              AND s.signatureRequest.status = :envelopeStatus
            """)
    long countSignedHistory(@Param("userId") Integer userId,
            @Param("envelopeStatus") SignatureRequestStatus envelopeStatus);

    /** Every step of several envelopes, to draw each one's signing order. */
    List<SignatureStep> findBySignatureRequestIdInOrderByStepOrderAsc(Collection<Long> signatureRequestIds);

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

    /** Whether any step's stamped image is this file — such a file must not be deleted. */
    boolean existsByImagePathSnapshot(String imagePathSnapshot);

    /** The most recently signed step with an image, for the startup storage check. */
    Optional<SignatureStep> findFirstByImagePathSnapshotIsNotNullOrderByIdDesc();

    /**
     * All active signature steps across in-progress signature requests,
     * with envelope and signer loaded for analytics.
     */
    @Query("""
            SELECT s FROM SignatureStep s
            JOIN FETCH s.signatureRequest r
            LEFT JOIN FETCH s.signer
            WHERE s.status = com.ecom.academic.model.SignatureStepStatus.ACTIVE
              AND r.status = com.ecom.academic.model.SignatureRequestStatus.IN_PROGRESS
            ORDER BY r.createdAt ASC
            """)
    List<SignatureStep> findAllActivePendingSteps();
}
