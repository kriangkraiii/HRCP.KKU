package com.ecom.search.dto;

import java.time.LocalDateTime;

/**
 * One result, in the shape the omnibox already reads.
 *
 * <p>The first seven fields are not named freely: {@code global-search.js}
 * renders {@code category}, {@code title}, {@code subtitle}, {@code url},
 * {@code icon}, {@code badge} and {@code badgeClass} off each item, and it fails
 * silently when one goes missing — a dropped {@code badgeClass} paints an
 * unstyled badge, a dropped {@code url} makes a link to nowhere, and neither
 * throws. Keeping the names lets the engine be swapped underneath without
 * touching the front end. {@code GlobalSearchApiTest} pins them.
 *
 * <p>The rest are additions the old renderer ignores and the results page uses.
 */
public record SearchHit(
        String category,
        String title,
        String subtitle,
        String url,
        String icon,
        String badge,
        String badgeClass,
        String entityType,
        Long entityId,
        String snippet,
        boolean external,
        LocalDateTime occurredAt,
        double score) {

    @Override
    public String url() {
        if (url == null || url.contains("#") || entityType == null || entityId == null) {
            return url;
        }
        return switch (entityType) {
            case "STAFF_MEMBER" -> url + "#staff-" + entityId;
            case "SYSTEM_USER" -> url + "#user-" + entityId;
            case "COMMITTEE_MEMBER" -> url + "#committee-" + entityId;
            case "ACADEMIC_REQUEST" -> url + "#req-" + entityId;
            case "POSITION_REQUEST" -> url + "#pos-req-" + entityId;
            case "ADMIN_FILE" -> url + "#row-" + entityId;
            case "PUBLICATION" -> url + "#pub-" + entityId;
            default -> url;
        };
    }
}

