# Project Plan: ปรับปรุง UI แถบสถานะ (Progress Stepper) ฝั่งแอดมินให้เคลื่อนไหวและแสดงผลสอดคล้องกับผู้ยื่น

> **Plan Document:** `docs/PLAN-admin-stepper-ui.md`  
> **Responsible Agents:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]`  
> **Status:** Planning (Ready for Approval)  
> **Target Components:** `detail-progress` ใน `style.css` และเทมเพลตรายละเอียดคำร้องแอดมินทั้ง 2 เฟส (Position Request & Academic Request)

---

## 1. ที่มาและปัญหาที่พบ (Problem Statement & Rationale)

1. **ปัญหาจุดปัจจุบันไม่ขยับ (No Pulse Animation on Active Step):**
   - ฝั่งผู้ยื่น (`.progress-step.active` และ `.timeline-v2-step.t-active`) มีแอนิเมชัน `stepPulse` ขยายและกระเพื่อมแบบคลื่นน้ำตลอดเวลา ทำให้มองเห็นทันทีว่าสถานะนั้นกำลังดำเนินการอยู่
   - ฝั่งแอดมิน (`.detail-progress-step.dp-active`) ในปัจจุบันกำหนดเพียง `transform: scale(1.12);` และเงาคงที่ จึงเป็นภาพนิ่งสนิท ไม่มีความเคลื่อนไหว (UI ไม่ขยับ)
2. **ปัญหาสีกลืนกันระหว่างขั้นที่เสร็จแล้วกับขั้นปัจจุบัน (Lack of Done vs Active Color Distinction):**
   - โค้ดในเทมเพลตใส่คลาส `dp-done` ให้ทั้งขั้นตอนที่เสร็จแล้วและขั้นตอนปัจจุบัน
   - ใน `style.css` คลาส `.dp-done` มีสไตล์สีน้ำเงิน (`#1565c0`) ทำให้จุดที่ 1 (รับคำร้อง), จุดที่ 2 (ตรวจสอบความถูกต้อง), และจุดที่ 3 (เสนอวาระกลั่นกรองฯ) มีสีน้ำเงินเหมือนกันหมด ไม่แยกสีเขียว (ผ่านแล้ว) กับสีฟ้า (กำลังทำ) เหมือนฝั่งผู้ยื่น แม้เส้นเชื่อมจะเป็นสีเขียวแล้วก็ตาม

---

## 2. ขอบเขตการเปลี่ยนแปลง (Scope of Changes)

### 2.1 สไตล์ชีต CSS ([style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css))

1. **กำหนดสไตล์ขั้นตอนที่ผ่านแล้ว (`.detail-progress-step.dp-done`):**
   - เปลี่ยนสีพื้นหลังและขอบของจุด (`.detail-progress-dot`) เป็น **สีเขียวสำเร็จ (`var(--color-success, #2e7d32)`)**
   - กลืนกับเส้นเชื่อมสีเขียว `linear-gradient(90deg, #2e7d32, #43a047)` อย่างสมบูรณ์
   - ตัวอักษรป้ายกำกับเป็นสีเขียว (`color: var(--color-success, #2e7d32)`)
2. **กำหนดสไตล์ขั้นตอนปัจจุบัน (`.detail-progress-step.dp-active`):**
   - เปลี่ยนสีพื้นหลังและขอบของจุด (`.detail-progress-dot`) เป็น **สีฟ้าเด่น (`var(--color-primary-medium, #1565c0)`)**
   - เพิ่มแอนิเมชันกระเพื่อมแบบไม่มีที่สิ้นสุด: `animation: stepPulse 2s var(--ease-out-quart) infinite;`
   - ป้ายกำกับตัวหนา 800 สีฟ้า
3. **รองรับ Dark Theme ([dark-theme.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/dark-theme.css)):**
   - ให้แน่ใจว่าใน Dark Theme จุดสถานะสีเขียว (ผ่านแล้ว) และสีฟ้ากระพริบ (กำลังทำ) แสดงผลคมชัดและสบายตา

---

### 2.2 เทมเพลตฝั่งแอดมิน (Admin Templates)

1. **คำร้องขอกำหนดตำแหน่งทางวิชาการ ([Position Admin Request Detail](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/request_detail.html)):**
   - บรรทัด 275: ปรับการให้คลาส `th:classappend` แยกขาดระหว่าง:
     - ขั้นตอนที่ผ่านแล้ว (`pIdx > iterStat.index`): ใส่คลาส `dp-done`
     - ขั้นตอนปัจจุบัน (`pIdx == iterStat.index`): ใส่คลาส `dp-active` (หากเป็นขั้นตอนสุดท้ายให้ใส่ `dp-done dp-final`)
2. **คำร้องขอประเมินการสอน ([Academic Admin Request Detail](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/admin/request_detail.html)):**
   - บรรทัด 61: ปรับการให้คลาส `th:classappend` ในลักษณะเดียวกัน ให้แยกชัดเจนระหว่าง `dp-done` และ `dp-active`

---

## 3. แผนการตรวจสอบและทดสอบ (Verification Plan)

1. **Automated Verification:**
   - รัน `./mvnw test-compile` เพื่อยืนยันว่าโปรเจกต์คอมไพล์ผ่านสมบูรณ์
2. **Visual & UI Verification:**
   - เปิดหน้า `/admin/position/request/{id}` ที่มีสถานะปัจจุบัน เช่น `SCREENING_COMMITTEE` (เสนอวาระกลั่นกรองฯ):
     - ตรวจสอบว่าจุดที่ 1 (`รับคำร้อง`) และจุดที่ 2 (`ตรวจสอบความถูกต้องฯ`) เปลี่ยนเป็น **สีเขียว**
     - ตรวจสอบว่าจุดที่ 3 (`เสนอวาระกลั่นกรองฯ`) เป็น **สีฟ้าพร้อมเอฟเฟกต์ pulse ขยายกระเพื่อมเข้า-ออกอย่างนุ่มนวล**
     - จุดที่ 4 ถึง 7 ยังคงเป็นสีเทาสะอาดตา
   - ทดสอบสลับเป็น Dark Mode เพื่อตรวจเช็คความกลมกลืนของสีและแอนิเมชัน
