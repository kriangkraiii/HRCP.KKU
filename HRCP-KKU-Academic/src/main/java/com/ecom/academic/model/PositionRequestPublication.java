package com.ecom.academic.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One publication put forward by one position request.
 *
 * <p>Until this table existed the connection was never recorded at all: the
 * Scopus picker asked the server for a formatted citation, wrote that text into
 * an ordinary input, and dropped the publication id in the browser. So nothing
 * could answer "which paper is this line?" — and the rule everyone agreed on,
 * that work submitted once cannot be submitted again, had nothing to check
 * against (GAP-11).
 *
 * <p>The rule itself is GAP-12: a publication on a request that has left
 * {@code DRAFT} is spent and disappears from the picker. There is no refusal
 * status to give it back: the flow only sends a request back for revision, and a
 * request under revision still holds the work it put forward.
 *
 * <p>{@code publicationId} is a plain number, not a {@code @ManyToOne} to
 * {@code ScopusPublication}. That entity belongs to the {@code external} module,
 * which reads this table to enforce the rule; mapping it here would point the
 * dependency both ways. It is also a mirror of an upstream feed that the sync
 * may delete and recreate, and a foreign key would turn that routine event into
 * a failed sync.
 */
@Entity
@Table(name = "position_request_publication",
        uniqueConstraints = @UniqueConstraint(name = "uq_pos_req_pub",
                columnNames = { "request_id", "publication_id" }),
        indexes = @Index(name = "idx_pos_req_pub_publication", columnList = "publication_id"))
public class PositionRequestPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private PositionRequest request;

    @Column(name = "publication_id", nullable = false)
    private Long publicationId;

    /** Which form the citation sits on. Today always 1 (ก.พ.ว.มข.03). */
    @Column(name = "document_type", nullable = false)
    private Integer documentType;

    /** The row number within that form, for tracing a link back to what the applicant sees. */
    @Column(name = "slot_index")
    private Integer slotIndex;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PositionRequestPublication() {
    }

    public PositionRequestPublication(PositionRequest request, Long publicationId,
            Integer documentType, Integer slotIndex) {
        this.request = request;
        this.publicationId = publicationId;
        this.documentType = documentType;
        this.slotIndex = slotIndex;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

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

    public Long getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(Long publicationId) {
        this.publicationId = publicationId;
    }

    public Integer getDocumentType() {
        return documentType;
    }

    public void setDocumentType(Integer documentType) {
        this.documentType = documentType;
    }

    public Integer getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(Integer slotIndex) {
        this.slotIndex = slotIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
