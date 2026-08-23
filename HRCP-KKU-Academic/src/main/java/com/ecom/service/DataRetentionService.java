package com.ecom.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.service.AdminStorageService;
import com.ecom.academic.service.UserStorageService;
import com.ecom.external.repository.FsFacultyChangeRepository;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.NotificationRepository;

/**
 * Centralized Data Retention & Housekeeping Service.
 *
 * <p>Automates data pruning and storage trash cleanup across the system to prevent
 * database and disk bloat while adhering to the Thai Computer-Related Crime Act
 * (minimum 90-day log retention).
 *
 * <p>Executes automatically:
 * <ul>
 *   <li>On Application Startup (ApplicationReadyEvent)</li>
 *   <li>On Daily Cron Schedule (default: 03:30 AM)</li>
 *   <li>On Manual Admin Trigger (via AdminController)</li>
 * </ul>
 */
@Service
@Transactional
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationRepository notificationRepository;
    private final AdminLogRepository adminLogRepository;
    private final FsFacultyChangeRepository fsFacultyChangeRepository;
    private final AdminStorageService adminStorageService;
    private final UserStorageService userStorageService;

    @Value("${app.retention.notification-deleted-days:60}")
    private int notificationDeletedDays;

    @Value("${app.retention.notification-ancient-days:180}")
    private int notificationAncientDays;

    @Value("${app.retention.admin-log-days:365}")
    private int adminLogDays;

    @Value("${app.retention.faculty-sync-days:180}")
    private int facultySyncDays;

    @Value("${app.retention.storage-trash-days:30}")
    private int storageTrashDays;

    public DataRetentionService(
            NotificationRepository notificationRepository,
            AdminLogRepository adminLogRepository,
            FsFacultyChangeRepository fsFacultyChangeRepository,
            @Lazy AdminStorageService adminStorageService,
            @Lazy UserStorageService userStorageService) {
        this.notificationRepository = notificationRepository;
        this.adminLogRepository = adminLogRepository;
        this.fsFacultyChangeRepository = fsFacultyChangeRepository;
        this.adminStorageService = adminStorageService;
        this.userStorageService = userStorageService;
    }

    /**
     * Data Transfer Object summarizing a retention maintenance run.
     */
    public record RetentionExecutionReport(
            int deletedNotificationsPurged,
            int ancientNotificationsPurged,
            int adminLogsPurged,
            int facultySyncLogsPurged,
            int adminStorageTrashPurged,
            int userStorageTrashPurged,
            long durationMs,
            String executionTimestamp
    ) {
        public int totalDatabaseRecordsPurged() {
            return deletedNotificationsPurged + ancientNotificationsPurged + adminLogsPurged + facultySyncLogsPurged;
        }

        public int totalStorageFilesPurged() {
            return adminStorageTrashPurged + userStorageTrashPurged;
        }
    }

    /**
     * Startup hook: Runs on application start to purge any stale legacy data.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStartup() {
        log.info("🚀 [Startup Retention] Starting system data retention check and cleanup...");
        try {
            RetentionExecutionReport report = runFullRetentionCycle();
            log.info("🚀 [Startup Retention Complete] DB records purged: {} (Notif: {}, Logs: {}, Sync: {}), Storage trash files: {} (Took {}ms)",
                    report.totalDatabaseRecordsPurged(),
                    (report.deletedNotificationsPurged() + report.ancientNotificationsPurged()),
                    report.adminLogsPurged(),
                    report.facultySyncLogsPurged(),
                    report.totalStorageFilesPurged(),
                    report.durationMs());
        } catch (Exception e) {
            log.error("⚠️ [Startup Retention Error] Failed during startup data retention cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Daily Cron job to enforce data retention policies.
     * Default schedule: 03:30 AM every day.
     */
    @Scheduled(cron = "${app.retention.cron:0 30 3 * * ?}")
    public void scheduledDailyRetention() {
        log.info("⏰ [Scheduled Retention] Starting daily data retention maintenance...");
        try {
            RetentionExecutionReport report = runFullRetentionCycle();
            log.info("⏰ [Scheduled Retention Complete] DB records purged: {}, Storage trash files: {} (Took {}ms)",
                    report.totalDatabaseRecordsPurged(),
                    report.totalStorageFilesPurged(),
                    report.durationMs());
        } catch (Exception e) {
            log.error("⚠️ [Scheduled Retention Error] Failed during scheduled data retention maintenance: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes a full data retention cycle across all high-growth tables and storage trash.
     */
    @Transactional
    public RetentionExecutionReport runFullRetentionCycle() {
        long start = System.currentTimeMillis();
        String ts = LocalDateTime.now().format(TS_FMT);

        log.info("Executing full data retention cycle (NotifDelDays={}, NotifMaxDays={}, AdminLogDays={}, SyncDays={}, TrashDays={})...",
                notificationDeletedDays, notificationAncientDays, adminLogDays, facultySyncDays, storageTrashDays);

        int notifDel = purgeSoftDeletedNotifications(notificationDeletedDays);
        int notifAncient = purgeAncientNotifications(notificationAncientDays);
        int adminLogs = purgeOldAdminLogs(adminLogDays);
        int facultySync = purgeOldFacultySyncLogs(facultySyncDays);
        int adminTrash = purgeAdminStorageTrash(storageTrashDays);
        int userTrash = purgeUserStorageTrash(storageTrashDays);

        long duration = System.currentTimeMillis() - start;
        return new RetentionExecutionReport(notifDel, notifAncient, adminLogs, facultySync, adminTrash, userTrash, duration, ts);
    }

    // ================= Retention Policy Pruning Methods =================

    /**
     * Purges soft-deleted notifications (isDeleted = true) older than the specified days.
     */
    @Transactional
    public int purgeSoftDeletedNotifications(int olderThanDays) {
        if (olderThanDays <= 0) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        int count = notificationRepository.deleteDeletedNotificationsBefore(cutoff);
        if (count > 0) {
            log.info("Purged {} soft-deleted notification records older than {} days (cutoff: {})", count, olderThanDays, cutoff);
        }
        return count;
    }

    /**
     * Purges ancient notifications older than the specified maximum retention days.
     */
    @Transactional
    public int purgeAncientNotifications(int olderThanDays) {
        if (olderThanDays <= 0) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        int count = notificationRepository.deleteAncientNotificationsBefore(cutoff);
        if (count > 0) {
            log.info("Purged {} ancient notification records older than {} days (cutoff: {})", count, olderThanDays, cutoff);
        }
        return count;
    }

    /**
     * Purges admin audit logs older than the specified days.
     * Note: Minimum threshold of 90 days is strictly enforced for Computer-Related Crime Act compliance.
     */
    @Transactional
    public int purgeOldAdminLogs(int olderThanDays) {
        // Enforce minimum 90 days legal retention safeguard
        int effectiveDays = Math.max(90, olderThanDays);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(effectiveDays);
        int count = adminLogRepository.deleteLogsBefore(cutoff);
        if (count > 0) {
            log.info("Purged {} admin audit logs older than {} days (cutoff: {})", count, effectiveDays, cutoff);
        }
        return count;
    }

    /**
     * Purges reviewed/processed faculty sync change logs (APPROVED / REJECTED) older than the specified days.
     */
    @Transactional
    public int purgeOldFacultySyncLogs(int olderThanDays) {
        if (olderThanDays <= 0) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        int count = fsFacultyChangeRepository.deleteProcessedChangesBefore(cutoff);
        if (count > 0) {
            log.info("Purged {} processed faculty sync change logs older than {} days (cutoff: {})", count, olderThanDays, cutoff);
        }
        return count;
    }

    /**
     * Purges expired trash files from Admin Cloud Storage.
     */
    public int purgeAdminStorageTrash(int olderThanDays) {
        if (adminStorageService == null || olderThanDays <= 0) return 0;
        return adminStorageService.purgeOldTrash(olderThanDays);
    }

    /**
     * Purges expired trash files from User Cloud Storage.
     */
    public int purgeUserStorageTrash(int olderThanDays) {
        if (userStorageService == null || olderThanDays <= 0) return 0;
        return userStorageService.purgeOldTrash(olderThanDays);
    }

    // ================= Getters for Current Retention Configuration =================

    public int getNotificationDeletedDays() { return notificationDeletedDays; }
    public int getNotificationAncientDays() { return notificationAncientDays; }
    public int getAdminLogDays() { return adminLogDays; }
    public int getFacultySyncDays() { return facultySyncDays; }
    public int getStorageTrashDays() { return storageTrashDays; }
}
