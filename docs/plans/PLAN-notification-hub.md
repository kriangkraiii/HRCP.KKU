# Plan: ระบบศูนย์การแจ้งเตือนและการเปิดอ่านแบบครบวงจร (Option C: In-App Notification Hub)

##  Context
ปัจจุบันระบบการแจ้งเตือนในเว็บยังมีข้อจำกัด:
1. การแจ้งเตือนบางประเภทลิงก์ไปยังหน้ากว้างๆ (เช่น `/dashboard` หรือ `/requests`) ทำให้ผู้ใช้ต้องไปค้นหาคำร้องเองอีกรอบ
2. การคลิกแจ้งเตือนยังไม่มีหน้าต่าง Modal เปิดอ่านรายละเอียดข้อความเต็ม วันเวลา และผู้ส่งเหมือนการเปิดอ่านอีเมล
3. ไอคอนกระดิ่งบนแถบด้านบน (Topbar) เป็นเพียงลิงก์เปลี่ยนหน้า ยังไม่มี Dropdown พรีวิวการแจ้งเตือนล่าสุด

**เป้าหมาย:** สร้างประสบการณ์การแจ้งเตือนที่สมบูรณ์แบบ — มี Topbar Dropdown พรีวิว, มี Reader Modal เปิดอ่านข้อความเต็มเหมือนเมล, และมี Deep Link ชี้ตรงไปยังคำร้อง/เอกสารนั้นๆ 100%

---

## ️ Architecture Breakdown

### 1. Deep Link Direct Routing (Backend)
- ปรับปรุง Service ทุกจุดให้ใส่ลิงก์ที่ระบุ ID คำร้องโดยตรง:
  - `AcademicEmailService.java`: ชี้ตรงไปที่ `/user/academic/request/{id}` และ `/admin/academic/request/{id}`
  - `PositionEmailService.java`: ชี้ตรงไปที่ `/admin/position/request/{id}`
  - `EvaluationExpiryScheduler.java`: ชี้ตรงไปที่ `/user/academic/request/{id}`

### 2. Notification API Extensions (Backend)
- `GET /api/notifications/recent`: ส่ง 5 การแจ้งเตือนล่าสุด + จำนวนที่ยังไม่ได้อ่าน เพื่อแสดงผลใน Topbar Dropdown
- `GET /api/notifications/{id}/detail`: ดึงข้อมูลรายละเอียดการแจ้งเตือนรายข้อความ

### 3. Topbar Bell Dropdown & Global Reader Modal (Frontend)
- **Topbar Dropdown (`base_academic.html`)**:
  - เมื่อคลิกกระดิ่งจะแสดง Dropdown รายการแจ้งเตือน 5 รายการล่าสุด
  - มีปุ่ม "ทำเครื่องหมายอ่านทั้งหมด" และ "ดูทั้งหมด"
- **Notification Reader Modal (`#notifReaderModal`)**:
  - แสดงหัวข้อ, วันเวลา, ผู้ส่ง, ประเภทแจ้งเตือน และข้อความเต็ม
  - มาร์คว่าอ่านแล้วอัตโนมัติเมื่อเปิดอ่าน
  - มีปุ่ม **"ไปยังคำร้อง / เอกสารที่เกี่ยวข้อง"** นำผู้ใช้ไปยังคำร้องได้ทันที

---

##  Task Breakdown

### Phase 1: Backend Deep Link & REST API Refactoring
- [ ] แก้ไขลิงก์ใน `AcademicEmailService.java`, `PositionEmailService.java`, และ `EvaluationExpiryScheduler.java`
- [ ] เพิ่ม REST API ใน `NotificationController.java`:
  - `GET /api/notifications/recent`
  - `GET /api/notifications/{id}/detail`
  - `POST /api/notifications/{id}/unread` (สำหรับปุ่มมาร์คว่ายังไม่ได้อ่าน)

### Phase 2: Topbar Dropdown & Global Reader Modal UI
- [ ] ปรับปรุง `base_academic.html`:
  - เปลี่ยนไอคอนกระดิ่งเป็น Bootstrap Dropdown
  - สร้าง Global Modal `#notifReaderModal` สำหรับเปิดอ่านข้อความ
- [ ] ปรับปรุง `notifications.html` ให้ส่ง Data Attributes สำหรับเปิด Modal ได้ทันที

### Phase 3: JavaScript Client Interactions
- [ ] ปรับปรุง `notification.js`:
  - เพิ่มฟังก์ชันเปิด Reader Modal และอัปเดตสถานะแบบ Real-time
  - เพิ่มฟังก์ชันโหลดและแสดงผล Topbar Dropdown เมื่อคลิกกระดิ่ง

### Phase 4: Testing & Verification
- [ ] รัน Unit & Integration Tests
- [ ] ทดสอบการคลิกแจ้งเตือนจากหน้าจอต่างๆ และทดสอบการนำทางไปยังหน้าคำร้อง

---

##  Verification Checklist
- [ ] คลิกกระดิ่งด้านบนจากหน้าใดก็ได้ → มี Dropdown พรีวิว 5 ข้อความล่าสุด
- [ ] คลิกที่ข้อความแจ้งเตือน → Modal เปิดแสดงรายละเอียดเต็ม และ Badge ตัวเลขแจ้งเตือนลดลงทันที
- [ ] ใน Modal มีปุ่ม "ไปยังคำร้องนี้" ที่พากลับไปยังคำร้องถูกต้อง 100%
