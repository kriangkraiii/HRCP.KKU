package com.ecom.academic.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Something that happened to a user's .p12 certificate. Append-only.
 *
 * <p>Plain ids rather than relations so a row outlives the certificate or user it
 * names — the point of the trail is to answer questions after the fact.
 */
@Entity
@Table(name = "digital_certificate_audit", indexes = {
        @Index(name = "idx_digital_cert_audit_user", columnList = "user_id, created_at")
})
public class DigitalCertificateAudit {

    public enum Event {
        /** A .p12 was installed. */
        UPLOADED,
        DEACTIVATED,
        PIN_CHANGED,
        PIN_OK,
        PIN_WRONG,
        /** Refused because of too many wrong PINs, without trying the PIN. */
        PIN_LOCKED,
        /** The server decrypted the saved PIN to sign on the user's behalf. */
        STORED_PIN_USED,
        PDF_SIGNED,
        /** A signed step ended up without a digital signature in the PDF. */
        PDF_SIGN_SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "certificate_id")
    private Long certificateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", length = 40, nullable = false)
    private Event event;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DigitalCertificateAudit() {
    }

    public DigitalCertificateAudit(Integer userId, Long certificateId, Event event, String detail, String ipAddress) {
        this.userId = userId;
        this.certificateId = certificateId;
        this.event = event;
        this.detail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
        this.ipAddress = ipAddress == null || ipAddress.length() <= 45 ? ipAddress : ipAddress.substring(0, 45);
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

    public Integer getUserId() {
        return userId;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public Event getEvent() {
        return event;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
