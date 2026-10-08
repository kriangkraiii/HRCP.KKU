package com.ecom.academic.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One revision of an envelope's signed PDF: the bytes it appended to the file.
 * Append-only; revision N's file is the deltas of 0..N concatenated.
 */
@Entity
@Table(name = "signed_pdf_revision", uniqueConstraints = @UniqueConstraint(
        name = "uq_signed_pdf_revision", columnNames = { "signature_request_id", "revision_no" }))
public class SignedPdfRevision {

    public enum Kind {
        /** Revision 0: the rendered document with every field in place. */
        BASE,
        /** A signer's signature (with their own fields). */
        SIGN,
        /** Values filled by the office, unsigned. */
        FILL,
        /** The final signature that freezes the document. */
        LOCK,
        /** Certificates and their status for the signature before it (PAdES LT, document security store). */
        LTV
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plain id: the row is only ever read through its envelope. */
    @Column(name = "signature_request_id", nullable = false)
    private Long signatureRequestId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10, nullable = false)
    private Kind kind;

    @Column(name = "step_id")
    private Long stepId;

    @Column(name = "actor_user_id")
    private Integer actorUserId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Column(name = "delta", nullable = false, columnDefinition = "bytea")
    private byte[] delta;

    @Column(name = "total_length", nullable = false)
    private long totalLength;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "cert_fingerprint", length = 64)
    private String certFingerprint;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SignedPdfRevision() {
    }

    public SignedPdfRevision(Long signatureRequestId, int revisionNo, Kind kind, Long stepId, Integer actorUserId,
            byte[] delta, long totalLength, String sha256, String certFingerprint) {
        this.signatureRequestId = signatureRequestId;
        this.revisionNo = revisionNo;
        this.kind = kind;
        this.stepId = stepId;
        this.actorUserId = actorUserId;
        this.delta = delta;
        this.totalLength = totalLength;
        this.sha256 = sha256;
        this.certFingerprint = certFingerprint;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSignatureRequestId() {
        return signatureRequestId;
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getStepId() {
        return stepId;
    }

    public Integer getActorUserId() {
        return actorUserId;
    }

    public byte[] getDelta() {
        return delta;
    }

    public long getTotalLength() {
        return totalLength;
    }

    public String getSha256() {
        return sha256;
    }

    public String getCertFingerprint() {
        return certFingerprint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
