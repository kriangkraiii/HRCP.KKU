# แผนการพัฒนา: ระบบจัดการเอกสารเลยกำหนด & ประสบการณ์การลงนามต่อเนื่อง (Overdue Management & Continuous Signing UX)

## 📌 1. ภาพรวมและวัตถุประสงค์ (Overview & Goals)
พัฒนาและยกระดับระบบการลงนามอิเล็กทรอนิกส์ตามแนวทาง **Option B + แนวทางที่ 2**:
1. **การจัดการเอกสารเลยกำหนด (Overdue Management - Option B):**
   - ผู้ลงนามสามารถกด **"ขอขยายเวลาลงนาม"** ส่งคำขอพร้อมเหตุผลไปยังผู้ยื่น/แอดมิน
   - แอดมินและผู้ยื่นสามารถกด **"แก้ไข/ขยายกำหนดวันลงนาม (Extend Due Date)"** บน Envelope ได้
2. **ระบบลงนามต่อเนื่อง (Continuous Signing Flow - แนวทางที่ 2):**
   - เมื่อเซ็นฉบับที่ 1 เสร็จ ระบบจะตรวจหาเอกสารฉบับถัดไปในคิวของผู้ใช้ และนำไปยังหน้าเซ็นฉบับถัดไปทันที (พร้อมแสดง Progress เช่น *ลงนามแล้ว 1/3 ฉบับ*)
3. **ยกระดับ UI/UX หน้ารอลงนาม (/esign/inbox):**
   - การ์ดสรุปสถานะ (KPI Overview): เอกสารรอลงนามทั้งหมด, ปกติ, เลยกำหนด
   - ค้นหาและตัวกรองตามชื่อเอกสาร / ประเภทคำร้อง / สถานะ
   - ปุ่ม **"⚡ เริ่มลงนามต่อเนื่องทั้งหมด"** สำหรับผู้บริหารที่มีเอกสารค้างเซ็นหลายฉบับ

---

## 🏗️ 2. สถาปัตยกรรมและการเปลี่ยนแปลง (Architecture & Implementation)

### 2.1 Backend / Service Layer
- **`SignatureWorkflowService.java`:**
  - `extendDueDate(Long envelopeId, LocalDateTime newDueAt, UserDtls actor)`: ขยายเวลากำหนดลงนาม
  - `requestExtension(Long stepId, String reason, UserDtls signer)`: แจ้งเตือนผู้ส่งคำร้องเพื่อขอขยายเวลา
  - `findNextPendingStep(UserDtls signer, Long currentStepId)`: ค้นหาเอกสารฉบับถัดไปที่ถึงคิวลงนามของบุคคลนี้

### 2.2 Controller Layer
- **`SigningController.java`:**
  - ปรับปรุง `POST /esign/sign/{stepId}`: หากมีเอกสารฉบับถัดไปในคิวของผู้ลงนาม จะส่ง Redirect ไปยังฉบับถัดไปพร้อมแจ้งความคืบหน้า
  - เพิ่ม `POST /esign/step/{stepId}/request-extension`: สำหรับส่งคำขอขยายเวลา
  - เพิ่ม `POST /esign/envelope/{id}/extend-due`: สำหรับผู้ส่ง/แอดมินปรับ Due Date

### 2.3 UI / View Layer
- **`src/main/resources/templates/academic/esign/inbox.html`:**
  - ปรับโฉมเป็น UI/UX แบบ Pro Max: KPI Summary Cards, Search Bar, Status Badges, Fast Action Buttons
  - Modal "ขอขยายเวลาลงนาม"
- **`src/main/resources/templates/academic/esign/sign.html`:**
  - แถบ Progress แสดงจำนวนเอกสารที่รอเซ็นของบุคคลนี้ (เช่น *ฉบับที่ 1 จาก 3 ฉบับ*)
  - ป้ายแจ้งเตือนเมื่อเอกสารเลยกำหนด พร้อมปุ่มขอขยายเวลา

---

## 🧪 3. แผนการทดสอบ (Verification Plan)
1. **Unit & Integration Tests:**
   - ทดสอบการขยาย Due Date และการแจ้งเตือน
   - ทดสอบการหา `findNextPendingStep` เมื่อมีเอกสารค้างหลายฉบับ
2. **Full Regression Suite:**
   - รัน `./mvnw test` ทุกโมดูลต้องผ่าน 100%
