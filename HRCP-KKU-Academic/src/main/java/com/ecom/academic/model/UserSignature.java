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
 * A signature someone has saved for reuse.
 *
 * <p>Whatever way it was made — drawn, uploaded, or typed — it is stored as a
 * transparent PNG, so the document stamping code has exactly one kind of input
 * to deal with.
 *
 * <p>Rows are soft-deleted rather than removed. A signature that has already
 * been applied to a signed document is evidence; deleting the file out from
 * under that document would leave a signature block that cannot be reproduced.
 */
@Entity
@Table(name = "user_signature", indexes = {
        @Index(name = "idx_user_signature_owner", columnList = "user_id, is_deleted")
})
public class UserSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserDtls user;

    /** What the owner calls it, e.g. "ลายเซ็นของฉัน". Optional; a default is generated. */
    @Column(name = "name", length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private SignatureKind kind;

    /** Generated filename inside the signature storage directory — never client-supplied. */
    @Column(name = "image_path", length = 255, nullable = false)
    private String imagePath;

    /**
     * For {@link SignatureKind#TYPE}, the text and font that produced the image.
     *
     * <p>Kept so a typed signature can be explained ("this is their name set in
     * this font") rather than only shown. The PNG remains the authoritative
     * artifact — re-rendering from text could differ if the font ever changes.
     */
    @Column(name = "typed_text", length = 255)
    private String typedText;

    @Column(name = "typed_font", length = 100)
    private String typedFont;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    /** Pre-selected when signing. At most one per person, enforced by a partial unique index. */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

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

    /** The URL that serves this image, via the ownership-checked controller. */
    public String getImageUrl() {
        return "/esign/signature/" + id + "/image";
    }

    /** A label for the card, falling back to a generated one when unnamed. */
    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "Signature " + (id != null ? id : "");
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserDtls getUser() {
        return user;
    }

    public void setUser(UserDtls user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SignatureKind getKind() {
        return kind;
    }

    public void setKind(SignatureKind kind) {
        this.kind = kind;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getTypedText() {
        return typedText;
    }

    public void setTypedText(String typedText) {
        this.typedText = typedText;
    }

    public String getTypedFont() {
        return typedFont;
    }

    public void setTypedFont(String typedFont) {
        this.typedFont = typedFont;
    }

    public Integer getWidthPx() {
        return widthPx;
    }

    public void setWidthPx(Integer widthPx) {
        this.widthPx = widthPx;
    }

    public Integer getHeightPx() {
        return heightPx;
    }

    public void setHeightPx(Integer heightPx) {
        this.heightPx = heightPx;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
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
