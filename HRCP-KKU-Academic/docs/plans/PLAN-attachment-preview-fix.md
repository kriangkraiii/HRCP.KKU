# แผนการพัฒนา (Project Plan): แก้ไขการเปิดดูไฟล์แนบให้แสดงผลในเบราว์เซอร์แทนการดาวน์โหลด (Attachment Inline Preview)

## 📋 ข้อมูลเบื้องต้น (Overview & Context)
- **ปัญหาที่พบ:** เมื่อผู้ใช้กดปุ่มไอคอนรูปดวงตา **"เปิดดูไฟล์" (`/attachment/{id}/view`)** บนตารางไฟล์แนบ (เช่น เอกสาร Word `.docx`, `.pdf`, หรือไฟล์อื่นๆ) เบราว์เซอร์ทำการดาวน์โหลดไฟล์ลงเครื่องแทนที่จะเปิดแสดงผลในหน้าต่างใหม่ (Inline Preview)
- **สาเหตุทางเทคนิค (Root Cause):**
  1. **ไฟล์ DOCX / Word Documents:** เว็บบราวเซอร์มาตรฐาน (Google Chrome, MS Edge, Safari, Mozilla Firefox) **ไม่มีตัวเรนเดอร์ไฟล์ DOCX ภายในตัว (No Native DOCX Renderer)** เมื่อ Server ส่งไฟล์พร้อม Header `Content-Type: application/vnd.openxmlformats-officedocument...` แม้จะใส่ `Content-Disposition: inline` เบราว์เซอร์จะสั่งดาวน์โหลดไฟล์ทันทีเนื่องจากไม่สามารถวาดผลลัพธ์บนแท็บได้
  2. **ระบบแปลงเอกสารที่มีอยู่แล้ว:** โครงการนี้มี Service สำหรับแปลง DOCX -> PDF ผ่าน LibreOffice อยู่แล้ว (`DocumentGenerationService.convertDocxToPdfCached(...)`) แต่ยังไม่ได้ถูกนำมาเชื่อมต่อเข้ากับ Endpoint สำหรับ View ไฟล์แนบ

---

## 🎯 วัตถุประสงค์ (Objectives)
1. เมื่อผู้ใช้คลิก **"เปิดดูไฟล์" (ปุ่มตา 👁️)**:
   - **กรณีไฟล์ PDF / รูปภาพ (PNG, JPG, JPEG):** ส่งไฟล์เป็น `Content-Type: application/pdf` หรือ `image/...` พร้อม `Content-Disposition: inline` เพื่อเปิดดูบนเบราว์เซอร์ทันที
   - **กรณีไฟล์ Word (DOCX):** ระบบจะแปลงไฟล์ DOCX เป็น PDF แบบ Real-time (พร้อมระบบ In-Memory Caching) และส่งกลับเป็น `Content-Type: application/pdf` แบบ `inline` เพื่อให้เบราว์เซอร์เปิดแสดงผลเอกสารได้ทันที
2. เมื่อผู้ใช้คลิก **"ดาวน์โหลด" (ปุ่มดาวน์โหลด 📥)**:
   - ยังคงดาวน์โหลดไฟล์ต้นฉบับจริง (เช่น `.docx` เดิม) พร้อม `Content-Disposition: attachment` อย่างถูกต้อง

---

## 🏗️ รายละเอียดการปรับปรุง (Architecture & Changes)

### 1. ปรับปรุง Controller สำหรับดูไฟล์แนบ (`viewAttachment`)
- **ไฟล์:**
  - `AcademicApplicantController.java` (`/user/academic/request/{id}/attachment/{attachmentId}/view`)
  - `AcademicAdminController.java` (`/admin/academic/request/{id}/attachment/{attachmentId}/view`)
  - `PositionAdminController.java` (`/admin/position/request/{id}/attachment/{attachmentId}/view`)
- **การทำงาน:**
  - ตรวจสอบนามสกุลไฟล์:
    - ถ้าเป็น `"DOCX"`:
      1. อ่าน byte array จาก `attachment.getStoredFilePath()`
      2. เรียก `documentGenerationService.convertDocxToPdfCached(docxBytes)`
      3. ส่งกลับ `ResponseEntity<byte[]>` ด้วย `MediaType.APPLICATION_PDF` และ `Content-Disposition: inline; filename="...pdf"`
      4. มี try-catch fallback กรณีแปลงไม่สำเร็จให้ส่ง DOCX ดั้งเดิม
    - ถ้าเป็น `"PDF"`, `"PNG"`, `"JPG"`, `"JPEG"`:
      - ส่งกลับตามปกติพร้อม `Content-Disposition: inline`

### 2. ตรวจสอบการพึ่งพา (Dependencies)
- ฉีด `DocumentGenerationService` เข้าไปใน `AcademicAdminController` และ `PositionAdminController` (หากยังไม่มี) เพื่อใช้เมธอดแปลงไฟล์แคช

---

## 🧪 แผนการทดสอบ (Verification Plan)
1. **Automated Unit Tests:**
   - เขียน Unit Test ตรวจสอบ `viewAttachment` เมื่อเรียกดูไฟล์ DOCX -> ตรวจสอบว่า Response Content-Type เป็น `application/pdf` และ Header เป็น `inline`
   - ตรวจสอบ `viewAttachment` เมื่อเรียกดูไฟล์ PDF -> Response เป็น `application/pdf` และ `inline`
   - ตรวจสอบ `downloadAttachment` -> Response Header เป็น `attachment` และนามสกุลไฟล์ดั้งเดิม
2. **Full Regression Test Suite:**
   - รัน `./mvnw test` เพื่อตรวจสอบความเข้ากันได้ของระบบทั้งหมด

---

## 🚀 ลำดับขั้นตอนการดำเนินงาน (Execution Steps)
- [ ] **Phase 1:** อัปเดต `AcademicApplicantController.java` เมธอด `viewAttachment` ให้แปลง DOCX เป็น PDF เมื่อเปิดดู
- [ ] **Phase 2:** อัปเดต `AcademicAdminController.java` เมธอด `viewAttachment`
- [ ] **Phase 3:** อัปเดต `PositionAdminController.java` เมธอด `viewAttachment`
- [ ] **Phase 4:** เขียน Unit Tests และรัน `./mvnw test`
