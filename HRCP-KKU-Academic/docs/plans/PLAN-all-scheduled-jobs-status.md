# PLAN: All Scheduled Jobs & Daily Execution Status on `/admin/external-sync`

## 1. Overview
แสดงรายละเอียดของงานอัตโนมัติทั้งหมด (All 9 Scheduled Jobs) ในโปรเจกต์ลงในตารางตารางเวลาอัตโนมัติบนหน้า `/admin/external-sync` พร้อมคำอธิบายวัตถุประสงค์ และแสดงสถานะการดึงข้อมูล/การทำงานในแต่ละวันอย่างละเอียด

---

## 2. All 9 Scheduled Jobs in Project

### หมวดที่ 1: การเชื่อมต่อภายนอก & งานวิจัย (External Integrations & Research Data)
1. **`fs.sync.users.cron` (01:30 น. ทุกวัน):** ดึงข้อมูลอาจารย์เฉพาะที่เปลี่ยนแปลง (Incremental Sync) จากระบบ Fund Management
2. **`fs.sync.scopus.cron` (02:00 น. ทุกวัน):** ดึงผลงานวิจัยของอาจารย์ทุกคนจาก Scopus API
3. **`harvest.cron` (02:30 น. ทุกวัน):** ดึงงานวิจัย 5 แหล่งใหม่ (OpenAlex, Crossref, DBLP, ThaiJO, KKU IR) พร้อม Deduplication 2.5 ชั้น และเติมระดับวารสาร (SJR Quartile & TCI Tier)
4. **`fs.sync.users.full-cron` (03:00 น. ทุกวันอาทิตย์):** ตรวจสอบข้อมูลอาจารย์ทั้งหมดแบบ Full Re-sync ป้องกันข้อมูลค้าง
5. **`cp.web.sync.cron` (03:30 น. ทุกวันอาทิตย์):** Sync ทำเนียบบุคลากรและรูปโปรไฟล์จากเว็บไซต์คณะ (computing.kku.ac.th)

### หมวดที่ 2: บำรุงรักษาระบบ & นโยบายความปลอดภัย (System Maintenance & Compliance)
6. **`app.images.cleanup-cron` (03:00 น. ทุกวัน):** กวาดล้างรูปภาพขยะ (Orphan Images) ที่ไม่มีการอ้างอิงเพื่อคืนเนื้อที่ Disk
7. **`app.retention.cron` (03:30 น. ทุกวัน):** จัดเก็บ Log และหมุนเวียนล้างข้อมูลที่เกิน 90 วันตาม พ.ร.บ.คอมพิวเตอร์

### หมวดที่ 3: แจ้งเตือน & ระบบงานวิชาการ (Academic Workflows & Reminders)
8. **`evaluation.expiry.cron` (08:00 น. ทุกวัน):** ตรวจสอบคำขอประเมินตำแหน่งทางวิชาการที่ใกล้หมดอายุ และส่งการแจ้งเตือน
9. **`app.esign.reminder-cron` (08:30 น. ทุกวัน):** ส่งอีเมลแจ้งเตือนการลงนาม E-Signature ที่ค้างเกิน 3 วัน

---

## 3. Tasks Breakdown

- [ ] **Task 1: Controller Layer (`ExternalSyncPageController.java`)**
  - Inject Cron expressions ทั้ง 9 ตัว
  - สร้างโครงสร้างข้อมูล `ScheduleItem` แสดงชื่องาน, คำอธิบาย, หมวดหมู่, cron, และเวลาครั้งถัดไป
  - คำนวณสรุปสถานะรายวัน (Daily Health Summary)
- [ ] **Task 2: UI Template (`external_sync.html`)**
  - ปรับปรุงตาราง "ตารางเวลาอัตโนมัติ" ให้แสดงแยก 3 หมวดหมู่อย่างเป็นระเบียบ สวยงาม
  - เพิ่ม Badge และคำอธิบายการทำงานในแต่ละวัน
  - แสดงสถานะสรุปของวัน (Daily Status Dashboard)
- [ ] **Task 3: Automated Verification**
  - อัปเดต `ExternalSyncPageControllerTest.java` ตรวจสอบครบทั้ง 9 งาน
  - รันการทดสอบ Unit & Integration Tests
