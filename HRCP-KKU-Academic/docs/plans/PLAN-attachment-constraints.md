# แผนการพัฒนา (Project Plan): จำกัดประเภทไฟล์แนบ (.pdf, .docx, .doc, .zip) ซ่อนปุ่มดูไฟล์ ZIP และจำกัดขนาดไฟล์แนบรวมไม่เกิน 75MB ต่อคำร้อง

##  ข้อมูลเบื้องต้น (Overview & Context)
ผู้ใช้งานต้องการปรับปรุงการแนบไฟล์ประกอบคำร้อง (Attachments) ดังนี้:
1. **อนุญาตเฉพาะนามสกุลไฟล์:** `.pdf`, `.docx`, `.doc`, `.zip` เท่านั้น (ไม่อนุญาตไฟล์ประเภทอื่น เช่น `.xlsx`, `.pptx`, `.png`, `.jpg`, `.rar`, `.7z` ฯลฯ)
2. **เงื่อนไขไฟล์ `.zip`:** ไฟล์ ZIP จะ**ไม่มีปุ่มเปิดดูไฟล์ (️)** มีเฉพาะปุ่ม**ดาวน์โหลด ()** (และปุ่มลบ ️ สำหรับผู้มีสิทธิ์)
3. **จำกัดขนาดไฟล์แนบรวม (Total Size Limit):** ขนาดไฟล์แนบรวมทั้งหมดของแต่ละคำร้องต้อง**ไม่เกิน 75 MB** (78,643,200 bytes)

---

##  วัตถุประสงค์ (Objectives)

1. **Client-side UI & Validation:**
   - อัปเดตข้อความใน Dropzone / Upload area ให้แสดงประเภทไฟล์ที่รองรับ: `.pdf, .docx, .doc, .zip` และแจ้งขีดจำกัดขนาดรวมไม่เกิน 75 MB
   - ตั้งค่า `accept=".pdf,.docx,.doc,.zip"` ใน `<input type="file">`
   - ในตารางแสดงไฟล์แนบ: ซ่อนปุ่ม "เปิดดูไฟล์" สำหรับไฟล์ประเภท `ZIP` (`th:if="${att.fileType != 'ZIP' and att.fileExtension != 'ZIP'}"`)
   - คำนวณขนาดไฟล์รวมใน JavaScript ก่อนกดอัปโหลด แจ้งเตือนผู้ใช้หากขนาดรวมเกิน 75 MB
2. **Server-side Validation:**
   - ตรวจสอบนามสกุลไฟล์ใน `AcademicApplicantController`, `AcademicAdminController`, และ `PositionAdminController`:
     - หากพบไฟล์ที่ไม่ใช่ `pdf`, `docx`, `doc`, `zip` ให้ปฏิเสธและแจ้งเตือน
   - ตรวจสอบขนาดไฟล์รวมของคำร้อง (`currentTotalSize + newFilesSize <= 75MB`):
     - หากขนาดรวมเกิน 75 MB ให้ปฏิเสธและแจ้งเตือน `ขนาดไฟล์แนบรวมทั้งหมดต้องไม่เกิน 75 MB ต่อคำร้อง`
3. **Configuration:**
   - ตั้งค่า `spring.servlet.multipart.max-file-size=75MB` และ `spring.servlet.multipart.max-request-size=100MB` ใน `application.properties`

---

## ️ รายละเอียดการปรับปรุง (Architecture & Changes)

### 1. Backend Service & Controllers
- **`AcademicRequestService.java` & `PositionRequestService.java`:**
  - เพิ่มเมธอด `getTotalAttachmentSize(Long requestId)` เพื่อคำนวณผลรวมขนาดไฟล์แนบปัจจุบันของคำร้อง
- **`AcademicApplicantController.java` (`uploadDocument1Attachments`):**
  - อนุญาตเฉพาะ `.pdf`, `.docx`, `.doc`, `.zip`
  - ตรวจสอบ `totalAttachmentSize + incomingSize <= 75 * 1024 * 1024`
- **`AcademicAdminController.java` (`uploadResult`):**
  - ตรวจสอบนามสกุลไฟล์ (.pdf, .docx, .doc, .zip) และขนาดรวม <= 75 MB
- **`PositionAdminController.java` (`uploadAttachment`):**
  - ตรวจสอบนามสกุลไฟล์ (.pdf, .docx, .doc, .zip) และขนาดรวม <= 75 MB

### 2. Frontend Templates & UI
- **`document_1_form.html`:**
  - อัปเดตข้อความ Dropzone: `รองรับไฟล์: .pdf, .docx, .doc, .zip (ขนาดรวมทั้งหมดไม่เกิน 75 MB ต่อคำร้อง)`
  - ปรับ `accept=".pdf,.docx,.doc,.zip"`
  - ในตารางไฟล์แนบ: ซ่อนปุ่มเปิดดูไฟล์ (️) เมื่อเป็นไฟล์ ZIP (`th:if="${att.fileType != 'ZIP' and att.fileExtension != 'ZIP'}"`)
  - อัปเดต JavaScript validation ตรวจสอบประเภทไฟล์และขนาดรวม
- **`doc_fragments/1.html`:**
  - ซ่อนปุ่มดูไฟล์สำหรับ ZIP ในหน้าสรุปสำหรับแอดมิน
- **`request_detail.html` (Academic Admin & Position Admin):**
  - ซ่อนปุ่มดูไฟล์สำหรับ ZIP ในตารางไฟล์แนบ
  - อัปเดต input accept attribute

### 3. Application Configuration
- **`application.properties`:**
  - ตั้งค่า `spring.servlet.multipart.max-file-size=75MB`
  - ตั้งค่า `spring.servlet.multipart.max-request-size=100MB`

---

##  แผนการทดสอบ (Verification Plan)

### Automated Tests
1. **FileTypeValidationTest:**
   - ทดสอบอัปโหลด `.pdf`, `.docx`, `.doc`, `.zip` -> สำเร็จ (HTTP 302 redirect success)
   - ทดสอบอัปโหลด `.xlsx`, `.pptx`, `.png`, `.jpg`, `.rar` -> ปฏิเสธ (HTTP 302 redirect error invalid file type)
2. **TotalSizeLimitTest:**
   - ทดสอบอัปโหลดไฟล์ขนาดรวม <= 75 MB -> สำเร็จ
   - ทดสอบอัปโหลดไฟล์ขนาดรวม > 75 MB -> ปฏิเสธ (HTTP 302 redirect error total size exceeded)
3. **ZipPreviewExclusionTest:**
   - ตรวจสอบว่าใน View/Template ไม่มีการเรนเดอร์ปุ่ม view สำหรับไฟล์แนบ ZIP

### Regression Test Suite
- รัน `./mvnw test` ครบทุก Module

---

##  ลำดับขั้นตอนการดำเนินงาน (Execution Steps)
- [ ] **Phase 1:** เพิ่ม `getTotalAttachmentSize` ใน `AcademicRequestService` และ `PositionRequestService`
- [ ] **Phase 2:** ปรับปรุง Validation นามสกุล (.pdf, .docx, .doc, .zip) และ Total Size (75MB) ใน `AcademicApplicantController`, `AcademicAdminController`, `PositionAdminController`
- [ ] **Phase 3:** อัปเดต UI Dropzone และเงื่อนไขซ่อนปุ่มดูไฟล์สำหรับ ZIP ใน `document_1_form.html`, `doc_fragments/1.html`, `request_detail.html`
- [ ] **Phase 4:** อัปเดต `application.properties` สำหรับ multipart upload
- [ ] **Phase 5:** เพิ่ม Unit Tests และรัน `./mvnw test`
