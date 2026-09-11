package com.ecom.external.harvest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ecom.external.harvest.config.HarvestProperties;
import com.ecom.external.harvest.model.HarvestContext;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.FsSyncState;
import com.ecom.external.repository.ExternalAuthorMappingRepository;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.FsSyncStateRepository;
import com.ecom.external.service.FsSyncWriter;
import com.ecom.service.SystemAlertService;

/**
 * Main orchestrator for multi-source academic publication harvesting.
 *
 * <p>Uses Java 21 Virtual Threads to execute external source adapters (Crossref, OpenAlex,
 * DBLP, ThaiJO, KKU IR) concurrently, then batches writes through {@link FsSyncWriter}
 * with deduplication and journal quartile enrichment.
 */
@Service
public class PublicationHarvestService {

    private static final Logger log = LoggerFactory.getLogger(PublicationHarvestService.class);
    private static final String SCHEDULE_ZONE = "Asia/Bangkok";
    private static final int WRITE_BATCH_SIZE = 50;

    private final List<PublicationSourceAdapter> adapters;
    private final HarvestProperties props;
    private final FsFacultyRepository facultyRepo;
    private final ExternalAuthorMappingRepository mappingRepo;
    private final FsSyncStateRepository syncStateRepo;
    private final FsSyncWriter writer;
    private final SystemAlertService alerts;
    private final HarvestProgressTracker progressTracker;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public PublicationHarvestService(
            List<PublicationSourceAdapter> adapters,
            HarvestProperties props,
            FsFacultyRepository facultyRepo,
            ExternalAuthorMappingRepository mappingRepo,
            FsSyncStateRepository syncStateRepo,
            FsSyncWriter writer,
            SystemAlertService alerts,
            HarvestProgressTracker progressTracker
    ) {
        this.adapters = adapters != null ? adapters : List.of();
        this.props = props;
        this.facultyRepo = facultyRepo;
        this.mappingRepo = mappingRepo;
        this.syncStateRepo = syncStateRepo;
        this.writer = writer;
        this.alerts = alerts;
        this.progressTracker = progressTracker;
    }

    /**
     * Daily scheduled cron job.
     */
    @Scheduled(cron = "${harvest.cron:0 0 4 * * *}", zone = SCHEDULE_ZONE)
    public void scheduledHarvest() {
        if (!props.isEnabled()) {
            log.info("Multi-source publication harvesting is disabled by configuration");
            return;
        }
        harvestAll();
    }

    /**
     * Asynchronously starts the multi-source harvest on a Virtual Thread.
     */
    public java.util.concurrent.CompletableFuture<List<HarvestResult>> harvestAllAsync() {
        return java.util.concurrent.CompletableFuture.supplyAsync(this::harvestAll, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Asynchronously starts a single source harvest on a Virtual Thread.
     */
    public java.util.concurrent.CompletableFuture<HarvestResult> harvestSourceAsync(String sourceName) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> harvestSource(sourceName), Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Runs all enabled source adapters in parallel using virtual threads.
     */
    public List<HarvestResult> harvestAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Publication harvest is already running — skipping request");
            return List.of(HarvestResult.skipped("ALL", "Another harvest job is already in progress"));
        }

        long startedAt = System.currentTimeMillis();
        log.info("Starting multi-source publication harvest across {} adapter(s)", adapters.size());

        try {
            List<FsFaculty> facultyList = facultyRepo.findAll();
            if (facultyList.isEmpty()) {
                log.warn("Publication harvest skipped: no faculty found in database");
                progressTracker.finish(List.of(), 0);
                return List.of(HarvestResult.skipped("ALL", "No faculty found in database"));
            }

            Map<Long, List<ExternalAuthorMapping>> mappingsByUserId = loadMappingsGroupedByUser();

            List<String> enabledSources = adapters.stream()
                    .filter(PublicationSourceAdapter::isEnabled)
                    .map(PublicationSourceAdapter::sourceName)
                    .toList();

            // Total real steps = 1 step per adapter + estimated 5 DB batch writing steps
            int totalSteps = Math.max(1, enabledSources.size() + 5);
            progressTracker.start(totalSteps, enabledSources);

            // Run each adapter concurrently on a Java 21 Virtual Thread
            List<HarvestResult> results = new ArrayList<>();
            int totalWritten = 0;

            int adapterTimeoutSeconds = Math.max(30, props.getAdapterTimeoutSeconds());
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Callable<HarvestResult>> tasks = new ArrayList<>();
                List<String> taskSources = new ArrayList<>();

                for (PublicationSourceAdapter adapter : adapters) {
                    if (!adapter.isEnabled()) {
                        continue;
                    }

                    String source = adapter.sourceName();
                    taskSources.add(source);

                    tasks.add(() -> {
                        String syncType = source.toLowerCase();
                        long taskStart = System.currentTimeMillis();
                        writer.markRunning(syncType);
                        try {
                            OffsetDateTime cursor = loadCursor(syncType);
                            progressTracker.updateSource(source, "RUNNING", 15, 0, "กำลังดึงข้อมูล...");

                            HarvestContext context = HarvestContext.of(
                                    cursor,
                                    props.getYearFrom(),
                                    facultyList,
                                    mappingsByUserId
                            );

                            log.info("Adapter [{}] starting harvest...", source);
                            HarvestResult result = adapter.harvest(context);

                            // Batch write harvested publications to database
                            int writtenForSource = 0;
                            if (result.success() && !result.publications().isEmpty()) {
                                try {
                                    writtenForSource = writeInBatches(result.publications(), source);
                                } catch (Exception wex) {
                                    log.error("Failed to write harvested batches for {}: {}", source, wex.getMessage(), wex);
                                    writer.recordFailure(syncType, wex);
                                    progressTracker.updateSource(source, "FAILED", 100, 0, "บันทึกล้มเหลว: " + wex.getMessage());
                                    return HarvestResult.failed(source, result.requestsMade(), System.currentTimeMillis() - taskStart, wex.getMessage());
                                }
                                writer.recordSuccess(syncType, writtenForSource, result.requestsMade(),
                                        result.durationMs(), result.newCursor(), result.message());
                                log.info("Adapter [{}] wrote {} publication(s)", source, writtenForSource);
                                progressTracker.updateSource(source, "SUCCESS", 100, result.publications().size(),
                                        String.format("สำเร็จ (%d รายการ, บันทึก %d)", result.publications().size(), writtenForSource));
                            } else if (result.success()) {
                                writer.recordSuccess(syncType, 0, result.requestsMade(),
                                        result.durationMs(), result.newCursor(), result.message());
                                progressTracker.updateSource(source, "SUCCESS", 100, 0, "สำเร็จ (ไม่พบรายการใหม่)");
                            } else {
                                writer.recordFailure(syncType, new RuntimeException(result.message()));
                                progressTracker.updateSource(source, "FAILED", 100, 0, result.message());
                            }

                            progressTracker.advance(source, "ประมวลผล " + source + " เสร็จสิ้น");
                            return result;
                        } catch (Throwable t) {
                            String err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                            log.error("Fatal unhandled exception in adapter [{}]: {}", source, err, t);
                            writer.recordFailure(syncType, t instanceof Exception ? (Exception) t : new RuntimeException(err));
                            progressTracker.updateSource(source, "FAILED", 100, 0, "ข้อผิดพลาด: " + err);
                            progressTracker.advance(source, "ประมวลผล " + source + " ล้มเหลว");
                            return HarvestResult.failed(source, 0, System.currentTimeMillis() - taskStart, err);
                        }
                    });
                }

                List<Future<HarvestResult>> futures = executor.invokeAll(tasks, adapterTimeoutSeconds, TimeUnit.SECONDS);
                for (int i = 0; i < futures.size(); i++) {
                    Future<HarvestResult> f = futures.get(i);
                    String sourceName = taskSources.get(i);
                    String syncType = sourceName.toLowerCase();
                    if (f.isCancelled()) {
                        log.warn("Adapter [{}] timed out after {}s", sourceName, adapterTimeoutSeconds);
                        writer.recordFailure(syncType, new TimeoutException("หมดเวลาการดึงข้อมูล (Timeout " + adapterTimeoutSeconds + "s)"));
                        progressTracker.updateSource(sourceName, "FAILED", 100, 0, "หมดเวลาเชื่อมต่อ (Timeout)");
                        results.add(HarvestResult.failed(sourceName, 0, adapterTimeoutSeconds * 1000L, "Timed out after " + adapterTimeoutSeconds + "s"));
                    } else {
                        try {
                            results.add(f.get());
                        } catch (Exception e) {
                            log.error("Failed to retrieve result for {}: {}", sourceName, e.getMessage());
                            writer.recordFailure(syncType, e);
                            results.add(HarvestResult.failed(sourceName, 0, 0, e.getMessage()));
                        }
                    }
                }
            }

            long totalDuration = System.currentTimeMillis() - startedAt;
            int totalWorks = results.stream().mapToInt(r -> r.publications().size()).sum();
            log.info("Multi-source harvest completed in {} ms: {} total works from {} sources",
                    totalDuration, totalWorks, results.size());

            alerts.success("เก็บเกี่ยวผลงานวิจัยรอบวัน",
                    "เสร็จสิ้น " + results.size() + " แหล่งข้อมูล รวม " + totalWorks + " รายการ (" + totalDuration + " ms)");

            progressTracker.finish(results, totalWritten);
            return results;

        } catch (Exception e) {
            log.error("Multi-source publication harvest encountered an unexpected failure: {}", e.getMessage(), e);
            alerts.failure("เก็บเกี่ยวผลงานวิจัยรอบวัน", "เกิดข้อผิดพลาด: " + e.getMessage());
            progressTracker.finish(List.of(HarvestResult.failed("ALL", 0, System.currentTimeMillis() - startedAt, e.getMessage())), 0);
            return List.of(HarvestResult.failed("ALL", 0, System.currentTimeMillis() - startedAt, e.getMessage()));
        } finally {
            running.set(false);
        }
    }

    public HarvestProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @PostConstruct
    public void onStartup() {
        // Automatically recover any stale RUNNING states on application startup
        resetStaleJobs();
    }

    /**
     * Resets any jobs that have been left in RUNNING status for longer than staleThresholdMinutes,
     * marking them as FAILED so they do not show an endless spinner on the UI.
     */
    public int resetStaleJobs() {
        int resetCount = 0;
        int thresholdMins = Math.max(5, props.getStaleThresholdMinutes());
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(thresholdMins);

        for (FsSyncState s : syncStateRepo.findAll()) {
            if (FsSyncState.STATUS_RUNNING.equalsIgnoreCase(s.getLastStatus())) {
                boolean isHarvestJob = adapters.stream().anyMatch(a -> a.sourceName().equalsIgnoreCase(s.getSyncType()));
                boolean isStale = s.getLastRunAt() == null || s.getLastRunAt().isBefore(cutoff);
                if (isStale || (isHarvestJob && !isRunning())) {
                    s.setLastStatus(FsSyncState.STATUS_FAILED);
                    s.setMessage("งานค้างเกินกำหนด (ระบบรีเซ็ตให้อัตโนมัติ)");
                    syncStateRepo.save(s);
                    resetCount++;
                    log.info("Reset stale RUNNING job for syncType: {}", s.getSyncType());
                }
            }
        }
        if (!isRunning() && progressTracker != null) {
            progressTracker.finish(List.of(), 0);
        }
        return resetCount;
    }

    /**
     * Executes a single specific adapter manually by source name with timeout protection.
     */
    public HarvestResult harvestSource(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return HarvestResult.failed("UNKNOWN", 0, 0, "Source name cannot be blank");
        }

        PublicationSourceAdapter adapter = adapters.stream()
                .filter(a -> a.sourceName().equalsIgnoreCase(sourceName))
                .findFirst()
                .orElse(null);

        if (adapter == null) {
            return HarvestResult.failed(sourceName, 0, 0, "No adapter registered for source: " + sourceName);
        }

        String source = adapter.sourceName();
        String syncType = source.toLowerCase();

        int totalSteps = 3; // 1 step fetch + 2 steps DB write
        progressTracker.start(totalSteps, List.of(source));
        progressTracker.updateSource(source, "RUNNING", 30, 0, "กำลังดึงข้อมูลจาก " + source + "...");
        progressTracker.advance(source, "กำลังดึงข้อมูลจาก " + source + "...");

        List<FsFaculty> facultyList = facultyRepo.findAll();
        Map<Long, List<ExternalAuthorMapping>> mappings = loadMappingsGroupedByUser();

        writer.markRunning(syncType);
        long startMs = System.currentTimeMillis();
        try {
            OffsetDateTime cursor = loadCursor(syncType);
            HarvestContext context = HarvestContext.of(cursor, props.getYearFrom(), facultyList, mappings);

            int timeoutSec = Math.max(30, props.getAdapterTimeoutSeconds());
            CompletableFuture<HarvestResult> future = CompletableFuture.supplyAsync(() -> adapter.harvest(context),
                    Executors.newVirtualThreadPerTaskExecutor());
            HarvestResult result = future.get(timeoutSec, TimeUnit.SECONDS);

            int written = 0;
            if (result.success() && !result.publications().isEmpty()) {
                written = writeInBatches(result.publications(), source);
                writer.recordSuccess(syncType, written, result.requestsMade(), result.durationMs(), result.newCursor(), result.message());
                progressTracker.updateSource(source, "SUCCESS", 100, result.publications().size(),
                        String.format("สำเร็จ (%d รายการ, บันทึก %d)", result.publications().size(), written));
            } else if (result.success()) {
                writer.recordSuccess(syncType, 0, result.requestsMade(), result.durationMs(), result.newCursor(), result.message());
                progressTracker.updateSource(source, "SUCCESS", 100, 0, "สำเร็จ (ไม่พบรายการใหม่)");
            } else {
                writer.recordFailure(syncType, new RuntimeException(result.message()));
                progressTracker.updateSource(source, "FAILED", 100, 0, result.message());
            }

            progressTracker.finish(List.of(result), written);
            return result;
        } catch (Throwable t) {
            String msg = (t instanceof TimeoutException)
                    ? "หมดเวลาเชื่อมต่อ (Timeout " + props.getAdapterTimeoutSeconds() + "s)"
                    : (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            log.error("Single-source harvest failed for {}: {}", source, msg, t);
            writer.recordFailure(syncType, t instanceof Exception ? (Exception) t : new RuntimeException(msg));
            progressTracker.updateSource(source, "FAILED", 100, 0, msg);
            HarvestResult failRes = HarvestResult.failed(source, 0, System.currentTimeMillis() - startMs, msg);
            progressTracker.finish(List.of(failRes), 0);
            return failRes;
        }
    }

    private int writeInBatches(List<RawPublication> publications) {
        return writeInBatches(publications, "DATABASE");
    }

    private int writeInBatches(List<RawPublication> publications, String source) {
        int written = 0;
        double threshold = props.getFuzzyThreshold();

        for (int i = 0; i < publications.size(); i += WRITE_BATCH_SIZE) {
            int end = Math.min(i + WRITE_BATCH_SIZE, publications.size());
            List<RawPublication> batch = publications.subList(i, end);
            written += writer.writeHarvestedBatch(batch, threshold);
            if (progressTracker != null) {
                progressTracker.advance("DATABASE", String.format("กำลังบันทึกข้อมูล %s (%d/%d)...",
                        source != null ? source : "", end, publications.size()));
            }
        }
        return written;
    }

    private Map<Long, List<ExternalAuthorMapping>> loadMappingsGroupedByUser() {
        Map<Long, List<ExternalAuthorMapping>> map = new HashMap<>();
        for (ExternalAuthorMapping m : mappingRepo.findAll()) {
            map.computeIfAbsent(m.getFsUserId(), k -> new ArrayList<>()).add(m);
        }
        return map;
    }

    private OffsetDateTime loadCursor(String syncType) {
        return syncStateRepo.findById(syncType)
                .map(FsSyncState::getLastUpdatedSince)
                .orElse(null);
    }

    public boolean isRunning() {
        return running.get();
    }
}
