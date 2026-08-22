package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.PositionDocument;

public interface PositionDocumentRepository extends JpaRepository<PositionDocument, Long> {

    @Query("SELECT d FROM PositionDocument d WHERE d.request.id = :requestId AND d.isDeleted = false ORDER BY d.documentType ASC")
    List<PositionDocument> findByRequestId(@Param("requestId") Long requestId);

    @Query("SELECT d FROM PositionDocument d WHERE d.request.id = :requestId AND d.documentType = :docType AND d.isDeleted = false ORDER BY d.isDraft ASC, d.copyNumber ASC, d.id DESC")
    List<PositionDocument> findByRequestIdAndDocType(@Param("requestId") Long requestId,
            @Param("docType") Integer docType);

    @Query("SELECT d FROM PositionDocument d WHERE d.request.id = :requestId AND d.documentType = :docType AND d.isDraft = true AND d.isDeleted = false")
    Optional<PositionDocument> findDraftByRequestIdAndDocType(@Param("requestId") Long requestId,
            @Param("docType") Integer docType);

    @Query("SELECT DISTINCT d.documentType FROM PositionDocument d WHERE d.request.id = :requestId AND d.isDraft = false AND d.isDeleted = false")
    List<Integer> findCompletedDocTypes(@Param("requestId") Long requestId);
}
