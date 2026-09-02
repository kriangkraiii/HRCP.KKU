# PLAN: Real-Time Harvest Progress Tracking & Resilience Engine

## 1. Overview
แก้ไขปัญหาการดึงข้อมูลช้า (DBLP Rate Limiting & KKU IR Connection Refused), เปลี่ยนกระบวนการเป็น Asynchronous Background Processing, และสร้างระบบ Real-Time Progress Tracking ที่คำนวณและแสดงเปอร์เซ็นต์ (%) ความคืบหน้าตามขั้นตอนจริงบนหน้าจอ `/admin/external-sync`

---

## 2. Analysis of the Issues

### A. ทำไมถึงดึงนาน?
1. **DBLP Sequential Requests:** มีอาจารย์ 50+ คน ทำให้ Adapter วนลูปยิง 50 requests ต่อเนื่อง แต่ละ request มีการหน่วง `throttle-ms` 300–500ms รวมเป็น 25–40 วินาที
2. **DBLP Rate Limiting (HTTP 500 / 503):** เซิร์ฟเวอร์ DBLP ตอบกลับด้วย 500/503 เมื่อตรวจพบคำขอถี่เกินไป
3. **KKU IR Connection Refused:** `https://repository.kku.ac.th/oai/request` ปิดพอร์ตหรือปฏิเสธการเชื่อมต่อ ทำให้ RestClient พยายาม Connect จนเสียเวลา
4. **Synchronous HTTP Form Submit:** Browser ค้างรอ Response ก้อนเดียว 30–40 วินาที โดยปุ่มแสดงเพียงข้อความ "กำลังดึงข้อมูล..." แบบจำลอง

### B. สถานะผลลัพธ์: ดึงสำเร็จหรือไม่?
- **OpenAlex:** สำเร็จ (250+ ผลงาน)
- **Crossref:** สำเร็จ (100+ ผลงาน)
- **ThaiJO:** สำเร็จ
- **DBLP:** สำเร็จบางส่วน (อาจารย์ช่วงแรกสำเร็จ ช่วงหลังเจอ 503)
- **KKU IR:** ล้มเหลว (เซิร์ฟเวอร์ปลายทางปฏิเสธการเชื่อมต่อ)

---

## 3. Technical Solution

### A. Harvest Progress Tracker (คำนวณ % จริง 100%)
- คำนวณจำนวน Steps ทั้งหมด:
  - OpenAlex = 1 step
  - Crossref = 1 step
  - ThaiJO = 1 step
  - KKU IR = 1 step
  - DBLP = จำนวนอาจารย์จริง ($N$ steps เช่น 50 steps)
  - DB Writing = จำนวนชุดข้อมูลจริง ($M$ steps)
- Total Steps = $1 + 1 + 1 + 1 + N + M$
- อัปเดตความคืบหน้าแบบ Real-time:
  $$\text{Real Progress \%} = \frac{\text{Completed Steps}}{\text{Total Steps}} \times 100$$

### B. Asynchronous Execution & Non-blocking API
- `POST /admin/external-sync/harvest/start` -> สั่งรัน Virtual Thread ในเบราว์เซอร์ไม่ค้าง
- `GET /admin/external-sync/harvest/progress` -> ส่ง JSON สดทุก 800ms

### C. Adapter Resilience
- **DBLP:** ใส่ Fast-Fail & Fallback บน 500/503
- **KKU IR:** ปรับ Connect Timeout สั้นลง (3 วินาที) และจับ Connection Refused อย่างนุ่มนวล

### D. UI Progress Modal
- แสดง Modal พร้อม Progress Bar ตัวเลข % จริง
- รายการสถานะรายแหล่ง (OpenAlex, Crossref, DBLP, ThaiJO, KKU IR) แบบ Real-time

---

## 4. Tasks Breakdown

- [ ] **Task 1: HarvestProgressTracker Engine**
  - สร้าง Service จัดการ State และคำนวณ % จริง
- [ ] **Task 2: Adapter Resilience & Progress Injection**
  - อัปเดต DBLP, KKU IR, OpenAlex, Crossref, ThaiJO ให้ส่ง Event เข้า Tracker
- [ ] **Task 3: REST API & Controller Layer**
  - เพิ่ม Endpoint `/harvest/start` และ `/harvest/progress`
- [ ] **Task 4: UI Real-time Modal & JavaScript Poller**
  - สร้าง Progress Modal และ JS Polling บน `external_sync.html`
- [ ] **Task 5: Automated Verification**
  - เขียน Unit Test ตรวจสอบ Progress Tracker และ Controller
