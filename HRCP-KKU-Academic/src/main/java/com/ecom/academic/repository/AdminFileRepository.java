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
}
