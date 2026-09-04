# แผนการดำเนินงาน: เพิ่มฟังก์ชันแนบลิงก์ไฟล์ (URL) ในแต่ละช่องเอกสารแนบการยื่นประเมินการสอน

**ไฟล์แผนงาน:** `docs/PLAN-eval-file-link.md`  
**สถานะ:** ร่างแผนงาน (Planning Phase - รอผู้ใช้พิจารณาอนุมัติ)  
**ผู้รับผิดชอบหลัก:** `project-planner`, `frontend-specialist`, `backend-specialist`

---

## 1. ที่มาและความต้องการ (Context & Requirements)

### 1.1 ปัญหาและความต้องการเดิม
ในการยื่นคำร้องประเมินการสอน (แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน - เอกสารที่ 1) ผู้ขอต้องแนบไฟล์เอกสารหลักฐานให้ครบทั้ง 5 ช่องตามรายการตรวจสอบ:
1. บันทึกนำส่งเอกสารประกอบประเมินผลการสอน
2. แผนการสอน พร้อมเกณฑ์และวิธีวัดประเมินผลผู้เรียน
3. สาระสำคัญของเนื้อหาวิชาฯ (เอกสารประกอบการสอน)
4. สื่อการสอน เช่น ชีทเอกสาร PowerPoint แผ่นใส ฯลฯ
5. ผลการประเมินการสอนโดยนักศึกษา

ปัจจุบันระบบรองรับเฉพาะการอัปโหลดไฟล์ขนาดไม่เกิน 75 MB ต่อช่อง (รองรับ `.pdf`, `.docx`, `.doc`, `.zip`) แต่ในทางปฏิบัติ:
- เอกสารบางประเภทมีขนาดใหญ่มาก เช่น คลิปวิดีโอบันทึกการสอน, โฟลเดอร์รวมสื่อการสอนขนาดใหญ่
- ผู้ขอจัดเก็บไฟล์ไว้ในคลาวด์ภายนอก เช่น Google Drive, OneDrive (KKU Mail), CMU/KKU Cloud, YouTube หรือ Dropbox
- **ผู้ใช้ต้องการ:** ให้ในแต่ละช่อง (1-5) สามารถเลือก **"แนบลิงก์ไฟล์ (URL)"** ควบคู่ไปกับหรือแทนที่การอัปโหลดไฟล์ได้ และลิงก์ที่แนบจะนับเป็นรายการเอกสารที่ผ่านเกณฑ์ของช่องนั้นๆ ด้วย

---

## 2. การออกแบบสถาปัตยกรรมและโครงสร้างข้อมูล (Architecture & Data Design)

### 2.1 โครงสร้างข้อมูลตาราง `AcademicAttachment`
ตาราง `academic_attachment` ปัจจุบันมีโครงสร้างที่รองรับการปรับใช้ได้ทันทีโดยไม่กระทบความสัมพันธ์เดิม:
- `id`: Primary Key
- `request_id`: FK อ้างอิงคำร้อง `AcademicRequest`
- `checklistItem`: หมายเลขช่อง (1 ถึง 5)
- `originalFilename`: ชื่อแสดงผลของลิงก์ เช่น *"วิดีโอบันทึกการสอนบทที่ 1-5 (Google Drive)"*
- `storedFilePath`: จัดเก็บ URL ของลิงก์ เช่น `https://drive.google.com/drive/folders/...`
- `fileType`: กำหนดค่าเป็น `"LINK"`
- `fileSize`: กำหนดเป็น `0L` (ไม่กินโควตา 75 MB ของไฟล์อัปโหลด)
- `isDeleted`: soft delete (`false`)
- `uploadedAt`: เวลาที่แนบลิงก์

> [!NOTE]
> **ความเข้ากันได้ย้อนหลัง (Backward Compatibility):**
> 1. ไม่ต้องสร้างตารางใหม่ ทำให้ฟังก์ชันเดิม เช่น `requestService.getAttachmentsGroupedBySlot(id)` ใช้งานได้ต่อเนื่องทันที
> 2. นับเป็น 1 รายการในช่องนั้นๆ ทันที ทำให้เงื่อนไข "ต้องมีเอกสารอย่างน้อย 1 รายการต่อช่อง ครบทั้ง 5 ช่อง" ผ่านอย่างเป็นธรรมชาติ

### 2.2 การจัดการความยาวของ URL ในฐานข้อมูล
- บางลิงก์ของ Google Drive / SharePoint อาจยาวเกิน 255 ตัวอักษร
- จัดทำ Flyway Migration `V20__support_link_attachments.sql`:
  - ปรับคอลัมน์ `stored_file_path` ให้รองรับ `VARCHAR(2048)`
  - ปรับคอลัมน์ `original_filename` ให้รองรับ `VARCHAR(500)`

---

## 3. รายละเอียดการพัฒนาแต่ละส่วน (Technical Implementation Plan)

### ส่วนที่ 1: ฝั่ง Backend & Service (`AcademicApplicantController` & `AcademicRequestService`)
1. **Endpoint เพิ่มลิงก์แนบในแต่ละช่อง:**
   - URL: `POST /user/academic/request/{id}/document-1/links/{slot}`
   - Parameters:
     - `@PathVariable Long id`: รหัสคำร้อง
     - `@PathVariable Integer slot`: หมายเลขช่อง (1-5)
     - `@RequestParam String linkUrl`: ลิงก์ URL (ต้องเริ่มต้นด้วย `http://` หรือ `https://`)
     - `@RequestParam(required = false) String linkTitle`: ชื่ออธิบายลิงก์ (ถ้าเว้นว่างจะดึงเป็น URL หรือชื่อโดเมนอัตโนมัติ)
   - ความปลอดภัย & การตรวจสอบ:
     - ตรวจสอบสิทธิ์ผู้ยื่น (สิทธิ์เจ้าของคำร้อง)
     - ตรวจสอบสถานะการล็อกเอกสาร (`canApplicantEditDocument(request, 1)`)
     - ตรวจสอบความถูกต้องของ URL รูปแบบ Scheme `http/https` ป้องกัน XSS และ `javascript:` pseudo-protocol
     - บันทึก `AcademicAttachment` ด้วย `fileType = "LINK"`, `fileSize = 0L`
2. **การลบไฟล์/ลิงก์ (`deleteAttachment`):**
   - ตรวจสอบ `if (!"LINK".equalsIgnoreCase(attachment.getFileType()))` ก่อนสั่ง `Files.deleteIfExists(Path.of(attachment.getStoredFilePath()))` เพื่อไม่ให้เกิด `InvalidPathException` จากสตริง URL
3. **Endpoint สำหรับเปิดดู/ดาวน์โหลด (`viewAttachment` / `downloadAttachment`):**
   - กรณีมีผู้คลิกลิงก์ผ่าน endpoint เดิม หาก `fileType == 'LINK'` ให้ redirect (`302 Found`) ไปยัง URL ปลายทางโดยตรง

---

### ส่วนที่ 2: ฝั่ง Frontend ผู้ขอ (`document_1_form.html`)
1. **การปรับ UI ในแต่ละช่องเอกสารแนบ (ช่องที่ 1 ถึง 5):**
   - เพิ่มแถบสลับ (Tab Pill หรือ Toggle Button):
     - `[ <i class="fas fa-file-upload"></i> อัปโหลดไฟล์ ]`
     - `[ <i class="fas fa-link"></i> แนบลิงก์ไฟล์ ]`
   - เมื่อเลือก **แนบลิงก์ไฟล์**:
     - ช่องกรอก **URL ลิงก์** (เช่น Google Drive, OneDrive, YouTube, เว็บไซต์ภายนอก) พร้อม placeholder แนะนำ
     - ช่องกรอก **ชื่อเอกสาร / คำอธิบายลิงก์** (เช่น "ลิงก์รวมไฟล์วิดีโอบันทึกการสอน")
     - ปุ่ม **"เพิ่มลิงก์"** พร้อมไอคอน `<i class="fas fa-plus-circle"></i>`
2. **การแสดงผลรายการที่แนบแล้วในช่องนั้นๆ:**
   - หาก `att.fileType == 'LINK'`:
     - แสดงไอคอนโซ่ลิงก์สีฟ้า `<i class="fas fa-link text-info"></i>`
     - แสดงชื่อลิงก์ และแสดง URL ตัวย่อ
     - Badge กำกับ `[ ลิงก์ภายนอก ]`
     - ปุ่ม **"เปิดลิงก์"** (`<a href="..." target="_blank" rel="noopener noreferrer">`) ไอคอน `<i class="fas fa-external-link-alt"></i>`
     - ปุ่ม **"ลบ"** สำหรับผู้ยื่นที่ยังแก้ไขได้
3. **การตรวจสอบความถูกต้องฝั่ง Client (JavaScript Validation):**
   - อัปเดตตรรกะตรวจเช็คความครบถ้วนของ 5 ช่อง: ช่องที่มีการแนบลิงก์จะถือว่ามีเอกสารแล้ว (`slotCounts[slot] > 0`)
   - ปรับข้อความสรุปสถานะ: *"ตรวจสอบครบ 5 ข้อ และแนบไฟล์/ลิงก์ครบทั้ง 5 ช่องแล้ว พร้อมบันทึกเอกสาร"*

---

### ส่วนที่ 3: ฝั่ง Admin และกรรมการตรวจเอกสาร (`admin/doc_fragments/1.html` & `request_detail.html`)
1. **ในหน้าตรวจสอบเอกสารที่ 1 ของเจ้าหน้าที่ (`admin/doc_fragments/1.html`):**
   - ตรวจสอบ `att.fileType == 'LINK'`
   - แสดงไอคอน `<i class="fas fa-link text-info"></i>` พร้อมชื่อลิงก์
   - เปลี่ยนปุ่มจาก ดู/ดาวน์โหลด เป็นปุ่ม **"เปิดลิงก์"** (`<a href="..." target="_blank" rel="noopener noreferrer">`)
2. **ในหน้ารายละเอียดคำร้องของเจ้าหน้าที่ (`admin/request_detail.html`):**
   - ปรับการแสดงผลแถวที่เป็น `LINK` ให้แสดง Badge "LINK" และปุ่มเปิดลิงก์ภายนอกได้อย่างถูกต้อง

---

## 4. แผนงานการตรวจสอบและทดสอบ (Verification Plan)

### 4.1 Automated Tests (Unit & Integration Tests)
- `AttachmentLinkTest.java`:
  - ทดสอบการเพิ่มลิงก์ที่มี URL ถูกต้อง (`https://drive.google.com/...`) สำเร็จ
  - ทดสอบการปฏิเสธ URL ที่ไม่ปลอดภัย (เช่น `javascript:...`, `file://...`)
  - ทดสอบเงื่อนไขว่าช่องที่มีเฉพาะลิงก์สามารถผ่านเกณฑ์ครบ 5 ช่องเพื่อกดบันทึกเอกสารได้
  - ทดสอบการลบรายการประเภทลิงก์ โดยไม่เกิดข้อผิดพลาดในการลบไฟล์ในเครื่อง (Physical file deletion bypass)

### 4.2 Manual Verification (UI/UX)
1. เข้าสู่ระบบในฐานะผู้ขอ เข้าหน้าแก้ไขเอกสารที่ 1
2. ทดสอบแนบไฟล์จริงในช่องที่ 1, 2 และแนบลิงก์ Google Drive ในช่องที่ 3, 4, 5
3. ตรวจสอบว่าระบบปลดล็อกปุ่ม "บันทึกเอกสาร" และยอมรับการบันทึกสำเร็จ
4. ทดสอบกดคลิกลิงก์ที่แนบ เปิดแท็บใหม่ไปยัง URL ที่ถูกต้อง (`target="_blank" rel="noopener noreferrer"`)
5. เข้าสู่ระบบในฐานะ Admin ตรวจสอบเอกสารที่ 1 เห็นลิงก์และสามารถคลิกเปิดดูได้ถูกต้อง

---

## 5. คำถามเชิงนโยบายและการตัดสินใจ (Socratic Gate & Design Questions)

1. **การจำกัดประเภทโดเมนของลิงก์:**  
   ต้องการจำกัดเฉพาะโดเมนทางการศึกษา เช่น Google Drive, OneDrive, KKU Domain หรือเปิดให้ใส่ได้ทุก URL ที่ขึ้นต้นด้วย `http://` / `https://`? *(ข้อเสนอแนะ: แนะนำเปิดให้ใส่ได้ทุก `https://` เพื่อความยืดหยุ่นในการแนบสื่อการสอน)*
2. **จำนวนลิงก์สูงสุดต่อช่อง:**  
   ต้องการจำกัดจำนวนลิงก์ต่อช่องหรือไม่ (เช่น สูงสุด 5 ลิงก์ต่อช่อง) หรือไม่จำกัดจำนวน? *(ข้อเสนอแนะ: แนะนำจำกัดไม่เกิน 10 รายการต่อช่องเพื่อความเป็นระเบียบเรียบร้อยของหน้าจอ)*
