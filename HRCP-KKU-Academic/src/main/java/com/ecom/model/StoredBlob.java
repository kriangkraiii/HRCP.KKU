package com.ecom.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A copy in the database of a small file the app cannot afford to lose: a
 * signature image or a .p12 certificate.
 *
 * <p>The file on disk is still the working copy. This row is what lets another
 * machine — a second test server, a new production host, a restored backup —
 * read the same file when its own disk does not have it. See V29.
 */
@Entity
@Table(name = "stored_blob")
public class StoredBlob {

    /** {@code kind + "/" + filename}, e.g. {@code signature/sig_ab12.png}. */
    @Id
    @Column(name = "blob_key", length = 300)
    private String key;

    @Column(name = "kind", length = 30, nullable = false)
    private String kind;

    /**
     * Spelled out as bytea: left to Hibernate, a large length turns into BLOB on
     * H2, which PostgreSQL mode rejects. H2 reads BYTEA as unbounded VARBINARY.
     */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StoredBlob() {
    }

    public StoredBlob(String kind, String filename, byte[] content) {
        this.key = keyOf(kind, filename);
        this.kind = kind;
        this.content = content;
        this.sizeBytes = (long) content.length;
        this.createdAt = LocalDateTime.now();
    }

    public static String keyOf(String kind, String filename) {
        return kind + "/" + filename;
    }

    public String getKey() {
        return key;
    }

    public String getKind() {
        return kind;
    }

    public byte[] getContent() {
        return content;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
