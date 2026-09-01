package com.ecom.external.controller;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.external.dto.PublicationDto;
import com.ecom.external.harvest.PublicationHarvestService;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsSyncState;
import com.ecom.external.repository.ExternalAuthorMappingRepository;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.FsApiClient;
import com.ecom.external.service.FsSyncService;
import com.ecom.external.service.ManualSyncGuard;
import com.ecom.external.service.ScopusQueryService;

/**
 * Admin view over the external-data mirror: sync status, manual triggers and a
 * faculty-wide publication search.
 *
 * <p>Everything is under {@code /admin/**}, which the security configuration
 * already restricts to {@code ROLE_ADMIN}; the method-level annotations state
 * the same requirement locally so the guarantee survives a URL change.
 */
@RestController
@RequestMapping("/admin/external")
@PreAuthorize("hasRole('ADMIN')")
public class ExternalSyncAdminController {

    private final FsSyncService syncService;
    private final ManualSyncGuard guard;
    private final ScopusQueryService scopusQuery;
    private final FsApiClient apiClient;
    private final FsFacultyRepository facultyRepo;
    private final ScopusPublicationRepository publicationRepo;
    private final PublicationHarvestService harvestService;
    private final ExternalAuthorMappingRepository mappingRepo;

    public ExternalSyncAdminController(FsSyncService syncService,
            ManualSyncGuard guard,
            ScopusQueryService scopusQuery,
            FsApiClient apiClient,
            FsFacultyRepository facultyRepo,
            ScopusPublicationRepository publicationRepo,
            PublicationHarvestService harvestService,
            ExternalAuthorMappingRepository mappingRepo) {
        this.syncService = syncService;
        this.guard = guard;
        this.scopusQuery = scopusQuery;
        this.apiClient = apiClient;
        this.facultyRepo = facultyRepo;
        this.publicationRepo = publicationRepo;
        this.harvestService = harvestService;
        this.mappingRepo = mappingRepo;
    }

    /** Current mirror contents and the outcome of the last run of each job. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, FsSyncState> state = syncService.currentState();

        Map<String, Object> body = new HashMap<>();
        body.put("api", apiClient.describe());
        body.put("facultyCount", facultyRepo.count());
        body.put("publicationCount", publicationRepo.count());
        body.put("facultyWithScopusId", facultyRepo.findAllWithScopusId().size());
        body.put("jobs", state.values().stream().map(this::describeJob).toList());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> describeJob(FsSyncState s) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", s.getSyncType());
        m.put("status", s.getLastStatus());
        m.put("lastRunAt", s.getLastRunAt());
        m.put("lastSuccessAt", s.getLastSuccessAt());
        m.put("rows", s.getRowsProcessed());
        m.put("requests", s.getRequestsMade());
        m.put("durationMs", s.getDurationMs());
        m.put("message", s.getMessage());
        return m;
    }

    /**
     * Runs the faculty sync now.
     *
     * <p>Shares {@link ManualSyncGuard} with the admin page: guarding only the
     * button would leave this endpoint as an open way to spend the upstream
     * request budget.
     *
     * @param full re-pull everyone instead of only records changed since the
     *             last run
     */
    @PostMapping("/sync/users")
    public ResponseEntity<FsSyncService.SyncResult> syncUsers(
            @RequestParam(defaultValue = "false") boolean full) {

        Duration wait = guard.claim(FsSyncState.TYPE_USERS);
        if (!wait.isZero()) {
            return tooManyRequests(wait);
        }

        FsSyncService.SyncResult result = syncService.syncUsers(full);
        if (!result.success()) {
            guard.release(FsSyncState.TYPE_USERS);
        }
        return ResponseEntity.ok(result);
    }

    /** Runs the publication sync now. Takes about a minute — it is throttled on purpose. */
    @PostMapping("/sync/scopus")
    public ResponseEntity<FsSyncService.SyncResult> syncScopus() {
        Duration wait = guard.claim(FsSyncState.TYPE_SCOPUS);
        if (!wait.isZero()) {
            return tooManyRequests(wait);
        }

        FsSyncService.SyncResult result = syncService.syncPublications();
        if (!result.success()) {
            guard.release(FsSyncState.TYPE_SCOPUS);
        }
        return ResponseEntity.ok(result);
    }

    /** 429 with {@code Retry-After}, mirroring how the upstream API rejects us. */
    private ResponseEntity<FsSyncService.SyncResult> tooManyRequests(Duration wait) {
        long seconds = Math.max(wait.toSeconds(), 1);
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(seconds))
                .body(FsSyncService.SyncResult.cooldown(
                        "cooldown active, retry in " + seconds + "s"));
    }

    /**
     * Faculty-wide publication search.
     *
     * <p>This is the one place that can read across professors, which is why it
     * lives on an admin-only controller rather than beside the applicant API.
     */
    @GetMapping("/publications")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(name = "fs_user_id", required = false) Long fsUserId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<PublicationDto> result = scopusQuery.adminSearch(fsUserId, q, page, size);

        Map<String, Object> body = new HashMap<>();
        body.put("data", result.getContent());
        body.put("total", result.getTotalElements());
        body.put("page", result.getNumber());
        return ResponseEntity.ok(body);
    }

    // =========================================================================
    // Multi-Source Harvesting Endpoints (V14)
    // =========================================================================

    /**
     * Triggers parallel harvest across all enabled external sources (Virtual Threads).
     */
    @PostMapping("/harvest/all")
    public ResponseEntity<List<HarvestResult>> triggerHarvestAll() {
        List<HarvestResult> results = harvestService.harvestAll();
        return ResponseEntity.ok(results);
    }

    /**
     * Triggers harvest for a specific source (e.g. crossref, openalex, dblp, thaijo, kkuir).
     */
    @PostMapping("/harvest/{source}")
    public ResponseEntity<HarvestResult> triggerHarvestSource(@PathVariable String source) {
        HarvestResult result = harvestService.harvestSource(source);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns publication counts broken down by data source.
     */
    @GetMapping("/harvest/stats")
    public ResponseEntity<Map<String, Object>> harvestStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", publicationRepo.count());

        Map<String, Long> bySource = new HashMap<>();
        for (Object[] row : publicationRepo.countGroupByDataSource()) {
            String source = (String) row[0];
            Long count = (Long) row[1];
            bySource.put(source != null ? source : "UNKNOWN", count);
        }
        stats.put("bySource", bySource);
        stats.put("isHarvestRunning", harvestService.isRunning());
        return ResponseEntity.ok(stats);
    }

    // =========================================================================
    // External Author Mappings (DBLP PID, ORCID)
    // =========================================================================

    @GetMapping("/author-mappings")
    public ResponseEntity<List<ExternalAuthorMapping>> getAllAuthorMappings(
            @RequestParam(required = false) Long fsUserId,
            @RequestParam(required = false) String provider) {
        if (fsUserId != null && provider != null) {
            return ResponseEntity.ok(mappingRepo.findByFsUserIdAndProvider(fsUserId, provider));
        } else if (fsUserId != null) {
            return ResponseEntity.ok(mappingRepo.findByFsUserId(fsUserId));
        } else if (provider != null) {
            return ResponseEntity.ok(mappingRepo.findByProvider(provider));
        }
        return ResponseEntity.ok(mappingRepo.findAll());
    }

    @PostMapping("/author-mappings")
    public ResponseEntity<ExternalAuthorMapping> saveAuthorMapping(@RequestBody ExternalAuthorMapping mapping) {
        if (mapping.getFsUserId() == null || mapping.getProvider() == null || mapping.getExternalPid() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<ExternalAuthorMapping> existing = mappingRepo.findByFsUserIdAndProviderAndExternalPid(
                mapping.getFsUserId(), mapping.getProvider().toUpperCase(), mapping.getExternalPid().trim());

        ExternalAuthorMapping toSave = existing.orElse(mapping);
        toSave.setProvider(mapping.getProvider().toUpperCase().trim());
        toSave.setExternalPid(mapping.getExternalPid().trim());
        toSave.setDisplayName(mapping.getDisplayName());
        toSave.setVerified(mapping.isVerified());

        return ResponseEntity.ok(mappingRepo.save(toSave));
    }

    @DeleteMapping("/author-mappings/{id}")
    public ResponseEntity<Void> deleteAuthorMapping(@PathVariable Long id) {
        if (mappingRepo.existsById(id)) {
            mappingRepo.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

