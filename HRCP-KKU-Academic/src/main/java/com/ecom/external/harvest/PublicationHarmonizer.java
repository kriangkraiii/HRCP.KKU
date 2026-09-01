package com.ecom.external.harvest;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.ScopusPublication;

/**
 * Harmonizes fields across different raw publication formats into a consistent
 * {@link ScopusPublication} entity structure.
 */
@Component
public class PublicationHarmonizer {

    /**
     * Maps a {@link RawPublication} to a {@link ScopusPublication} entity,
     * applying field normalization, DOI cleaning, and length boundaries.
     */
    public ScopusPublication harmonize(RawPublication raw, Long fsUserId, String dedupHash) {
        ScopusPublication p = new ScopusPublication();

        p.setFsUserId(fsUserId);
        p.setDataSource(raw.dataSource() != null ? raw.dataSource() : "UNKNOWN");
        p.setExternalId(truncate(raw.externalId(), 512));
        p.setEid(buildSyntheticEid(raw));
        p.setDoi(normalizeDoi(raw.doi()));
        p.setTitle(truncate(normalizeWhitespace(raw.title()), 2000));
        p.setPublicationName(truncate(normalizeWhitespace(raw.publicationName()), 512));
        p.setPublicationYear(raw.publicationYear());
        p.setCoverDate(raw.coverDate());
        p.setCitedBy(raw.citedBy() != null ? raw.citedBy() : 0);
        p.setAuthorNames(normalizeAuthors(raw.authorNames()));
        p.setAbstractText(raw.abstractText());
        p.setAggregationType(truncate(raw.aggregationType(), 128));
        p.setSubtype(truncate(raw.subtype(), 16));
        p.setSubtypeDescription(truncate(raw.subtypeDescription(), 128));
        p.setIssn(normalizeIssn(raw.issn()));
        p.setEissn(normalizeIssn(raw.eissn()));
        p.setIsbn(truncate(raw.isbn(), 64));
        p.setVolume(truncate(raw.volume(), 64));
        p.setIssue(truncate(raw.issue(), 64));
        p.setPageRange(truncate(raw.pageRange(), 64));
        p.setArticleNumber(truncate(raw.articleNumber(), 64));
        p.setAuthKeywords(raw.authKeywords());
        p.setScopusUrl(truncate(raw.url(), 1024));
        p.setOpenalexId(truncate(raw.openalexId(), 128));
        p.setCrossrefScore(raw.crossrefScore());
        p.setLanguage(truncate(raw.language(), 8));
        p.setDedupHash(dedupHash);
        p.setRawSourceMetadata(raw.rawMetadataJson());
        p.setSyncedAt(LocalDateTime.now());

        return p;
    }

    /**
     * Merges non-null enriched fields from a harvested publication into an existing entity.
     */
    public void mergeIntoExisting(ScopusPublication existing, RawPublication raw, String dedupHash) {
        if (existing.getDoi() == null && raw.doi() != null) {
            existing.setDoi(normalizeDoi(raw.doi()));
        }
        if (existing.getPublicationYear() == null && raw.publicationYear() != null) {
            existing.setPublicationYear(raw.publicationYear());
        }
        if (existing.getAbstractText() == null && raw.abstractText() != null) {
            existing.setAbstractText(raw.abstractText());
        }
        if (existing.getIssn() == null && raw.issn() != null) {
            existing.setIssn(normalizeIssn(raw.issn()));
        }
        if (existing.getEissn() == null && raw.eissn() != null) {
            existing.setEissn(normalizeIssn(raw.eissn()));
        }
        if (existing.getOpenalexId() == null && raw.openalexId() != null) {
            existing.setOpenalexId(truncate(raw.openalexId(), 128));
        }
        if (existing.getCrossrefScore() == null && raw.crossrefScore() != null) {
            existing.setCrossrefScore(raw.crossrefScore());
        }
        if (raw.citedBy() != null && (existing.getCitedBy() == null || raw.citedBy() > existing.getCitedBy())) {
            existing.setCitedBy(raw.citedBy());
        }
        if (existing.getDedupHash() == null) {
            existing.setDedupHash(dedupHash);
        }
        existing.setSyncedAt(LocalDateTime.now());
    }

    public static String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) {
            return null;
        }
        String clean = doi.trim();
        if (clean.startsWith("https://doi.org/")) {
            clean = clean.substring("https://doi.org/".length());
        } else if (clean.startsWith("http://doi.org/")) {
            clean = clean.substring("http://doi.org/".length());
        } else if (clean.startsWith("doi:")) {
            clean = clean.substring("doi:".length());
        }
        return clean.trim().toLowerCase();
    }

    public static String normalizeWhitespace(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    public static String normalizeAuthors(String authorNames) {
        if (authorNames == null || authorNames.isBlank()) {
            return null;
        }
        return authorNames.trim();
    }

    public static String normalizeIssn(String issn) {
        if (issn == null || issn.isBlank()) {
            return null;
        }
        String clean = issn.trim().toUpperCase();
        return clean.length() <= 16 ? clean : clean.substring(0, 16);
    }

    private static String buildSyntheticEid(RawPublication raw) {
        if (raw.externalId() != null && !raw.externalId().isBlank()) {
            return raw.dataSource() + ":" + raw.externalId();
        }
        if (raw.doi() != null && !raw.doi().isBlank()) {
            return "DOI:" + normalizeDoi(raw.doi());
        }
        return raw.dataSource() + ":" + System.currentTimeMillis() + "-" + Math.abs(raw.hashCode());
    }

    public static String truncate(String val, int maxLen) {
        if (val == null) {
            return null;
        }
        return val.length() <= maxLen ? val : val.substring(0, maxLen);
    }
}
