package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ecom.academic.model.AdminFile;

public interface AdminFileRepository extends JpaRepository<AdminFile, Long> {

    List<AdminFile> findByFolderIsNullAndIsDeletedFalseOrderByOriginalFilenameAsc();

    List<AdminFile> findByFolderIdAndIsDeletedFalseOrderByOriginalFilenameAsc(Long folderId);

    List<AdminFile> findByIsDeletedTrue();

    long countByIsDeletedTrue();

    List<AdminFile> findByIsDeletedFalse();

    @org.springframework.data.jpa.repository.Query("SELECT f FROM AdminFile f WHERE f.isDeleted = false AND LOWER(f.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY f.originalFilename ASC")
    List<AdminFile> searchAdminFiles(@org.springframework.data.repository.query.Param("keyword") String keyword);

    List<AdminFile> findByIsDeletedTrueAndDeletedAtBefore(java.time.LocalDateTime cutoff);
}
