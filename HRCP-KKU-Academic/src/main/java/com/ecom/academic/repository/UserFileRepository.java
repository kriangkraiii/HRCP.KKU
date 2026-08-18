package com.ecom.academic.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ecom.academic.model.UserFile;

public interface UserFileRepository extends JpaRepository<UserFile, Long> {
    List<UserFile> findByOwnerIdAndFolderIsNullAndIsDeletedFalseOrderByOriginalFilenameAsc(Integer ownerId);
    List<UserFile> findByOwnerIdAndFolderIdAndIsDeletedFalseOrderByOriginalFilenameAsc(Integer ownerId, Long folderId);
    List<UserFile> findByOwnerIdAndIsDeletedTrue(Integer ownerId);
    long countByOwnerIdAndIsDeletedTrue(Integer ownerId);
    List<UserFile> findByOwnerIdAndIsDeletedFalse(Integer ownerId);

    @org.springframework.data.jpa.repository.Query("SELECT f FROM UserFile f WHERE f.ownerId = :ownerId AND f.isDeleted = false AND LOWER(f.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY f.originalFilename ASC")
    List<UserFile> searchByOwner(@org.springframework.data.repository.query.Param("ownerId") Integer ownerId, @org.springframework.data.repository.query.Param("keyword") String keyword);

    List<UserFile> findByIsDeletedTrueAndDeletedAtBefore(java.time.LocalDateTime cutoff);
}
