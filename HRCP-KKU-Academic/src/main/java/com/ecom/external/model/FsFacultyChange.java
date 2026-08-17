package com.ecom.external.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A proposed edit to a faculty record, waiting for an administrator's decision.
 *
 * <p>Faculty details flow into official promotion documents, so an upstream edit
 * — a corrected surname, a changed academic position — must not silently rewrite
 * what a request already relies on. The sync therefore stages changes here
 * instead of writing straight to {@link FsFaculty}; only an approval applies them.
 *
 * <p>New faculty are exempt: there is no local record to overwrite, and holding
 * them for review would lock a new staff member out of the system. Only updates
 * to records we already hold land in this queue.
 */
@Entity
@Table(name = "fs_faculty_change", indexes = {
        // The review page lists PENDING newest-first; this is its only query.
        @Index(name = "idx_fs_change_status_detected", columnList = "status, detected_at"),
        // Re-syncs look up the open change for a person before creating another.
        @Index(name = "idx_fs_change_user_status", columnList = "fs_user_id, status")
})
public class FsFacultyChange {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which faculty record this change is about. */
    @Column(name = "fs_user_id", nullable = false)
    private Long fsUserId;

    /** Name shown on the review page, captured at detection time. */
    @Column(name = "display_name", length = 512)
    private String displayName;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_PENDING;

    /**
     * Field-by-field difference as JSON:
     * {@code {"positionTitle":{"old":"ผศ.","new":"รศ."}}}. Rendered directly by
     * the review page so an admin sees exactly what would change.
     */
    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;

    /** The full upstream record, applied verbatim on approval. */
    @Column(name = "proposed_json", columnDefinition = "TEXT")
    private String proposedJson;

    @Column(name = "field_count")
    private Integer fieldCount;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** E-mail of the administrator who decided. */
    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    public FsFacultyChange() {
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFsUserId() {
        return fsUserId;
    }

    public void setFsUserId(Long fsUserId) {
        this.fsUserId = fsUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDiffJson() {
        return diffJson;
    }

    public void setDiffJson(String diffJson) {
        this.diffJson = diffJson;
    }

    public String getProposedJson() {
        return proposedJson;
    }

    public void setProposedJson(String proposedJson) {
        this.proposedJson = proposedJson;
    }

    public Integer getFieldCount() {
        return fieldCount;
    }

    public void setFieldCount(Integer fieldCount) {
        this.fieldCount = fieldCount;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }
}
