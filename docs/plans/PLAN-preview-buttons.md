# PLAN: เพิ่มปุ่มดูตัวอย่างเอกสาร (Document Preview Buttons) ทั่วทั้งระบบ

> **เป้าหมาย:** เพิ่มปุ่ม "ดูตัวอย่าง" (Preview) ให้ครอบคลุมทุกจุดจัดการเอกสาร ทั้งฝั่งผู้ดูแลระบบ (Admin) และผู้ยื่นคำร้อง (Applicant) โดยจัดวางปุ่มไว้ข้างๆ ปุ่ม "กรอก/แก้ไขเอกสาร" และในตารางรายการเอกสารทั้งหมด พร้อมระบบ PDF Preview Modal ที่สวยงามและเป็นมาตรฐานเดียวกันทั้งโครงการ

---

##  1. การวิเคราะห์จุดที่มีการจัดการเอกสารในระบบ (Scope & Locations)

### ก. ฝั่งผู้ดูแลระบบ (Admin)
1. **คำร้องประเมินการสอน (Teaching Evaluation - Phase 1)**
   - ไฟล์: `templates/academic/admin/request_detail.html`
   - **ส่วน "จัดการเอกสาร (8 แบบฟอร์ม)"**:
     - เพิ่มปุ่ม `[  ดูตัวอย่าง ]` ข้างปุ่ม `[  กรอก/แก้ไข ]` ในแต่ละแถวเอกสารหลัก (เมื่อมีข้อมูล/เอกสารถูกสร้างแล้ว)
     - เพิ่มปุ่ม `[  ดูตัวอย่าง ]` ในรายการสำเนาเอกสารย่อย (`doc-grid-sub`) ด้านข้างปุ่ม `[ DOCX ]` และ `[ PDF ]`
2. **คำร้องขอกำหนดตำแหน่งทางวิชาการ (Academic Position - Phase 2)**
   - ไฟล์: `templates/academic/position/admin/request_detail.html`
   - **ส่วน "จัดการเอกสาร (9 แบบฟอร์ม)"**:
     - เพิ่มปุ่ม `[  ดูตัวอย่าง ]` ข้างปุ่ม `[  กรอก/แก้ไข ]` ของเอกสารแต่ละชุด (เมื่อเอกสารถูกกรอกแล้ว `completedDocs.contains(key)`)

---

### ข. ฝั่งผู้ยื่นคำร้อง (Applicant)
1. **คำร้องประเมินการสอน (Teaching Evaluation - Phase 1)**
   - ไฟล์: `templates/academic/applicant/request_detail.html`
   - **ส่วน "เอกสารของฉัน"**:
     - ตรวจสอบให้ทุกรายการเอกสาร (ตั้งแต่เอกสารที่ 0 ถึง 8) มีปุ่ม `[  ดูตัวอย่าง ]` คู่กับปุ่ม `[ DOCX ]` และ `[ PDF ]` ครบถ้วน
2. **คำร้องขอกำหนดตำแหน่งทางวิชาการ (Academic Position - Phase 2)**
   - ไฟล์: `templates/academic/position/applicant/request_detail.html`
   - **ส่วนการ์ดเอกสาร**:
     - ตรวจสอบปุ่ม `[  ดูตัวอย่าง ]` ข้างปุ่ม `[  แก้ไขเอกสาร ]` ให้เป็นสไตล์มาตรฐานเดียวกัน

---

### ค. ระบบแสดงตัวอย่างเอกสารแบบ Modal (Universal PDF Preview Modal)
- ไฟล์: `templates/academic/base_academic.html` หรือคอมโพเนนต์ส่วนกลาง
- รองรับการเปิดดูตัวอย่าง PDF ทันทีในหน้าต่าง Modal โดยไม่ต้องดาวน์โหลดไฟล์ออกมา
- มีปุ่มปิด, ปุ่มขยายเต็มจอ (Full Screen) และปุ่มดาวน์โหลด/สั่งพิมพ์ในตัว Modal

---

##  2. รูปแบบดีไซน์ของปุ่ม (Design & Styling)
- ใช้สไตล์ **Outline Button** สีฟ้าหรือสีเขียวสอดคล้องกับธีม:
  ```html
  <button type="button" class="btn btn-sm btn-outline-info btn-doc-preview" 
          th:attr="data-preview-url=@{...},data-doc-title=${...}">
      <i class="fas fa-eye me-1"></i> ดูตัวอย่าง
  </button>
  ```
- วางไว้เคียงข้างปุ่ม `[  กรอก/แก้ไข ]` อย่างเป็นระเบียบ สบายตา
- รองรับทั้ง Light Theme และ Dark Theme

---

##  3. แผนการดำเนินงาน (Task Breakdown)

- [ ] **Phase 1: สร้าง Universal PDF Preview Modal และ Script ใน base_academic.html**
  - เพิ่มโครงสร้าง Modal กลาง `#globalDocPreviewModal` ที่มี iframe รองรับ PDF stream
  - เขียน Event Listener สากลสำหรับ `.btn-doc-preview` ทุกปุ่มในระบบ
- [ ] **Phase 2: ปรับปรุงหน้า Admin คำร้องประเมินการสอน (`academic/admin/request_detail.html`)**
  - เพิ่มปุ่มดูตัวอย่างข้างปุ่มกรอก/แก้ไขในหัวข้อเอกสารที่ 0-7
  - เพิ่มปุ่มดูตัวอย่างในแถบสำเนาเอกสาร
- [ ] **Phase 3: ปรับปรุงหน้า Admin คำร้องขอตำแหน่ง (`academic/position/admin/request_detail.html`)**
  - เพิ่มปุ่มดูตัวอย่างข้างปุ่มกรอก/แก้ไขในเอกสารที่ 0-9
- [ ] **Phase 4: ปรับปรุงหน้า Applicant (`academic/applicant/request_detail.html` & `academic/position/applicant/request_detail.html`)**
  - ตรวจสอบความสม่ำเสมอของปุ่มดูตัวอย่างในทุกเอกสาร
- [ ] **Phase 5: ทดสอบการทำงาน (Verification)**
  - ทดสอบกดปุ่มดูตัวอย่างทุกแบบฟอร์มใน Admin
  - ทดสอบกดปุ่มดูตัวอย่างในฝั่ง Applicant
  - ตรวจสอบความถูกต้องทั้ง Light Theme และ Dark Theme
