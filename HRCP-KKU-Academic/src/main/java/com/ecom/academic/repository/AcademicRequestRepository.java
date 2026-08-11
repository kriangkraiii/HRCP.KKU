package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;

public interface AcademicRequestRepository extends JpaRepository<AcademicRequest, Long> {

    List<AcademicRequest> findByApplicantIdOrderByCreatedAtDesc(Integer applicantId);

    List<AcademicRequest> findByCurrentStatus(RequestStatus status);

    List<AcademicRequest> findAllByOrderByCreatedAtDesc();

    List<AcademicRequest> findByApplicantIdAndCurrentStatusNotIn(Integer applicantId, List<RequestStatus> statuses);

    List<AcademicRequest> findByApplicantIdAndCurrentStatus(Integer applicantId, RequestStatus status);

    List<AcademicRequest> findByApplicantNameContainingIgnoreCaseOrderByCreatedAtDesc(String name);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM AcademicRequest r WHERE (LOWER(r.applicant.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.applicant.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY r.createdAt DESC")
    List<AcademicRequest> searchByNameOrEmail(@org.springframework.data.repository.query.Param("keyword") String keyword);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM AcademicRequest r WHERE r.applicant.id = :userId AND (LOWER(r.requestCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(CAST(r.id AS string)) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY r.createdAt DESC")
    List<AcademicRequest> searchByApplicant(@org.springframework.data.repository.query.Param("userId") Integer userId, @org.springframework.data.repository.query.Param("keyword") String keyword);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM AcademicRequest r WHERE (LOWER(r.requestCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(CAST(r.id AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.applicant.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.applicant.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY r.createdAt DESC")
    List<AcademicRequest> searchForAdmin(@org.springframework.data.repository.query.Param("keyword") String keyword);
}
