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

/**
 * One thing that happened to a signing envelope. Append-only.
 *
 * <p>Separate from {@code admin_logs}, which records administrator actions for
 * operational review. This is evidence about a specific document's signatures
 * and has to survive alongside it, including the events that are not
 * administrator actions at all — a signer opening the document, a reminder
 * going out, a chain expiring.
 */
@Entity
@Table(name = "signature_audit_event", indexes = {
        @Index(name = "idx_sig_audit_request", columnList = "signature_request_id, created_at")
})
public class SignatureAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mirrors the {@code ON DELETE CASCADE} in V6 so the schema Hibernate
     * generates for tests behaves like the real one. The trail is meaningless
     * without the envelope it describes, and is never deleted on its own.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signature_request_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private SignatureRequest signatureRequest;

    /** The step involved, when the event concerns one. Not a FK-mapped relation
     *  so an event can outlive a step row. */
    @Column(name = "step_id")
    private Long stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30, nullable = false)
    private SignatureAuditEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserDtls actor;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

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

    public SignatureRequest getSignatureRequest() {
        return signatureRequest;
    }

    public void setSignatureRequest(SignatureRequest signatureRequest) {
        this.signatureRequest = signatureRequest;
    }

    public Long getStepId() {
        return stepId;
    }

    public void setStepId(Long stepId) {
        this.stepId = stepId;
    }

    public SignatureAuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(SignatureAuditEventType eventType) {
        this.eventType = eventType;
    }

    public UserDtls getActor() {
        return actor;
    }

    public void setActor(UserDtls actor) {
        this.actor = actor;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
