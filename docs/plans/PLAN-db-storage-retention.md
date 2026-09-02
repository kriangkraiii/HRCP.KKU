# Project Plan: Database Retention & Storage Auto-Purge System

**Task Slug:** `db-storage-retention`  
**Target:** Automated data retention policies for high-growth database tables and disk storage trash to prevent system bloat.

---

## 1. Context & Objectives

The application generates audit logs, system notifications, directory sync logs, and cloud storage trash files during normal operation. Without an automated retention and pruning policy, these tables and folders will accumulate data indefinitely.

### Specific Retention Targets:
1. **`Notification`:**
   - Purge soft-deleted notifications (`isDeleted = true`) older than **60–90 days** (default: 60 days).
   - (Optional) Purge ancient read notifications older than **180 days**.
2. **`AdminLog` (Audit Log):**
   - Purge audit logs older than **365 days (1 year)** or **730 days (2 years)** while strictly preserving the mandatory 90-day minimum required by the Computer-Related Crime Act (พ.ร.บ.คอมพิวเตอร์).
3. **`FsFacultyChange` (Faculty Sync Diff Log):**
   - Purge processed changes (`status IN ('APPROVED', 'REJECTED')`) older than **180 days (6 months)**.
4. **`AdminStorage` & `UserStorage` Trash:**
   - Permanently delete files in trash (`isDeleted = true`) older than **30 days** from both disk and database.

---

## 2. Architecture & Design

We will build a centralized **`DataRetentionService`** and **`DataRetentionScheduler`** that:
- Runs automatically on **Application Startup** (`ApplicationReadyEvent`) for immediate cleanup.
- Runs daily via a configurable **Cron Job** (default: `03:30 AM`).
- Provides an **Admin API endpoint** (`POST /admin/system/run-retention`) for manual trigger with execution reports.
- Employs Spring Data JPA `@Modifying` bulk delete queries for optimal performance.

---

## 3. Task Breakdown

### Phase 1: Repository Query Methods
- [ ] **`NotificationRepository`**:
  - Add `@Modifying @Query` for `deleteByIsDeletedTrueAndCreatedAtBefore(LocalDateTime cutoff)`.
  - Add `@Modifying @Query` for `deleteByCreatedAtBefore(LocalDateTime cutoff)`.
- [ ] **`AdminLogRepository`**:
  - Add `@Modifying @Query` for `deleteByTimestampBefore(LocalDateTime cutoff)`.
- [ ] **`FsFacultyChangeRepository`**:
  - Add `@Modifying @Query` for `deleteByStatusNotAndDetectedAtBefore(String status, LocalDateTime cutoff)`.

### Phase 2: Core Data Retention Engine (`DataRetentionService.java`)
- [ ] Implement `DataRetentionService`:
  - `purgeDeletedNotifications(int days)`
  - `purgeOldAdminLogs(int days)`
  - `purgeOldFacultySyncLogs(int days)`
  - `purgeExpiredStorageTrash(int days)`
  - `runFullRetentionCycle()` returning `RetentionReport(int notifPurged, int logsPurged, int fsPurged, int trashPurged, long durationMs)`.

### Phase 3: Scheduler & Startup Integration
- [ ] Integrate `@EventListener(ApplicationReadyEvent.class)` for startup sweep.
- [ ] Integrate `@Scheduled(cron = "${app.retention.cron:0 30 3 * * ?}")` for daily maintenance.
- [ ] Add configuration properties in `application.properties`:
  - `app.retention.cron=0 30 3 * * ?`
  - `app.retention.notification-days=60`
  - `app.retention.admin-log-days=365`
  - `app.retention.faculty-sync-days=180`
  - `app.retention.storage-trash-days=30`

### Phase 4: Admin Controller & UI Integration
- [ ] Add `@PostMapping("/admin/system/run-retention")` in `AdminController.java`.
- [ ] Add `@GetMapping("/admin/system/retention-status")` returning current table counts and estimated prune preview.

### Phase 5: Verification & Automated Tests
- [ ] Write unit tests (`DataRetentionServiceTest.java`) covering:
  - Pruning soft-deleted notifications while keeping active/new ones.
  - Pruning old admin logs while preserving recent logs (< 90 days).
  - Pruning approved/rejected faculty sync logs while keeping PENDING items.
  - Full execution reporting.
