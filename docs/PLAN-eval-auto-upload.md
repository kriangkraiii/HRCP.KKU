# แผนการดำเนินงาน: ปรับการยื่นประเมินการสอนไม่บังคับแนบเอกสาร/ลิงก์ และอัปโหลดไฟล์อัตโนมัติพร้อมหลอด Progress Bar

**ไฟล์แผนงาน:** `docs/PLAN-eval-auto-upload.md`  
**สถานะ:** ร่างแผนงาน (Planning Phase - รอผู้ใช้พิจารณาอนุมัติ)  
**ผู้รับผิดชอบหลัก:** `project-planner`, `backend-specialist`, `frontend-specialist`

---

## 1. ที่มาและความต้องการ (Context & Requirements)

### 1.1 ปัญหาและข้อเท็จจริงในระบบปัจจุบัน
1. **การบังคับแนบไฟล์ใน Phase 1 (ประเมินการสอน):**
   - ใน `AcademicApplicantController.java` มีการตรวจสอบทั้งตอนบันทึกแบบฟอร์มเอกสารที่ 2 (`submitDocument2`) และตอนกดยื่นคำร้องประเมินการสอน (`submitAcademicRequest`) ว่าต้องมีไฟล์แนบอย่างน้อย 1 ไฟล์ (`countAttachments == 0` จะถูกปฏิเสธ)
   - ในหน้าจอ `document_2_form.html` มีการแสดง Badge สีแดง `"จำเป็นต้องแนบ (อย่างน้อย 1 รายการ)"` และ JavaScript Validation ปิดปุ่มบันทึก (`submitBtn.disabled = true`) หากยังไม่มีไฟล์แนบ
   - **ความต้องการใหม่:** ปรับให้ **ไม่บังคับ** แนบเอกสารหรือลิงก์ (Optional) ผู้ยื่นสามารถส่งคำร้องประเมินการสอนได้แม้ไม่มีไฟล์แนบเพิ่มเติม
2. **ขั้นตอนการอัปโหลดไฟล์ในเอกสารที่ 2:**
   - ปัจจุบัน เมื่อผู้ใช้เลือกไฟล์ผ่านปุ่มหรือวางไฟล์ใน Dropzone ระบบจะนำไฟล์เข้าสู่ Queue Preview ด้านล่าง และผู้ใช้ต้องกดปุ่ม **"อัปโหลดไฟล์ที่เลือก"** อีกครั้งหนึ่ง จึงจะเริ่มอัปโหลด (เป็นการกด Submit แบบ Full Page Reload)
   - **ความต้องการใหม่:** เมื่อผู้ใช้เลือกไฟล์หรือลากไฟล์มาวาง **ไม่ต้องกดปุ่มอัปโหลดเอง** ให้ระบบทำการอัปโหลดทันที (Auto-upload via AJAX/XHR) พร้อมแสดง **หลอดความคืบหน้า (Upload Progress Bar)** แสดงเปอร์เซ็นต์ (0% - 100%) และขนาดไฟล์แบบเรียลไทม์

---

## 2. การวิเคราะห์ผลกระทบและการออกแบบ (Architecture & Changes)

### 2.1 Backend Layer (`AcademicApplicantController.java`)
1. **ปลดล็อกเงื่อนไขบังคับไฟล์แนบใน `submitDocument2`:**
   - นำเงื่อนไข `if (activeAttachments == 0)` ออก เพื่อให้อาจารย์สามารถบันทึกเอกสารที่ 2 ได้โดยไม่ต้องมีไฟล์แนบ
   - ยังคงการตรวจสอบโควตาขนาดไฟล์รวมไม่เกิน 75 MB (`totalSize > MAX_TOTAL_BYTES`)
2. **ปลดล็อกเงื่อนไขบังคับไฟล์แนบใน `submitAcademicRequest`:**
   - นำเงื่อนไข `if (requestService.countAttachments(id) == 0)` ออก เพื่อให้อาจารย์สามารถกดยื่นคำร้อง (Submit Draft) ได้โดยไม่ต้องมีไฟล์แนบ
3. **Endpoint อัปโหลดไฟล์ (`/request/{id}/document-2/attachments`):**
   - รองรับการตอบกลับทั้งแบบ Form POST ปกติ (Redirect) และแบบ AJAX/XHR (เมื่อ Browser ส่ง XHR แล้ว Browser จะตาม Redirect อัตโนมัติและได้ Status 200 หรือส่ง JSON status)

### 2.2 Frontend & UI Layer (`document_2_form.html`)
1. **ปรับข้อความและสถานะความจำเป็น:**
   - เปลี่ยน Badge จาก `"จำเป็นต้องแนบ (อย่างน้อย 1 รายการ)"` เป็น `"แนบไฟล์หรือลิงก์เพิ่มเติม (ไม่บังคับ)"`
   - ปรับ JavaScript `validateDoc2()`: ให้ตรวจเฉพาะการติ๊กครบ 5 ข้อ และขนาดไฟล์ไม่เกิน 75 MB (ไม่นำ `hasAttachment` มาเป็นเงื่อนไขในการปิดปุ่มบันทึก)
   - กล่องสถานะด้านล่าง:
     - หากมีไฟล์แนบ: แสดงจำนวนรายการและขนาดรวม
     - หากไม่มีไฟล์แนบ: แสดงสถานะพร้อมบันทึก (ระบุว่าไม่มีไฟล์แนบเพิ่มเติม)
2. **ระบบ Auto-Upload & Progress Bar:**
   - นำกล่อง Queue Preview และปุ่มกดยืนยันอัปโหลดออก
   - เพิ่ม UI Component **หลอดอัปโหลด (Progress Bar Container)**:
     - Progress Bar แบบ Bootstrap มีลูกเล่นแถบสีและแอนิเมชัน (`progress-bar-striped progress-bar-animated bg-success`)
     - แสดงชื่อไฟล์ที่กำลังอัปโหลด, ขนาดไฟล์, ตัวเลขเปอร์เซ็นต์ความคืบหน้า (0% → 100%) และ Spinner
   - การทำงาน JavaScript (Client-side):
     - เมื่อ `fileInput.change` หรือเกิดเหตุการณ์ Dropzone `drop`:
     - ตรวจสอบนามสกุลไฟล์ (.pdf, .docx, .doc, .zip) และขนาดไฟล์รวมทันที
     - หากผ่าน ให้แสดง Progress Bar และสร้าง `XMLHttpRequest` ส่ง `FormData` ไปยัง Endpoint อัปโหลด
     - ผูก `xhr.upload.onprogress` คำนวณเปอร์เซ็นต์ `Math.round((e.loaded / e.total) * 100)` และอัปเดตความกว้างหลอด
     - เมื่อเสร็จสิ้น (`xhr.onload`): แสดงสถานะ "อัปโหลดสำเร็จ" และทำการรีเฟรชหน้าเพื่อแสดงรายการไฟล์ที่เพิ่งอัปโหลดอย่างสมบูรณ์

---

## 3. แผนการดำเนินงาน (Task Breakdown)

### Phase 1: Backend Validation Updates (`backend-specialist`)
- **ไฟล์:** `HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicApplicantController.java`
- ปลดเงื่อนไข `activeAttachments == 0` ใน `submitDocument2`
- ปลดเงื่อนไข `countAttachments(id) == 0` ใน `submitAcademicRequest`

### Phase 2: Frontend UI & Auto-Upload Script (`frontend-specialist`)
- **ไฟล์:** `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_2_form.html`
- เปลี่ยน Badge และปรับปรุง JS validation `validateDoc2` ให้การแนบไฟล์/ลิงก์เป็นทางเลือก (Optional)
- เพิ่ม HTML Component สำหรับ Upload Progress Bar
- เขียนสคริปต์ XHR Auto-Upload ผูกกับ `change` และ `drop` events พร้อมการแสดงหลอดความคืบหน้า 0-100%

### Phase 3: Automated Tests & Verification (`project-planner` / `testing-patterns`)
- **ไฟล์:** `HRCP-KKU-Academic/src/test/java/com/ecom/academic/RequiredAttachmentNoticeTest.java`
- ปรับปรุง Test Case จากเดิมที่เคยตรวจสอบว่า "ต้องบังคับแนบไฟล์" ให้เป็น "สามารถบันทึกและยื่นคำร้องได้โดยไม่ต้องมีไฟล์แนบ"
- ตรวจสอบว่าชุดทดสอบทั้งหมดรันผ่าน 100%

---

## 4. แผนการทดสอบและการตรวจสอบ (Verification Plan)

1. **Automated Tests:**
   ```bash
   ./mvnw test -Dtest=RequiredAttachmentNoticeTest,PositionRankRuleTest
   ```
2. **Manual Verification:**
   - ทดสอบเปิดเอกสารที่ 2 โดยไม่แนบไฟล์ใดๆ → ติ๊กถูกครบ 5 ข้อ → ปุ่ม "บันทึกเอกสาร" ต้องเปิดใช้งานได้ และสามารถบันทึกได้สำเร็จ
   - ทดสอบยื่นคำร้อง (Submit) จากหน้า New Request โดยไม่มีไฟล์แนบ → คำร้องต้องสามารถเปลี่ยนสถานะเป็น RECEIVED (ยื่นสำเร็จ) ได้
   - ทดสอบเลือกไฟล์ในเอกสารที่ 2 → ระบบต้องเริ่มอัปโหลดทันทีโดยไม่ต้องกดปุ่ม → หลอด Progress Bar ต้องวิ่งแสดงเปอร์เซ็นต์อย่างราบรื่น และแสดงไฟล์ที่อัปโหลดเสร็จเมื่อสิ้นสุด
