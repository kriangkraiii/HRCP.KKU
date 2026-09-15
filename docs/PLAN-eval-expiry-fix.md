# แผนงาน: แก้ไขปัญหาวันหมดอายุของผลประเมินการสอนไม่ตรงกันในทุกจุดของระบบ (Comprehensive Evaluation Expiry Fix)

**รหัสเอกสารแผนงาน:** `docs/PLAN-eval-expiry-fix.md`  
**สถานะ:** รอผู้ใช้ตรวจสอบและอนุมัติ (Awaiting User Approval)  
**Agent:** `project-planner`  
**ผู้เชี่ยวชาญร่วม:** `backend-specialist`, `frontend-specialist`  

---

## 1. บริบทและปัญหาที่พบ (Problem Statement & Deep Discovery)

ผู้ใช้ส่งคำสั่งตรวจสอบ:
> *"วันหมดอายุของผลประเมินการสอนไม่ตรง /plan"*

จากการสืบค้นและวิเคราะห์ซอร์สโค้ดในเชิงลึกตลอดทั้งระบบ ตั้งแต่ฐานข้อมูล, เซอร์วิส, คอนโทรลเลอร์, ไปจนถึงเทมเพลตและสคริปต์หน้าบ้าน พบจุดที่เป็นสาเหตุทำให้ **วันหมดอายุของผลประเมินการสอนไม่ตรงกัน** หรือ **แสดงผลคลาดเคลื่อน** ทั้งสิ้น 5 ประการ ดังนี้:

### 1.1 ลำดับความสำคัญ (Ladder Priority) ใน `resolveExpiry` ไม่ตรงกับ `getLatestEvaluationExpiry`
- ใน `AcademicRequestService.getLatestEvaluationExpiry()`:
  - อันดับ 1: `doc9.get("expiration_date")` (วันหมดอายุที่ระบุในเอกสาร 9)
  - อันดับ 2: `doc9.get("evaluation_date") + 3 ปี` (วันประเมินจริง + 3 ปี)
  - อันดับ 3: `req.getEvaluationExpiryDate()` (ค่าที่บันทึกในฐานข้อมูล)
  - อันดับ 4: `req.getSubmissionDate() + 3 ปี`
- แต่ใน `AcademicRequestService.resolveExpiry()` (ที่ใช้คัดกรองคำร้องสำหรับยื่นขอกำหนดตำแหน่ง):
  - สลับเอา `if (request.getEvaluationExpiryDate() != null) return request.getEvaluationExpiryDate();` ขึ้นมาก่อน `doc9.get("evaluation_date") + 3 ปี`
  - **ผลกระทบ:** หากระบบเคยบันทึกค่าเริ่มต้นไว้ตอนยื่นคำร้อง (`submissionDate + 3 ปี`) เมื่อแอดมินมาลงวันประเมินจริงในเอกสารที่ 9 เมธอด `resolveExpiry` จะยังคงคืนค่าเดิมจากฐานข้อมูล ทำให้ผลประเมินในหน้าเลือกยื่นตำแหน่งกับหน้า Dashboard **แสดงวันหมดอายุคนละวันกัน**

### 1.2 `EvaluationSummary.expiryDate()` ขาด Fallback จาก `expiryAt`
- ใน `AcademicRequestService.summarizeEvaluation()`:
  - ฟิลด์ `expiryDate` (String) ถูกดึงจาก `doc9.get("expiration_date")` โดยตรง
  - หากแอดมินกรอกเฉพาะ "วันที่ประเมิน" (`evaluation_date`) แต่ไม่ได้พิมพ์ช่อง "วันหมดอายุ" (`expiration_date`) ฟิลด์ `choice.summary.expiryDate()` จะกลายเป็น `null` ทันที
  - **ผลกระทบ:** ในหน้า `new_request.html` และ `request_detail.html` จะแสดงผลเป็นค่าว่างหรือ `—` ทั้งที่ในการ์ด Countdown บน Dashboard มีวันหมดอายุคำนวณอยู่

### 1.3 `AcademicApplicantController.dashboard` ไม่ได้แสดงวันหมดอายุและไม่เคยทริกเกอร์การซิงค์
- ในหน้า Dashboard หลักของผู้ยื่น (`/user/academic/dashboard`):
  - การ์ดผลประเมินแสดงเพียงไอคอน `ผ่านการประเมิน` แต่ไม่มีการระบุวันหมดอายุของผลประเมินฉบับนั้นให้อาจารย์ทราบ
  - และไม่ได้มีการเรียก `getLatestEvaluationExpiry` เพื่อช่วย Auto-heal ซิงค์วันหมดอายุลงในฐานข้อมูลสำหรับคำร้องเก่า

### 1.4 `EvaluationExpiryScheduler` อ่านค่าแคชเก่าจากฐานข้อมูลโดยไม่ตรวจสอบความสดใหม่
- ใน `EvaluationExpiryScheduler.java`:
  - ตรวจสอบ `if (expiry == null)` หากในฐานข้อมูลมีค่าเก่าอยู่ (แม้ว่าจะไม่ตรงกับเอกสารที่ 9 ฉบับล่าสุด) จะใช้วันหมดอายุเก่านั้นไปส่งอีเมลแจ้งเตือนอาจารย์

### 1.5 การแปลง Date ใน JavaScript (`new Date(isoString)`) เสี่ยงคลาดเคลื่อนข้ามวัน (Timezone Edge Case)
- ใน `position/applicant/dashboard.html`:
  - `const expiryDate = new Date(expiryStr);` เมื่อรับ ISO String ที่ไม่มี Timezone เช่น `"2029-02-17T00:00:00"` เบราว์เซอร์บางตัวหรืออุปกรณ์ที่มีการตั้งเวลา UTC อาจตีความเหลื่อมไป 1 วัน ทำให้วันที่แสดงบน Countdown ไม่ตรงกับเอกสารที่ 9

---

## 2. แนวทางการแก้ไขที่เสนอ (Proposed Architecture & Implementation Plan)

### ส่วนที่ 1: การประสานลอจิกวันหมดอายุให้เป็นมาตรฐานเดียวกัน (Single Source of Truth)

#### [MODIFY] [AcademicRequestService.java](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/AcademicRequestService.java)
1. **ปรับ `resolveExpiry`:**
   - เรียงลำดับความสำคัญให้ตรงกับ `getLatestEvaluationExpiry` 100%:
     1. `doc9.get("expiration_date")`
     2. `doc9.get("evaluation_date") + 3 ปี`
     3. `request.getEvaluationExpiryDate()` (ถ้ามีและไม่ขัดแย้ง)
     4. `request.getSubmissionDate() + 3 ปี`
2. **ปรับ `summarizeEvaluation`:**
   - ให้ `expiryDate` (String) มี Fallback อัตโนมัติ:
     `firstNonBlank(doc9.get("expiration_date"), formatThaiDate(expiryAt))`
   - เพิ่มฟังก์ชัน `formatThaiDate(LocalDateTime dt)` แปลงเป็นวันที่ภาษาไทยแบบมาตรฐาน (เช่น `"17 กุมภาพันธ์ 2572"`) เพื่อให้แสดงผลตรงกันเสมอ
3. **ปรับ `saveDocument`:**
   - เมื่อมีการบันทึกเอกสารที่ 9 ให้คำนวณและอัปเดต `request.setEvaluationExpiryDate(expiry)` เสมอ

#### [MODIFY] [EvaluationExpiryScheduler.java](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/EvaluationExpiryScheduler.java)
- ปรับให้อ่านวันหมดอายุผ่าน `academicService.getLatestEvaluationExpiry(user.getId())` เสมอ เพื่อให้การแจ้งเตือนทางอีเมลใช้วันหมดอายุจริงที่ถูกต้องตามเอกสารที่ 9

---

### ส่วนที่ 2: การแสดงผลบนหน้าเว็บ (UI Synchronization)

#### [MODIFY] [academic/applicant/dashboard.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/dashboard.html)
- เพิ่มการแสดงผลวันหมดอายุของผลประเมินในแถบ `ผ่านการประเมิน` ของแต่ละคำร้อง:
  - แสดง `(ผลประเมินหมดอายุ: วันที่ เดือน ปี)` เพื่อให้อาจารย์ทราบได้ทันทีจากหน้า Dashboard ของตนเอง

#### [MODIFY] [academic/position/applicant/dashboard.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/dashboard.html)
- ปรับปรุง JavaScript ฟังก์ชันการสร้าง `expiryDate`:
  - จัดการแยก parse วันที่ `[year, month, day]` โดยตรง ป้องกันปัญหา Timezone offset เหลื่อมข้ามวัน

---

## 3. แผนการทดสอบและการตรวจสอบ (Verification Plan)

### 3.1 Automated Tests
- รันชุดทดสอบความถูกต้องของวันหมดอายุ:
  ```bash
  ./mvnw test -Dtest=EvaluationEligibilityTest
  ```
- เพิ่มเทสเคสครอบคลุม:
  1. เอกสาร 9 ระบุ `expiration_date` ตรงๆ
  2. เอกสาร 9 ระบุเฉพาะ `evaluation_date` (คำนวณ +3 ปี)
  3. ตรวจสอบว่า `resolveExpiry` กับ `getLatestEvaluationExpiry` คืนค่าวันหมดอายุเดียวกันเสมอ
  4. ตรวจสอบว่า `EvaluationSummary.expiryDate()` ไม่เป็นค่าว่างเมื่อมี `expiryAt`

### 3.2 Manual Verification
1. เปิดหน้าแบบประเมินเอกสารที่ 9 ในมุมมองแอดมิน ทดสอบการบันทึกวันหมดอายุ
2. เปิดหน้า Dashboard อาจารย์ทั้ง 2 แท็บ ตรวจสอบว่าวันหมดอายุในการ์ด Countdown ตรงกับวันที่ระบุในเอกสารที่ 9
3. เปิดหน้ายื่นขอกำหนดตำแหน่ง (`/user/position/new-request`) ยืนยันว่าวันที่ระบุในกล่องผลประเมินตรงกันทุกตัวอักษร
