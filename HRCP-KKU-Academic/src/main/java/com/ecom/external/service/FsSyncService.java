package com.ecom.external.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ecom.external.config.FsApiProperties;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.FsSyncState;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.FsSyncStateRepository;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mirrors the Fund Management platform into our own tables on a schedule.
 *
 * <p>Every job here has the same shape: <b>fetch everything over HTTP first,
 * then write in batched transactions</b>. Holding a transaction open across
 * dozens of throttled HTTP calls would pin a pool connection for minutes, so the
 * network phase deliberately runs outside any transaction. The writes themselves
 * live in {@link FsSyncWriter} because a transactional method invoked on
 * {@code this} would never be proxied.
 *
 * <p><b>Retention policy: this sync never deletes.</b> Every write is an insert
 * or an update, and no code path here removes a local row. If a record vanishes
 * upstream — a publication retracted from the feed, a faculty member removed —
 * our copy survives untouched. That is deliberate: a promotion request that
 * already cites a publication must keep rendering years later, and an upstream
 * outage returning a short list must not wipe our data. Rows that stopped
 * arriving are visible by their stale {@code syncedAt}, which the admin status
 * endpoint reports, rather than by being gone.
 *
 * <p>Each job is guarded by a flag so the nightly trigger and a manual admin
 * trigger cannot run concurrently and double-write.
 */
@Service
public class FsSyncService {

    private static final Logger log = LoggerFactory.getLogger(FsSyncService.class);

    /** All cron expressions in this class are read in Thai time. */
    static final String SCHEDULE_ZONE = "Asia/Bangkok";

    /** Rows per write transaction. Matches {@code hibernate.jdbc.batch_size}. */
    private static final int WRITE_BATCH = 50;

    private final FsApiClient api;
    private final FsApiProperties props;
    private final FsSyncWriter writer;
    private final FsFacultyRepository facultyRepo;
    private final FsSyncStateRepository syncStateRepo;

    private final AtomicBoolean usersRunning = new AtomicBoolean(false);
    private final AtomicBoolean scopusRunning = new AtomicBoolean(false);

    public FsSyncService(FsApiClient api,
            FsApiProperties props,
            FsSyncWriter writer,
            FsFacultyRepository facultyRepo,
            FsSyncStateRepository syncStateRepo) {
        this.api = api;
        this.props = props;
        this.writer = writer;
        this.facultyRepo = facultyRepo;
        this.syncStateRepo = syncStateRepo;
    }

    // ------------------------------------------------------------------
    // Faculty directory
    // ------------------------------------------------------------------

    /**
     * The zone is pinned rather than inherited. {@code WebConfig} sets the JVM
     * default to Asia/Bangkok in a {@code @PostConstruct}, but bean
     * initialisation order is not guaranteed, so a cron that relied on it could
     * silently resolve against UTC and fire seven hours early.
     */
    @Scheduled(cron = "${fs.sync.users.cron:0 30 1 * * *}", zone = SCHEDULE_ZONE)
    public void scheduledUserSync() {
        syncUsers(false);
    }

    /**
     * Weekly full re-read of the directory.
     *
     * <p>An incremental pull only returns records changed since the cursor, so a
     * field edited by a process that does not bump {@code updated_at} would never
     * reach us. The full pass repairs that.
     *
     * <p>It is <b>not</b> a reconcile: nothing is deleted locally. See the
     * retention note on {@link #syncPublications()}.
     */
    @Scheduled(cron = "${fs.sync.users.full-cron:0 0 3 * * SUN}", zone = SCHEDULE_ZONE)
    public void scheduledFullUserSync() {
        syncUsers(true);
    }

    /**
     * @param full ignore the incremental cursor and re-pull every faculty record
     */
    public SyncResult syncUsers(boolean full) {
        if (!props.isUsable()) {
            log.debug("Faculty sync skipped — external API disabled or key missing");
            return SyncResult.skipped("external API disabled");
        }
        if (!usersRunning.compareAndSet(false, true)) {
            return SyncResult.skipped("already running");
        }

        long startedAt = System.currentTimeMillis();
        api.resetRequestCount();
        writer.markRunning(FsSyncState.TYPE_USERS);

        try {
            OffsetDateTime cursor = full ? null : facultyRepo.findMaxSourceUpdatedAt();
            List<JsonNode> rows = api.fetchUsers(cursor);

            int written = 0;
            for (List<JsonNode> batch : FsApiClient.partition(rows, WRITE_BATCH)) {
                written += writer.writeFacultyBatch(batch);
            }

            long elapsed = System.currentTimeMillis() - startedAt;
            writer.recordSuccess(FsSyncState.TYPE_USERS, written, api.getRequestCount(), elapsed,
                    facultyRepo.findMaxSourceUpdatedAt(),
                    (full ? "full" : "incremental") + " pull, " + rows.size() + " row(s) received");

            log.info("Faculty sync finished: {} row(s) written in {} request(s), {} ms",
                    written, api.getRequestCount(), elapsed);
            return SyncResult.ok(written, api.getRequestCount(), elapsed);

        } catch (Exception e) {
            log.error("Faculty sync failed: {}", e.toString(), e);
            writer.recordFailure(FsSyncState.TYPE_USERS, e);
            return SyncResult.failed(e.getMessage());
        } finally {
            usersRunning.set(false);
        }
    }

    // ------------------------------------------------------------------
    // Scopus publications
    // ------------------------------------------------------------------

    @Scheduled(cron = "${fs.sync.scopus.cron:0 0 2 * * *}", zone = SCHEDULE_ZONE)
    public void scheduledPublicationSync() {
        syncPublications();
    }

    /**
     * Pulls publications for every faculty member who has a Scopus author id.
     *
     * <p>Faculty are batched ({@code fs.api.user-batch-size}) rather than queried
     * one id per request: 45 separate calls would burn half the per-minute budget
     * for no reason, while a single call for all 45 would lose the whole run if
     * one id were rejected.
     *
     * <p>Publications that no longer come back from upstream are left in place —
     * see the retention policy on this class. A run that returns fewer rows than
     * last time shrinks nothing locally.
     */
    public SyncResult syncPublications() {
        if (!props.isUsable()) {
            return SyncResult.skipped("external API disabled");
        }
        if (!scopusRunning.compareAndSet(false, true)) {
            return SyncResult.skipped("already running");
        }

        long startedAt = System.currentTimeMillis();
        api.resetRequestCount();
        writer.markRunning(FsSyncState.TYPE_SCOPUS);

        try {
            List<Long> ids = facultyRepo.findAllWithScopusId().stream()
                    .map(FsFaculty::getFsUserId)
                    .toList();

            if (ids.isEmpty()) {
                log.warn("Publication sync skipped — no faculty carry a Scopus id yet. Run the faculty sync first.");
                writer.recordSuccess(FsSyncState.TYPE_SCOPUS, 0, 0,
                        System.currentTimeMillis() - startedAt, null, "no faculty with scopus_id");
                return SyncResult.ok(0, 0, System.currentTimeMillis() - startedAt);
            }

            int written = 0;
            int batches = 0;
            for (List<Long> facultyBatch : FsApiClient.partition(ids, props.getUserBatchSize())) {
                List<JsonNode> rows = api.fetchPublications(facultyBatch, props.getYearFrom(), null);
                for (List<JsonNode> writeBatch : FsApiClient.partition(rows, WRITE_BATCH)) {
                    written += writer.writePublicationBatch(writeBatch);
                }
                batches++;
                log.debug("Publication batch {} covering {} faculty returned {} row(s)",
                        batches, facultyBatch.size(), rows.size());
            }

            long elapsed = System.currentTimeMillis() - startedAt;
            writer.recordSuccess(FsSyncState.TYPE_SCOPUS, written, api.getRequestCount(), elapsed,
                    null, ids.size() + " faculty in " + batches + " batch(es)");

            log.info("Publication sync finished: {} row(s) upserted for {} faculty in {} request(s), {} ms",
                    written, ids.size(), api.getRequestCount(), elapsed);
            return SyncResult.ok(written, api.getRequestCount(), elapsed);

        } catch (Exception e) {
            log.error("Publication sync failed: {}", e.toString(), e);
            writer.recordFailure(FsSyncState.TYPE_SCOPUS, e);
            return SyncResult.failed(e.getMessage());
        } finally {
            scopusRunning.set(false);
        }
    }

    // ------------------------------------------------------------------

    public Map<String, FsSyncState> currentState() {
        Map<String, FsSyncState> byType = new HashMap<>();
        syncStateRepo.findAll().forEach(s -> byType.put(s.getSyncType(), s));
        return byType;
    }

    /**
     * The upstream feed contains addresses with a trailing space inside the
     * domain, so a plain equality check against local accounts silently failed.
     * Everything is stored trimmed and lower-cased rather than patched per call site.
     */
    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Publications carry {@code SCOPUS_ID:85198403691} while the faculty record
     * holds the bare number. Stripping the prefix keeps the two comparable.
     */
    static String normalizeScopusId(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        int colon = v.indexOf(':');
        return colon >= 0 ? v.substring(colon + 1).trim() : v;
    }

    /**
     * Outcome of one sync run, surfaced on the admin page.
     *
     * <p>{@link Outcome#SKIPPED} is deliberately distinct from
     * {@link Outcome#FAILED}: "a run is already in progress" is normal and must
     * not be reported to an administrator as an error, or real failures stop
     * being noticed.
     */
    public record SyncResult(Outcome outcome, int rows, int requests, long durationMs, String message) {

        public enum Outcome {
            /** Completed and wrote data. */
            OK,
            /** Did not run — already running, disabled, or on cooldown. */
            SKIPPED,
            /** Ran and broke. */
            FAILED
        }

        public boolean success() {
            return outcome == Outcome.OK;
        }

        public boolean skipped() {
            return outcome == Outcome.SKIPPED;
        }

        static SyncResult ok(int rows, int requests, long durationMs) {
            return new SyncResult(Outcome.OK, rows, requests, durationMs, null);
        }

        static SyncResult skipped(String why) {
            return new SyncResult(Outcome.SKIPPED, 0, 0, 0, why);
        }

        public static SyncResult cooldown(String message) {
            return new SyncResult(Outcome.SKIPPED, 0, 0, 0, message);
        }

        static SyncResult failed(String why) {
            return new SyncResult(Outcome.FAILED, 0, 0, 0, why);
        }
    }
}
