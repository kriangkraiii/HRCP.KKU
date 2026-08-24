package com.ecom.academic.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;

public interface SignatureRequestRepository extends JpaRepository<SignatureRequest, Long> {

    /**
     * Envelopes for one document, newest first.
     *
     * <p>More than one can exist over time: a declined or cancelled round leaves
     * its record behind and a fresh envelope is created for the next attempt.
     */
    List<SignatureRequest> findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(
            SignatureModule module, Long requestId, Integer documentType);

    /**
     * The envelope currently holding a document, if any.
     *
     * <p>This is the lock: while one exists the document form must refuse edits,
     * otherwise people would be signing a moving target.
     */
    @Query("""
            SELECT r FROM SignatureRequest r
            WHERE r.module = :module AND r.requestId = :requestId
              AND r.documentType = :documentType
              AND r.status IN (com.ecom.academic.model.SignatureRequestStatus.IN_PROGRESS,
                               com.ecom.academic.model.SignatureRequestStatus.COMPLETED)
            ORDER BY r.createdAt DESC
            """)
    List<SignatureRequest> findBlockingEnvelopes(
            @Param("module") SignatureModule module,
            @Param("requestId") Long requestId,
            @Param("documentType") Integer documentType);

    /** All envelopes attached to one request, for the detail page timeline. */
    List<SignatureRequest> findByModuleAndRequestIdOrderByDocumentTypeAsc(
            SignatureModule module, Long requestId);

    /**
     * An envelope with its steps loaded.
     *
     * <p>{@code SignatureRequest.allSigned()}, {@code getProgressLabel()} and
     * {@code activeStep()} all read the step collection, which is lazy — use this
     * wherever the envelope will outlive its transaction.
     */
    @Query("""
            SELECT DISTINCT r FROM SignatureRequest r
            LEFT JOIN FETCH r.steps
            WHERE r.id = :id
            """)
    Optional<SignatureRequest> findByIdWithSteps(@Param("id") Long id);

    /** Verification needs the steps, and the page renders outside a transaction. */
    @Query("""
            SELECT DISTINCT r FROM SignatureRequest r
            LEFT JOIN FETCH r.steps
            WHERE r.verificationCode = :code
            """)
    Optional<SignatureRequest> findByVerificationCodeWithSteps(@Param("code") String code);

    Optional<SignatureRequest> findByVerificationCode(String verificationCode);

    boolean existsByVerificationCode(String verificationCode);

    /** Open envelopes past their due date, for the reminder/expiry scheduler. */
    List<SignatureRequest> findByStatusAndDueAtBefore(SignatureRequestStatus status, LocalDateTime cutoff);

    List<SignatureRequest> findByStatusOrderByCreatedAtAsc(SignatureRequestStatus status);

    @Query("""
            SELECT DISTINCT r FROM SignatureRequest r
            LEFT JOIN FETCH r.steps s
            LEFT JOIN FETCH s.signer
            WHERE r.status = :status
            ORDER BY r.createdAt DESC
            """)
    List<SignatureRequest> findByStatusWithSteps(@Param("status") SignatureRequestStatus status);

    @Query("""
            SELECT DISTINCT r FROM SignatureRequest r
            LEFT JOIN FETCH r.steps s
            LEFT JOIN FETCH s.signer
            WHERE r.initiatedBy.id = :userId AND r.status = :status
            ORDER BY r.createdAt DESC
            """)
    List<SignatureRequest> findByInitiatedByWithSteps(@Param("userId") Integer userId,
            @Param("status") SignatureRequestStatus status);

    @Modifying
    @Query("""
            UPDATE SignatureRequest r
            SET r.signedDocxPath = :docxPath, r.signedPdfPath = :pdfPath
            WHERE r.id = :id
            """)
    int updateSignedDocumentPaths(@Param("id") Long id,
            @Param("docxPath") String docxPath,
            @Param("pdfPath") String pdfPath);
}
