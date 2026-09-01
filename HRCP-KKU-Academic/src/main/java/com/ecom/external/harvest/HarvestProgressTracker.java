package com.ecom.external.harvest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.ecom.external.harvest.model.HarvestResult;

/**
 * Thread-safe real-time progress tracker for Multi-Source Publication Harvesting.
 * Calculates exact, accurate percentages based on completed execution steps.
 */
@Component
public class HarvestProgressTracker {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger totalSteps = new AtomicInteger(100);
    private final AtomicInteger completedSteps = new AtomicInteger(0);

    private volatile String currentSource = "";
    private volatile String currentTask = "พร้อมทำงาน";
    private volatile int totalWorksFound = 0;
    private volatile long startedAt = 0L;
    private volatile long durationMs = 0L;
    private volatile boolean completed = false;
    private volatile String completionSummary = "";

    private final Map<String, SourceProgress> sources = new ConcurrentHashMap<>();

    public record SourceProgress(
            String sourceName,
            String status, // PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
            int percent,
            int itemsFound,
            String message
    ) {}

    public record ProgressSnapshot(
            boolean running,
            boolean completed,
            int percent,
            int totalSteps,
            int completedSteps,
            String currentSource,
            String currentTask,
            int totalWorksFound,
            long durationMs,
            String completionSummary,
            List<SourceProgress> sources
    ) {}

    public void start(int totalStepCount, List<String> initialSources) {
        this.running.set(true);
        this.completed = false;
        this.totalSteps.set(Math.max(1, totalStepCount));
        this.completedSteps.set(0);
        this.currentSource = initialSources != null && !initialSources.isEmpty() ? initialSources.get(0) : "";
        this.currentTask = "กำลังเริ่มต้นการดึงข้อมูล...";
        this.totalWorksFound = 0;
        this.startedAt = System.currentTimeMillis();
        this.durationMs = 0L;
        this.completionSummary = "";

        this.sources.clear();
        if (initialSources != null) {
            for (String src : initialSources) {
                this.sources.put(src.toUpperCase(), new SourceProgress(src.toUpperCase(), "PENDING", 0, 0, "รอดำเนินการ"));
            }
        }
    }

    public void updateSource(String source, String status, int percent, int itemsFound, String message) {
        String key = source.toUpperCase();
        this.sources.put(key, new SourceProgress(key, status, percent, itemsFound, message));
    }

    public void advance(String source, String taskMessage) {
        this.currentSource = source;
        this.currentTask = taskMessage;
        this.completedSteps.incrementAndGet();
        if (startedAt > 0) {
            this.durationMs = System.currentTimeMillis() - startedAt;
        }
    }

    public void advance(String source, String taskMessage, int stepIncrement) {
        this.currentSource = source;
        this.currentTask = taskMessage;
        this.completedSteps.addAndGet(Math.max(1, stepIncrement));
        if (startedAt > 0) {
            this.durationMs = System.currentTimeMillis() - startedAt;
        }
    }

    public void addFoundWorks(int count) {
        this.totalWorksFound += Math.max(0, count);
    }

    public void finish(List<HarvestResult> results, int totalWritten) {
        this.running.set(false);
        this.completed = true;
        this.completedSteps.set(this.totalSteps.get());
        if (startedAt > 0) {
            this.durationMs = System.currentTimeMillis() - startedAt;
        }

        int successCount = 0;
        int totalWorks = 0;
        if (results != null) {
            for (HarvestResult r : results) {
                if (r.success()) {
                    successCount++;
                    totalWorks += r.publications().size();
                    updateSource(r.sourceName(), "SUCCESS", 100, r.publications().size(), r.message());
                } else {
                    updateSource(r.sourceName(), "FAILED", 100, 0, r.message());
                }
            }
        }

        this.totalWorksFound = totalWorks;
        this.currentTask = "เสร็จสิ้นการดึงข้อมูลทั้งหมด";
        this.completionSummary = String.format(
                "ดึงงานวิจัยสำเร็จ %d/%d แหล่งข้อมูล — รวม %d รายการ บันทึกลงฐานข้อมูล %d รายการ (%.1f วินาที)",
                successCount, results != null ? results.size() : 0, totalWorks, totalWritten, durationMs / 1000.0
        );
    }

    public ProgressSnapshot snapshot() {
        int total = Math.max(1, totalSteps.get());
        int current = completedSteps.get();
        int pct = (int) Math.min(100, Math.round((current * 100.0) / total));
        if (completed) {
            pct = 100;
        }

        long currentDuration = startedAt > 0 ? (System.currentTimeMillis() - startedAt) : 0L;
        if (completed && durationMs > 0) {
            currentDuration = durationMs;
        }

        List<SourceProgress> srcList = new ArrayList<>(sources.values());
        return new ProgressSnapshot(
                running.get(),
                completed,
                pct,
                total,
                current,
                currentSource,
                currentTask,
                totalWorksFound,
                currentDuration,
                completionSummary,
                srcList
        );
    }

    public boolean isRunning() {
        return running.get();
    }

    public void reset() {
        this.running.set(false);
        this.completed = false;
        this.totalSteps.set(100);
        this.completedSteps.set(0);
        this.currentSource = "";
        this.currentTask = "พร้อมทำงาน";
        this.totalWorksFound = 0;
        this.startedAt = 0L;
        this.durationMs = 0L;
        this.completionSummary = "";
        this.sources.clear();
    }
}
