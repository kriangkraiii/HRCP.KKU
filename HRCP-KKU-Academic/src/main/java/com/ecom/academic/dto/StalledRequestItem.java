package com.ecom.academic.dto;

import java.io.Serializable;

/**
 * DTO representing an academic or position request that has been stalled without status movement.
 */
public record StalledRequestItem(
        String requestCode,
        String detailUrl,
        String applicantName,
        String requestType,
        String currentStatusLabel,
        String statusColor,
        String lastUpdatedFormatted,
        long stalledDays,
        boolean isCritical
) implements Serializable {

    public String stalledBadgeClass() {
        return isCritical ? "badge-countdown-danger" : "badge-countdown-warning";
    }
}
