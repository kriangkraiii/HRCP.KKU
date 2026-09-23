# Plan: ปรับให้ฝั่งผู้ยื่นหลังยื่นคำร้องแล้วดาวน์โหลดได้เฉพาะ PDF เท่านั้น (Applicant PDF-Only Download After Submission)

## 📌 Context & Overview
ในปัจจุบัน หน้ารายละเอียดคำร้องขอรับการประเมินผลการสอนของฝั่งผู้ยื่น (`/user/academic/request/{id}`) ในส่วนรายการเอกสารที่สร้างแล้ว (เอกสารที่ 1 และเอกสารที่ 2) มีการแสดงปุ่มการดำเนินการ 3 ปุ่ม ได้แก่:
1. `[ดูตัวอย่าง]` (พรีวิวเอกสาร PDF)
2. `[DOCX]` (ดาวน์โหลดไฟล์ Microsoft Word)
3. `[PDF]` (ดาวน์โหลดไฟล์ PDF)

**ความต้องการของผู้ใช้:**
เฉพาะฝั่งผู้ยื่น เมื่อยื่นคำร้องเข้าระบบแล้ว (สถานะพ้นแบบร่าง / Submitted) ให้มีให้ดาวน์โหลดเฉพาะไฟล์ PDF เท่านั้น (ตัดปุ่ม DOCX ออก) เพื่อความเป็นทางการและป้องกันการนำไฟล์ร่าง Word ไปแก้ไขหลังยื่น โดยที่ฝั่งเจ้าหน้าที่/ผู้ดูแลระบบ (Admin) ยังคงสามารถดาวน์โหลดไฟล์ได้ทั้ง DOCX และ PDF ตามปกติ

---

## 🎯 Success Criteria
1. **Frontend UI:** ในหน้ารายละเอียดคำร้องของผู้ยื่น (`request_detail.html`) ปุ่มดาวน์โหลด `DOCX` จะไม่แสดงสำหรับคำร้องที่ยื่นแล้ว เหลือเฉพาะปุ่ม `ดูตัวอย่าง` และ `PDF`
2. **Backend Security Guard:** ใน `AcademicApplicantController.java` หากผู้ยื่นพยายามเรียก API ดาวน์โหลดโดยส่งพารามิเตอร์ `?format=docx` สำหรับคำร้องที่ยื่นแล้ว ระบบจะปฏิเสธ (403 Forbidden) หรือสลับไปส่งไฟล์ `PDF` ให้อัตโนมัติ (Fallback)
3. **Admin Unaffected:** หน้ารายละเอียดคำร้องฝั่งเจ้าหน้าที่ (`/admin/academic/request/{id}`) ยังคงมีปุ่ม `DOCX` และ `PDF` ให้ใช้งานได้ตามปกติครบถ้วน
4. **Draft Consistency:** ขณะที่ผู้ยื่นกำลังกรอกแบบร่าง (ยังไม่กดยื่น) สามารถกำหนดให้คง DOCX ไว้เพื่อตรวจสอบ หรือปรับเป็น PDF ด้วยตามที่ตกลง

---

## 🏗️ Architecture & Component Analysis

### 1. ไฟล์ที่เกี่ยวข้องโดยตรง
- **Template ผู้ยื่น:** `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/request_detail.html` (บรรทัด 171–187)
- **Controller ผู้ยื่น:** `HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicApplicantController.java`
  - เมธอด `downloadDocument(@PathVariable Long id, @PathVariable Long docId, @RequestParam String format, ...)`
  - เมธอด `downloadDocumentByType(@PathVariable Long id, @PathVariable int type, @RequestParam String format, ...)`

### 2. สิทธิ์และสถานะ (State & Access Control)
- สถานะคำร้องที่ถือว่า "ยื่นแล้ว": `request.getCurrentStatus() != RequestStatus.DRAFT` (เช่น `RECEIVED`, `SUB_COMMITTEE_APPOINTED`, ฯลฯ)
- หมายเหตุ: หน้ารายละเอียดคำร้องของผู้ยื่น (`request_detail.html`) ถูกออกแบบไว้เฉพาะคำร้องที่ `status != DRAFT` อยู่แล้ว (หากเป็น DRAFT จะถูก redirect ไปยัง `/user/academic/new-request`)

---

## 📋 Task Breakdown

### Phase 1: Frontend UI Modification
- [x] ปรับแต่งไฟล์ `templates/academic/applicant/request_detail.html`:
  - ลบปุ่มลิงก์ `DOCX` (`<a ... ?format=docx class="btn btn-sm btn-outline-primary">...</a>`)
  - จัดระเบียบปุ่มให้เหลือปุ่ม `[ดูตัวอย่าง]` ควบคู่กับ `[PDF]` พร้อมปรับไอคอนหัวการ์ดเป็น `fa-file-alt`
- [x] ปรับแต่งไฟล์ `templates/academic/position/applicant/request_detail.html`:
  - เพิ่มปุ่ม `[PDF]`
  - ซ่อนปุ่ม `[DOCX]` เมื่อส่งคำร้องแล้ว (แสดงเฉพาะตอนที่เป็นแบบร่าง `DRAFT`)
- [x] อัปเดต `templates/academic/guide_user.html` ให้ระบุว่าเอกสารหลังยื่นดาวน์โหลดเป็น PDF

### Phase 2: Backend Download Security & Fallback (ทางเลือก A)
- [x] ปรับปรุง `AcademicApplicantController.java`:
  - ในเมธอด `downloadDocument` และ `downloadDocumentByType`:
    - ปรับ `defaultValue` เป็น `"pdf"`
    - ตรวจสอบสถานะคำร้อง หาก `request.getCurrentStatus() != RequestStatus.DRAFT` บังคับ `format = "pdf"` ทันที (Seamless Fallback)
- [x] ปรับปรุง `PositionApplicantController.java`:
  - ในเมธอด `downloadDocument`:
    - ปรับ `defaultValue` เป็น `"pdf"`
    - ตรวจสอบสถานะคำร้อง หาก `request.getCurrentStatus() != PositionRequestStatus.DRAFT` บังคับ `format = "pdf"` ทันที (Seamless Fallback)

### Phase 3: Socratic & Edge Case Decisions
- [x] ควบคู่: คงปุ่ม `[ดูตัวอย่าง]` ควบคู่กับ `[PDF]`
- [x] ทางเลือก A: บังคับ Fallback เป็น PDF หากมีการขอดาวน์โหลด docx เข้ามาตรงๆ หลังยื่น
- [x] ขยายขอบเขต: ครอบคลุมทั้งคำขอประเมินการสอนและคำขอกำหนดตำแหน่งทางวิชาการ
- [x] ฝั่งเจ้าหน้าที่/แอดมินแยกคอนโทรลเลอร์อิสระ (`AcademicAdminController.java`, `PositionAdminController.java`) ยังคงดาวน์โหลด DOCX และ PDF ได้ตามปกติ

---

## 🧪 Phase X: Verification Plan

### Automated & Build Checks
- [x] รันการคอมไพล์โค้ด Maven:
  ```bash
  ./mvnw compile -DskipTests
  ```
- [x] รันการทดสอบ Unit & Flow Tests ที่เกี่ยวข้องกับ Document Download:
  ```bash
  ./mvnw test -Dtest=ApplicantCannotDownloadCommitteeDocumentsTest,FullJourneyMockMvcTest
  ```

### Manual Verification Checklist
- [x] ตรวจสอบโครงสร้างปุ่มใน `request_detail.html` (ทั้ง 2 ระบบ): เหลือเฉพาะปุ่ม `ดูตัวอย่าง` และ `PDF` เมื่อส่งคำร้องแล้ว
- [x] ตรวจสอบความปลอดภัย Backend: พารามิเตอร์ `format=docx` จะถูก override เป็น `pdf` เมื่อคำร้องพ้น DRAFT (ทางเลือก A)
- [x] ตรวจสอบฝั่ง Admin: ยังคงมีปุ่มครบถ้วนไม่ได้รับผลกระทบ

## ✅ PHASE X COMPLETE
- Compilation: ✅ Pass (./mvnw compile -DskipTests)
- Tests: ✅ Pass (6 tests run, 0 failures, 0 errors in ApplicantCannotDownloadCommitteeDocumentsTest & FullJourneyMockMvcTest)
- Date: 2026-09-24
