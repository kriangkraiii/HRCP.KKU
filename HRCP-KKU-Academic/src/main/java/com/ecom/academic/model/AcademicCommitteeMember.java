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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * ผู้ทรงคุณวุฒิภายนอก / External Expert / Reader / Assessor data stored in DB.
 */
@Entity
@Table(name = "academic_committee_member", indexes = {
        @Index(name = "idx_comm_email", columnList = "email"),
        @Index(name = "idx_comm_affiliation", columnList = "affiliation"),
        @Index(name = "idx_comm_type", columnList = "committee_type"),
        @Index(name = "idx_comm_active", columnList = "is_active")
})
public class AcademicCommitteeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Thai Information ---
    @Column(nullable = false)
    private String title; // เช่น ศ., รศ.ดร., ผศ.ดร., ผศ., อ.

    @Column(nullable = false, name = "first_name")
    private String firstName;

    @Column(nullable = false, name = "last_name")
    private String lastName;

    @Column(name = "academic_position")
    private String academicPosition; // เช่น ศาสตราจารย์, รองศาสตราจารย์, ผู้ช่วยศาสตราจารย์

    @Column(nullable = false)
    private String affiliation; // สถาบัน/มหาวิทยาลัยต้นสังกัด เช่น "คณะวิทยาศาสตร์ มหาวิทยาลัยขอนแก่น"

    @Column(name = "expertise_field")
    private String expertiseField;

    // --- English Information ---
    @Column(name = "title_en")
    private String titleEn; // เช่น Prof. Dr., Assoc. Prof. Dr., Asst. Prof. Dr., Dr.

    @Column(name = "first_name_en")
    private String firstNameEn;

    @Column(name = "last_name_en")
    private String lastNameEn;

    @Column(name = "academic_position_en")
    private String academicPositionEn; // เช่น Professor, Associate Professor, Assistant Professor

    @Column(name = "affiliation_en")
    private String affiliationEn; // เช่น "Faculty of Science, Khon Kaen University"

    @Column(name = "expertise_field_en")
    private String expertiseFieldEn;

    // --- Contact & Configuration ---
    @Column(nullable = false)
    private String email; // อีเมลสำหรับรับแจ้งเตือนและ KKU SSO

    @Column(name = "phone_number")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "committee_type", nullable = false)
    private CommitteeType committeeType = CommitteeType.EXTERNAL_READER;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserDtls user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public AcademicCommitteeMember() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Full formal name with title (TH) */
    public String getFullName() {
        String t = title != null ? title.trim() : "";
        String f = firstName != null ? firstName.trim() : "";
        String l = lastName != null ? lastName.trim() : "";
        return (t + " " + f + " " + l).trim();
    }

    /** Full formal name with title (EN) */
    public String getFullNameEn() {
        String t = titleEn != null ? titleEn.trim() : "";
        String f = firstNameEn != null ? firstNameEn.trim() : "";
        String l = lastNameEn != null ? lastNameEn.trim() : "";
        String full = (t + " " + f + " " + l).trim();
        return full.isEmpty() ? getFullName() : full;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getAcademicPosition() {
        return academicPosition;
    }

    public void setAcademicPosition(String academicPosition) {
        this.academicPosition = academicPosition;
    }

    public String getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public String getExpertiseField() {
        return expertiseField;
    }

    public void setExpertiseField(String expertiseField) {
        this.expertiseField = expertiseField;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getFirstNameEn() {
        return firstNameEn;
    }

    public void setFirstNameEn(String firstNameEn) {
        this.firstNameEn = firstNameEn;
    }

    public String getLastNameEn() {
        return lastNameEn;
    }

    public void setLastNameEn(String lastNameEn) {
        this.lastNameEn = lastNameEn;
    }

    public String getAcademicPositionEn() {
        return academicPositionEn;
    }

    public void setAcademicPositionEn(String academicPositionEn) {
        this.academicPositionEn = academicPositionEn;
    }

    public String getAffiliationEn() {
        return affiliationEn;
    }

    public void setAffiliationEn(String affiliationEn) {
        this.affiliationEn = affiliationEn;
    }

    public String getExpertiseFieldEn() {
        return expertiseFieldEn;
    }

    public void setExpertiseFieldEn(String expertiseFieldEn) {
        this.expertiseFieldEn = expertiseFieldEn;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase() : null;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public CommitteeType getCommitteeType() {
        return committeeType;
    }

    public void setCommitteeType(CommitteeType committeeType) {
        this.committeeType = committeeType;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public UserDtls getUser() {
        return user;
    }

    public void setUser(UserDtls user) {
        this.user = user;
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
}
