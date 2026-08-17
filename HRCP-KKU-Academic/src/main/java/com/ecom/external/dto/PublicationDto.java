package com.ecom.external.dto;

import com.ecom.external.model.ScopusPublication;

/**
 * What the browser is allowed to see for one publication.
 *
 * <p>Deliberately narrower than the entity: {@code rawJson}, the owning
 * {@code fsUserId} and the upstream document id never leave the server. Sending
 * the entity directly would leak the owner id into every picker response and
 * make it trivial to probe for other people's rows.
 */
public record PublicationDto(
        Long id,
        String eid,
        String title,
        String publicationName,
        Integer year,
        String doi,
        Integer citedBy,
        String authorNames,
        String quartile,
        Double percentile,
        String type,
        String volume,
        String issue,
        String pages,
        String url,
        boolean openAccess) {

    public static PublicationDto from(ScopusPublication p) {
        return new PublicationDto(
                p.getId(),
                p.getEid(),
                p.getTitle(),
                p.getPublicationName(),
                p.getPublicationYear(),
                p.getDoi(),
                p.getCitedBy(),
                p.getAuthorNames(),
                p.getCiteScoreQuartile(),
                p.getCiteScorePercentile(),
                p.getSubtypeDescription() != null ? p.getSubtypeDescription() : p.getAggregationType(),
                p.getVolume(),
                p.getIssue(),
                pageLabel(p),
                p.getScopusUrl(),
                p.getOpenAccess() != null && p.getOpenAccess() == 1);
    }

    /** E-journals use an article number where print journals give a page range. */
    private static String pageLabel(ScopusPublication p) {
        if (p.getPageRange() != null && !p.getPageRange().isBlank()) {
            return p.getPageRange();
        }
        return p.getArticleNumber();
    }

    /**
     * Citation line in the format the position forms expect, e.g.
     * {@code Arch-Int N. (2024). Title. Applied Sciences, 14(13), 5866.}
     */
    public String toCitation() {
        StringBuilder sb = new StringBuilder();
        if (authorNames != null) {
            sb.append(authorNames.replace(" | ", ", "));
        }
        if (year != null) {
            sb.append(" (").append(year).append(")");
        }
        if (title != null) {
            sb.append(". ").append(title);
        }
        if (publicationName != null) {
            sb.append(". ").append(publicationName);
        }
        if (volume != null) {
            sb.append(", ").append(volume);
            if (issue != null) {
                sb.append("(").append(issue).append(")");
            }
        }
        if (pages != null) {
            sb.append(", ").append(pages);
        }
        if (doi != null) {
            sb.append(". https://doi.org/").append(doi);
        }
        return sb.append('.').toString();
    }
}
