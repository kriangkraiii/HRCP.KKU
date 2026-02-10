package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.AcademicDocument;

public interface AcademicDocumentRepository extends JpaRepository<AcademicDocument, Long> {

    List<AcademicDocument> findByRequestId(Long requestId);

    Optional<AcademicDocument> findByRequestIdAndDocumentTypeAndCopyNumber(Long requestId, Integer documentType,
            Integer copyNumber);

    List<AcademicDocument> findByRequestIdAndDocumentType(Long requestId, Integer documentType);
}
