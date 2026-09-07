package com.ecom.search.model;

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

/**
 * One indexable thing, flattened into the shape the search query needs.
 *
 * <p>Written by {@code SearchIndexer} and read only through
 * {@code SearchDocumentQueryRepository}, which issues its own SQL — nothing
 * loads these through JPA to answer a search.
 *
 * <p><b>Two columns are missing on purpose.</b> {@code search_text} and
 * {@code tsv} are generated columns created by {@code V21}, and they are not
 * mapped here. On PostgreSQL {@code ddl-auto=validate} checks mapped columns and
 * ignores extra ones, so validation passes. On H2 — where the test schema comes
 * from this class and Flyway never runs — they simply do not exist, which is
 * why the portable SQL flavour rebuilds the concatenation inline instead of
 * naming them.
 *
 * <p>Do not be tempted to map {@code tsv} as a read-only column. Hibernate would
 * validate its type, {@code tsvector} has no JDBC type, and the application
 * would refuse to start against the real database.
 *
 * <p>The indexes are not declared as {@code @Index} either: they are GIN and
 * partial b-tree indexes, neither of which JPA can express and neither of which
 * H2 can build. They live in {@code V21} alone.
 */
@Entity
@Table(name = "search_document",
        // Declared here as well as in V21, because the two databases get their
        // schema from different places: PostgreSQL from the migration, H2 in the
        // tests from this class. Leaving it out meant H2 had no uniqueness at
        // all, so two index threads handling the same row both inserted and the
        // next lookup failed with "2 results were returned" — a fault the tests
        // could produce and production could not, which is the wrong way round.
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "ux_search_doc_entity",
                columnNames = { "entity_type", "entity_id", "doc_part" }))
public class SearchDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------------------------------------------
    // Identity of the indexed thing
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 40, nullable = false)
    private SearchEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /**
     * Distinguishes several documents produced from one source row.
     *
     * <p>{@code MAIN} for the row itself; a form or attachment uses its own key
     * so a request and its nine documents do not fight over one index row.
     */
    @Column(name = "doc_part", length = 48, nullable = false)
    private String docPart = "MAIN";

    // ------------------------------------------------------------------
    // Searchable text, weighted separately by the ranking formula
    // ------------------------------------------------------------------

    @Column(name = "title", length = 1000, nullable = false)
    private String title;

    @Column(name = "subtitle", length = 1000)
    private String subtitle;

    @Column(name = "keywords", length = 2000)
    private String keywords;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    // ------------------------------------------------------------------
    // Presentation — stored so rendering a page of hits needs no joins
    // ------------------------------------------------------------------

    @Column(name = "category", length = 120, nullable = false)
    private String category;

    @Column(name = "url", length = 2048, nullable = false)
    private String url;

    @Column(name = "admin_url", length = 2048)
    private String adminUrl;

    @Column(name = "icon", length = 80)
    private String icon;

    @Column(name = "badge", length = 120)
    private String badge;

    @Column(name = "badge_class", length = 120)
    private String badgeClass;

    /** True when {@link #url} leaves the application, so the link opens in a new tab. */
    @Column(name = "is_external", nullable = false)
    private boolean external = false;

    // ------------------------------------------------------------------
    // Authorisation — one predicate over these two answers "may they see it"
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    private SearchVisibility visibility;

    @Column(name = "owner_user_id")
    private Integer ownerUserId;

    // ------------------------------------------------------------------
    // Facets and ranking inputs
    // ------------------------------------------------------------------

    @Column(name = "status", length = 50)
    private String status;

    /** Multiplies the relevance score. Defaults come from {@link SearchEntityType}. */
    @Column(name = "weight", nullable = false)
    private float weight = 1.0f;

    /** When the thing itself last happened, for the recency term. */
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    // ------------------------------------------------------------------
    // Index bookkeeping
    // ------------------------------------------------------------------

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "indexed_at", nullable = false)
    private LocalDateTime indexedAt;

    /**
     * SHA-256 of the concatenated text.
     *
     * <p>Lets the indexer skip the UPDATE when nothing changed, which keeps the
     * nightly reconcile from rewriting every row and regenerating every tsvector.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Soft delete, mirroring the source's own soft delete.
     *
     * <p>The row stays so the reconciler need not rebuild it if the flag is
     * reverted — and so a resurrected document keeps its id.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    // ------------------------------------------------------------------
    // File text extraction queue
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_state", length = 16, nullable = false)
    private ExtractionState extractionState = ExtractionState.NONE;

    @Column(name = "file_path", length = 2048)
    private String filePath;

    /** Size and last-modified, so a replaced file is re-read and an unchanged one is not. */
    @Column(name = "file_fingerprint", length = 80)
    private String fileFingerprint;

    @Column(name = "extraction_error", length = 500)
    private String extractionError;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @PrePersist
    void onIndex() {
        if (indexedAt == null) {
            indexedAt = LocalDateTime.now();
        }
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SearchEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(SearchEntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getDocPart() {
        return docPart;
    }

    public void setDocPart(String docPart) {
        this.docPart = docPart;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAdminUrl() {
        return adminUrl;
    }

    public void setAdminUrl(String adminUrl) {
        this.adminUrl = adminUrl;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public void setBadgeClass(String badgeClass) {
        this.badgeClass = badgeClass;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public SearchVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(SearchVisibility visibility) {
        this.visibility = visibility;
    }

    public Integer getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Integer ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(LocalDateTime sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(LocalDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public ExtractionState getExtractionState() {
        return extractionState;
    }

    public void setExtractionState(ExtractionState extractionState) {
        this.extractionState = extractionState;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileFingerprint() {
        return fileFingerprint;
    }

    public void setFileFingerprint(String fileFingerprint) {
        this.fileFingerprint = fileFingerprint;
    }

    public String getExtractionError() {
        return extractionError;
    }

    public void setExtractionError(String extractionError) {
        this.extractionError = extractionError;
    }

    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(LocalDateTime extractedAt) {
        this.extractedAt = extractedAt;
    }
}
