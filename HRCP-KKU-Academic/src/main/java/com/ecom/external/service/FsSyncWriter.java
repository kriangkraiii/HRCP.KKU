package com.ecom.external.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.external.harvest.PublicationDeduplicator;
import com.ecom.external.harvest.PublicationHarmonizer;
import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.FsFacultyChange;
import com.ecom.external.model.FsSyncState;
import com.ecom.external.model.JournalTier;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.FsFacultyChangeRepository;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.FsSyncStateRepository;
import com.ecom.external.repository.JournalTierRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Transactional write side of the sync, kept in its own bean on purpose.
 *
 * <p>{@code @Transactional} is applied by a proxy, so a method calling a
 * transactional method <em>on itself</em> gets no transaction at all. Batching
 * these writes into separate {@code REQUIRES_NEW} transactions only works if the
 * call crosses a bean boundary — which is exactly what this class provides for
 * {@link FsSyncService} and {@link com.ecom.external.harvest.PublicationHarvestService}.
 */
@Component
public class FsSyncWriter {

    private static final Logger log = LoggerFactory.getLogger(FsSyncWriter.class);

    private final FsFacultyRepository facultyRepo;
    private final FsFacultyChangeRepository changeRepo;
    private final ScopusPublicationRepository publicationRepo;
    private final FsSyncStateRepository syncStateRepo;
    private final PublicationHarmonizer harmonizer;
    private final PublicationDeduplicator deduplicator;
    private final JournalTierRepository journalTierRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    public FsSyncWriter(FsFacultyRepository facultyRepo,
            FsFacultyChangeRepository changeRepo,
            ScopusPublicationRepository publicationRepo,
            FsSyncStateRepository syncStateRepo,
            PublicationHarmonizer harmonizer,
            PublicationDeduplicator deduplicator,
            JournalTierRepository journalTierRepo) {
        this.facultyRepo = facultyRepo;
        this.changeRepo = changeRepo;
        this.publicationRepo = publicationRepo;
        this.syncStateRepo = syncStateRepo;
        this.harmonizer = harmonizer;
        this.deduplicator = deduplicator;
        this.journalTierRepo = journalTierRepo;
    }

    /**
     * Processes one batch of faculty records.
     *
     * <p>Three outcomes per row:
     * <ul>
     *   <li><b>new person</b> — inserted straight away. There is no local record
     *       to protect, and queuing it would leave a new staff member unable to
     *       use the system until someone happened to review the queue.</li>
     *   <li><b>changed person</b> — <em>not</em> written. The proposed values are
     *       staged in {@link FsFacultyChange} for an administrator to confirm,
     *       because these fields end up in official promotion documents.</li>
     *   <li><b>unchanged</b> — only {@code syncedAt} is refreshed, so a stale
     *       timestamp still means "upstream stopped sending this record".</li>
     * </ul>
     *
     * @return how many rows were applied directly (inserts + touch-ups)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeFacultyBatch(List<JsonNode> batch) {
        List<FsFaculty> toSave = new ArrayList<>(batch.size());
        int staged = 0;

        for (JsonNode row : batch) {
            Long fsUserId = FsApiClient.longValue(row, "user_id");
            if (fsUserId == null) {
                continue;
            }

            FsFaculty existing = facultyRepo.findById(fsUserId).orElse(null);
            if (existing == null) {
                FsFaculty created = new FsFaculty();
                created.setFsUserId(fsUserId);
                FsFacultyMapper.apply(created, row);
                toSave.add(created);
                continue;
            }

            Map<String, Map<String, String>> diff = FsFacultyMapper.diff(existing, row);
            if (diff.isEmpty()) {
                // Nothing to review; just record that upstream still knows them.
                existing.setSyncedAt(LocalDateTime.now());
                existing.setSourceUpdatedAt(FsFacultyMapper.parseOffset(FsApiClient.text(row, "updated_at")));
                toSave.add(existing);
                continue;
            }

            stageChange(existing, row, diff);
            staged++;
        }

        facultyRepo.saveAll(toSave);
        if (staged > 0) {
            log.info("{} faculty change(s) staged for admin approval", staged);
        }
        return toSave.size();
    }

    /**
     * Records a proposed edit, replacing any still-open one for the same person
     * so a field that changes nightly cannot flood the review queue.
     */
    private void stageChange(FsFaculty existing, JsonNode row, Map<String, Map<String, String>> diff) {
        FsFacultyChange change = changeRepo
                .findFirstByFsUserIdAndStatus(existing.getFsUserId(), FsFacultyChange.STATUS_PENDING)
                .orElseGet(FsFacultyChange::new);

        change.setFsUserId(existing.getFsUserId());
        change.setDisplayName(existing.getDisplayName());
        change.setStatus(FsFacultyChange.STATUS_PENDING);
        change.setDiffJson(writeJson(diff));
        change.setProposedJson(row.toString());
        change.setFieldCount(diff.size());
        change.setDetectedAt(LocalDateTime.now());
        change.setReviewedAt(null);
        change.setReviewedBy(null);
        changeRepo.save(change);
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Could not serialise faculty diff: {}", e.toString());
            return "{}";
        }
    }

    /**
     * Upserts one batch of publications on the {@code (fs_user_id, eid)} key.
     *
     * <p>Existing rows are mutated rather than replaced so the local id stays
     * stable across re-syncs — a professor's saved selection keeps resolving to
     * the same paper.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writePublicationBatch(List<JsonNode> batch) {
        LocalDateTime now = LocalDateTime.now();
        List<ScopusPublication> toSave = new ArrayList<>(batch.size());

        for (JsonNode row : batch) {
            Long fsUserId = FsApiClient.longValue(row, "user_id");
            String eid = FsApiClient.text(row, "eid");
            if (fsUserId == null || eid == null) {
                continue;
            }

            ScopusPublication p = publicationRepo.findByFsUserIdAndEid(fsUserId, eid)
                    .orElseGet(ScopusPublication::new);
            p.setFsUserId(fsUserId);
            p.setEid(eid);
            p.setUserScopusId(FsSyncService.normalizeScopusId(FsApiClient.text(row, "user_scopus_id")));
            p.setScopusId(FsSyncService.normalizeScopusId(FsApiClient.text(row, "scopus_id")));
            p.setScopusUrl(FsApiClient.text(row, "scopus_url"));
            p.setSourceDocumentId(FsApiClient.longValue(row, "document_id"));
            p.setTitle(FsApiClient.text(row, "title"));
            p.setPublicationName(FsApiClient.text(row, "publication_name"));
            p.setPublicationYear(FsApiClient.integer(row, "publication_year"));
            p.setCoverDate(parseOffset(FsApiClient.text(row, "cover_date")));
            p.setDoi(FsApiClient.text(row, "doi"));
            p.setCitedBy(FsApiClient.integer(row, "cited_by"));
            p.setAuthorNames(FsApiClient.text(row, "author_names"));
            p.setSourceId(FsApiClient.text(row, "source_id"));
            p.setAbstractText(FsApiClient.text(row, "abstract"));
            p.setAggregationType(FsApiClient.text(row, "aggregation_type"));
            p.setSubtype(FsApiClient.text(row, "subtype"));
            p.setSubtypeDescription(FsApiClient.text(row, "subtype_description"));
            p.setIssn(FsApiClient.text(row, "issn"));
            p.setEissn(FsApiClient.text(row, "eissn"));
            p.setIsbn(FsApiClient.text(row, "isbn"));
            p.setVolume(FsApiClient.text(row, "volume"));
            p.setIssue(FsApiClient.text(row, "issue"));
            p.setPageRange(FsApiClient.text(row, "page_range"));
            p.setArticleNumber(FsApiClient.text(row, "article_number"));
            p.setAuthKeywords(FsApiClient.text(row, "authkeywords"));
            p.setFundAcr(FsApiClient.text(row, "fund_acr"));
            p.setFundSponsor(FsApiClient.text(row, "fund_sponsor"));
            p.setOpenAccess(FsApiClient.integer(row, "openaccess"));
            p.setOpenAccessFlag(FsApiClient.integer(row, "openaccess_flag"));
            p.setAffiliationAfid(FsApiClient.text(row, "affiliation_afid"));
            p.setAffiliationName(FsApiClient.text(row, "affiliation_name"));
            p.setAffiliationCity(FsApiClient.text(row, "affiliation_city"));
            p.setAffiliationCountry(FsApiClient.text(row, "affiliation_country"));
            p.setAffiliationUrl(FsApiClient.text(row, "affiliation_url"));
            p.setAffiliationsJson(FsApiClient.text(row, "affiliations_json"));
            p.setUserAffiliationAfid(FsApiClient.text(row, "user_affiliation_afid"));
            p.setUserAffiliationName(FsApiClient.text(row, "user_affiliation_name"));
            p.setUserAffiliationCity(FsApiClient.text(row, "user_affiliation_city"));
            p.setUserAffiliationCountry(FsApiClient.text(row, "user_affiliation_country"));
            p.setUserAffiliationUrl(FsApiClient.text(row, "user_affiliation_url"));
            p.setCiteScorePercentile(FsApiClient.doubleValue(row, "cite_score_percentile"));
            p.setCiteScoreQuartile(FsApiClient.text(row, "cite_score_quartile"));
            p.setCiteScoreStatus(FsApiClient.text(row, "cite_score_status"));
            p.setCiteScoreRank(FsApiClient.integer(row, "cite_score_rank"));
            p.setConferenceName(FsApiClient.text(row, "conference_name"));
            p.setConferenceVenue(FsApiClient.text(row, "conference_venue"));
            p.setConferenceCity(FsApiClient.text(row, "conference_city"));
            p.setConferenceCountry(FsApiClient.text(row, "conference_country"));
            p.setConferenceLocation(FsApiClient.text(row, "conference_location"));
            p.setRawJson(row.toString());
            p.setSyncedAt(now);
            toSave.add(p);
        }

        publicationRepo.saveAll(toSave);
        return toSave.size();
    }

    /**
     * Processes one batch of harvested raw publications with 2.5-level deduplication,
     * harmonization, and journal quartile/tier enrichment.
     *
     * @param batch raw publications harvested from external adapters
     * @param fuzzyThreshold threshold for Level 2.5 pg_trgm similarity
     * @return count of inserted/updated publications
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeHarvestedBatch(List<RawPublication> batch, double fuzzyThreshold) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        List<ScopusPublication> toSave = new ArrayList<>(batch.size());

        for (RawPublication raw : batch) {
            Long fsUserId = raw.targetFsUserId();
            if (fsUserId == null || raw.title() == null || raw.title().isBlank()) {
                continue;
            }

            PublicationDeduplicator.DedupResult dedup = deduplicator.findDuplicate(raw, fsUserId, fuzzyThreshold);

            ScopusPublication target;
            if (dedup.isDuplicate()) {
                // Merge enriched fields into existing publication
                target = dedup.existing().get();
                harmonizer.mergeIntoExisting(target, raw, dedup.computedHash());
                enrichJournalTier(target);
                log.debug("Merged harvested work from {} into existing pub id {} (dedup: {})",
                        raw.dataSource(), target.getId(), dedup.matchLevel());
            } else {
                // Insert as new publication
                target = harmonizer.harmonize(raw, fsUserId, dedup.computedHash());
                enrichJournalTier(target);
                log.debug("Inserting new harvested work from {} for user {} (hash: {})",
                        raw.dataSource(), fsUserId, dedup.computedHash());
            }

            toSave.add(target);
        }

        publicationRepo.saveAll(toSave);
        return toSave.size();
    }

    /**
     * Looks up SJR and TCI tier rankings by ISSN/eISSN and attaches them to the publication.
     */
    private void enrichJournalTier(ScopusPublication p) {
        if (p.getIssn() == null && p.getEissn() == null) {
            return;
        }

        Integer year = p.getPublicationYear();
        List<JournalTier> tiers = journalTierRepo.findByIssnOrEissnAndYear(p.getIssn(), p.getEissn(), year);
        if (tiers.isEmpty()) {
            tiers = journalTierRepo.findLatestByIssnOrEissn(p.getIssn(), p.getEissn());
        }

        for (JournalTier t : tiers) {
            if ("SJR".equalsIgnoreCase(t.getProvider()) && (p.getSjrQuartile() == null || p.getSjrQuartile().isBlank())) {
                p.setSjrQuartile(t.getTier());
            } else if ("TCI".equalsIgnoreCase(t.getProvider()) && (p.getTciTier() == null || p.getTciTier().isBlank())) {
                p.setTciTier(t.getTier());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(String type) {
        FsSyncState state = load(type);
        state.setLastRunAt(LocalDateTime.now());
        state.setLastStatus(FsSyncState.STATUS_RUNNING);
        syncStateRepo.save(state);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String type, int rows, int requests, long durationMs,
            OffsetDateTime cursor, String message) {
        FsSyncState state = load(type);
        state.setLastStatus(FsSyncState.STATUS_OK);
        state.setLastSuccessAt(LocalDateTime.now());
        state.setRowsProcessed(rows);
        state.setRequestsMade(requests);
        state.setDurationMs(durationMs);
        if (cursor != null) {
            state.setLastUpdatedSince(cursor);
        }
        state.setMessage(truncate(message));
        syncStateRepo.save(state);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String type, Exception e) {
        FsSyncState state = load(type);
        state.setLastStatus(FsSyncState.STATUS_FAILED);
        state.setMessage(truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        syncStateRepo.save(state);
    }

    private FsSyncState load(String type) {
        return syncStateRepo.findById(type).orElseGet(() -> new FsSyncState(type));
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    private static OffsetDateTime parseOffset(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            log.debug("Unparseable timestamp from upstream: {}", value);
            return null;
        }
    }
}
