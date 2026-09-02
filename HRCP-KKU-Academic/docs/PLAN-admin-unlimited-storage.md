# Plan: Admin Unlimited Storage (ยกเลิกการจำกัดขนาดพื้นที่จัดเก็บสำหรับแอดมิน)

##  Context & Problem
- ปัจจุบันในระบบคลังเอกสาร (`AdminStorageService` และ `file_manager.html` / `user_storage.html`):
  1. มีการกำหนดค่า Default Max Storage เช่น `app.storage.admin.max-bytes = 10 GB` (และ `app.storage.user.max-bytes = 500 MB`)
  2. การ์ดแสดงผลสรุปพื้นที่ "พื้นที่ใช้งาน" (ดังภาพหน้าจอ) แสดงเพียงขนาดไฟล์ที่ใช้ไป (เช่น `1.0 MB`) แต่ในส่วนโควตาแสดงการจำกัดขนาด หรือแถบความจุที่คำนวณจากขีดจำกัด
  3. แอดมินระบุว่า: **"แอดมินไม่จำกัดขนาดที่เก็บนะ"** — สำหรับผู้ดูแลระบบ (Admin) ไม่ควรมีการจำกัดขนาดพื้นที่จัดเก็บ (Unlimited Storage) และ UI ควรแสดงผลให้ชัดเจนว่า "ไม่จำกัด"

---

##  Objectives
1. **Admin Quota Exemption / Unlimited Mode**:
   - ปรับ `AdminStorageService` ให้รองรับ Unlimited Storage (ไม่มีการโยน `IllegalStateException` บล็อกการอัปโหลดไฟล์ของ Admin)
   - หากผู้ใช้เป็น `ROLE_ADMIN` หรือเข้าใช้งานคลังไฟล์ฝั่งผู้ดูแลระบบ (`/admin/file-manager/storage`) ระบบจะไม่จำกัดโควตา
2. **UI Enhancement (แสดงผลชัดเจนและสวยงาม)**:
   - ในการ์ดแสดงผลพื้นที่ใช้งาน:
     - แสดงขนาดที่ใช้จริง (เช่น `1.0 MB`)
     - มีป้ายกำกับ **`ไม่จำกัด (Unlimited)`** หรือ **`ไม่จำกัดโควตา`** ชัดเจน แทนการแสดง `/ 10.0 GB` หรือเปอร์เซ็นต์ที่ต้องกังวลเรื่องพื้นที่เต็ม
     - ซ่อนหรือปรับแถบ Progress Bar ให้เหมาะสมกับสถานะ Unlimited
3. **Consistency across User & Admin Storage**:
   - หาก Admin ใช้งานในหน้า `/user/academic/storage` (คลังส่วนตัว) ให้ตรวจสอบสิทธิ์ หากเป็น Admin จะได้สิทธิ์ Unlimited เช่นกัน

---

##  Task Breakdown

### Phase 1: Backend Service & Logic Update
- [ ] **[MODIFY] `AdminStorageService.java`**:
  - อัปเดต `validateStorageQuota(long incomingBytes)` ให้ไม่บล็อกการอัปโหลดเมื่อเป็น Unlimited (หรือเมื่อ `maxStorageBytes <= 0`)
  - เพิ่มเมธอด `isUnlimited()` หรือปรับการคืนค่า `maxStorageBytes` / `remainingBytes` ให้สะท้อนสถานะ Unlimited
- [ ] **[MODIFY] `UserStorageService.java`**:
  - รองรับการตรวจสอบ Role ของเจ้าของไฟล์ หากเป็น Admin ให้ข้ามการตรวจสอบโควตา
- [ ] **[MODIFY] `FileManagerController.java` & `UserFileManagerController.java`**:
  - ส่งค่า `isUnlimited = true` และข้อความ `maxStorage = "ไม่จำกัด"` ไปยัง Model สำหรับ Admin

### Phase 2: UI & Template Update
- [ ] **[MODIFY] `file_manager.html` (Admin File Manager)**:
  - ปรับการ์ด "พื้นที่ใช้งาน":
    - แสดงขนาดที่ใช้ (เช่น `1.0 MB`)
    - แสดง Badge `<span class="badge bg-success-subtle text-success">ไม่จำกัด</span>`
    - ซ่อน Progress Bar หรือแสดงเป็นแถบ Infinity / Unlimited
- [ ] **[MODIFY] `user_storage.html` (User Storage)**:
  - ในกล่อง Storage Quota Bar: หาก `isUnlimited == true` ให้แสดง "ไม่จำกัด" และไม่แสดงแถบเตือนพื้นที่เต็ม

### Phase 3: Unit Testing & Verification
- [ ] อัปเดตและรัน Test Suite:
  - ทดสอบการอัปโหลดไฟล์ขนาดใหญ่ของ Admin โดยไม่ติด Quota Exception
  - ทดสอบ Controller Model Attributes (`isUnlimited`, `maxStorage`)
  - รัน `./mvnw test`

---

##  Socratic Gate & Open Questions for User
1. **ขอบเขตของ Unlimited Storage**:
   - ต้องการให้เป็น Unlimited เฉพาะ **คลังไฟล์ระบบแอดมิน (`/admin/file-manager`)** หรือรวมถึง **คลังไฟล์ส่วนตัว (`/user/academic/storage`) ของบัญชีที่เป็น Admin** ด้วยหรือไม่?
2. **รูปแบบการแสดงผล UI ที่ต้องการ**:
   - สำหรับการ์ด "พื้นที่ใช้งาน" ต้องการให้แสดงเป็น:
     - **แบบ ก:** แสดงขนาดที่ใช้ (เช่น `1.0 MB`) พร้อมป้ายกำกับ `(ไม่จำกัด)` ด้านข้าง
     - **แบบ ข:** แสดงขนาดที่ใช้ และด้านล่างเขียนว่า `พื้นที่ใช้งาน (ไม่จำกัดโควตา)`
