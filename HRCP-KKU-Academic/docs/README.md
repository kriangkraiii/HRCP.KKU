# เอกสารออกแบบระบบ HRCP-KKU-Academic

## ระบบจัดการคำร้องบุคลากรทางวิชาการ — วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น

---

## สารบัญ

| ลำดับ | เอกสาร | คำอธิบาย |
|-------|--------|---------|
| 1 | [ภาพรวมระบบ](01-system-overview.md) | ตัวแสดง (Actors), Use Case Diagram ระดับระบบ |
| 2 | [ระบบยืนยันตัวตน](02-authentication.md) | เข้าสู่ระบบ, OTP, ลืมรหัสผ่าน |
| 3 | [คำร้องประเมินผลการสอน (Phase 1)](03-academic-request.md) | สร้าง/จัดการคำร้องประเมินผลการสอน |
| 4 | [คำร้องขอกำหนดตำแหน่ง (Phase 2)](04-position-request.md) | สร้าง/จัดการคำร้องขอกำหนดตำแหน่ง |
| 5 | [คำร้องทั่วไป](05-petition.md) | ยื่นคำร้องทั่วไป/อุทธรณ์ |
| 6 | [จัดการบุคลากร](06-staff-management.md) | CRUD บุคลากร/กรรมการ |
| 7 | [จัดการไฟล์](07-file-management.md) | ถังขยะ, กู้คืน, ลบถาวร |
| 8 | [จัดการผู้ใช้งาน](08-user-management.md) | CRUD ผู้ใช้/แอดมิน |
| 9 | [การตั้งค่าและแจ้งเตือน](09-settings-notifications.md) | Auto-Draft, อีเมล, แจ้งเตือนหมดอายุ |
| A | [ภาคผนวก — แผนภาพสถานะ](appendix-status-flows.md) | State diagrams ทั้ง 3 ประเภทคำร้อง |

---

## โครงสร้างเอกสารในแต่ละ Subsystem

แต่ละไฟล์ประกอบด้วย 4 ส่วนหลัก:

1. **Use Case Diagram** — แผนภาพ Use Case (Mermaid) แยกตาม Role
2. **Use Case Description** — ตารางอธิบายรายละเอียดแต่ละ Use Case
3. **System Sequence Diagram** — แผนภาพลำดับ (Mermaid) แสดง flow การทำงาน
4. **User Interface Design** — คำอธิบายหน้าจอ + ตาราง Component + ช่องวาง Screenshot

---

## วิธีวาง Screenshot

1. Run ระบบด้วย `mvn spring-boot:run`
2. เปิด browser ไปที่หน้าจอที่ต้องการ
3. Capture screenshot แล้วบันทึกลงโฟลเดอร์ `screenshots/`
4. ตั้งชื่อไฟล์ตามรูปแบบ: `{subsystem}-{role}-{screen}.png`
   - เช่น `auth-guest-login.png`, `academic-admin-request-list.png`

---

## เทคโนโลยีที่ใช้ในระบบ

| รายการ | เทคโนโลยี |
|--------|----------|
| Backend | Spring Boot 4.0.3, Java 21 |
| Database | MySQL |
| Frontend | Thymeleaf, Bootstrap 5, JavaScript |
| Authentication | Spring Security |
| Email | Spring Mail (Async) |
| Document Generation | Apache POI (DOCX), LibreOffice (PDF) |
