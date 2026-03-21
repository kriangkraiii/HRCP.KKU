package com.ecom.academic.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ecom.academic.model.UserFolder;

public interface UserFolderRepository extends JpaRepository<UserFolder, Long> {
    List<UserFolder> findByOwnerIdAndParentIsNullOrderByNameAsc(Integer ownerId);
    List<UserFolder> findByOwnerIdAndParentIdOrderByNameAsc(Integer ownerId, Long parentId);
}
