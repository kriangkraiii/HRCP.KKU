package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;

public interface PositionRequestRepository extends JpaRepository<PositionRequest, Long> {

    /**
     * Loads a request with its applicant already fetched.
     *
     * <p>For background work: a notification thread must not be handed an
     * entity belonging to the request thread's still-open session, so it
     * re-reads its own copy with everything it needs already attached.
     */
    @Query("SELECT r FROM PositionRequest r LEFT JOIN FETCH r.applicant WHERE r.id = :id")
    Optional<PositionRequest> findByIdWithApplicant(@Param("id") Long id);

    @Query("SELECT r FROM PositionRequest r WHERE r.applicant.id = :userId ORDER BY r.createdAt DESC")
    List<PositionRequest> findByApplicantId(@Param("userId") Integer userId);

    @Query("SELECT r FROM PositionRequest r WHERE r.applicant.id = :userId AND r.currentStatus NOT IN :terminalStatuses")
    Optional<PositionRequest> findActiveByApplicantId(@Param("userId") Integer userId,
            @Param("terminalStatuses") List<PositionRequestStatus> terminalStatuses);

    /**
     * This applicant's requests that have actually spent the evaluation they
     * were built on, with that evaluation already fetched.
     *
     * <p>Scoped to one applicant deliberately, not asked faculty-wide. The rule
     * it feeds removes courses from what a person may choose, and a question
     * asked across everyone would let one applicant's request take a course away
     * from a colleague who happened to be evaluated on the same one.
     *
     * @param free statuses that consume nothing — {@code DRAFT}, which has not
     *             been submitted, and {@code REJECTED}, which has to give its
     *             course back
     */
    @Query("""
            SELECT r FROM PositionRequest r
            JOIN FETCH r.linkedEvaluation
            WHERE r.applicant.id = :userId
              AND r.currentStatus NOT IN :free
            ORDER BY r.createdAt ASC
            """)
    List<PositionRequest> findConsumingEvaluations(@Param("userId") Integer userId,
            @Param("free") List<PositionRequestStatus> free);

    @Query("SELECT r FROM PositionRequest r WHERE r.applicant.id = :userId AND r.currentStatus = 'DRAFT'")
    Optional<PositionRequest> findDraftByApplicantId(@Param("userId") Integer userId);

    @Query("SELECT r FROM PositionRequest r ORDER BY r.createdAt DESC")
    List<PositionRequest> findAllOrderByCreatedAtDesc();

    @Query("SELECT r FROM PositionRequest r WHERE r.currentStatus = :status ORDER BY r.createdAt DESC")
    List<PositionRequest> findByStatus(@Param("status") PositionRequestStatus status);

    @Query("SELECT COUNT(r) FROM PositionRequest r")
    long countAll();

    @Query("SELECT MAX(r.id) FROM PositionRequest r")
    Optional<Long> findMaxId();

    @Query("SELECT r FROM PositionRequest r WHERE (LOWER(r.applicant.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.applicant.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY r.createdAt DESC")
    List<PositionRequest> searchByNameOrEmail(@Param("keyword") String keyword);

    @Query("SELECT r FROM PositionRequest r WHERE r.applicant.id = :userId AND (LOWER(r.requestCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(CAST(r.id AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.targetPosition) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.major) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY r.createdAt DESC")
    List<PositionRequest> searchByApplicant(@Param("userId") Integer userId, @Param("keyword") String keyword);

    @Query("SELECT r FROM PositionRequest r WHERE (LOWER(r.requestCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(CAST(r.id AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.applicant.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.applicant.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.targetPosition) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.major) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY r.createdAt DESC")
    List<PositionRequest> searchForAdmin(@Param("keyword") String keyword);
}
