package com.ecom.external.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Maps a faculty member to an external identifier that cannot be inferred
 * automatically — a DBLP PID, an ORCID, or a Researcher ID.
 *
 * <p>DBLP has no affiliation field, so the harvester cannot discover which
 * papers belong to a KKU author by itself. An administrator seeds the
 * association here, and the DBLP adapter reads it at harvest time.
 *
 * <p>ORCID and Researcher ID mappings are not used by any adapter today,
 * but storing them in the same table is cheaper than adding them later.
 */
@Entity
@Table(name = "external_author_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ext_author_provider_pid",
                columnNames = { "fs_user_id", "provider", "external_pid" }))
public class ExternalAuthorMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fs_user_id", nullable = false)
    private Long fsUserId;

    /** {@code DBLP}, {@code ORCID}, or {@code RESEARCHER_ID}. */
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    /** The identifier on that platform, e.g. {@code homepages/1/2/3456}. */
    @Column(name = "external_pid", nullable = false, length = 512)
    private String externalPid;

    @Column(name = "display_name", length = 512)
    private String displayName;

    @Column(name = "verified")
    private boolean verified;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ExternalAuthorMapping() {
    }

    public ExternalAuthorMapping(Long fsUserId, String provider, String externalPid) {
        this.fsUserId = fsUserId;
        this.provider = provider;
        this.externalPid = externalPid;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternalPid() {
        return externalPid;
    }

    public void setExternalPid(String externalPid) {
        this.externalPid = externalPid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
