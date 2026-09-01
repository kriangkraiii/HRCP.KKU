package com.ecom.external.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One faculty member's copy of a Scopus publication, mirrored from
 * {@code GET /api/ext/v1/scopus/publications}.
 *
 * <p>A paper co-authored inside the faculty arrives once per author, so the
 * natural key is {@code (fsUserId, eid)} — enforced below and used as the
 * upsert target by the sync.
 */
@Entity
@Table(name = "scopus_publication",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scopus_pub_user_eid",
                columnNames = { "fs_user_id", "eid" }),
        indexes = {
                // The one query the picker runs: this professor's papers, newest first.
                // Covers the plain "my publications" list and the year-filtered variant.
                @Index(name = "idx_scopus_pub_user_year", columnList = "fs_user_id, publication_year"),
                // Admin-side lookups and de-duplication across authors.
                @Index(name = "idx_scopus_pub_eid", columnList = "eid"),
                @Index(name = "idx_scopus_pub_doi", columnList = "doi"),
                // Lets the sync report on / clean up rows an author no longer has.
                @Index(name = "idx_scopus_pub_synced_at", columnList = "synced_at")
        })
public class ScopusPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ----- ownership (drives every authorization check) -----

    /** Upstream faculty id this row belongs to. Never expose another user's rows. */
    @Column(name = "fs_user_id", nullable = false)
    private Long fsUserId;

    @Column(name = "user_scopus_id", length = 64)
    private String userScopusId;

    // ----- identity -----

    /** Scopus EID — globally unique per document. Cross-system key. */
    @Column(name = "eid", nullable = false, length = 64)
    private String eid;

    @Column(name = "scopus_id", length = 64)
    private String scopusId;

    @Column(name = "scopus_url", length = 1024)
    private String scopusUrl;

    /** Upstream's own document id; kept for support questions, not for joins. */
    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    // ----- core bibliographic data -----

    @Column(name = "title", length = 2000)
    private String title;

    @Column(name = "publication_name", length = 512)
    private String publicationName;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "cover_date")
    private OffsetDateTime coverDate;

    @Column(name = "doi", length = 255)
    private String doi;

    @Column(name = "cited_by")
    private Integer citedBy;

    @Column(name = "author_names", columnDefinition = "TEXT")
    private String authorNames;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "aggregation_type", length = 128)
    private String aggregationType;

    @Column(name = "subtype", length = 16)
    private String subtype;

    @Column(name = "subtype_description", length = 128)
    private String subtypeDescription;

    @Column(name = "issn", length = 32)
    private String issn;

    @Column(name = "eissn", length = 32)
    private String eissn;

    @Column(name = "isbn", length = 64)
    private String isbn;

    @Column(name = "volume", length = 64)
    private String volume;

    @Column(name = "issue", length = 64)
    private String issue;

    @Column(name = "page_range", length = 64)
    private String pageRange;

    @Column(name = "article_number", length = 64)
    private String articleNumber;

    /** JSON-array string of author keywords, e.g. {@code ["a","b"]}. */
    @Column(name = "authkeywords", columnDefinition = "TEXT")
    private String authKeywords;

    @Column(name = "fund_acr", length = 255)
    private String fundAcr;

    @Column(name = "fund_sponsor", length = 512)
    private String fundSponsor;

    @Column(name = "openaccess")
    private Integer openAccess;

    @Column(name = "openaccess_flag")
    private Integer openAccessFlag;

    // ----- affiliation (document level, aggregated across all authors) -----

    @Column(name = "affiliation_afid", length = 512)
    private String affiliationAfid;

    @Column(name = "affiliation_name", columnDefinition = "TEXT")
    private String affiliationName;

    @Column(name = "affiliation_city", length = 512)
    private String affiliationCity;

    @Column(name = "affiliation_country", length = 512)
    private String affiliationCountry;

    @Column(name = "affiliation_url", columnDefinition = "TEXT")
    private String affiliationUrl;

    /** Structured affiliation list, kept verbatim for anyone who needs the detail. */
    @Column(name = "affiliations_json", columnDefinition = "TEXT")
    private String affiliationsJson;

    // ----- affiliation (this faculty member's own, on this document) -----

    @Column(name = "user_affiliation_afid", length = 512)
    private String userAffiliationAfid;

    @Column(name = "user_affiliation_name", columnDefinition = "TEXT")
    private String userAffiliationName;

    @Column(name = "user_affiliation_city", length = 512)
    private String userAffiliationCity;

    @Column(name = "user_affiliation_country", length = 512)
    private String userAffiliationCountry;

    @Column(name = "user_affiliation_url", columnDefinition = "TEXT")
    private String userAffiliationUrl;

    // ----- metrics (these are what the position forms actually ask for) -----

    @Column(name = "cite_score_percentile")
    private Double citeScorePercentile;

    @Column(name = "cite_score_quartile", length = 8)
    private String citeScoreQuartile;

    @Column(name = "cite_score_status", length = 32)
    private String citeScoreStatus;

    @Column(name = "cite_score_rank")
    private Integer citeScoreRank;

    // ----- conference (present on conference papers) -----

    @Column(name = "conference_name", columnDefinition = "TEXT")
    private String conferenceName;

    @Column(name = "conference_venue", length = 512)
    private String conferenceVenue;

    @Column(name = "conference_city", length = 255)
    private String conferenceCity;

    @Column(name = "conference_country", length = 255)
    private String conferenceCountry;

    @Column(name = "conference_location", length = 512)
    private String conferenceLocation;

    // ----- bookkeeping -----

    /** Whole upstream row, so added fields are not lost before we map them. */
    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    // ----- multi-source harvesting (V14) -----

    /** Which pipeline produced this row. Existing rows default to {@code SCOPUS}. */
    @Column(name = "data_source", nullable = false, length = 32)
    private String dataSource = "SCOPUS";

    /** Source-specific document key (DOI, OpenAlex ID, DBLP key, OAI identifier). */
    @Column(name = "external_id", length = 512)
    private String externalId;

    @Column(name = "openalex_id", length = 128)
    private String openalexId;

    /** Crossref relevance score, when the paper was found via affiliation search. */
    @Column(name = "crossref_score")
    private Double crossrefScore;

    @Column(name = "sjr_quartile", length = 8)
    private String sjrQuartile;

    @Column(name = "tci_tier", length = 8)
    private String tciTier;

    /** SHA-256 of normalised title + year + first-author surname. Dedup Level 2. */
    @Column(name = "dedup_hash", length = 128)
    private String dedupHash;

    /** Full response body from the source, stored verbatim for audit and re-parsing. */
    @Column(name = "raw_source_metadata", columnDefinition = "TEXT")
    private String rawSourceMetadata;

    @Column(name = "language", length = 8)
    private String language;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    public ScopusPublication() {
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

    public String getUserScopusId() {
        return userScopusId;
    }

    public void setUserScopusId(String userScopusId) {
        this.userScopusId = userScopusId;
    }

    public String getEid() {
        return eid;
    }

    public void setEid(String eid) {
        this.eid = eid;
    }

    public String getScopusId() {
        return scopusId;
    }

    public void setScopusId(String scopusId) {
        this.scopusId = scopusId;
    }

    public String getScopusUrl() {
        return scopusUrl;
    }

    public void setScopusUrl(String scopusUrl) {
        this.scopusUrl = scopusUrl;
    }

    public Long getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Long sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPublicationName() {
        return publicationName;
    }

    public void setPublicationName(String publicationName) {
        this.publicationName = publicationName;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
    }

    public OffsetDateTime getCoverDate() {
        return coverDate;
    }

    public void setCoverDate(OffsetDateTime coverDate) {
        this.coverDate = coverDate;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public Integer getCitedBy() {
        return citedBy;
    }

    public void setCitedBy(Integer citedBy) {
        this.citedBy = citedBy;
    }

    public String getAuthorNames() {
        return authorNames;
    }

    public void setAuthorNames(String authorNames) {
        this.authorNames = authorNames;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getAggregationType() {
        return aggregationType;
    }

    public void setAggregationType(String aggregationType) {
        this.aggregationType = aggregationType;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public String getSubtypeDescription() {
        return subtypeDescription;
    }

    public void setSubtypeDescription(String subtypeDescription) {
        this.subtypeDescription = subtypeDescription;
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

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getPageRange() {
        return pageRange;
    }

    public void setPageRange(String pageRange) {
        this.pageRange = pageRange;
    }

    public String getArticleNumber() {
        return articleNumber;
    }

    public void setArticleNumber(String articleNumber) {
        this.articleNumber = articleNumber;
    }

    public String getAuthKeywords() {
        return authKeywords;
    }

    public void setAuthKeywords(String authKeywords) {
        this.authKeywords = authKeywords;
    }

    public String getFundAcr() {
        return fundAcr;
    }

    public void setFundAcr(String fundAcr) {
        this.fundAcr = fundAcr;
    }

    public String getFundSponsor() {
        return fundSponsor;
    }

    public void setFundSponsor(String fundSponsor) {
        this.fundSponsor = fundSponsor;
    }

    public Integer getOpenAccess() {
        return openAccess;
    }

    public void setOpenAccess(Integer openAccess) {
        this.openAccess = openAccess;
    }

    public Integer getOpenAccessFlag() {
        return openAccessFlag;
    }

    public void setOpenAccessFlag(Integer openAccessFlag) {
        this.openAccessFlag = openAccessFlag;
    }

    public String getAffiliationAfid() {
        return affiliationAfid;
    }

    public void setAffiliationAfid(String affiliationAfid) {
        this.affiliationAfid = affiliationAfid;
    }

    public String getAffiliationName() {
        return affiliationName;
    }

    public void setAffiliationName(String affiliationName) {
        this.affiliationName = affiliationName;
    }

    public String getAffiliationCity() {
        return affiliationCity;
    }

    public void setAffiliationCity(String affiliationCity) {
        this.affiliationCity = affiliationCity;
    }

    public String getAffiliationCountry() {
        return affiliationCountry;
    }

    public void setAffiliationCountry(String affiliationCountry) {
        this.affiliationCountry = affiliationCountry;
    }

    public String getAffiliationUrl() {
        return affiliationUrl;
    }

    public void setAffiliationUrl(String affiliationUrl) {
        this.affiliationUrl = affiliationUrl;
    }

    public String getAffiliationsJson() {
        return affiliationsJson;
    }

    public void setAffiliationsJson(String affiliationsJson) {
        this.affiliationsJson = affiliationsJson;
    }

    public String getUserAffiliationAfid() {
        return userAffiliationAfid;
    }

    public void setUserAffiliationAfid(String userAffiliationAfid) {
        this.userAffiliationAfid = userAffiliationAfid;
    }

    public String getUserAffiliationName() {
        return userAffiliationName;
    }

    public void setUserAffiliationName(String userAffiliationName) {
        this.userAffiliationName = userAffiliationName;
    }

    public String getUserAffiliationCity() {
        return userAffiliationCity;
    }

    public void setUserAffiliationCity(String userAffiliationCity) {
        this.userAffiliationCity = userAffiliationCity;
    }

    public String getUserAffiliationCountry() {
        return userAffiliationCountry;
    }

    public void setUserAffiliationCountry(String userAffiliationCountry) {
        this.userAffiliationCountry = userAffiliationCountry;
    }

    public String getUserAffiliationUrl() {
        return userAffiliationUrl;
    }

    public void setUserAffiliationUrl(String userAffiliationUrl) {
        this.userAffiliationUrl = userAffiliationUrl;
    }

    public Double getCiteScorePercentile() {
        return citeScorePercentile;
    }

    public void setCiteScorePercentile(Double citeScorePercentile) {
        this.citeScorePercentile = citeScorePercentile;
    }

    public String getCiteScoreQuartile() {
        return citeScoreQuartile;
    }

    public void setCiteScoreQuartile(String citeScoreQuartile) {
        this.citeScoreQuartile = citeScoreQuartile;
    }

    public String getCiteScoreStatus() {
        return citeScoreStatus;
    }

    public void setCiteScoreStatus(String citeScoreStatus) {
        this.citeScoreStatus = citeScoreStatus;
    }

    public Integer getCiteScoreRank() {
        return citeScoreRank;
    }

    public void setCiteScoreRank(Integer citeScoreRank) {
        this.citeScoreRank = citeScoreRank;
    }

    public String getConferenceName() {
        return conferenceName;
    }

    public void setConferenceName(String conferenceName) {
        this.conferenceName = conferenceName;
    }

    public String getConferenceVenue() {
        return conferenceVenue;
    }

    public void setConferenceVenue(String conferenceVenue) {
        this.conferenceVenue = conferenceVenue;
    }

    public String getConferenceCity() {
        return conferenceCity;
    }

    public void setConferenceCity(String conferenceCity) {
        this.conferenceCity = conferenceCity;
    }

    public String getConferenceCountry() {
        return conferenceCountry;
    }

    public void setConferenceCountry(String conferenceCountry) {
        this.conferenceCountry = conferenceCountry;
    }

    public String getConferenceLocation() {
        return conferenceLocation;
    }

    public void setConferenceLocation(String conferenceLocation) {
        this.conferenceLocation = conferenceLocation;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    // ----- multi-source harvest accessors (V14) -----

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getOpenalexId() {
        return openalexId;
    }

    public void setOpenalexId(String openalexId) {
        this.openalexId = openalexId;
    }

    public Double getCrossrefScore() {
        return crossrefScore;
    }

    public void setCrossrefScore(Double crossrefScore) {
        this.crossrefScore = crossrefScore;
    }

    public String getSjrQuartile() {
        return sjrQuartile;
    }

    public void setSjrQuartile(String sjrQuartile) {
        this.sjrQuartile = sjrQuartile;
    }

    public String getTciTier() {
        return tciTier;
    }

    public void setTciTier(String tciTier) {
        this.tciTier = tciTier;
    }

    public String getDedupHash() {
        return dedupHash;
    }

    public void setDedupHash(String dedupHash) {
        this.dedupHash = dedupHash;
    }

    public String getRawSourceMetadata() {
        return rawSourceMetadata;
    }

    public void setRawSourceMetadata(String rawSourceMetadata) {
        this.rawSourceMetadata = rawSourceMetadata;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
