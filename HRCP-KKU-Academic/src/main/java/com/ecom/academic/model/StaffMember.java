package com.ecom.academic.model;

import java.time.LocalDateTime;

import com.ecom.model.UserDtls;

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
@Table(name = "staff_member", indexes = {
        @Index(name = "idx_staff_name", columnList = "first_name, last_name"),
        @Index(name = "idx_staff_role_active", columnList = "staff_role, is_active")
})
public class StaffMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "academic_title")
    private String academicTitle;

    /**
     * English names, derived from the directory's combined {@code name_en} by
     * {@link com.ecom.external.service.EnglishNameSplitter}. Like the Thai name
     * and academic title above, the directory sync rewrites these on every run,
     * so a hand edit only survives until the next sync.
     */
    @Column(name = "first_name_en")
    private String firstNameEn;

    @Column(name = "last_name_en")
    private String lastNameEn;

    /** English academic title, straight from the directory's position_en. */
    @Column(name = "academic_title_en")
    private String academicTitleEn;

    @Column(name = "staff_type")
    private String staffType;

    @Column(name = "department")
    private String department;

    @Column(name = "staff_role")
    private String staffRole;

    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * The {@code fs_faculty} record this row mirrors, or null for a row someone
     * typed in by hand.
     *
     * <p>Without it there is no way to tell "the same person, re-synced" from "a
     * second person with the same name", so every sync would pile up duplicates.
     * Rows with a null value are never touched by the sync — hand-entered
     * committee members and externals stay exactly as they were entered.
     */
    @Column(name = "fs_user_id", unique = true)
    private Long fsUserId;

    /**
     * The login account this person signs with, or null when they have none.
     *
     * <p>Distinct from {@link #fsUserId}, which points at the upstream directory
     * mirror and only ever supplies a name. This one points at an account that
     * can log in, be notified, and press "sign" — without it a dean is just text
     * printed above a dotted line, which is exactly what the signature feature
     * exists to replace.
     *
     * <p>Nullable by design: external committee members are named in documents
     * but never sign in this system. The directory sync never writes this field —
     * {@code applyIdentity} touches only name, title and department — so an
     * administrator's linking decision survives every nightly run, the same way
     * {@code staffRole} does.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserDtls user;

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

    public String getFullName() {
        String fn = (firstName != null ? firstName : "");
        String ln = (lastName != null ? lastName : "");
        return (fn + " " + ln).trim();
    }

    public String getDisplayName() {
        if (academicTitle != null && !academicTitle.isEmpty()) {
            return academicTitle + getFullName();
        }
        return getFullName();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getAcademicTitle() {
        return academicTitle;
    }

    public void setAcademicTitle(String academicTitle) {
        this.academicTitle = academicTitle;
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

    public String getAcademicTitleEn() {
        return academicTitleEn;
    }

    public void setAcademicTitleEn(String academicTitleEn) {
        this.academicTitleEn = academicTitleEn;
    }

    /**
     * English counterpart of {@link #getFullName()}, or {@code null} when the
     * directory gave us no English name for this person.
     */
    public String getFullNameEn() {
        String first = firstNameEn != null ? firstNameEn.trim() : "";
        String last = lastNameEn != null ? lastNameEn.trim() : "";
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? null : combined;
    }

    public String getStaffType() {
        return staffType;
    }

    public void setStaffType(String staffType) {
        this.staffType = staffType;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStaffRole() {
        return staffRole;
    }

    public void setStaffRole(String staffRole) {
        this.staffRole = staffRole;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Long getFsUserId() {
        return fsUserId;
    }

    public void setFsUserId(Long fsUserId) {
        this.fsUserId = fsUserId;
    }

    /** True when this row is kept in step with the faculty directory. */
    public boolean isFromDirectory() {
        return fsUserId != null;
    }

    public UserDtls getUser() {
        return user;
    }

    public void setUser(UserDtls user) {
        this.user = user;
    }

    /**
     * Whether this person can be asked to sign inside the system.
     *
     * <p>Drives the signer picker: someone without an account can still be named
     * in a document, but selecting them as a signer would create a step nobody
     * could ever complete.
     */
    public boolean isSignable() {
        return user != null;
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
