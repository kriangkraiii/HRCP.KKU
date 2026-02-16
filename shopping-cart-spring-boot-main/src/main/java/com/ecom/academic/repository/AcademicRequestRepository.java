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
}
