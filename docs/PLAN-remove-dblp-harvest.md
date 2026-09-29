# Project Plan: ปิดและปลดการดึงข้อมูลงานวิจัยจาก DBLP (Remove DBLP Harvest Plan)

> **เอกสารแผนงาน:** `docs/PLAN-remove-dblp-harvest.md`  
> **ผู้รับผิดชอบหลัก:** `@[project-planner]` ร่วมกับ `@[backend-specialist]` และ `@[frontend-specialist]`  
> **สถานะ:** ดำเนินการเรียบร้อยแล้ว (Implementation Completed)  
> **ประเภทโครงการ:** WEB / BACKEND (Spring Boot + Thymeleaf)

---

## 1. ที่มาและเหตุผล (Overview & Background)

### 1.1 เหตุผลในการนำ DBLP ออก
1. **ปัญหา Bot Challenge (Anubis Proof-of-Work):**
   - เว็บไซต์ `dblp.org` ปัจจุบันมีระบบ Anubis คอยบล็อกการเชื่อมต่อ HTTP API แบบ Non-Browser ทำให้ส่งรหัส HTTP 500 (Sad Anubis) กลับมา
2. **ความซ้ำซ้อนของข้อมูล (Data Redundancy):**
   - งานวิจัยคอมพิวเตอร์ใน DBLP ทั้งหมด (IEEE, ACM, Springer, Elsevier ฯลฯ) มีการจัดทำดัชนีอยู่ในฐานข้อมูล **OpenAlex**, **Crossref** และ **Scopus** อยู่แล้ว 100%
   - การตัด DBLP ออกจึง**ไม่ส่งผลกระทบต่อความครบถ้วนของข้อมูลงานวิจัยอาจารย์** แต่จะช่วยให้รอบการซิงค์รวดเร็วขึ้น ไม่มี Error/Warning หลุดกวนใจในหน้า Dashboard

---

## 2. เกณฑ์ความสำเร็จ (Success Criteria)

- [x] เมื่อสั่ง "ดึงงานวิจัยทุกแหล่งพร้อมกัน" ระบบจะรันเฉพาะแหล่งที่เปิดใช้งาน (OpenAlex, Crossref, ThaiJO, KKU IR) รวม 4 แหล่ง โดยไม่มี DBLP เข้ามารบกวน
- [x] ในหน้าต่าง Real-time Progress Modal จะแสดงเฉพาะ 4 แหล่งหลักอย่างสะอาดตา
- [x] เมนู Dropdown "เลือกดึงเฉพาะแหล่ง" จะนำตัวเลือก DBLP ออก หรือระบุว่าปิดการใช้งาน
- [x] ในการ์ดแสดงสถิติและตาราง Dashboard ปรับตัวเลขอ้างอิงเป็น "4 แหล่ง" (OpenAlex, Crossref, ThaiJO, KKU IR)
- [x] กำหนดค่าปิดการใช้งานในระดับ Config (`harvest.dblp.enabled=false`) เพื่อให้ยังคงโค้ด Adapter ไว้ เผื่อนำกลับมาใช้ได้ในอนาคตหาก DBLP เปิด API ทางการ

---

## 3. สแตกเทคโนโลยีและขอบเขตไฟล์ที่เกี่ยวข้อง (Tech Stack & Affected Scope)

| ส่วนประกอบ | ไฟล์เป้าหมาย | หน้าที่ |
|---|---|---|
| **Configuration** | `HRCP-KKU-Academic/src/main/resources/application.properties`<br>`HarvestProperties.java` | ตั้งค่า `harvest.dblp.enabled=false` เป็นค่าเริ่มต้น |
| **Backend Service** | `PublicationHarvestService.java` | ให้ Virtual Threads ข้าม DBLP โดยอัตโนมัติเมื่อ `!isEnabled()` |
| **Page Controller** | `ExternalSyncPageController.java` | ปรับข้อความ "งานวิจัย 5 แหล่ง" เป็น "4 แหล่ง" และซ่อน DBLP จากรายการ Dashboard |
| **Frontend Template** | `external_sync.html` | ปรับป้ายสถิติ, Dropdown เมนู, และ Progress Modal นำ DBLP ออก |

---

## 4. แผนการดำเนินงาน (Task Breakdown)

### Task 1: ปิดการทำงาน DBLP ใน Configuration & Adapter Properties
- **ID:** `TASK-CONFIG-DISABLE-DBLP`
- **Agent:** `@[backend-specialist]`
- **Skill:** `clean-code`, `java-pro`
- **Priority:** P0
- **Target Files:**
  - `HRCP-KKU-Academic/src/main/resources/application.properties`
  - `HRCP-KKU-Academic/src/main/java/com/ecom/external/harvest/config/HarvestProperties.java`
- **Input:** ค่าเริ่มต้นเดิม `enabled = true`
- **Output:**
  - กำหนด `private boolean enabled = false;` ใน `DblpProps`
  - เพิ่มคอมเมนต์และค่า `harvest.dblp.enabled=false` ใน `application.properties`
- **Verify:** เมื่อแอปพลิเคชัน Start ตรวจสอบว่า `dblpAdapter.isEnabled()` คืนค่า `false`

---

### Task 2: ปรับแต่ง Controller แสดงผลสถิติและงานวิจัยภายนอก
- **ID:** `TASK-CONTROLLER-SYNC-JOBS`
- **Agent:** `@[backend-specialist]`
- **Skill:** `clean-code`
- **Priority:** P1
- **Target File:** `HRCP-KKU-Academic/src/main/java/com/ecom/external/controller/ExternalSyncPageController.java`
- **Input:**
  - `sourceCounts.put("DBLP", 0L);`
  - รายการ Sync Job DBLP ใน Dashboard table
  - คำอธิบายการ์ด "งานวิจัย 5 แหล่งใหม่"
- **Output:**
  - ซ่อนหรือยกเว้น DBLP ออกจากรายการ SyncJobDisplay ที่ต้องแสดงใน Dashboard
  - ปรับคำอธิบายเป็น "งานวิจัย 4 แหล่งใหม่ (OpenAlex, Crossref, ThaiJO, KKU IR)"
- **Verify:** เปิดหน้า `/admin/external-sync` แล้วไม่พบแถวงานวิจัย DBLP แสดงค้างสถานะหรือข้อผิดพลาด

---

### Task 3: ปรับแต่ง UI ในหน้า External Sync Template
- **ID:** `TASK-UI-REMOVE-DBLP`
- **Agent:** `@[frontend-specialist]`
- **Skill:** `frontend-design`, `clean-code`
- **Priority:** P1
- **Target File:** `HRCP-KKU-Academic/src/main/resources/templates/admin/external_sync.html`
- **Input:**
  - Badge แสดงสถิติ DBLP (บรรทัด ~121)
  - ปุ่ม Dropdown ดึงเฉพาะ DBLP (บรรทัด ~254)
  - แถว DBLP ใน `#harvestSourcesList` ของ Modal (บรรทัด ~640)
  - ข้อความปุ่ม "ดึงงานวิจัยทุกแหล่งพร้อมกัน (5 แหล่ง)"
- **Output:**
  - นำ Badge และ Dropdown Item ของ DBLP ออก
  - นำรายการ DBLP ออกจาก Progress Modal
  - ปรับข้อความเป็น "(4 แหล่ง)"
- **Verify:** ในหน้าเว็บไม่ปรากฏปุ่มหรือรายการ DBLP และเมื่อกดดึงข้อมูลทุกแหล่ง Modal จะมีเพียง 4 แหล่งหลัก

---

## 5. แผนการตรวจสอบคุณภาพ (Phase X: Final Verification)

- [x] **Config Check:** ตรวจสอบว่า `harvest.dblp.enabled` เป็น `false`
- [x] **Harvest All Execution:** กด "ดึงงานวิจัยทุกแหล่งพร้อมกัน" แล้วตัววัดความคืบหน้า (Progress Bar) ทำงานจนถึง 100% สำเร็จอย่างราบรื่น ไม่มีข้อความ "Sad Anubis" หรือ 500 error
- [x] **Dropdown Check:** เมนูดึงเฉพาะแหล่งไม่มี DBLP ให้เลือกกด
- [x] **Clean Console & Logs:** ไม่มี Warning หรือ Error เกี่ยวกับ DBLP ใน Application Logs
