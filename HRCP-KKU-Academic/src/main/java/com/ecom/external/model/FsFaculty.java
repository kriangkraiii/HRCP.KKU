package com.ecom.external.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Local mirror of a faculty record from the Fund Management platform
 * ({@code GET /api/ext/v1/users}). Refreshed by a nightly cron — never edited
 * by hand, so a sync can always overwrite it.
 *
 * <p>The upstream {@code user_id} is the primary key: it is the same id the
 * Scopus endpoint keys publications on, so keeping it avoids a translation
 * table on every join.
 */
@Entity
@Table(name = "fs_faculty", indexes = {
        // Local UserDtls is matched to this table by e-mail on every request that
        // needs autofill or publication scoping — by far the hottest lookup here.
        @Index(name = "idx_fs_faculty_email", columnList = "email"),
        @Index(name = "idx_fs_faculty_scopus_id", columnList = "scopus_id"),
        // Incremental sync stores MAX(source_updated_at) and asks for newer rows.
        @Index(name = "idx_fs_faculty_source_updated", columnList = "source_updated_at")
})
public class FsFaculty {

    /** Upstream {@code user_id}. Stable across syncs — not generated locally. */
    @Id
    @Column(name = "fs_user_id")
    private Long fsUserId;

    @Column(name = "prefix", length = 100)
    private String prefix;

    @Column(name = "user_fname", length = 255)
    private String firstName;

    @Column(name = "user_lname", length = 255)
    private String lastName;

    @Column(name = "gender", length = 32)
    private String gender;

    /**
     * Stored already trimmed and lower-cased: the upstream feed has records with
     * a trailing space in the domain, which silently broke e-mail matching.
     */
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "tel", length = 64)
    private String tel;

    @Column(name = "tel_format", length = 64)
    private String telFormat;

    @Column(name = "position_title", length = 255)
    private String positionTitle;

    @Column(name = "position_en", length = 255)
    private String positionEn;

    @Column(name = "prefix_position_en", length = 100)
    private String prefixPositionEn;

    @Column(name = "manage_position", length = 255)
    private String managePosition;

    @Column(name = "name_en", length = 255)
    private String nameEn;

    @Column(name = "suffix_en", length = 100)
    private String suffixEn;

    @Column(name = "scopus_id", length = 64)
    private String scopusId;

    @Column(name = "scholar_author_id", length = 64)
    private String scholarAuthorId;

    @Column(name = "lab_name", length = 512)
    private String labName;

    @Column(name = "room", length = 128)
    private String room;

    @Column(name = "cp_web_id", length = 512)
    private String cpWebId;

    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "role_name", length = 64)
    private String roleName;

    /**
     * Upstream active flag. The published contract says {@code "1"}/{@code "0"}
     * but the live feed sends {@code "A"} — kept as the raw string and
     * interpreted by {@link #isActive()} so a contract change cannot corrupt it.
     */
    @Column(name = "is_active", length = 8)
    private String isActive;

    /** Upstream {@code updated_at}; drives the next incremental pull. */
    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    /** Whole upstream record, so new fields survive until we map them. */
    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    public FsFaculty() {
    }

    /** Treats both the documented ({@code "1"}) and actual ({@code "A"}) flags as active. */
    public boolean isActive() {
        return "A".equalsIgnoreCase(isActive) || "1".equals(isActive);
    }

    /** Thai display name, e.g. {@code ผศ. ดร. งามนิจ อาจอินทร์}. */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            sb.append(prefix.trim()).append(' ');
        }
        if (firstName != null) {
            sb.append(firstName.trim()).append(' ');
        }
        if (lastName != null) {
            sb.append(lastName.trim());
        }
        return sb.toString().trim();
    }

    public Long getFsUserId() {
        return fsUserId;
    }

    public void setFsUserId(Long fsUserId) {
        this.fsUserId = fsUserId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
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

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getTelFormat() {
        return telFormat;
    }

    public void setTelFormat(String telFormat) {
        this.telFormat = telFormat;
    }

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
    }

    public String getPositionEn() {
        return positionEn;
    }

    public void setPositionEn(String positionEn) {
        this.positionEn = positionEn;
    }

    public String getPrefixPositionEn() {
        return prefixPositionEn;
    }

    public void setPrefixPositionEn(String prefixPositionEn) {
        this.prefixPositionEn = prefixPositionEn;
    }

    public String getManagePosition() {
        return managePosition;
    }

    public void setManagePosition(String managePosition) {
        this.managePosition = managePosition;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getSuffixEn() {
        return suffixEn;
    }

    public void setSuffixEn(String suffixEn) {
        this.suffixEn = suffixEn;
    }

    public String getScopusId() {
        return scopusId;
    }

    public void setScopusId(String scopusId) {
        this.scopusId = scopusId;
    }

    public String getScholarAuthorId() {
        return scholarAuthorId;
    }

    public void setScholarAuthorId(String scholarAuthorId) {
        this.scholarAuthorId = scholarAuthorId;
    }

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getCpWebId() {
        return cpWebId;
    }

    public void setCpWebId(String cpWebId) {
        this.cpWebId = cpWebId;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getIsActive() {
        return isActive;
    }

    public void setIsActive(String isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(OffsetDateTime sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }
}
