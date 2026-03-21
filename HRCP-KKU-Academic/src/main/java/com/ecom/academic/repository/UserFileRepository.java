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
}
