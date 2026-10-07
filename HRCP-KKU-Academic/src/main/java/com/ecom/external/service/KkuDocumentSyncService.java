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
 * Service to synchronize KKU HR regulations and announcements from the HR site's two academic-rank pages —
 * พนักงานมหาวิทยาลัย (https://hr2.kku.ac.th/?page_id=5546) and ข้าราชการ (https://hr2.kku.ac.th/?page_id=5532).
 * Runs on a monthly schedule via cron and supports manual trigger by administrators.
 */
@Service
public class KkuDocumentSyncService {

    private static final Logger log = LoggerFactory.getLogger(KkuDocumentSyncService.class);
    public static final String SYNC_TYPE = "kku_regulations";

    public static final String EMPLOYEE_URL = "https://hr2.kku.ac.th/?page_id=5546";
    public static final String CIVIL_SERVANT_URL = "https://hr2.kku.ac.th/?page_id=5532";

    /** Category prefix for the civil-servant page — its documents follow ก.พ.อ. criteria, not the employee regulation */
    public static final String CIVIL_SERVANT_AUDIENCE = "ข้าราชการ";

    /** Gap in displayOrder between pages, so the second page's documents always list after the first's */
    private static final int ORDER_STRIDE = 1000;

    @Value("${kku.hr.sync.url:" + EMPLOYEE_URL + "}")
    private String kkuHrUrl = EMPLOYEE_URL;

    @Value("${kku.hr.sync.civil-servant-url:" + CIVIL_SERVANT_URL + "}")
    private String civilServantUrl = CIVIL_SERVANT_URL;

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
        } else {
            fixStaleIsNewFlags();
        }
    }

    /**
     * Reconciles existing database records where isNew was previously hardcoded to true.
     */
    @Transactional
    public void fixStaleIsNewFlags() {
        List<KkuRegulationDoc> docs = docRepo.findAll();
        boolean anyChanged = false;
        for (KkuRegulationDoc doc : docs) {
            boolean shouldBeNew = isActuallyNew(doc.getTitle(), doc.getPublishedYear());
            if (doc.getIsNew() == null || !doc.getIsNew().equals(shouldBeNew)) {
                doc.setIsNew(shouldBeNew);
                docRepo.save(doc);
                anyChanged = true;
            }
        }
        if (anyChanged) {
            log.info("Repaired stale isNew flags for KKU regulation documents in database.");
        }
    }

    public static boolean isActuallyNew(String title, String year) {
        if (title == null) return false;
        if (title.contains("🆕") || title.contains("NEW") || title.matches(".*\\(\\s*ใหม่\\s*\\).*")) {
            return true;
        }
        if (year != null) {
            try {
                int y = Integer.parseInt(year.trim());
                if (y >= 2569) return true;
            } catch (NumberFormatException ignored) {}
        }
        return title.contains("2569");
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
     * Performs synchronization from every source page. A page that cannot be fetched or parsed does not stop the
     * others — what was read is saved, and the run is reported as failed with the page that broke.
     */
    @Transactional
    public synchronized SyncResult syncNow() {
        long startTime = System.currentTimeMillis();
        FsSyncState state = syncStateRepo.findById(SYNC_TYPE).orElseGet(() -> new FsSyncState(SYNC_TYPE));
        state.setLastRunAt(LocalDateTime.now());
        state.setLastStatus(FsSyncState.STATUS_RUNNING);

        try {
            List<KkuRegulationDoc> parsedDocs = new ArrayList<>();
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            List<String> failures = new ArrayList<>();
            String[][] sources = { { kkuHrUrl, null }, { civilServantUrl, CIVIL_SERVANT_AUDIENCE } };
            for (int i = 0; i < sources.length; i++) {
                String url = sources[i][0];
                try {
                    log.info("Fetching KKU HR regulations from {}", url);
                    List<KkuRegulationDoc> fromPage = parser.parse(fetchHtml(url), sources[i][1]);
                    if (fromPage.isEmpty()) {
                        failures.add(url + " (ไม่พบเอกสาร)");
                    }
                    for (KkuRegulationDoc doc : fromPage) {
                        // ไฟล์เดียวกันอยู่ได้ทั้งสองหน้า — หน้าแรกที่เจอเป็นเจ้าของ ไม่อย่างนั้นหมวดจะสลับไปมาทุกรอบ
                        if (seenKeys.add(doc.getFileKey())) {
                            doc.setDisplayOrder(i * ORDER_STRIDE + (doc.getDisplayOrder() == null ? 0 : doc.getDisplayOrder()));
                            parsedDocs.add(doc);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Fetching {} failed: {}", url, e.getMessage());
                    failures.add(url + " (" + e.getMessage() + ")");
                }
            }
            if (parsedDocs.isEmpty()) {
                String errMsg = "No documents found while parsing " + String.join(", ", failures);
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
                    if (existing.getIsNew() == null || !existing.getIsNew().equals(parsed.getIsNew())) {
                        existing.setIsNew(parsed.getIsNew());
                        changed = true;
                    }
                    if (parsed.getPublishedYear() != null && !parsed.getPublishedYear().equals(existing.getPublishedYear())) {
                        existing.setPublishedYear(parsed.getPublishedYear());
                        changed = true;
                    }

                    if (changed) {
                        docRepo.save(existing);
                        updated++;
                    }
                } else {
                    docRepo.save(parsed);
                    added++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            boolean complete = failures.isEmpty();
            state.setLastStatus(complete ? FsSyncState.STATUS_OK : FsSyncState.STATUS_FAILED);
            if (complete) {
                state.setLastSuccessAt(LocalDateTime.now());
            }
            state.setRowsProcessed(parsedDocs.size());
            state.setDurationMs(duration);
            String msg = String.format("%s: พบ %d รายการ (เพิ่มใหม่ %d, อัปเดต %d) ในเวลา %d ms",
                    complete ? "ซิงค์สำเร็จ" : "ซิงค์ได้บางส่วน", parsedDocs.size(), added, updated, duration)
                    + (complete ? "" : " — ดึงไม่ได้: " + String.join(", ", failures));
            state.setMessage(msg);
            syncStateRepo.save(state);

            log.info(msg);
            return new SyncResult(complete, parsedDocs.size(), added, updated, msg);

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

    protected String fetchHtml(String url) throws java.io.IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(30000)
                .get()
                .html();
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
