package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ecom.academic.model.AdminFolder;

public interface AdminFolderRepository extends JpaRepository<AdminFolder, Long> {

    List<AdminFolder> findByParentIsNullOrderByNameAsc();

    List<AdminFolder> findByParentIdOrderByNameAsc(Long parentId);
}
