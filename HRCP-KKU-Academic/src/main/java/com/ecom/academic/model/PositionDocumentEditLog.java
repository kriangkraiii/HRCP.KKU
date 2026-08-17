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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "position_document_edit_log", indexes = {
        @Index(name = "idx_pos_doc_log_req_date", columnList = "request_id, edited_at DESC")
})
public class PositionDocumentEditLog {

    public enum EditAction {
        CREATED("สร้าง", "#2e7d32", "fa-plus-circle"),
        UPDATED("แก้ไข", "#1565c0", "fa-edit"),
        DRAFT_SAVED("บันทึกร่าง", "#f57f17", "fa-save");

        private final String thaiLabel;
        private final String color;
        private final String icon;

        EditAction(String thaiLabel, String color, String icon) {
            this.thaiLabel = thaiLabel;
            this.color = color;
            this.icon = icon;
        }

        public String getThaiLabel() { return thaiLabel; }
        public String getColor() { return color; }
        public String getIcon() { return icon; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private PositionRequest request;

    @Column(name = "document_type", nullable = false)
    private Integer documentType;

    @Column(name = "document_label")
    private String documentLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, columnDefinition = "varchar(30)")
    private EditAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edited_by")
    private UserDtls editedBy;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @PrePersist
    protected void onCreate() {
        editedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PositionRequest getRequest() { return request; }
    public void setRequest(PositionRequest request) { this.request = request; }

    public Integer getDocumentType() { return documentType; }
    public void setDocumentType(Integer documentType) { this.documentType = documentType; }

    public String getDocumentLabel() { return documentLabel; }
    public void setDocumentLabel(String documentLabel) { this.documentLabel = documentLabel; }

    public EditAction getAction() { return action; }
    public void setAction(EditAction action) { this.action = action; }

    public UserDtls getEditedBy() { return editedBy; }
    public void setEditedBy(UserDtls editedBy) { this.editedBy = editedBy; }

    public LocalDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(LocalDateTime editedAt) { this.editedAt = editedAt; }
}
