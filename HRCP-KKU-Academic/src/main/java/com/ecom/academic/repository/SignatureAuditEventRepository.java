package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.SignatureAuditEvent;

public interface SignatureAuditEventRepository extends JpaRepository<SignatureAuditEvent, Long> {

    /** The trail for one envelope, oldest first, as it would be read in evidence. */
    List<SignatureAuditEvent> findBySignatureRequestIdOrderByCreatedAtAsc(Long signatureRequestId);
}
