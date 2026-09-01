package com.ecom.external.harvest.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Result returned by a {@link com.ecom.external.harvest.PublicationSourceAdapter}.
 */
public record HarvestResult(
        String sourceName,
        List<RawPublication> publications,
        OffsetDateTime newCursor,
        int requestsMade,
        long durationMs,
        boolean success,
        String message
) {
    public static HarvestResult ok(String sourceName, List<RawPublication> publications, OffsetDateTime newCursor, int requestsMade, long durationMs, String message) {
        return new HarvestResult(sourceName, publications, newCursor, requestsMade, durationMs, true, message);
    }

    public static HarvestResult failed(String sourceName, int requestsMade, long durationMs, String error) {
        return new HarvestResult(sourceName, List.of(), null, requestsMade, durationMs, false, error);
    }

    public static HarvestResult skipped(String sourceName, String reason) {
        return new HarvestResult(sourceName, List.of(), null, 0, 0L, true, reason);
    }
}
