# แผนงาน: ปรับปรุงความถูกต้องของวันหมดอายุผลประเมินการสอน และดีไซน์ปุ่ม Liquid Glass สไตล์ Apple

**รหัสเอกสารแผนงาน:** `docs/PLAN-eval-expiry-liquid-glass.md`  
**สถานะ:** ดำเนินการเสร็จสมบูรณ์ (Completed & Verified)  
**Agent:** `project-planner`  
**ผู้เชี่ยวชาญ:** `backend-specialist`, `frontend-specialist`, `antigravity-design-expert`  

---

## 1. บริบทและปัญหาที่พบ (Context & Problem Statement)

### 1.1 ปัญหาวันหมดอายุของผลประเมินการสอนไม่ตรงกัน (Evaluation Expiry Mismatch)
จากการตรวจสอบโค้ดพบสาเหตุหลัก 3 ประการ:
1. **ความไม่สอดคล้องกันระหว่าง `getLatestEvaluationExpiry` กับ `summarize / resolveExpiry`:**
   - ใน `AcademicRequestService.getLatestEvaluationExpiry()`: ระบบตรวจสอบเพียง `evaluation_date` หรือ `faculty_board_meeting_date` แล้วคำนวณ `+ 3 ปี` โดย **ไม่ได้อ่านคีย์ `expiration_date`** จากเอกสารที่ 9 (Doc 9) เลย
   - แต่ใน `resolveExpiry()` (ที่ใช้คัดกรองผลประเมินสำหรับยื่นขอกำหนดตำแหน่ง): ตรวจสอบ `doc9.get("expiration_date")` เป็นอันดับแรก ส่งผลให้แดชบอร์ดกับหน้าเลือกผลประเมินอาจแสดงวันหมดอายุ **ไม่ตรงกัน**
2. **การแคชค่าเก่าในฐานข้อมูล (`evaluationExpiryDate`):**
   - เมื่อมีการคำนวณวันหมดอายุครั้งแรก (เช่น วันที่ยื่นคำร้อง + 3 ปี) ค่าจะถูกบันทึกไว้ใน `academic_request.evaluation_expiry_date`
   - หากแอดมินมาออกเอกสารที่ 9 หรือกำหนดวันหมดอายุจริงในภายหลัง เมธอด `getLatestEvaluationExpiry()` ที่ตรวจสอบ `if (req.getEvaluationExpiryDate() != null) return req.getEvaluationExpiryDate();` จะคืนค่าเก่าที่แคชไว้เสมอ ทำให้วันหมดอายุไม่อัปเดตตามเอกสารจริง
3. **JavaScript บนหน้ากรอกเอกสารที่ 9 ทับค่าวันหมดอายุเดิมทุกครั้งที่เปิดหน้า (`doc_fragments/9.html`):**
   - ใน `doc_fragments/9.html`: ฟังก์ชัน `calculateExpiry()` ถูกเรียกทันทีตอน `DOMContentLoaded` และเขียนทับช่อง `input[name="expiration_date"]` ทันที แม้ว่าจะมีข้อมูลวันที่ถูกบันทึกไว้เดิมแล้วในฐานข้อมูลก็ตาม

---

### 1.2 การปรับดีไซน์ปุ่มทั้งสองฝั่งเป็น Liquid Glass สไตล์ Apple
- ปุ่มลอยนำทางด้านบนของหน้าเอกสาร (`.floating-doc-btn-back` ฝั่งซ้าย และ `.floating-doc-btn-preview` ฝั่งขวา) รวมถึง `.btn-back-pill` เดิมใช้สไตล์สีขาวทึบ (Solid White) และขอบธรรมดา ทำให้ไม่แสดงมิติของกระจก
- **เป้าหมาย:** ยกระดับปุ่มทั้งสองฝั่งและปุ่มนำทางในระบบให้เป็น **Liquid Glass สไตล์ Apple**:
  - ใช้ Frosted Glass Translucency (`backdrop-filter: blur(20px) saturate(180%)`)
  - ขอบแบบ Specular Highlight บางเฉียบ พร้อมประกายแสงสะท้อนด้านบน (`inset 0 1px 1px rgba(255,255,255,0.8)`)
  - เงาฟุ้งนุ่มลึกแบบ Apple Material (`box-shadow: 0 8px 32px rgba(0,0,0,0.1)`)
  - ปุ่มกลับ (Back) โทน Pure Liquid Frost สบายตา
  - ปุ่มดูตัวอย่าง (Preview) โทน Apple Sapphire Tinted Glass
  - รองรับทั้ง Light Mode และ Dark Mode อย่างสมบูรณ์

---

## 2. รายละเอียดการแก้ไข (Proposed Changes)

### ส่วนที่ 1: ฝั่ง Backend & Logic วันหมดอายุผลประเมิน

#### 1. ปรับปรุง `AcademicRequestService.java`
- ปรับ `getLatestEvaluationExpiry(Integer applicantId)`:
  - ดึงข้อมูลเอกสารที่ 9 และอ่าน `expiration_date` ผ่าน `parseThaiDate` เป็นลำดับแรก
  - หากไม่มี `expiration_date` ให้ใช้ `evaluation_date + 3 ปี` หรือ `faculty_board_meeting_date + 3 ปี`
  - หากยังไม่มี ให้ fallback เป็น `submissionDate + 3 ปี`
  - ซิงค์ค่าที่คำนวณได้ลงใน `req.setEvaluationExpiryDate(expiry)` ทันที เพื่อให้ฐานข้อมูลมีค่าตรงกับเอกสารเสมอ
- เมื่อมีการบันทึกเอกสารที่ 9 (`saveDocument` สำหรับ `documentType == 9`):
  - ทำการ Parse `expiration_date` จาก `jsonData` และอัปเดต `request.setEvaluationExpiryDate(expiry)` อัตโนมัติ

#### 2. ปรับปรุง JavaScript ใน `doc_fragments/9.html`
- ปรับ `calculateExpiry()` ไม่ให้เขียนทับค่าหาก `expiryInput.value` มีค่าอยู่แล้ว (หรือมีค่าใน `existingData`)
- ให้คำนวณอัตโนมัติเฉพาะเมื่อ:
  - ช่องวันหมดอายุยังว่างอยู่
  - หรือ ผู้ใช้มีการเปลี่ยนค่าในช่อง `evaluation_date`

---

### ส่วนที่ 2: ฝั่ง UI/CSS ดีไซน์ Liquid Glass สไตล์ Apple

#### 1. ปรับปรุง `style.css`
- ปรับสไตล์ของ `.floating-doc-btn`, `.floating-doc-btn-back`, `.floating-doc-btn-preview` และ `.btn-back-pill`:
  - ใช้พื้นหลังโปร่งแสงแบบกระจก: `rgba(255, 255, 255, 0.72)`
  - เอฟเฟกต์เบลอ: `backdrop-filter: blur(20px) saturate(180%); -webkit-backdrop-filter: blur(20px) saturate(180%);`
  - เส้นขอบและแสงตกกระทบ: `border: 1px solid rgba(255, 255, 255, 0.6); box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08), inset 0 1px 1.5px rgba(255, 255, 255, 0.9);`
  - การเคลื่อนไหว (Micro-interactions): มีการขยายและยกตัวนุ่มนวลเมื่อ Hover (`transform: translateY(-2px) scale(1.02)`)
  - ปรับปรุงสีของปุ่มดูตัวอย่างเป็น Tinted Sapphire Glass ที่สดใสและพรีเมียม

#### 2. ปรับปรุง `dark-theme.css`
- ปรับแต่งให้แสดงผลเป็น Dark Liquid Glass (Dark Obsidian Glass) ที่มีมิติแสงขอบเงาในโหมดมืดอย่างสวยงาม

---

## 3. แผนการทดสอบและการตรวจสอบ (Verification Plan)

### 3.1 Automated Tests
- รันชุดทดสอบความถูกต้องของวันหมดอายุ:
  ```bash
  ./mvnw test -Dtest=EvaluationEligibilityTest
  ```
- รันชุดทดสอบภาพรวม:
  ```bash
  ./mvnw test -Dtest=FullJourneyMockMvcTest
  ```

### 3.2 Manual / Visual Verification
1. **ตรวจสอบหน้าเอกสาร (Document Form):**
   - ตรวจสอบปุ่ม `[ ← กลับ ]` ทางซ้าย และ `[ 👁 ดูตัวอย่าง ]` ทางขวา ว่าแสดงผลเป็น Liquid Glass โปร่งแสง มีแสงสะท้อนและเบลอพื้นหลังอย่างสวยงามทั้งใน Light Mode และ Dark Mode
2. **ตรวจสอบวันหมดอายุ:**
   - ตรวจสอบในหน้าเอกสารที่ 9 (Admin) ว่าวันหมดอายุไม่ถูกทับโดยพลการ
   - ตรวจสอบในหน้า Dashboard ของอาจารย์ว่าวันหมดอายุในการ์ด Countdown ตรงกับวันที่ระบุในเอกสารที่ 9 อย่างถูกต้องแม่นยำ
