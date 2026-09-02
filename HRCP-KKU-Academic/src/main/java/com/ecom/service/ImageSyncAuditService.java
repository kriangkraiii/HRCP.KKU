package com.ecom.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Service to audit and maintain consistency between image files on disk
 * and database references across the entire project.
 *
 * <p>Features:
 * <ul>
 *   <li>Runs automatically on application startup (ApplicationReadyEvent)</li>
 *   <li>Runs on a scheduled daily Cron job (default: 03:00 AM)</li>
 *   <li>Detects and purges orphan image files (files on disk not referenced by any DB user)</li>
 *   <li>Auto-heals broken DB references (users referencing non-existent files reset to default.png)</li>
 *   <li>Guarantees protected system assets (default.png, system logos) are never deleted</li>
 * </ul>
 */
@Service
public class ImageSyncAuditService {

    private static final Logger log = LoggerFactory.getLogger(ImageSyncAuditService.class);

    private final ProfileImageStorage profileImageStorage;
    private final UserRepository userRepository;
    private final com.ecom.academic.service.AdminStorageService adminStorageService;

    public ImageSyncAuditService(
            ProfileImageStorage profileImageStorage,
            UserRepository userRepository,
            @org.springframework.context.annotation.Lazy com.ecom.academic.service.AdminStorageService adminStorageService) {
        this.profileImageStorage = profileImageStorage;
        this.userRepository = userRepository;
        this.adminStorageService = adminStorageService;
    }

    /**
     * Audit report data transfer object.
     */
    public record ImageAuditReport(
            int totalDiskFiles,
            int totalDbUsers,
            int activeDbImageReferences,
            List<String> orphanFilesFound,
            List<String> orphanFilesPurged,
            List<String> missingDbFilesFound,
            List<String> missingDbFilesHealed,
            long durationMs
    ) {}

    /**
     * Triggered automatically upon application startup.
     * Cleans up any leftover/legacy orphan images and heals missing references immediately.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStartup() {
        log.info("[Startup Image & Storage Sync] Initializing startup check, orphan cleanup, and expired trash purge...");
        try {
            ImageAuditReport report = auditAndSync(true, false);
            int adminTrashPurged = adminStorageService != null ? adminStorageService.purgeOldTrash(30) : 0;
            // คลังไฟล์ส่วนตัวของผู้ยื่นถูกปิดและ drop ตารางไปแล้ว จึงไม่มีถังขยะให้ล้าง
            int userTrashPurged = 0;
            log.info("[Startup Sync Complete] Disk files: {}, DB users: {}, Orphan images purged: {}, Missing references healed: {}, Expired trash files purged: {} (Took {}ms)",
                    report.totalDiskFiles(), report.totalDbUsers(),
                    report.orphanFilesPurged().size(), report.missingDbFilesHealed().size(),
                    (adminTrashPurged + userTrashPurged), report.durationMs());
        } catch (Exception e) {
            log.error("[Startup Image & Storage Sync Error] Failed during startup check: {}", e.getMessage(), e);
        }
    }

    /**
     * Daily Cron job to clean up unused orphan images, purge expired trash, and synchronize database references.
     * Default schedule: 03:00 AM every day.
     */
    @Scheduled(cron = "${app.images.cleanup-cron:0 0 3 * * ?}")
    public void scheduledDailyCleanup() {
        log.info("[Scheduled Storage Cleanup] Starting daily cron image audit, orphan cleanup, and 30-day trash purge...");
        try {
            ImageAuditReport report = auditAndSync(true, true);
            int adminTrashPurged = adminStorageService != null ? adminStorageService.purgeOldTrash(30) : 0;
            // คลังไฟล์ส่วนตัวของผู้ยื่นถูกปิดและ drop ตารางไปแล้ว จึงไม่มีถังขยะให้ล้าง
            int userTrashPurged = 0;
            log.info("[Scheduled Storage Cleanup Complete] Disk files: {}, Orphan images purged: {}, Missing references healed: {}, Expired trash files purged: {} (Took {}ms)",
                    report.totalDiskFiles(), report.orphanFilesPurged().size(),
                    report.missingDbFilesHealed().size(), (adminTrashPurged + userTrashPurged),
                    report.durationMs());
        } catch (Exception e) {
            log.error("[Scheduled Storage Cleanup Error] Failed during scheduled cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Runs audit and synchronization.
     *
     * @param purgeOrphans     if true, deletes unreferenced non-protected files from disk
     * @param healMissingInDb if true, resets broken DB image references back to "default.png"
     * @return audit and execution report
     */
    @Transactional
    public ImageAuditReport auditAndSync(boolean purgeOrphans, boolean healMissingInDb) {
        long start = System.currentTimeMillis();
        log.info("Starting Image and Database sync audit (purgeOrphans={}, healMissing={})...", purgeOrphans, healMissingInDb);

        List<String> diskFiles = profileImageStorage.listAllStoredImages();
        List<UserDtls> allUsers = userRepository.findAll();

        Set<String> dbReferencedImages = new HashSet<>();
        List<String> missingFilesFound = new ArrayList<>();
        List<String> missingFilesHealed = new ArrayList<>();

        // 1. Check all users in DB
        for (UserDtls user : allUsers) {
            String img = user.getProfileImage();
            if (img != null && !img.isBlank()) {
                dbReferencedImages.add(img);

                // Check if file exists on disk (unless it's a protected system file)
                if (!profileImageStorage.isProtected(img) && !profileImageStorage.exists(img)) {
                    missingFilesFound.add(img + " (User ID: " + user.getId() + " - " + user.getEmail() + ")");
                    if (healMissingInDb) {
                        log.warn("Auto-healing missing image for user {}: {} -> default.png", user.getEmail(), img);
                        user.setProfileImage("default.png");
                        userRepository.save(user);
                        missingFilesHealed.add(img);
                    }
                }
            }
        }

        // 2. Check disk files for orphans
        List<String> orphanFilesFound = new ArrayList<>();
        List<String> orphanFilesPurged = new ArrayList<>();

        for (String file : diskFiles) {
            if (profileImageStorage.isProtected(file)) {
                continue; // Protected system file (e.g. default.png, logos)
            }
            if (!dbReferencedImages.contains(file)) {
                orphanFilesFound.add(file);
                if (purgeOrphans) {
                    boolean deleted = profileImageStorage.deleteIfPresent(file);
                    if (deleted) {
                        orphanFilesPurged.add(file);
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Image audit complete in {}ms. Disk files: {}, DB users: {}, Orphans found/purged: {}/{}, Missing found/healed: {}/{}",
                duration, diskFiles.size(), allUsers.size(),
                orphanFilesFound.size(), orphanFilesPurged.size(),
                missingFilesFound.size(), missingFilesHealed.size());

        return new ImageAuditReport(
                diskFiles.size(),
                allUsers.size(),
                dbReferencedImages.size(),
                orphanFilesFound,
                orphanFilesPurged,
                missingFilesFound,
                missingFilesHealed,
                duration
        );
    }
}
