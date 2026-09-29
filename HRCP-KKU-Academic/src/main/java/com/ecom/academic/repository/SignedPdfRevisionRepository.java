package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.SignedPdfRevision;

public interface SignedPdfRevisionRepository extends JpaRepository<SignedPdfRevision, Long> {

    List<SignedPdfRevision> findBySignatureRequestIdOrderByRevisionNoAsc(Long signatureRequestId);
}
