package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;

public interface AcademicRequestRepository extends JpaRepository<AcademicRequest, Long> {


    /**
     * Loads a request with its applicant already fetched.
     *
     * <p>For background work: a notification thread must not be handed an
     * entity belonging to the request thread's still-open session, so it
     * re-reads its own copy with everything it needs already attached.
     */
    @Query("SELECT r FROM AcademicRequest r LEFT JOIN FETCH r.applicant WHERE r.id = :id")
    Optional<AcademicRequest> findByIdWithApplicant(@Param("id") Long id);

    List<AcademicRequest> findByApplicantIdOrderByCreatedAtDesc(Integer applicantId);

    List<AcademicRequest> findByCurrentStatus(RequestStatus status);

    List<AcademicRequest> findAllByOrderByCreatedAtDesc();

    List<AcademicRequest> findByApplicantIdAndCurrentStatusNotIn(Integer applicantId, List<RequestStatus> statuses);

    List<AcademicRequest> findByApplicantIdAndCurrentStatus(Integer applicantId, RequestStatus status);

    /**
     * Requests whose applicant matches by name or e-mail.
     *
     * <p>Backs the admin request-queue filter. Every name column is listed
     * because {@code applicant.name} is the legacy combined column and SSO never
     * writes it — searching it alone returns nothing for anyone the directory
     * provisioned. See {@link com.ecom.repository.UserRepository#searchUsers}.
     *
     * @param pattern a {@code %term%} pattern from
     *                {@link com.ecom.search.service.SearchQueryNormalizer#likePattern}
     */
    @Query("""
            SELECT r FROM AcademicRequest r
            WHERE LOWER(COALESCE(r.applicant.firstName, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastName, '')) LIKE :pattern
               OR LOWER(CONCAT(COALESCE(r.applicant.firstName, ''), ' ', COALESCE(r.applicant.lastName, ''))) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.firstNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.name, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.email, '')) LIKE :pattern
            ORDER BY r.createdAt DESC
            """)
    List<AcademicRequest> searchByNameOrEmail(@Param("pattern") String pattern);

    /** One applicant's own requests, matched on request code or id. */
    @Query("""
            SELECT r FROM AcademicRequest r
            WHERE r.applicant.id = :userId
              AND (LOWER(COALESCE(r.requestCode, '')) LIKE :pattern
                OR LOWER(CAST(r.id AS string)) LIKE :pattern)
            ORDER BY r.createdAt DESC
            """)
    List<AcademicRequest> searchByApplicant(@Param("userId") Integer userId, @Param("pattern") String pattern);

    /** Every request, matched on code, id, or any of the applicant's names. */
    @Query("""
            SELECT r FROM AcademicRequest r
            WHERE LOWER(COALESCE(r.requestCode, '')) LIKE :pattern
               OR LOWER(CAST(r.id AS string)) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.firstName, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastName, '')) LIKE :pattern
               OR LOWER(CONCAT(COALESCE(r.applicant.firstName, ''), ' ', COALESCE(r.applicant.lastName, ''))) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.firstNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.name, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.email, '')) LIKE :pattern
            ORDER BY r.createdAt DESC
            """)
    List<AcademicRequest> searchForAdmin(@Param("pattern") String pattern);

    /**
     * Finished evaluations whose result lapses before a given moment.
     *
     * <p>Backs the expiry reminder job. Rows with no computed expiry are left
     * out: there is nothing to warn anyone about yet.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM AcademicRequest r
            WHERE r.evaluationExpiryDate IS NOT NULL
              AND r.evaluationExpiryDate < :before
              AND r.currentStatus IN :statuses
            ORDER BY r.evaluationExpiryDate ASC
            """)
    List<AcademicRequest> findExpiringBefore(
            @org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before,
            @org.springframework.data.repository.query.Param("statuses") List<RequestStatus> statuses);
}
