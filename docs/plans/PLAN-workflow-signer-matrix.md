# แผนการพัฒนา: ระบบตั้งค่าผู้ลงนามและลำดับขั้นตอนการลงนาม (Document Workflow & Signer Matrix Settings)

##  1. ภาพรวมและวัตถุประสงค์ (Overview & Goals)
พัฒนาหน้าตั้งค่า **Document Signer & Workflow Configuration** ในเมนูผู้ดูแลระบบ (`/admin/academic/settings/signers`) เพื่อให้แอดมินสามารถ:
1. ปรับแต่งลำดับขั้นตอนการลงนาม (Signer Order / Sequence 1 $\rightarrow$ 2 $\rightarrow$ 3) ของเอกสารทั้ง 17 ฉบับ
2. เปิด/ปิดการลงนามในแต่ละตำแหน่งของเอกสารนั้น (Enable/Disable Slots)
3. กำหนดบุคคลเริ่มต้น (Default Signer) ประจำแต่ละตำแหน่ง (เช่น คณบดี, รองคณบดี, เจ้าหน้าที่ HR) โดยดึงจากตาราง `StaffMember` ที่ผูกบัญชีไว้แล้ว
4. เชื่อมโยงการตั้งค่าเข้ากับระบบเวียนลงนามอัตโนมัติ (Dynamic Envelope Initiation)

---

## ️ 2. โครงสร้างสถาปัตยกรรม (Architecture & Data Model)

### 2.1 Database Migration: `V8__create_document_workflow_config.sql`
- สร้างตาราง `document_workflow_config`:
  - `id` (PK)
  - `module` (VARCHAR: `ACADEMIC` / `POSITION`)
  - `document_type` (INT)
  - `slot_key` (VARCHAR: e.g. `applicant`, `hr`, `head`, `dean`)
  - `role_label` (VARCHAR)
  - `anchor_placeholder` (VARCHAR)
  - `default_staff_role` (VARCHAR)
  - `step_order` (INT)
  - `is_enabled` (BOOLEAN)
  - `default_signer_user_id` (FK to `user_dtls`)
  - `created_at`, `updated_at`
  - `UNIQUE (module, document_type, slot_key)`

### 2.2 Entity & Repository
- `DocumentWorkflowConfig.java`: JPA Entity
- `DocumentWorkflowConfigRepository.java`: Spring Data JPA Repository พร้อมเมธอดค้นหาตาม `module` และ `documentType`

### 2.3 Service Layer
- `DocumentWorkflowConfigService.java`:
  - `getEffectiveSlots(module, docType)`: คืนค่า SignatureSlot ตามการตั้งค่าใน DB (fallback ไปที่ `SignatureAnchorRegistry` หากยังไม่ได้ตั้งค่า)
  - `getDefaultSigners(module, docType)`: คืนค่า Map ของ `slotKey -> userId`
  - `saveConfigs(List<ConfigDTO>)`: บันทึกการเปลี่ยนแปลงจากหน้า UI
  - `resetToDefaults(module, docType)`: รีเซ็ตกลับเป็นค่าเริ่มต้นของเทมเพลต

### 2.4 Controller & UI Layer
- `DocumentWorkflowConfigController.java`:
  - `GET /admin/academic/settings/signers`: แสดงหน้าตั้งค่า
  - `POST /admin/academic/settings/signers`: รับข้อมูลฟอร์มและบันทึก
- `templates/academic/admin/signer_settings.html`:
  - แท็บแยก Phase 1 (ผลการสอน 8 ฉบับ) และ Phase 2 (ตำแหน่งวิชาการ 9 ฉบับ)
  - ตารางสรุป Slot, สวิตช์ On/Off, ตัวเลือก Order (1, 2, 3...), และดรอปดาวน์เลือก Default Signer จากบุคลากร
- เมนูนำทางใน `base_academic.html`: เพิ่มลิงก์ "ตั้งค่าผู้ลงนามเอกสาร" ในแถบเมนูแอดมิน

---

## [Test] 3. แผนการทดสอบ (Verification Plan)
1. **Unit & Integration Tests:**
   - ทดสอบ `DocumentWorkflowConfigServiceTest` (การดึง Effective Slots, การบันทึก, การ Fallback)
   - ทดสอบ `DocumentWorkflowConfigControllerTest` (การเข้าถึงหน้าตั้งค่าและการบันทึก)
   - ทดสอบ `SignatureWorkflowServiceTest` (ยืนยันว่าการสร้าง Envelope ใช้งานลำดับและคนเซ็นที่ตั้งค่าไว้จริง)
2. **Full Regression Suite:**
   - รัน `./mvnw test` ทุกโมดูลต้องผ่าน 100%
