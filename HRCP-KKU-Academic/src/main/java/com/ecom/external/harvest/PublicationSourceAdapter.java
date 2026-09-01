package com.ecom.external.harvest;

import com.ecom.external.harvest.model.HarvestContext;
import com.ecom.external.harvest.model.HarvestResult;

/**
 * Common contract for external academic publication harvesters.
 * Implementations are executed concurrently inside virtual threads by {@link PublicationHarvestService}.
 */
public interface PublicationSourceAdapter {

    /**
     * Unique identifier for this source, matching {@code data_source} in {@link com.ecom.external.model.ScopusPublication}
     * and {@code sync_type} in {@link com.ecom.external.model.FsSyncState}.
     * E.g. {@code CROSSREF}, {@code OPENALEX}, {@code DBLP}, {@code THAIJO}, {@code KKUIR}.
     */
    String sourceName();

    /**
     * Executes the harvesting routine for the given context.
     * Must not throw — catch network/parse exceptions and return {@link HarvestResult#failed}.
     */
    HarvestResult harvest(HarvestContext context);

    /**
     * Whether this adapter is currently enabled via configuration.
     */
    boolean isEnabled();
}
