# PLAN: Multi-Source Harvest UI & Status Dashboard on `/admin/external-sync`

## 1. Overview
เพิ่มปุ่มและแผงควบคุมการดึงข้อมูลงานวิจัย 5 แหล่งใหม่ (OpenAlex, Crossref, DBLP, ThaiJO, KKU IR) บนหน้าจอผู้ดูแลระบบ `/admin/external-sync` พร้อมตารางแสดงสถานะผลการทำงานล่าสุดในแต่ละวันว่าดึงสำเร็จหรือไม่

---

## 2. Technical Scope & Architecture

### A. Backend (`ExternalSyncPageController.java`)
1. **Inject `PublicationHarvestService`**: เพื่อเรียกใช้งาน `harvestAll()` และ `harvestSource(source)`
2. **Data Aggregation for View**:
   - ดึงสถิติจำนวนผลงานแยกตามแหล่ง `publicationRepo.countGroupByDataSource()`
   - ดึงสถานะการทำงานล่าสุดของทุก Source จาก `fs_sync_state`
   - เพิ่มเวลา Cron ของการ Harvest (`harvest.cron`) เข้าสู่ตารางเวลาอัตโนมัติ
3. **Action Endpoints**:
   - `POST /admin/external-sync/harvest-all` — ทริกเกอร์รันดึงงานวิจัยทุกแหล่งพร้อมกัน
   - `POST /admin/external-sync/harvest/{source}` — ทริกเกอร์รันดึงงานวิจัยเฉพาะแหล่งที่ระบุ

### B. Frontend / Thymeleaf Template (`external_sync.html`)
1. **Source Breakdown Counters**: การ์ดแสดงจำนวนผลงานแยกตามแหล่งข้อมูล (Scopus, OpenAlex, Crossref, DBLP, ThaiJO, KKU IR)
2. **Action Cards (ปุ่มกดดึงข้อมูลแบบเข้าชุด)**:
   - การ์ด "งานวิจัยจากฐานข้อมูลภายนอก (5 แหล่งข้อมูล)"
   - ปุ่มหลัก: `ดึงงานวิจัยทุกแหล่งพร้อมกัน (Harvest All)`
   - ปุ่มย่อยแบบ Dropdown / Grid: `OpenAlex`, `Crossref`, `DBLP`, `ThaiJO`, `KKU IR`
3. **Execution Status Dashboard (ผลการทำงานล่าสุด)**:
   - ตารางแสดงสถานะของทุกงาน (ทั้งข้อมูลอาจารย์, Scopus, OpenAlex, Crossref, DBLP, ThaiJO, KKU IR)
   - Badge สถานะ: สำเร็จ (เขียว), ล้มเหลว (แดง), กำลังทำงาน (ฟ้า)
   - เวลาที่ดึงสำเร็จล่าสุด, จำนวนรายการที่ได้, จำนวนคำขอ, เวลาที่ใช้, ข้อความหมายเหตุ

---

## 3. Tasks Breakdown

- [ ] **Task 1: Controller Layer Updates**
  - Inject `PublicationHarvestService` ใน `ExternalSyncPageController`
  - เพิ่มเมธอด POST สำหรับ `/harvest-all` และ `/harvest/{source}`
  - ส่ง `sourceCounts` และสถานะ Harvest Jobs เข้าสู่ Model
- [ ] **Task 2: UI Template Redesign & Integration**
  - ออกแบบ Action Card สำหรับ Multi-Source Harvest ใน `external_sync.html`
  - ปรับปรุงตาราง "ผลการทำงานล่าสุด" ให้รองรับทุกแหล่งพร้อม Badge และรายละเอียดครบถ้วน
  - เพิ่มตัวนับสถิติผลงานแยกตาม Source
- [ ] **Task 3: Automated Tests & Verification**
  - เขียน Unit Test / WebMvcTest ตรวจสอบ Controller และ Endpoint Actions
  - รัน Full Test Suite ยืนยันความสมบูรณ์ 100%
