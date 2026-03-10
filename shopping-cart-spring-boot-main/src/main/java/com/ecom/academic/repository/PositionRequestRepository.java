package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;

public interface PositionRequestRepository extends JpaRepository<PositionRequest, Long> {

    @Query("SELECT r FROM PositionRequest r WHERE r.applicant.id = :userId ORDER BY r.createdAt DESC")
    List<PositionRequest> findByApplicantId(@Param("userId") Integer userId);

    @Query("SELECT r FROM PositionRequest r WHERE r.applicant.id = :userId AND r.currentStatus NOT IN :terminalStatuses")
    Optional<PositionRequest> findActiveByApplicantId(@Param("userId") Integer userId,
            @Param("terminalStatuses") List<PositionRequestStatus> terminalStatuses);

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
}
