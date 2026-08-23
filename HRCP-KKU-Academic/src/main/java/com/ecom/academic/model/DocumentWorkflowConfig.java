package com.ecom.academic.model;

import java.time.LocalDateTime;

import com.ecom.model.UserDtls;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Stores custom signer configuration and ordering for a document slot.
 */
@Entity
@Table(name = "document_workflow_config", uniqueConstraints = {
    @UniqueConstraint(name = "uq_doc_workflow_slot", columnNames = {"module", "document_type", "slot_key"})
})
public class DocumentWorkflowConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 20)
    private SignatureModule module;

    @Column(name = "document_type", nullable = false)
    private int documentType;

    @Column(name = "slot_key", nullable = false, length = 50)
    private String slotKey;

    @Column(name = "role_label", nullable = false, length = 150)
    private String roleLabel;

    @Column(name = "anchor_placeholder", nullable = false, length = 100)
    private String anchorPlaceholder;

    @Column(name = "default_staff_role", length = 50)
    private String defaultStaffRole;

    @Column(name = "step_order", nullable = false)
    private int stepOrder = 1;

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_signer_user_id")
    private UserDtls defaultSigner;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DocumentWorkflowConfig() {
    }

    public DocumentWorkflowConfig(SignatureModule module, int documentType, String slotKey,
            String roleLabel, String anchorPlaceholder, String defaultStaffRole,
            int stepOrder, boolean isEnabled, UserDtls defaultSigner) {
        this.module = module;
        this.documentType = documentType;
        this.slotKey = slotKey;
        this.roleLabel = roleLabel;
        this.anchorPlaceholder = anchorPlaceholder;
        this.defaultStaffRole = defaultStaffRole;
        this.stepOrder = stepOrder;
        this.isEnabled = isEnabled;
        this.defaultSigner = defaultSigner;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SignatureModule getModule() {
        return module;
    }

    public void setModule(SignatureModule module) {
        this.module = module;
    }

    public int getDocumentType() {
        return documentType;
    }

    public void setDocumentType(int documentType) {
        this.documentType = documentType;
    }

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public void setRoleLabel(String roleLabel) {
        this.roleLabel = roleLabel;
    }

    public String getAnchorPlaceholder() {
        return anchorPlaceholder;
    }

    public void setAnchorPlaceholder(String anchorPlaceholder) {
        this.anchorPlaceholder = anchorPlaceholder;
    }

    public String getDefaultStaffRole() {
        return defaultStaffRole;
    }

    public void setDefaultStaffRole(String defaultStaffRole) {
        this.defaultStaffRole = defaultStaffRole;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(int stepOrder) {
        this.stepOrder = stepOrder;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public UserDtls getDefaultSigner() {
        return defaultSigner;
    }

    public void setDefaultSigner(UserDtls defaultSigner) {
        this.defaultSigner = defaultSigner;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
