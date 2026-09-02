# Plan: ระบบเร่งความเร็วการสร้างและพรีวิวเอกสาร (Hybrid Option A + B)

## 📌 Context
ปัญหาปัจจุบัน: เมื่อผู้ใช้กด "ดูตัวอย่าง" ในหน้ารายละเอียดคำร้องหรือหน้ากรอกฟอร์ม หน้าจอจะขึ้น **"กำลังสร้างเอกสาร..."** หมุนรอ 2–5 วินาที เนื่องจากระบบต้องเริ่ม Process LibreOffice ใหม่จากศูนย์ (Cold Start) และสร้างไฟล์ตามคำขอแบบ Synchronous ทุกครั้ง

**เป้าหมาย:** ลดเวลาการแสดงผลตัวอย่างเอกสารให้เหลือ **< 50ms (Instant)** สำหรับเอกสารที่บันทึกแล้ว และ **~300–500ms** สำหรับการแปลงสด

---

## 🏗️ Architectural Strategy (Hybrid Option A + B)

### 1. Option A: ปรับแต่ง LibreOffice Engine ให้แปลงเร็วขึ้น (5–8x Speedup)
- **Profile Directory Reuse:** เปลี่ยนจากการสร้าง/ลบโฟลเดอร์ user profile ใหม่ทุกครั้ง มาใช้ Pre-initialized Profile Pool ช่วยลด I/O overhead ไปได้กว่า 1.5–2.0 วินาทีต่อไฟล์
- **Optimized CLI Flags:** ใช้พารามิเตอร์ `--headless --norestore --nolockcheck --nodefault --nologo` ร่วมกับ buffer memory tuning
- **Two-Tier Cache (L1 In-Memory + L2 Persistent Disk):** แคชไฟล์ PDF ตาม SHA-256 ของ DOCX bytes ลงหน่วยความจำและโฟลเดอร์ temp พร้อม TTL เพื่อให้ไม่ต้องแปลงซ้ำแม้รีสตาร์ตเซิร์ฟเวอร์

### 2. Option B: Asynchronous Pre-generation & Cache Warming (Instant <50ms)
- **Save Trigger:** เมื่อผู้ใช้กด "บันทึกข้อมูล" ในหน้าฟอร์มเอกสาร ระบบจะส่ง event ให้ `@Async DocumentPrewarmService` นำข้อมูลไปสร้าง DOCX + แปลงเป็น PDF เก็บไว้ใน Cache เบื้องหลังทันที
- **View Detail Trigger:** เมื่อเปิดหน้ารายละเอียดคำร้อง (`request_detail.html`) ระบบจะสแกนเอกสารที่กรอกแล้ว และ Pre-warm PDF ลงแคชใน background thread (low-priority)
- **Instant Response:** เมื่อผู้ใช้กดปุ่ม "ดูตัวอย่าง" ตัว Controller จะส่ง PDF จากแคชกลับไปทันทีในเวลา < 50ms

---

## 📋 Task Breakdown

### Phase 1: LibreOffice Engine & Two-Tier Cache Optimization (Option A)
- [ ] ปรับปรุง `DocumentGenerationService.java`:
  - พัฒนา Profile Pool สำหรับ LibreOffice
  - เพิ่ม Two-Tier Cache (L1 Memory LRU + L2 Temp Disk Cache)
  - เพิ่ม Metrics จับเวลาการแปลงเอกสาร (ms)

### Phase 2: Async Pre-warm Service & Event Triggers (Option B)
- [ ] สร้าง `DocumentPrewarmService.java` พร้อม `@Async` method:
  - `prewarmAcademicDocument(Long requestId, int docType, Map<String, Object> data)`
  - `prewarmPositionDocument(Long requestId, int docType, Map<String, Object> data)`
  - `prewarmRequestDocuments(Long requestId, boolean isPosition)`
- [ ] เชื่อมต่อ Event Triggers ใน Controller:
  - `AcademicApplicantController.java` & `AcademicAdminController.java`
  - `PositionApplicantController.java` & `PositionAdminController.java`

### Phase 3: Frontend & Client Experience Polish
- [ ] ปรับปรุง `doc_preview.js` ป้องกัน Double-click และเพิ่ม latency tracker
- [ ] ปรับปรุงข้อความและ animation แสดงผลขณะโหลด

### Phase 4: Verification & Benchmarking
- [ ] ทดสอบ Unit Tests & Integration Tests
- [ ] ทำ Load/Benchmark Test วัด Latency ก่อนและหลังทำ Hybrid Caching

---

## 🎯 Verification Checklist
- [ ] กดบันทึกฟอร์มเอกสาร แล้วกด "ดูตัวอย่าง" ทันที → โหลดขึ้นใน < 100ms
- [ ] เข้าหน้ารายละเอียดคำร้องที่มีเอกสารครบ 9 รายการ แล้วกดดูตัวอย่างทีละไฟล์ → เปิดได้ทันทีโดยไม่ต้องรอหมุน
- [ ] ตัวอย่างเอกสาร PDF ฟอนต์ Sarabun, ตาราง, และเครื่องหมายถูก/กากบาทยังคงตรงตามมาตรฐาน มข. 100%
