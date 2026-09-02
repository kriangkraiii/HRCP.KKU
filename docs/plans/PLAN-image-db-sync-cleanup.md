# Project Plan: Image & Database Sync and Auto-Cleanup System

**Task Slug:** `image-db-sync-cleanup`  
**Target:** Real-time old image removal on change/delete + Disk & DB consistency check & auto-healing.

---

## 1. Context & Objectives
- Automatically delete old profile image files from disk when a user/admin uploads a replacement photo.
- Automatically delete the associated profile image when a user is deleted from the system.
- Protect default/system assets (`default.png`, logos) from deletion.
- Verify consistency between filesystem (`uploads/profile_img/`) and database (`UserDtls.profileImage`).
- Auto-heal broken references (fallback to `default.png` if file is missing).
- Provide an audit service to purge orphan files on demand or on schedule.

---

## 2. Task Breakdown

### Phase 1: Storage Layer Enhancements (`ProfileImageStorage.java`)
- [ ] Add `deleteIfUnused(String filename)` with protection for `default.png` and whitelist.
- [ ] Add `listAllStoredImages()` to list physical files on disk safely without directory traversal.

### Phase 2: User Service Lifecycle Hooking (`UserServiceImpl.java`)
- [ ] In `updateUser()`: Delete old image after new image successfully saved.
- [ ] In `updateProfileImageOnly()`: Delete old image after replacement.
- [ ] In `deleteUserById()`: Delete user's profile image after user removal.

### Phase 3: Audit & Auto-Healing Service (`ImageSyncAuditService.java`)
- [ ] Compare DB `profileImage` list vs disk files.
- [ ] Auto-heal missing files in DB &rarr; update to `default.png`.
- [ ] Purge orphan files not referenced in DB.

### Phase 4: Verification & Automated Tests
- [ ] Unit tests for safe deletion rules & whitelist protection.
- [ ] Verification tests for orphan detection and purge.
