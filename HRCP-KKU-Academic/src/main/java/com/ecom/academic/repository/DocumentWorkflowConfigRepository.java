package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ecom.academic.model.DocumentWorkflowConfig;
import com.ecom.academic.model.SignatureModule;

@Repository
public interface DocumentWorkflowConfigRepository extends JpaRepository<DocumentWorkflowConfig, Long> {

    @Query("SELECT c FROM DocumentWorkflowConfig c LEFT JOIN FETCH c.defaultSigner WHERE c.module = :module AND c.documentType = :docType ORDER BY c.stepOrder ASC")
    List<DocumentWorkflowConfig> findByModuleAndDocumentType(
            @Param("module") SignatureModule module,
            @Param("docType") int documentType);

    @Query("SELECT c FROM DocumentWorkflowConfig c LEFT JOIN FETCH c.defaultSigner ORDER BY c.module ASC, c.documentType ASC, c.stepOrder ASC")
    List<DocumentWorkflowConfig> findAllWithSigner();

    Optional<DocumentWorkflowConfig> findByModuleAndDocumentTypeAndSlotKey(
            SignatureModule module, int documentType, String slotKey);

    void deleteByModuleAndDocumentType(SignatureModule module, int documentType);
}
