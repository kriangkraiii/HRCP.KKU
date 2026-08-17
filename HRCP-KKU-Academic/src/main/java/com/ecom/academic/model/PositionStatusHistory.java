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
@Table(name = "position_status_history", indexes = {
        @Index(name = "idx_pos_hist_req_date", columnList = "request_id, changed_at DESC")
})
public class PositionStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private PositionRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", columnDefinition = "varchar(50)")
    private PositionRequestStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, columnDefinition = "varchar(50)")
    private PositionRequestStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private UserDtls changedBy;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @Column(name = "note")
    private String note;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
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

    public PositionRequestStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(PositionRequestStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public PositionRequestStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(PositionRequestStatus newStatus) {
        this.newStatus = newStatus;
    }

    public UserDtls getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(UserDtls changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
