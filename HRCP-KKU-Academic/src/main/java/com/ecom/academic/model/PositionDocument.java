package com.ecom.academic.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@EntityListeners(com.ecom.search.index.SearchIndexListener.class)
@Entity
@Table(name = "position_document", indexes = {
        @Index(name = "idx_pos_doc_req_type", columnList = "request_id, document_type"),
        @Index(name = "idx_pos_doc_req_draft", columnList = "request_id, is_draft, is_deleted")
})
public class PositionDocument {

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

    
    @Column(name = "json_data", columnDefinition = "TEXT")
    private String jsonData;

    @Column(name = "generated_file_path")
    private String generatedFilePath;

    @Column(name = "copy_number")
    private Integer copyNumber;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft = false;

    @Column(name = "filled_by")
    private String filledBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * เวลาที่แอดมินส่งเอกสารฉบับนี้กลับมาให้ผู้ยื่นแก้ไข
     *
     * <p>null = ผู้ยื่นแก้ไม่ได้ (หลังส่งคำร้องแล้วเอกสารอยู่ในมือแอดมิน) — ค่านี้คือประตูเดียวที่
     * เปิดให้ผู้ยื่นกลับมาแก้เอกสารหลังส่งคำร้อง เหมือน {@code AcademicDocument} ของเฟส 1
     * ก่อนหน้านี้เฟส 2 ไม่มีประตูนี้เลย ผู้ยื่นจึงแก้เอกสารของตัวเองได้ตลอดเวลาแม้ลงนามไปแล้ว
     */
    @Column(name = "revision_requested_at")
    private LocalDateTime revisionRequestedAt;

    /** เหตุผลที่แอดมินส่งกลับมาให้แก้ไข — แสดงให้ผู้ยื่นเห็นบนหน้าเอกสาร */
    @Column(name = "revision_note", length = 500)
    private String revisionNote;

    /** เวลาที่ผู้ยื่นกด "ยื่นการแก้ไข" — ใช้กับเอกสารที่ไม่มีช่องลงนามของผู้ยื่น (ดู V30) */
    @Column(name = "revision_submitted_at")
    private LocalDateTime revisionSubmittedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PositionRequest getRequest() {
        return request;
    }

    public void setRequest(PositionRequest request) {
        this.request = request;
    }

    public Integer getDocumentType() {
        return documentType;
    }

    public void setDocumentType(Integer documentType) {
        this.documentType = documentType;
    }

    public String getDocumentLabel() {
        return documentLabel;
    }

    public void setDocumentLabel(String documentLabel) {
        this.documentLabel = documentLabel;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }

    public String getGeneratedFilePath() {
        return generatedFilePath;
    }

    public void setGeneratedFilePath(String generatedFilePath) {
        this.generatedFilePath = generatedFilePath;
    }

    public Integer getCopyNumber() {
        return copyNumber;
    }

    public void setCopyNumber(Integer copyNumber) {
        this.copyNumber = copyNumber;
    }

    public Boolean getIsDraft() {
        return isDraft;
    }

    public void setIsDraft(Boolean isDraft) {
        this.isDraft = isDraft;
    }

    public String getFilledBy() {
        return filledBy;
    }

    public void setFilledBy(String filledBy) {
        this.filledBy = filledBy;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getRevisionRequestedAt() {
        return revisionRequestedAt;
    }

    public void setRevisionRequestedAt(LocalDateTime revisionRequestedAt) {
        this.revisionRequestedAt = revisionRequestedAt;
    }

    public String getRevisionNote() {
        return revisionNote;
    }

    public void setRevisionNote(String revisionNote) {
        this.revisionNote = revisionNote;
    }

    public LocalDateTime getRevisionSubmittedAt() {
        return revisionSubmittedAt;
    }

    public void setRevisionSubmittedAt(LocalDateTime revisionSubmittedAt) {
        this.revisionSubmittedAt = revisionSubmittedAt;
    }

    /** ผู้ยื่นกดยื่นการแก้ไขรอบล่าสุดแล้ว (ส่งกลับรอบใหม่ = ยังไม่ได้ยื่น) */
    public boolean isRevisionSubmitted() {
        return revisionRequestedAt != null && revisionSubmittedAt != null
                && !revisionSubmittedAt.isBefore(revisionRequestedAt);
    }

    /**
     * แอดมินส่งเอกสารฉบับนี้กลับมาให้ผู้ยื่นแก้ และผู้ยื่นยังไม่ได้ยื่นการแก้ไข
     *
     * <p>ยื่นแล้วประตูแก้ไขปิด เอกสารกลับไปอยู่ในมือเจ้าหน้าที่เหมือนตอนส่งคำร้องครั้งแรก
     */
    public boolean isRevisionRequested() {
        return revisionRequestedAt != null && !isRevisionSubmitted();
    }
}
