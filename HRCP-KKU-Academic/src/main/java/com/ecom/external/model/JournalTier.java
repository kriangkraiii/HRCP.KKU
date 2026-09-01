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
 * One year of one journal's standing in a ranking system (SJR or TCI).
 *
 * <p>Rankings are published annually, so the natural key is
 * {@code (issn, provider, year)}. Both ISSN and eISSN are stored because a
 * publication record may carry either one; the enrichment step tries both.
 */
@Entity
@Table(name = "journal_tier",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_journal_tier",
                columnNames = { "issn", "provider", "tier_year" }))
public class JournalTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issn", length = 16)
    private String issn;

    @Column(name = "eissn", length = 16)
    private String eissn;

    @Column(name = "title", nullable = false, length = 1024)
    private String title;

    /** {@code SJR} or {@code TCI}. */
    @Column(name = "provider", nullable = false, length = 16)
    private String provider;

    /** {@code Q1}–{@code Q4}, {@code TCI-1}, {@code TCI-2}. */
    @Column(name = "tier", nullable = false, length = 8)
    private String tier;

    @Column(name = "tier_year", nullable = false)
    private int year;

    @Column(name = "sjr_score")
    private Double sjrScore;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public JournalTier() {
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
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

    public String getIssn() {
        return issn;
    }

    public void setIssn(String issn) {
        this.issn = issn;
    }

    public String getEissn() {
        return eissn;
    }

    public void setEissn(String eissn) {
        this.eissn = eissn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Double getSjrScore() {
        return sjrScore;
    }

    public void setSjrScore(Double sjrScore) {
        this.sjrScore = sjrScore;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
