# Project Plan: ระบบทดสอบการส่งอีเมลผ่าน KKU SMTP Relay ในหน้าการตั้งค่าแอดมิน (Admin Settings)

> **เอกสารแผนงาน:** `docs/PLAN-admin-email-test.md`  
> **ผู้รับผิดชอบ:** `@[project-planner]` ร่วมกับ `@[backend-specialist]` และ `@[frontend-specialist]`  
> **สถานะ:** วางแผนงาน (Planning - Ready for Approval)

---

## 1. ที่มาและเหตุผล (Rationale & Objective)

1. **ข้อจำกัดด้านความปลอดภัยของ KKU Mail Gateway (`smtp.kku.ac.th:587`):**
   - สำนักดิจิทัล มข. กำหนดนโยบาย IP-Whitelisted Relay ไม่อนุญาตให้เครื่อง Client ภายนอก (เช่น เครื่องพัฒนา Local Mac หรือ Client VPN IP `10.48.104.88`) ทำ Relay ส่งอีเมลออกไปยังโดเมนภายนอก (`@kkumail.com`, `@gmail.com`) ส่งผลให้เกิดข้อผิดพลาด `550 #5.1.0 Address rejected.`
   - การส่งอีเมลไปยังผู้รับภายนอกจะได้รับอนุญาตเฉพาะเมื่อส่งออกจากเครื่อง Server Production/Staging ที่ได้รับการลงทะเบียน IP ไว้เท่านั้น (IP `10.198.110.27`)
2. **ความต้องการของผู้ใช้:**
   - ผู้ดูแลระบบต้องการปุ่มทดสอบระบบส่งอีเมลที่ติดตั้งอยู่ในโปรเจกต์ และเลือกให้วางไว้ใน **เมนูการตั้งค่า (Settings)** เพื่อความสะดวกในการตรวจสอบความพร้อมและสถานะการทำงานของระบบ
3. **ประโยชน์ระยะยาว (DevOps & Maintenance):**
   - ผู้ดูแลระบบสามารถตรวจสอบการเชื่อมต่อ SMTP Server (Port 587 STARTTLS) ได้ทันทีเมื่อใดก็ตามที่มีข้อสงสัย
   - ป้องกันการต้องเข้าตรวจดู Server Log ผ่าน Command-line โดยตรง
   - มีระบบบันทึก Audit Log (`AdminLog`) ตรวจสอบย้อนหลังได้ตามมาตรฐาน พ.ร.บ. คอมพิวเตอร์

---

## 2. ขอบเขตงาน (Scope of Work)

| ส่วนงาน | รายละเอียด | เทคโนโลยี/ไฟล์ที่เกี่ยวข้อง |
|---|---|---|
| **1. Backend Endpoint** | สร้าง REST API สำหรับส่งอีเมลทดสอบ พร้อมตรวจสอบสิทธิ์ `ROLE_ADMIN` | `AcademicSettingsController.java` |
| **2. Email Dispatcher** | ส่งอีเมลทดสอบ HTML Format พร้อมข้อมูลวินิจฉัย (Diagnostics) | `JavaMailSender`, `EmailTemplateHelper` |
| **3. Audit Logging** | บันทึกประวัติการกดทดสอบส่งอีเมล | `AdminLogService` |
| **4. Frontend UI** | เพิ่มการ์ด "ทดสอบการส่งอีเมล (KKU SMTP Relay Diagnostic)" ในหน้าการตั้งค่า (แสดงเฉพาะ Admin) | `academic/settings.html` |
| **5. Client-side Interaction** | เรียก API แบบ AJAX, ส่ง CSRF Token, มีสถานะ Loading และแสดง Alert แจ้งผล | Vanilla JS, Fetch API |
| **6. Verification** | ทดสอบ Unit Test / Compilation และทดสอบการทำงาน | `./mvnw test-compile` |

---

## 3. รายละเอียดการออกแบบเชิงเทคนิค (Technical Design)

### 3.1 Backend Controller & API
- **Endpoint:** `POST /admin/academic/settings/test-email`
- **Security Check:**
  - ตรวจสอบ `Principal != null`
  - ตรวจสอบสิทธิ์ผู้ใช้ต้องเป็น `ROLE_ADMIN` (หากไม่ใช่ ส่ง `403 Forbidden`)
- **Request Parameters:**
  - `targetEmail` (String, optional) — อีเมลปลายทางที่ต้องการทดสอบ หากเว้นว่างจะใช้อีเมลของแอดมินที่กำลังล็อกอินอยู่
- **Validation:**
  - ตรวจสอบรูปแบบอีเมล (Email Regex) หากรูปแบบผิด คืน `{ "success": false, "message": "รูปแบบอีเมลไม่ถูกต้อง" }`
- **Mail Payload:**
  - **From:** `EmailTemplateHelper.resolveSenderEmail(null)` (`noreply@kku.ac.th`)
  - **From Name:** `"HRCP-KKU ระบบบริการงานวิชาการ"`
  - **To:** `targetEmail`
  - **Subject:** `"[HRCP-KKU] ทดสอบระบบส่งอีเมลผ่าน KKU SMTP Relay"`
  - **HTML Content:** เทมเพลตอีเมลสวยงาม ระบุข้อมูล:
    - วันที่และเวลาที่ส่ง (Bangkok Time)
    - เครื่องเซิร์ฟเวอร์ต้นทาง (`10.198.110.27`)
    - SMTP Host ที่เชื่อมต่อ (`smtp.kku.ac.th:587`)
    - สถานะ: ยืนยันการเชื่อมต่อสำเร็จ
- **Response Format (JSON):**
  ```json
  {
    "success": true,
    "message": "ส่งอีเมลทดสอบไปยัง kriangkrai.p@kkumail.com สำเร็จแล้ว",
    "recipient": "kriangkrai.p@kkumail.com",
    "timestamp": "14/09/2026 18:35:00"
  }
  ```
  หากส่งไม่สำเร็จ (เช่น Connection Timeout หรือ Mail Rejected):
  ```json
  {
    "success": false,
    "message": "เกิดข้อผิดพลาดในการเชื่อมต่อ SMTP: [สาเหตุข้อผิดพลาด]"
  }
  ```
- **Audit Logging:**
  - บันทึกลง `adminLogService.log(adminEmail, adminName, "TEST_EMAIL_RELAY", "ทดสอบส่งอีเมลไปยัง " + targetEmail, ipAddress)`

---

### 3.2 Frontend UI (academic/settings.html)
- **เงื่อนไขการแสดงผล:**
  - ใช้ `th:if="${user != null and user.role == 'ROLE_ADMIN'}"` เพื่อให้แสดงเฉพาะเมื่อผู้ใช้ที่เข้าหน้าการตั้งค่าคือ Admin เท่านั้น (ผู้ใช้ทั่วไปจะไม่เห็นส่วนนี้)
- **การจัดวาง (Layout):**
  - วางเป็นการ์ดใหม่ใน `#settingsContainer` ภายใต้หัวข้อ **"ระบบและการส่งอีเมล (KKU SMTP Relay Diagnostic)"**
- **องค์ประกอบในการ์ด:**
  1. **Status Badges & Info:**
     - เซิร์ฟเวอร์ส่งเมล: `smtp.kku.ac.th:587` (STARTTLS / Whitelisted IP: `10.198.110.27`)
     - ที่อยู่อีเมลผู้ส่ง: `noreply@kku.ac.th`
  2. **Input Field:**
     - ช่องระบุอีเมลปลายทาง (Input text/email) มีค่า Default เป็นอีเมลของแอดมิน หรือ `kriangkrai.p@kkumail.com`
  3. **Action Button:**
     - ปุ่ม "ส่งอีเมลทดสอบ" สีน้ำเงินหลัก พร้อมไอคอน `fas fa-paper-plane`
  4. **Loading & Feedback Area:**
     - เมื่อกดส่ง ปุ่มจะแสดง Spinner หมุน และปิดการกดซ้ำ (`disabled`)
     - กล่องข้อความตอบกลับ:
       - 🟢 สีเขียว: แจ้งผลสำเร็จ พร้อมเวลาที่ได้รับข้อความยืนยัน
       - 🔴 สีแดง: แจ้งข้อความ Error ที่ Server ตอบกลับมาอย่างชัดเจน

---

## 4. แผนการตรวจสอบและทดสอบ (Verification Plan)

1. **Compilation Check:**
   - รัน `./mvnw test-compile` เพื่อตรวจสอบ Syntax และ Type Safety
2. **Security & Role Check:**
   - ตรวจสอบว่าผู้ใช้ทั่วไป (`ROLE_USER`) ไม่เห็นการ์ดนี้ในหน้า `/user/academic/settings` และไม่สามารถเรียก API ได้
3. **End-to-End Test:**
   - ทดสอบผ่านหน้าเว็บ Admin Settings
   - ทดสอบส่งไปยังอีเมลภายใน `@kku.ac.th` และอีเมลภายนอก `@kkumail.com`

---

## 5. สรุปรายการไฟล์ที่จะแก้ไข (Files to Modify)

1. `HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicSettingsController.java`
   - เพิ่ม `POST /admin/academic/settings/test-email`
   - Inject `JavaMailSender`
2. `HRCP-KKU-Academic/src/main/resources/templates/academic/settings.html`
   - เพิ่มส่วน UI การ์ดทดสอบอีเมลสำหรับ Admin
   - เพิ่มฟังก์ชัน JavaScript AJAX สำหรับส่งคำขอ
3. `HRCP-KKU-Academic/src/test/java/com/ecom/academic/controller/AcademicSettingsControllerTest.java` (หรือ Test ที่เกี่ยวข้อง)
   - เพิ่ม Unit Test ตรวจสอบการทำงานของ Endpoint
