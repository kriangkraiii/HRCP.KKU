package com.ecom.external.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.external.model.FsSyncState;
import com.ecom.external.model.KkuRegulationDoc;
import com.ecom.external.repository.FsSyncStateRepository;
import com.ecom.external.repository.KkuRegulationDocRepository;

/**
 * Service to synchronize KKU HR regulations and announcements from https://hr2.kku.ac.th/?page_id=5546.
 * Runs on a monthly schedule via cron and supports manual trigger by administrators.
 */
@Service
public class KkuDocumentSyncService {

    private static final Logger log = LoggerFactory.getLogger(KkuDocumentSyncService.class);
    public static final String SYNC_TYPE = "kku_regulations";

    @Value("${kku.hr.sync.url:https://hr2.kku.ac.th/?page_id=5546}")
    private String kkuHrUrl;

    @Value("${kku.hr.sync.enabled:true}")
    private boolean syncEnabled;

    private final KkuDocumentParser parser;
    private final KkuRegulationDocRepository docRepo;
    private final FsSyncStateRepository syncStateRepo;

    public KkuDocumentSyncService(KkuDocumentParser parser,
                                  KkuRegulationDocRepository docRepo,
                                  FsSyncStateRepository syncStateRepo) {
        this.parser = parser;
        this.docRepo = docRepo;
        this.syncStateRepo = syncStateRepo;
    }

    public static class CategoryGroup {
        private String title;
        private String icon;
        private String color;
        private List<KkuRegulationDoc> docs = new ArrayList<>();

        public CategoryGroup(String title, String icon, String color) {
            this.title = title;
            this.icon = icon;
            this.color = color;
        }

        public String getTitle() { return title; }
        public String getIcon() { return icon; }
        public String getColor() { return color; }
        public List<KkuRegulationDoc> getDocs() { return docs; }
        public void addDoc(KkuRegulationDoc doc) { this.docs.add(doc); }
    }

    public static class SyncResult {
        private final boolean success;
        private final int totalParsed;
        private final int addedCount;
        private final int updatedCount;
        private final String message;

        public SyncResult(boolean success, int totalParsed, int addedCount, int updatedCount, String message) {
            this.success = success;
            this.totalParsed = totalParsed;
            this.addedCount = addedCount;
            this.updatedCount = updatedCount;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public int getTotalParsed() { return totalParsed; }
        public int getAddedCount() { return addedCount; }
        public int getUpdatedCount() { return updatedCount; }
        public String getMessage() { return message; }
    }

    /**
     * Seed initial records on application startup if database is empty.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (docRepo.count() == 0) {
            log.info("KkuRegulationDoc table is empty on startup. Triggering initial sync...");
            syncNow();
        }
    }

    /**
     * Cron scheduler: runs once every month (Default: 02:00 AM on the 1st of every month in Asia/Bangkok timezone).
     */
    @Scheduled(cron = "${kku.hr.sync.cron:0 0 2 1 * ?}", zone = "Asia/Bangkok")
    public void runMonthlyScheduledSync() {
        if (!syncEnabled) {
            log.info("KKU regulation doc sync is disabled by configuration.");
            return;
        }
        log.info("Executing monthly scheduled sync for KKU HR regulations...");
        syncNow();
    }

    /**
     * Performs synchronization from https://hr2.kku.ac.th/?page_id=5546.
     */
    @Transactional
    public synchronized SyncResult syncNow() {
        long startTime = System.currentTimeMillis();
        FsSyncState state = syncStateRepo.findById(SYNC_TYPE).orElseGet(() -> new FsSyncState(SYNC_TYPE));
        state.setLastRunAt(LocalDateTime.now());
        state.setLastStatus(FsSyncState.STATUS_RUNNING);

        try {
            log.info("Fetching KKU HR regulations from {}", kkuHrUrl);
            String html = Jsoup.connect(kkuHrUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(30000)
                    .get()
                    .html();

            List<KkuRegulationDoc> parsedDocs = parser.parse(html);
            if (parsedDocs.isEmpty()) {
                String errMsg = "No documents found while parsing " + kkuHrUrl;
                log.warn(errMsg);
                state.setLastStatus(FsSyncState.STATUS_FAILED);
                state.setMessage(errMsg);
                state.setDurationMs(System.currentTimeMillis() - startTime);
                syncStateRepo.save(state);
                return new SyncResult(false, 0, 0, 0, errMsg);
            }

            int added = 0;
            int updated = 0;

            for (KkuRegulationDoc parsed : parsedDocs) {
                Optional<KkuRegulationDoc> existingOpt = docRepo.findByFileKey(parsed.getFileKey());
                if (existingOpt.isPresent()) {
                    KkuRegulationDoc existing = existingOpt.get();
                    boolean changed = false;

                    if (!existing.getTitle().equals(parsed.getTitle())) {
                        existing.setTitle(parsed.getTitle());
                        changed = true;
                    }
                    if (!existing.getCategory().equals(parsed.getCategory())) {
                        existing.setCategory(parsed.getCategory());
                        existing.setCategoryIcon(parsed.getCategoryIcon());
                        existing.setCategoryColor(parsed.getCategoryColor());
                        changed = true;
                    }
                    if (!existing.getFileUrl().equals(parsed.getFileUrl())) {
                        existing.setFileUrl(parsed.getFileUrl());
                        changed = true;
                    }
                    if (existing.getDisplayOrder() == null || !existing.getDisplayOrder().equals(parsed.getDisplayOrder())) {
                        existing.setDisplayOrder(parsed.getDisplayOrder());
                        changed = true;
                    }

                    if (changed) {
                        docRepo.save(existing);
                        updated++;
                    }
                } else {
                    parsed.setIsNew(true);
                    docRepo.save(parsed);
                    added++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            state.setLastStatus(FsSyncState.STATUS_OK);
            state.setLastSuccessAt(LocalDateTime.now());
            state.setRowsProcessed(parsedDocs.size());
            state.setDurationMs(duration);
            String msg = String.format("ซิงค์สำเร็จ: พบ %d รายการ (เพิ่มใหม่ %d, อัปเดต %d) ในเวลา %d ms",
                    parsedDocs.size(), added, updated, duration);
            state.setMessage(msg);
            syncStateRepo.save(state);

            log.info(msg);
            return new SyncResult(true, parsedDocs.size(), added, updated, msg);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errMsg = "เกิดข้อผิดพลาดในการซิงค์: " + e.getMessage();
            log.error("Sync KKU HR regulations failed: {}", e.getMessage(), e);

            state.setLastStatus(FsSyncState.STATUS_FAILED);
            state.setDurationMs(duration);
            state.setMessage(errMsg);
            syncStateRepo.save(state);

            return new SyncResult(false, 0, 0, 0, errMsg);
        }
    }

    /**
     * Returns all documents grouped by category for frontend display.
     */
    public List<CategoryGroup> getGroupedDocuments() {
        List<KkuRegulationDoc> allDocs = docRepo.findAllByOrderByDisplayOrderAscIdAsc();
        Map<String, CategoryGroup> groupMap = new LinkedHashMap<>();

        for (KkuRegulationDoc doc : allDocs) {
            String cat = doc.getCategory();
            CategoryGroup group = groupMap.computeIfAbsent(cat,
                    k -> new CategoryGroup(k, doc.getCategoryIcon(), doc.getCategoryColor()));
            group.addDoc(doc);
        }

        return new ArrayList<>(groupMap.values());
    }

    /**
     * Gets the current synchronization state.
     */
    public FsSyncState getSyncState() {
        return syncStateRepo.findById(SYNC_TYPE).orElse(null);
    }
}
