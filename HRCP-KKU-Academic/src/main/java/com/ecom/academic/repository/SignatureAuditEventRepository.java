package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.SignatureAuditEvent;
import com.ecom.academic.model.SignatureModule;

public interface SignatureAuditEventRepository extends JpaRepository<SignatureAuditEvent, Long> {

    /** The trail for one envelope, oldest first, as it would be read in evidence. */
    List<SignatureAuditEvent> findBySignatureRequestIdOrderByCreatedAtAsc(Long signatureRequestId);

    /** ทุกเหตุการณ์ลงนามของคำร้องหนึ่ง (ทุกเอกสาร ทุกซอง) ใหม่สุดก่อน — แท็บประวัติการลงนามของแอดมิน */
    @Query("""
            SELECT e FROM SignatureAuditEvent e
            JOIN FETCH e.signatureRequest r
            LEFT JOIN FETCH e.actor
            WHERE r.module = :module AND r.requestId = :requestId
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<SignatureAuditEvent> findTrailOfRequest(@Param("module") SignatureModule module,
            @Param("requestId") Long requestId);
}
