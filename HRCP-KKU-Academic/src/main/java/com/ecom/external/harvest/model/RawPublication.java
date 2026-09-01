package com.ecom.external.harvest.model;

import java.time.OffsetDateTime;

/**
 * Normalized intermediate representation of a publication harvested from any external source.
 * Converted to {@link com.ecom.external.model.ScopusPublication} by {@link com.ecom.external.harvest.PublicationHarmonizer}.
 */
public record RawPublication(
        Long targetFsUserId,
        String externalId,
        String doi,
        String title,
        String publicationName,
        Integer publicationYear,
        OffsetDateTime coverDate,
        Integer citedBy,
        String authorNames,
        String abstractText,
        String aggregationType,
        String subtype,
        String subtypeDescription,
        String issn,
        String eissn,
        String isbn,
        String volume,
        String issue,
        String pageRange,
        String articleNumber,
        String authKeywords,
        String url,
        Double crossrefScore,
        String openalexId,
        String language,
        String dataSource,
        String rawMetadataJson
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long targetFsUserId;
        private String externalId;
        private String doi;
        private String title;
        private String publicationName;
        private Integer publicationYear;
        private OffsetDateTime coverDate;
        private Integer citedBy;
        private String authorNames;
        private String abstractText;
        private String aggregationType;
        private String subtype;
        private String subtypeDescription;
        private String issn;
        private String eissn;
        private String isbn;
        private String volume;
        private String issue;
        private String pageRange;
        private String articleNumber;
        private String authKeywords;
        private String url;
        private Double crossrefScore;
        private String openalexId;
        private String language;
        private String dataSource;
        private String rawMetadataJson;

        public Builder targetFsUserId(Long val) { this.targetFsUserId = val; return this; }
        public Builder externalId(String val) { this.externalId = val; return this; }
        public Builder doi(String val) { this.doi = val; return this; }
        public Builder title(String val) { this.title = val; return this; }
        public Builder publicationName(String val) { this.publicationName = val; return this; }
        public Builder publicationYear(Integer val) { this.publicationYear = val; return this; }
        public Builder coverDate(OffsetDateTime val) { this.coverDate = val; return this; }
        public Builder citedBy(Integer val) { this.citedBy = val; return this; }
        public Builder authorNames(String val) { this.authorNames = val; return this; }
        public Builder abstractText(String val) { this.abstractText = val; return this; }
        public Builder aggregationType(String val) { this.aggregationType = val; return this; }
        public Builder subtype(String val) { this.subtype = val; return this; }
        public Builder subtypeDescription(String val) { this.subtypeDescription = val; return this; }
        public Builder issn(String val) { this.issn = val; return this; }
        public Builder eissn(String val) { this.eissn = val; return this; }
        public Builder isbn(String val) { this.isbn = val; return this; }
        public Builder volume(String val) { this.volume = val; return this; }
        public Builder issue(String val) { this.issue = val; return this; }
        public Builder pageRange(String val) { this.pageRange = val; return this; }
        public Builder articleNumber(String val) { this.articleNumber = val; return this; }
        public Builder authKeywords(String val) { this.authKeywords = val; return this; }
        public Builder url(String val) { this.url = val; return this; }
        public Builder crossrefScore(Double val) { this.crossrefScore = val; return this; }
        public Builder openalexId(String val) { this.openalexId = val; return this; }
        public Builder language(String val) { this.language = val; return this; }
        public Builder dataSource(String val) { this.dataSource = val; return this; }
        public Builder rawMetadataJson(String val) { this.rawMetadataJson = val; return this; }

        public RawPublication build() {
            return new RawPublication(
                    targetFsUserId, externalId, doi, title, publicationName, publicationYear,
                    coverDate, citedBy, authorNames, abstractText, aggregationType, subtype,
                    subtypeDescription, issn, eissn, isbn, volume, issue, pageRange,
                    articleNumber, authKeywords, url, crossrefScore, openalexId, language,
                    dataSource, rawMetadataJson
            );
        }
    }
}
