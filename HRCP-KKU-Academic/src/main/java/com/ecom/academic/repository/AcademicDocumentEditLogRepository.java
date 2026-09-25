package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.AcademicDocumentEditLog;
import com.ecom.academic.model.AcademicRequest;

public interface AcademicDocumentEditLogRepository extends JpaRepository<AcademicDocumentEditLog, Long> {

    List<AcademicDocumentEditLog> findByRequestOrderByEditedAtDesc(AcademicRequest request);

    Optional<AcademicDocumentEditLog> findFirstByRequestAndDocumentTypeOrderByEditedAtDescIdDesc(
            AcademicRequest request, Integer documentType);
}
