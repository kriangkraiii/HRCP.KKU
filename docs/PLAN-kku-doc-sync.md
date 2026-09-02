# Plan: KKU HR Documents Automated Sync & Dynamic Library

## 📌 Problem & Context
Currently, the `/user/academic/documents` library is powered by a static JavaScript array hardcoded in `documents.html`. When KKU HR publishes new regulations, announcements, or checklists on `https://hr2.kku.ac.th/?page_id=5546` (such as the new 2569 regulations/announcements), the system cannot detect or display them unless developer manually updates HTML code.

## 🎯 Goal
1. Fix the document PDF preview overlay so it renders cleanly and fully in the viewport.
2. Build an automated sync pipeline (`KkuDocumentSyncService`) with Spring `@Scheduled` (cron: every 1 month) that parses `https://hr2.kku.ac.th/?page_id=5546` with Jsoup.
3. Store documents in a new JPA entity `KkuRegulationDoc` with category grouping, new-item badges (🆕), and checksum/URL deduplication.
4. Render `/user/academic/documents` dynamically from the database.
5. Provide a "Sync Now" button in the Admin External Sync dashboard (`/admin/external-sync`).

---

## 🏗️ Architecture & Component Design

### 1. Database Model (`KkuRegulationDoc`)
- `id` (Long, PK)
- `category` (String: "ข้อบังคับมหาวิทยาลัยขอนแก่น", "ประกาศมหาวิทยาลัยขอนแก่น", "เอกสารแนบท้าย", "คำจำกัดความ 1-3", "คำจำกัดความ 4")
- `title` (String: Thai document title)
- `fileUrl` (String: full URL to PDF on KKU HR server or proxy)
- `fileKey` (String: unique URL/filename slug for duplicate prevention)
- `displayOrder` (Integer)
- `isNew` (Boolean: true if published in latest sync or has 🆕 flag)
- `publishedYear` (String: e.g. "2569", "2566")
- `createdAt` (LocalDateTime)
- `updatedAt` (LocalDateTime)

### 2. Scraping & Sync Service (`KkuDocumentSyncService`)
- Uses **Jsoup** with proper user-agent and timeout to fetch `https://hr2.kku.ac.th/?page_id=5546`.
- Extracts category headers (e.g. `fusion-title`, `h4`, `h5`) and lists (`fusion-checklist`, `a[href$='.pdf']`).
- Normalizes URL encoding and resolves relative URLs.
- Compares with existing records:
  - Inserts new documents and flags `isNew = true`.
  - Updates titles if modified upstream.
  - Retains existing records if upstream is temporarily down (fail-safe).
- Logs sync summary in `SystemLog` / `ExternalSync` history.

### 3. Cron Scheduler
- Configurable via `application.properties`:
  ```properties
  kku.hr.sync.cron=0 0 2 1 * ? # 02:00 AM on the 1st of every month
  kku.hr.sync.enabled=true
  kku.hr.sync.url=https://hr2.kku.ac.th/?page_id=5546
  ```

### 4. Admin Management (`/admin/external-sync`)
- Add a new card in `/admin/external-sync` showing:
  - Total regulations & announcements synced
  - Last sync timestamp & status
  - "ซิงค์ทันที" (Sync Now) trigger button

### 5. Frontend Dynamic View (`/user/academic/documents`)
- Controller passes `List<KkuRegulationCategoryDto>` (or category groups) from DB.
- If DB is empty, automatically triggers initial seed sync.
- Fully responsive PDF viewer with download and modal options.

---

## 📋 Task Breakdown

### Phase 1: Database & Repository
- [ ] Create `KkuRegulationDoc` entity with indices on `category` and `fileKey`.
- [ ] Create `KkuRegulationDocRepository` with queries for categories and ordering.

### Phase 2: Parser & Sync Engine
- [ ] Implement `KkuDocumentParser` to parse Avada/Fusion checklist structure from KKU HR.
- [ ] Implement `KkuDocumentSyncService` with transactional upsert logic and error isolation.
- [ ] Add `@Scheduled(cron = "${kku.hr.sync.cron:0 0 2 1 * ?}")` method.

### Phase 3: Controller & UI Integration
- [ ] Update `AcademicApplicantController.documentsLibrary(Model model)` to supply documents from DB.
- [ ] Update `documents.html` to render categories and documents dynamically with Thymeleaf.
- [ ] Add "ซิงค์คลังเอกสาร มข." card and manual trigger endpoint in `ExternalSyncPageController`.

### Phase 4: Verification & Testing
- [ ] Unit tests for `KkuDocumentParser` against recorded HTML fixtures.
- [ ] Integration test for `KkuDocumentSyncService` verifying idempotent upsert.
- [ ] Manual test of the PDF overlay and search filter.
