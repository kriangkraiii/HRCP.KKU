package com.ecom.external.harvest.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsFaculty;

/**
 * Execution context supplied to each {@link com.ecom.external.harvest.PublicationSourceAdapter}.
 */
public record HarvestContext(
        OffsetDateTime since,
        int yearFrom,
        List<FsFaculty> targetFaculty,
        Map<Long, List<ExternalAuthorMapping>> authorMappingsByUserId
) {
    public static HarvestContext of(
            OffsetDateTime since,
            int yearFrom,
            List<FsFaculty> targetFaculty,
            Map<Long, List<ExternalAuthorMapping>> authorMappings
    ) {
        return new HarvestContext(since, yearFrom, targetFaculty, authorMappings);
    }
}
