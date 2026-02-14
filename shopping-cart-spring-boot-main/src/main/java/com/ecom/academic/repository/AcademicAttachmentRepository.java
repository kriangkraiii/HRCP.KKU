package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.AcademicAttachment;

public interface AcademicAttachmentRepository extends JpaRepository<AcademicAttachment, Long> {

    List<AcademicAttachment> findByRequestIdOrderByUploadedAtDesc(Long requestId);

    long countByRequestId(Long requestId);
}
