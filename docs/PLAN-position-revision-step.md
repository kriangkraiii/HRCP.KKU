# Project Plan: ปรับปรุงแถบสถานะ (Progress Stepper) คำร้องขอตำแหน่งทางวิชาการให้แสดงสถานะ "ส่งแก้ไข" สอดคล้องกับหน้าอื่น

> **Plan Document:** `docs/PLAN-position-revision-step.md`  
> **Responsible Agents:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]`  
> **Status:** Planning (Ready for Review)  
> **Target Files:**  
> - `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/dashboard.html` (Tab 2: ตำแหน่งทางวิชาการ)  
> - `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/dashboard.html` (ถ้ามีการเรียกใช้)  
> - ตรวจสอบความสอดคล้องกับ `PositionRequestStatus.java` และ `style.css` (`timeline-v2`)  

---

## 1. ที่มาและปัญหาที่พบ (Problem Statement & Rationale)

1. **ปัญหาการแสดงผลผิดพลาดในหน้าแดชบอร์ดผู้ยื่น (Applicant Dashboard - Tab ตำแหน่งทางวิชาการ):**
   - เมื่อคำร้องขอตำแหน่งทางวิชาการถูกส่งกลับมาให้แก้ไข (`posReq.currentStatus == REVISION_REQUESTED`):
     - มีกล่องแจ้งเตือนสีส้ม "เจ้าหน้าที่ส่งเอกสารกลับมาให้ท่านแก้ไข" และป้าย "ส่งแก้ไข" / "ต้องแก้ไขเอกสาร" ขึ้นอย่างถูกต้อง
     - **แต่ในแถบ Progress Stepper (`.timeline-v2`):** กลับแสดงขั้นที่ 1 (รับคำร้อง) และขั้นที่ 2 (ตรวจสอบความถูกต้อง/ครบถ้วน) เป็นสีน้ำเงิน (เสร็จแล้ว) และแสดงขั้นที่ 3 (เสนอวาระกลั่นกรองฯ) เป็นสีส้มเด่น (กำลังดำเนินการ)
   - **สาเหตุทางเทคนิค (Root Cause):**
     - โค้ดใน `academic/applicant/dashboard.html` (บรรทัด 340–353) ใช้การเปรียบเทียบ `posReq.currentStatus.ordinal() >= step.ordinal()`
     - ค่า `ordinal` ของ `REVISION_REQUESTED` คือ 4 ซึ่งมากกว่า `SCREENING_COMMITTEE` (3), `DOCUMENT_VERIFICATION` (2), และ `DOCUMENT_RECEIVED` (1)
     - แถบสถานะจึงคำนวณว่าขั้นที่ 1 และ 2 ผ่านแล้ว และไปไฮไลต์ขั้นที่ 3 (เสนอวาระกลั่นกรองฯ) ด้วยสีส้ม `#e65100` ของขั้นกลั่นกรอง ทั้งที่ความจริงคำร้องยังอยู่ในสถานะ "ส่งแก้ไข" ที่ขั้นตรวจเอกสาร และยังไม่ได้ส่งต่อไปยังกลั่นกรองฯ

2. **ความไม่สอดคล้องกับหน้าอื่น (Inconsistency with Other Pages):**
   - ในหน้าอื่น เช่น **หน้าแอดมิน** (`academic/position/admin/request_detail.html`):
     - ขั้นที่ 1 (รับคำร้อง) เป็นสีเขียว (ผ่านแล้ว)
     - ขั้นที่ 2 (ตรวจสอบความถูกต้อง/ครบถ้วน) ถูกแทนที่ด้วยสถานะ **"ส่งแก้ไข"** โดยใช้ไอคอน `fa-circle-exclamation` สีส้มเตือน (`dp-revise`)
     - ขั้นที่ 3 ถึง 8 เป็นสีเทา (ยังไม่ถึง)
   - ในหน้า **ประเมินผลการสอน** (Tab 1 ของแดชบอร์ดผู้ยื่น):
     - มีการใช้คลาส `t-revise` ร่วมกับ `st.iconFor(step)` แสดงไอคอนตกใจสีส้มอย่างชัดเจน

---

## 2. ขอบเขตการเปลี่ยนแปลง (Scope of Changes)

### 2.1 เทมเพลตแดชบอร์ดผู้ยื่น (`academic/applicant/dashboard.html`)
- ในส่วน **Position Progress Tracker** (บรรทัด 338–355):
  - เปลี่ยนจากการเทียบ `ordinal()` มาใช้ `pIdx = posReq.currentStatus.progressIndex()` หรือตัวแปร `revising` ในลักษณะเดียวกับหน้าแอดมิน:
    ```html
    th:with="pIdx=${posReq.currentStatus.progressIndex()},
             revising=${posReq.currentStatus.name() == 'REVISION_REQUESTED' and step.name() == 'DOCUMENT_VERIFICATION'}"
    ```
  - กำหนดคลาสสำหรับ `.timeline-v2-step`:
    - เมื่อ `revising == true` ให้ใส่คลาส `t-revise`
    - เมื่อ `pIdx > stepStat.index` ให้ใส่คลาส `t-completed` (สีเขียวเสร็จแล้ว)
    - เมื่อ `pIdx == stepStat.index` (กรณีปกติที่ไม่ใช่ revising) ให้ใส่คลาส `t-active`
  - กำหนดไอคอน `.timeline-v2-icon`:
    - เมื่อ `revising == true` ให้แสดงไอคอน `fa-circle-exclamation`
    - กรณีปกติ ให้แสดงไอคอนตาม `${step.icon}`
  - กำหนดป้ายกำกับ `.timeline-v2-label`:
    - เมื่อ `revising == true` ให้แสดงข้อความ `${posReq.currentStatus.thaiLabel}` ("ส่งแก้ไข") หรือตามที่กำหนด
    - กรณีปกติ ให้แสดง `${step.thaiLabel}`
  - ลบ inline style `th:data-bg` และ `th:data-fg` ที่ผูกกับ `step.color` ออก เพื่อให้คลาสของระบบ (`t-revise`, `t-completed`, `t-active`) ใน `style.css` ทำงานได้อย่างสมบูรณ์

### 2.2 เทมเพลตแดชบอร์ดเดิม (`academic/position/applicant/dashboard.html`)
- ปรับปรุงให้รองรับเงื่อนไข `revising`:
  - แสดงไอคอน `fa-circle-exclamation` เมื่อ `revising`
  - แสดงข้อความ `${req.currentStatus.thaiLabel}` ("ส่งแก้ไข") แทน `${s.thaiLabel}`

### 2.3 ตรวจสอบสไตล์ CSS (`style.css` & `dark-theme.css`)
- ยืนยันว่าคลาส `.timeline-v2-step.t-revise`:
  - แสดงผลสีส้ม `var(--color-warning, #f57f17)` สวยงาม
  - ไอคอน `fa-circle-exclamation` ชัดเจน
  - มีความกลมกลืนทั้งใน Light Theme และ Dark Theme

---

## 3. แผนงานและลำดับขั้นตอนการดำเนินงาน (Task Breakdown)

| Phase | Task | Agent | Deliverable / Action |
|:---|:---|:---|:---|
| **Phase 1** | ปรับแก้เทมเพลต `academic/applicant/dashboard.html` | `@[frontend-specialist]` | ปรับ logic stepper ของ Tab 2 ให้ใช้ `revising`, `pIdx`, และคลาส `t-revise` |
| **Phase 2** | ปรับแก้เทมเพลต `academic/position/applicant/dashboard.html` | `@[frontend-specialist]` | ตรวจสอบและอัปเดตไอคอน/ข้อความป้ายกำกับเมื่อเป็นสถานะ `REVISION_REQUESTED` |
| **Phase 3** | ตรวจสอบ CSS และ UI สภาพแวดล้อมจริง | `@[frontend-specialist]` | ยืนยันว่าสไตล์สีและไอคอนตรงตามรูปภาพตัวอย่างที่ 2 และสอดคล้องกับหน้าแอดมิน |
| **Phase 4** | Verification & Validation | `@[project-planner]` | รันการตรวจสอบและทดสอบการแสดงผลสถานะทั้ง 8 ขั้นตอน |

---

## 4. แผนการตรวจสอบและทดสอบ (Verification Plan)

1. **Automated Verification:**
   - รันการตรวจสอบ template syntax และ build ของโปรเจกต์
2. **Visual & UI Verification:**
   - ตรวจสอบคำร้องที่มีสถานะ `REVISION_REQUESTED` (ส่งแก้ไข):
     - ขั้นที่ 1 (รับคำร้อง) แสดงเป็นสีเขียว (ผ่านแล้ว)
     - ขั้นที่ 2 (ตรวจสอบความถูกต้อง/ครบถ้วน) แสดงเป็น **"ส่งแก้ไข"** พร้อมไอคอน `!` (fa-circle-exclamation) วงกลมสีส้ม
     - ขั้นที่ 3 (เสนอวาระกลั่นกรองฯ) และขั้นถัดไป แสดงเป็นสีเทา (ยังไม่ดำเนินการ)
   - ตรวจสอบคำร้องที่มีสถานะปกติอื่นๆ (เช่น `DOCUMENT_RECEIVED`, `DOCUMENT_VERIFICATION`, `SCREENING_COMMITTEE`) ให้แสดงผลถูกต้องตามลำดับ
   - ตรวจสอบการแสดงผลในหน้าจอ Light Theme และ Dark Theme

