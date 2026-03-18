package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.PositionAttachment;

public interface PositionAttachmentRepository extends JpaRepository<PositionAttachment, Long> {

    @Query("SELECT a FROM PositionAttachment a WHERE a.request.id = :requestId AND (a.isDeleted = false OR a.isDeleted IS NULL) ORDER BY a.uploadedAt DESC")
    List<PositionAttachment> findActiveByRequestId(@Param("requestId") Long requestId);

    @Query("SELECT COUNT(a) FROM PositionAttachment a WHERE a.request.id = :requestId AND (a.isDeleted = false OR a.isDeleted IS NULL)")
    long countActiveByRequestId(@Param("requestId") Long requestId);
}
