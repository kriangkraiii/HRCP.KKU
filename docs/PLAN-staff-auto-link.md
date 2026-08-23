# แผนการพัฒนา: ระบบ Auto-Link และ Auto-Role บุคลากร (Staff Member) กับบัญชีผู้ใช้ (UserDtls)

## 📌 ภาพรวมและปัญหา (Context & Problem)
1. **ปัญหาปัจจุบัน:** ในหน้าจัดการบุคลากร (`staff_list.html`) บุคลากรทุกคนแสดงสถานะ **"ยังไม่ผูก"** (Unlinked) ทำให้ไม่สามารถเลือกเป็นผู้ลงนามในระบบเอกสารอิเล็กทรอนิกส์ (E-Signature) ได้ ทั้งที่ข้อมูลในระบบ (`user_dtls` และ `fs_faculty` จากเว็บ `computing.kku.ac.th`) มีชื่อ-นามสกุล และอีเมล (`@kku.ac.th`) ตรงกัน 100%
2. **บทบาทเริ่มต้น:** ทุกคนถูกตั้งเป็น `GENERAL` ไม่ได้ดึงตำแหน่งบริหาร (`manage_position` เช่น คณบดี, รองคณบดี) มาตั้งเป็นบทบาท `DEAN`, `HEAD` โดยอัตโนมัติ

---

## 🎯 เป้าหมาย (Objectives)
- **Option A (Smart Auto-Linking & Auto-Role on Sync):** ปรับปรุง `StaffDirectorySync` ให้ค้นหาและผูก `UserDtls` อัตโนมัติด้วย Email และ ชื่อ-สกุล พร้อมแมป `manage_position` เป็นบทบาท `DEAN` / `HEAD` โดยอัตโนมัติ
- **Option B (One-Click Admin Auto-Link & Sync):** เพิ่มปุ่ม `⚡ ผูกบัญชีอัตโนมัติ` และ `🔄 ซิงค์ข้อมูลต้นทาง` ในหน้า `staff_list.html` ให้ผู้ดูแลระบบสามารถกด Re-link / Re-sync ได้ตลอดเวลาพร้อมสรุปผล

---

## 🛠️ รายละเอียดการเปลี่ยนแปลง (Detailed Technical Breakdown)

### 1. Backend Service: `StaffDirectorySync.java`
- **Auto-Link ในวงจร Sync:**
  - เมื่อสร้างหรืออัปเดต `StaffMember` จาก `FsFaculty`:
    - ค้นหา `UserDtls` ผ่าน `faculty.getEmail()` (Cleaned/Trimmed lowercase).
    - หากไม่พบอีเมล ให้ Fallback ค้นหาด้วยชื่อ-นามสกุลภาษาไทย (`firstName`, `lastName`) หรือภาษาอังกฤษ (`firstNameEn`, `lastNameEn`).
    - ตรวจสอบความปลอดภัย: หากพบบัญชีและยังไม่มี `StaffMember` คนอื่นผูกอยู่ ให้กำหนด `staff.setUser(user)` ทันที
- **Auto-Role Mapping จากตำแหน่งบริหาร:**
  - อ่าน `faculty.getManagePosition()`:
    - มีคำว่า "คณบดี" (และไม่ใช่ "รองคณบดี") -> ตั้ง `staffRole = "DEAN"`
    - มีคำว่า "รองคณบดี", "หัวหน้าสาขา", "ประธานหลักสูตร" -> ตั้ง `staffRole = "HEAD"`
    - มีคำว่า "กรรมการ" -> ตั้ง `staffRole = "COMMITTEE"`
    - มีคำว่า "เจ้าหน้าที่" หรือ "HR" -> ตั้ง `staffRole = "HR"`
  - **Preserve Manual Edits:** จะอัปเดตบทบาทเฉพาะกรณีที่ `staffRole` เดิมยังเป็น `GENERAL` หรือ `null` เท่านั้น เพื่อไม่ให้ทับค่าที่ Admin จงใจแก้ไขเอง

### 2. Backend Service: `StaffMemberService.java`
- เพิ่มเมธอด `autoLinkAllAccounts()` สำหรับการรันแบบ On-demand (ผูกย้อนหลังข้อมูลเดิมทั้งหมด):
  - ดึง `StaffMember` ทั้งหมดที่ยัง `user == null`
  - ค้นหา `FsFaculty` ผ่าน `fsUserId` เพื่อเอา `email` มาจับคู่กับ `UserDtls`
  - จับคู่และบันทึกข้อมูล พร้อมอัปเดต Role ถ้ายังเป็น `GENERAL`
  - สรุปผลลัพธ์เป็น `AutoLinkResult(int totalScanned, int newlyLinked, int rolesUpdated, List<String> unlinkedNames)`

### 3. Controller: `StaffMemberController.java`
- เพิ่ม Endpoint:
  - `POST /admin/academic/staff/auto-link`: สั่งให้ `staffMemberService.autoLinkAllAccounts()` ทำงาน บันทึก Admin Audit Log แล้ว Redirect กลับพร้อม Flash Message แจ้งจำนวนที่ผูกสำเร็จ
  - `POST /admin/academic/staff/sync`: สั่งให้ `staffDirectorySync.importFromDirectory()` + `autoLinkAllAccounts()` ทำงานพร้อมกัน บันทึก Log และแจ้งผลลัพธ์

### 4. Frontend UI: `staff_list.html`
- ปรับปรุง Header Toolbar:
  - เพิ่มปุ่ม `⚡ ผูกบัญชีอัตโนมัติ (Auto-Link)`
  - เพิ่มปุ่ม `🔄 ซิงค์ข้อมูลบุคลากร (Sync Directory)`
- แสดง Alert Banner เมื่อกดสำเร็จ (เช่น *"ผูกบัญชีสำเร็จ 35 คน, ปรับบทบาทบริหาร 4 คน"*)
- ตารางบุคลากรจะแสดง Badge สีเขียว **"ผูกแล้ว"** พร้อม Tooltip แสดง Email ของบัญชีผู้ใช้ที่ผูกไว้

---

## 🧪 แผนการทดสอบ (Verification Plan)
1. **Automated Tests:**
   - สร้าง Unit Test `StaffDirectorySyncTest` เพื่อทดสอบ:
     - การจับคู่อัตโนมัติด้วย Email
     - การจับคู่ด้วย ชื่อ-สกุล (Fallback)
     - การแมปตำแหน่งบริหาร คณบดี -> DEAN, รองคณบดี -> HEAD
     - การป้องกันการผูกบัญชีซ้ำซ้อน (Uniqueness Constraint Safety)
   - สร้าง Unit Test `StaffMemberAutoLinkTest` เพื่อทดสอบเมธอด `autoLinkAllAccounts()`
2. **Execution & Manual Verification:**
   - รัน Maven Test เพื่อตรวจสอบความถูกต้องของ Test Suite ทั้งหมด
   - ตรวจสอบหน้า `/admin/academic/staff` เพื่อยืนยันว่ารายชื่อบุคลากรทั้งหมดเชื่อมโยงกับบัญชีผู้ใช้จริงสำเร็จและแสดงบทบาทถูกต้อง
