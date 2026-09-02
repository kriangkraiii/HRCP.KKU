# แผนการพัฒนา: ปรับปรุงเทมเพลตเอกสาร และระบบลำดับขั้นตอนการลงนาม (Signer Workflow & Dynamic Signers)

##  ภาพรวมและปัญหา (Context & Problem)
1. **ชื่อ Hardcoded ในเอกสาร [doc_1.docx](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/docx/doc_1.docx):**
   - ในเทมเพลตเดิมมีชื่อ `(นางสาวจิราภรณ์  หอมอ่อน)` ฝังตายตัวในช่อง "นักทรัพยากรบุคคล" ทำให้ไม่สามารถเปลี่ยนชื่อตามเจ้าหน้าที่ HR ผู้รับผิดชอบจริงได้ และใน `SignatureAnchorRegistry.java` ยังไม่ได้เปิดช่อง (Slot) ให้เจ้าหน้าที่ HR ลงนามในเอกสารฉบับนี้
2. **ลำดับขั้นการลงนาม (Sequential Signer Workflow):**
   - ผู้ยื่น (Applicant) ลงนามในส่วนของตนเองให้ครบทุกเอกสารก่อนส่งคำร้อง
   - เมื่อยื่นเสร็จ Admin / นักทรัพยากรบุคคล (HR) เข้ามาตรวจสอบและลงนามในส่วนของเจ้าหน้าที่
   - ส่งต่อไปยังหัวหน้าสาขาวิชา (Head) $\rightarrow$ คณบดี (Dean) ตามลำดับขั้น
   - หากมีการ "ปฏิเสธการลงนาม (Decline)" ในขั้นตอนใด กระบวนการจะหยุดลงทันที และแจ้งเตือนตีกลับไปยังผู้ส่งเอกสารโดยไม่ส่งต่อไปยังผู้บริหารลำดับถัดไป
3. **การเลือกผู้ลงนามแบบไดนามิก (Dynamic Default Signers):**
   - รองรับการเปลี่ยนตัวคณบดี, รองคณบดี, หรือเจ้าหน้าที่ HR โดยดึงจากตาราง `StaffMember` ที่ผูกบัญชีไว้แล้วโดยอัตโนมัติ

---

##  เป้าหมาย (Objectives)
1. **แก้ไขเทมเพลต `doc_1.docx`:** เปลี่ยนข้อความ Hardcoded `(นางสาวจิราภรณ์  หอมอ่อน)` ให้เป็น Dynamic Placeholder `({{hr_staff_name}})`
2. **อัปเดต Signature Registry:** เพิ่ม Signature Slot ที่ 2 ให้ Doc 1 (`hr`, "นักทรัพยากรบุคคล", `hr_staff_name`, "HR", order 2)
3. **แมปข้อมูลและตัวเลือกผู้ลงนามใน `DocumentGenerationService.java`:** รองรับการเติม `hr_staff_name` อัตโนมัติ
4. **ยืนยันและเสริมความแข็งแกร่งของ Sequential Gate ใน `SignatureWorkflowService.java`:** ลำดับการเซ็น 1 -> 2 -> 3 -> 4 และหยุดทันทีเมื่อถูกปฏิเสธ (Decline)

---

##  รายละเอียดการแก้ไขโค้ด (Detailed Technical Tasks)

### 1. Template Layer: `src/main/resources/templates/docx/doc_1.docx`
- แก้ไข `word/document.xml` ในไฟล์ `doc_1.docx`:
  - แทนที่ `(นางสาวจิราภรณ์  หอมอ่อน)` ด้วย `({{hr_staff_name}})`

### 2. Service Layer: `SignatureAnchorRegistry.java`
- อัปเดต `DocKey(SignatureModule.ACADEMIC, 1)`:
  - Slot 1: `applicant` (ผู้ขอรับการประเมิน, `applicant_name`, APPLICANT, order 1)
  - Slot 2: `hr` (นักทรัพยากรบุคคล, `hr_staff_name`, "HR", order 2)

### 3. Service Layer: `DocumentGenerationService.java`
- ในการสร้าง Doc 1 (`generateSignedDocx` / `generatePreviewDocx`):
  - ตรวจสอบ `hr_staff_name` หากเว้นว่าง ให้ดึงชื่อจาก Staff Member ที่มี Role `HR` หรือ Admin ผู้ตรวจสอบ

### 4. Workflow Layer: `SignatureWorkflowService.java`
- ยืนยันการทำงานของ `activateNextStep()`: Step 2 จะไม่ถูกเปิดให้เซ็นจนกว่า Step 1 จะลงนามเสร็จ
- ยืนยันการทำงานของ `decline()`: ยกเลิก Envelope ทันทีและส่ง Notification ตีกลับ

---

## [Test] แผนการทดสอบ (Verification Plan)
1. **Unit Tests:**
   - ทดสอบ `SignatureAnchorRegistryTest` (ยืนยัน Slot ของ Doc 1 มีทั้ง Applicant และ HR)
   - ทดสอบ `SignatureWorkflowServiceTest` (ยืนยัน Sequential Step Activation และ Decline Termination)
2. **Integration & Document Generation Test:**
   - ทดสอบสร้าง Doc 1 และ Doc 1 Signed ด้วย `DocumentGenerationService`
   - รัน `./mvnw test`
