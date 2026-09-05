# แผนงาน (Project Plan): รวมการแนบไฟล์ขอประเมินการสอนเป็นช่องเดียว (Unified Single Attachment)

**รหัสเอกสาร:** `docs/PLAN-single-eval-attachment.md`  
**สถานะ:** ดำเนินการเสร็จสมบูรณ์และผ่านการทดสอบแล้ว (Implemented & Verified)  
**Agent:** `project-planner`  

---

## 1. ข้อมูลและวัตถุประสงค์ (Overview & Context)

ปัจจุบันในขั้นตอนการยื่นคำร้องขอประเมินผลการสอน (Academic Request — เอกสารที่ 1 แบบตรวจสอบเบื้องต้น):
- หน้ากรอกฟอร์มของผู้ยื่น (`document_1_form.html`) มีช่องสำหรับอัปโหลดไฟล์และแนบลิงก์แยกออกเป็น **5 ช่องตามหัวข้อแบบตรวจสอบ (Slots 1–5)**
- ระบบบังคับว่าผู้ยื่นต้องแนบไฟล์หรือลิงก์**ให้ครบทั้ง 5 ช่อง** มิฉะนั้นปุ่มบันทึกเอกสารจะไม่ทำงาน และเซิร์ฟเวอร์จะปฏิเสธคำร้อง
- เจ้าหน้าที่/ผู้ดูแลระบบที่เข้ามาตรวจเอกสาร (`doc_fragments/1.html`) ก็แสดงผลไฟล์แยกเป็น 5 ช่องตามลำดับ

**ความต้องการของผู้ใช้งาน:**
> *"การแนบไฟล์ขอประเมินการสอนก่อนยื่นเปลี่ยนมาให้แนบช่องเดียวไม่ต้องแยก 5 แล้ว"*

ปรับเปลี่ยนรูปแบบการแนบเอกสารประกอบการประเมินการสอนจากเดิมที่แยก 5 ช่อง **ให้เหลือเพียงช่องเดียว (Single Unified Upload Box)** ที่รองรับการอัปโหลดหลายไฟล์ (Multiple files) และแนบลิงก์ภายนอกได้ในจุดเดียว เพื่อความสะดวกรวดเร็วและลดความซับซ้อนของผู้ขอรับการประเมิน

---

## 2. การเปรียบเทียบสถาปัตยกรรม (Current vs. Proposed Architecture)

```
[เดิม (Current State - 5 Slots)]
+--------------------------------------------------------------------+
| ตารางเช็คลิสต์ 5 ข้อ (ผู้ยื่นติ๊ก ✓ รับรองเอกสาร 5 รายการ)             |
+--------------------------------------------------------------------+
| ช่องที่ 1: บันทึกนำส่ง       [Dropzone 1] [Form อัปโหลด 1] [ลิงก์ 1]  |
| ช่องที่ 2: แผนการสอน         [Dropzone 2] [Form อัปโหลด 2] [ลิงก์ 2]  |
| ช่องที่ 3: สาระสำคัญเนื้อหา   [Dropzone 3] [Form อัปโหลด 3] [ลิงก์ 3]  |
| ช่องที่ 4: สื่อการสอน        [Dropzone 4] [Form อัปโหลด 4] [ลิงก์ 4]  |
| ช่องที่ 5: ผลประเมินนักศึกษา [Dropzone 5] [Form อัปโหลด 5] [ลิงก์ 5]  |
+--------------------------------------------------------------------+
* Validation: ต้องมีไฟล์/ลิงก์อย่างน้อย 1 รายการในทุกช่อง (1-5)

                                 │
                                 ▼ เปลี่ยนเป็น

[ใหม่ (Proposed State - Single Unified Box)]
+--------------------------------------------------------------------+
| ตารางเช็คลิสต์ 5 ข้อ (ผู้ยื่นยังคงติ๊ก ✓ รับรองเอกสารทั้ง 5 ข้อตาม มข.)  |
+--------------------------------------------------------------------+
| เอกสารแนบประกอบการประเมินผลการสอน (ช่องเดียว รวมศูนย์)                |
|  [ สลับโหมด: 📁 อัปโหลดไฟล์ | 🔗 แนบลิงก์ภายนอก ]                    |
|  [ Dropzone ขนาดใหญ่: ลากไฟล์มาวาง หรือคลิกเลือก (หลายไฟล์พร้อมกัน) ] |
|  [ หรือ ฟอร์มระบุ URL คลาวด์ เช่น Google Drive / OneDrive / Cloud ]   |
|                                                                    |
|  รายการเอกสารที่แนบแล้ว (Unified File & Link List)                   |
|  • ไฟล์ที่ 1 (PDF)  - 12.5 MB [ดู] [ดาวน์โหลด] [ลบ]                |
|  • ไฟล์ที่ 2 (ZIP)  - 34.0 MB      [ดาวน์โหลด] [ลบ]                |
|  • ลิงก์ที่ 1 (LINK) - Google Drive [เปิดลิงก์] [ลบ]                 |
|  (ขนาดรวม: 46.5 / 75.0 MB)                                         |
+--------------------------------------------------------------------+
* Validation: แนบไฟล์หรือลิงก์อย่างน้อย 1 รายการ และขนาดรวมไม่เกิน 75 MB
```

---

## 3. รายละเอียดการเปลี่ยนแปลงแต่ละส่วน (Proposed Changes)

### 3.1 ฝั่งผู้ยื่นคำร้อง (Applicant View & Form)
- **ไฟล์เป้าหมาย:** `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_1_form.html`
- **การเปลี่ยนแปลง:**
  1. **ยุบ UI แนบไฟล์:** นำ Loop แสดง 5 ช่องเดิมออก แล้วแทนที่ด้วยกล่องแนบเอกสารเดี่ยว (Card: `เอกสารแนบประกอบการประเมินผลการสอน`)
  2. **Unified Dropzone & Link Form:**
     - มีแท็บสลับระหว่าง "อัปโหลดไฟล์" และ "แนบลิงก์ (URL)"
     - Dropzone รองรับ Drag & Drop, Multi-file selection, นามสกุล `.pdf, .docx, .doc, .zip`
     - แสดงรายการไฟล์ที่เลือกเตรียมอัปโหลด (Queue) พร้อมขนาดรวม
     - Endpoint Action ชี้ไปที่ `/user/academic/request/{id}/document-1/attachments`
     - Endpoint Link ชี้ไปที่ `/user/academic/request/{id}/document-1/links`
  3. **ตารางแสดงรายการเอกสารแนบรวม:** แสดงไฟล์และลิงก์ทั้งหมดของคำร้องในตาราง/การ์ดเดียว มีไอคอนแยกประเภทไฟล์ (PDF, Word, ZIP, Cloud Link), ขนาดไฟล์, ปุ่มเปิดดู (ยกเว้น ZIP), ปุ่มดาวน์โหลด, ปุ่มเปิดลิงก์ภายนอก และปุ่มลบ
  4. **JavaScript Validation:**
     - ปรับฟังก์ชัน `validateDoc1()`:
       - ตรวจสอบการติ๊ก Checklist 5 ข้อ (ยังคงต้องติ๊กครบ 5 ข้อตามแบบฟอร์ม มข.1607-66)
       - ตรวจสอบไฟล์แนบ: ขอเพียงมีเอกสารแนบอย่างน้อย 1 รายการ (`attachmentCount >= 1`)
       - ตรวจสอบขนาดไฟล์รวมไม่เกิน 75 MB (`totalBytes <= 75 * 1024 * 1024`)
       - อัปเดตกล่องสถานะ Alert เตือนอย่างชัดเจนและเปิดให้กดปุ่ม "บันทึกเอกสาร" เมื่อผ่านเงื่อนไข

### 3.2 ฝั่งเจ้าหน้าที่ / ผู้ดูแลระบบ (Admin View Fragment)
- **ไฟล์เป้าหมาย:** `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/doc_fragments/1.html`
- **การเปลี่ยนแปลง:**
  1. นำโครงสร้างที่แยกตารางเป็น 5 ช่อง (`slotLabels`) ออก
  2. ปรับเป็นตารางหรือรายการเอกสารแนบแบบรวมศูนย์ (Single Attachment List) แสดงชื่อไฟล์/ลิงก์, ประเภท, ขนาด, วันเวลาที่อัปโหลด, และปุ่มเปิดดู/ดาวน์โหลด/เปิดลิงก์
  3. รองรับเอกสารเดิมที่มี `checklistItem` บันทึกไว้ โดยแสดงผลได้อย่างกลมกลืน

### 3.3 ฝั่ง Backend Controller & Validation
- **ไฟล์เป้าหมาย:** `HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicApplicantController.java`
- **การเปลี่ยนแปลง:**
  1. **`showDocument1` (GET):**
     - ส่งรายการเอกสารทั้งหมด (`attachments = requestService.getAttachments(id)` หรือ `getActiveAttachments`)
     - ส่งขนาดรวม `totalAttachmentSize` และจำนวนไฟล์รวม `attachmentCount` ไปยัง Model
     - ยังคงคำนวณ `attachmentsBySlot` ไว้เป็น fallback เพื่อไม่ให้เกิด NullPointerException กับจุดอ้างอิงเดิม
  2. **`uploadDocument1Attachments` (POST):**
     - ช่องทางการอัปโหลดสามารถเรียกได้ทั้ง `/request/{id}/document-1/attachments` (โดยไม่ต้องส่ง `{slot}`)
     - เมื่อผู้ใช้อัปโหลด ให้กำหนด `checklistItem = 1` หรือปล่อยเป็น default
     - ตรวจสอบขนาดไฟล์รวมของคำร้อง (`currentTotalSize + file.getSize() <= 75 MB`)
  3. **`attachDocument1Link` (POST):**
     - เรียกใช้งานผ่าน `/request/{id}/document-1/links` โดย `checklistItem` ตั้งเป็น default
  4. **`submitDocument1` (POST):**
     - **ยกเลิก** เงื่อนไขการตรวจสอบ `missingSlots` ที่บังคับให้ต้องมีไฟล์ครบทั้ง 5 ช่อง
     - **เปลี่ยนเป็น:** ตรวจสอบว่ามีเอกสารแนบ (ไฟล์หรือลิงก์) อย่างน้อย 1 รายการ:
       ```java
       long totalAttachments = requestService.countActiveAttachments(id);
       long totalSize = requestService.getTotalAttachmentSize(id);
       final long MAX_TOTAL_BYTES = 75L * 1024L * 1024L;

       if (totalAttachments == 0) {
           redirectAttributes.addFlashAttribute("error", "กรุณาแนบไฟล์หรือลิงก์เอกสารประกอบอย่างน้อย 1 รายการก่อนบันทึก");
           return "redirect:/user/academic/request/" + id + "/document-1?error=no_attachments";
       }
       if (totalSize > MAX_TOTAL_BYTES) {
           redirectAttributes.addFlashAttribute("error", "ขนาดไฟล์แนบรวมทั้งหมดเกิน 75 MB");
           return "redirect:/user/academic/request/" + id + "/document-1";
       }
       ```

### 3.4 ฐานข้อมูลและการเข้ากันได้ย้อนหลัง (Database & Backward Compatibility)
- ฟิลด์ `checklistItem` ใน Entity `AcademicAttachment` และตาราง `academic_attachments` **ยังคงเก็บไว้ตามเดิม** (ไม่ต้อง Drop Column หรือแก้ DB Migration)
- คำร้องเดิมที่มีการบันทึกไฟล์ในช่อง 1–5 ไว้แล้ว ข้อมูลจะไม่สูญหายและจะถูกดึงมาแสดงผลในช่องเดี่ยวใหม่อย่างต่อเนื่อง 100%

---

## 4. สรุปผลการยืนยันของผู้ใช้งาน (User Confirmations)
- **ประเด็นที่ 1 (Validation Requirement):** **ใช่ (ยืนยัน)** — คงการติ๊กรับรองครบ 5 ข้อตามแบบฟอร์มทางการของ มข. และบังคับแนบไฟล์หรือลิงก์อย่างน้อย 1 รายการก่อนบันทึก
- **ประเด็นที่ 2 (Total Quota Limit):** **ใช่ (ยืนยัน)** — คงขีดจำกัดขนาดไฟล์รวมไว้ที่ 75 MB ต่อคำร้อง รองรับไฟล์ `.pdf, .docx, .doc, .zip`
- **ประเด็นที่ 3 (Legacy Data Display):** **ใช่ (ยืนยัน)** — ข้อมูลเดิมที่เคยอัปโหลดแยกช่องไว้ นำมาแสดงผลรวมเป็นรายการเดียวทั้งหมดอย่างต่อเนื่อง
- **ฟังก์ชันแนบลิงก์ (URL Attachment):** **คงไว้ครบถ้วน 100%** — ไม่ได้ตัดออก มีแท็บ "แนบลิงก์ (URL)" รองรับ Google Drive, OneDrive, Cloud URL พร้อมปุ่มเปิดลิงก์และลบลิงก์ตามปกติ

---

## 5. แผนการทดสอบและผลการตรวจสอบ (Verification Results)

### Automated Tests
1. **`AttachmentConstraintsTest.java`:**
   - ทดสอบกรณีไม่มีเอกสารแนบเลย -> ปฏิเสธ (`submitDoc1_missingSlots_shouldBeRejected`) ✅ ผ่าน
   - ทดสอบกรณีมีเอกสารแนบและขนาดไม่เกิน 75 MB -> บันทึกสำเร็จ (`submitDoc1_all5SlotsPresent_shouldSucceed`) ✅ ผ่าน
   - ทดสอบกรณีขนาดรวมเกิน 75 MB -> ปฏิเสธ (`submitDoc1_slotOver75MB_shouldBeRejected`) ✅ ผ่าน
   - ทดสอบการแนบลิงก์และตรวจสอบสถานะ (`submitDoc1_withLinkAttachments_shouldSucceed`) ✅ ผ่าน
   - ทดสอบการเปิดลิงก์และลบลิงก์ (`viewAndDownloadAttachment_linkType`, `deleteAttachment_linkAttachment`) ✅ ผ่าน
   - **ผลลัพธ์:** ผ่านครบทั้ง 13/13 tests (0 Failures, 0 Errors)
2. **`UAT_AcademicApplicantTest.java`:**
   - ทดสอบ Journey ทั้งหมดของผู้ยื่นคำร้อง รวมถึงการกรอกเอกสารที่ 1 (Document 1)
   - **ผลลัพธ์:** ผ่านครบทั้ง 27/27 tests (0 Failures, 0 Errors)

---

## 6. ลำดับขั้นตอนการพัฒนา (Execution Steps)

- [x] **Step 1:** ปรับปรุง Backend Controller (`AcademicApplicantController.java` & `AcademicAdminController.java`) เพื่อปลดล็อกเงื่อนไข 5 ช่อง และรองรับการบันทึกแบบ Single Attachment Quota
- [x] **Step 2:** ปรับปรุงหน้าจอผู้ยื่น (`document_1_form.html`) ให้เป็นกล่องแนบไฟล์ช่องเดียว พร้อมปุ่มสลับแท็บอัปโหลดไฟล์/แนบลิงก์ และ JavaScript Validation ใหม่
- [x] **Step 3:** ปรับปรุงหน้าจอแอดมิน (`academic/admin/doc_fragments/1.html`) ให้แสดงรายการไฟล์แนบแบบรวมศูนย์
- [x] **Step 4:** ปรับปรุง Unit & Integration Tests ใน `AttachmentConstraintsTest.java`
- [x] **Step 5:** รัน `./mvnw test` เพื่อตรวจสอบความถูกต้องทั้งหมด (40 tests run, 0 failures)
