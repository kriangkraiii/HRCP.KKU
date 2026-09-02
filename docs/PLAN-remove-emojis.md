# PLAN: Remove Emojis Across Project (ลบอิโมจิออกจากระบบ)

> **File:** `docs/PLAN-remove-emojis.md`  
> **Status:** [COMPLETED] ดำเนินการเสร็จสิ้นและตรวจสอบเรียบร้อยแล้ว  
> **Target Project:** `HRCP-KKU-Academic` & Workspace Documentation  
> **Project Type:** WEB (Spring Boot 3 + Thymeleaf + JavaScript + Documentation)

---

## 1. ภาพรวมและเหตุผล (Overview & Context)

การใช้อิโมจิ (Emojis) ในระบบงานราชการ/วิชาการระดับมหาวิทยาลัย (KKU HRCP Academic Request System) อาจทำให้ภาพลักษณ์ดูไม่เป็นทางการ (Informal) และอาจก่อให้เกิดปัญหาการแสดงผลบนบางแพลตฟอร์มหรือในการแปลงเอกสารราชการ

แผนงานนี้จัดทำขึ้นเพื่อค้นหา จำแนก และกำจัด/ทดแทนอิโมจิที่ไม่จำเป็นออกจากระบบอย่างเป็นระบบ โดยคำนึงถึงความปลอดภัยของระบบเอกสารทางวิชาการ (Academic Templates / PDF / DOCX)

---

## 2. ขอบเขตและเกณฑ์ความสำเร็จ (Scope & Success Criteria)

### 2.1 เกณฑ์ความสำเร็จ (Success Criteria)
1. **UI / Frontend:** กำจัดอิโมจิออกจากหน้าเว็บ, ป็อปอัปแจ้งเตือน (Alerts/Modals), และ Dashboard โดยแทนที่ด้วย UI Icon มาตรฐาน (เช่น FontAwesome / SVG) หรือลบออกให้เหลือข้อความล้วน [สำเร็จ]
2. **Java Backend & Email:** ลบอิโมจิออกจากข้อความแจ้งเตือน (Notifications), อีเมลอัตโนมัติ (Email Templates), และ Log Statements [สำเร็จ]
3. **Official Documents Safety:** ปกป้องสัญลักษณ์กล่องกาเครื่องหมายราชการ (`☐`, `☑`, `✓`, `✗`) ในฟอร์ม ก.พ.อ. และไฟล์แม่แบบ `.docx` / `.pdf` ไม่ให้เสียหาย [สำเร็จ]
4. **Docs & Reports:** ปรับปรุงไฟล์เอกสาร `README.md` และรายงานให้ดูเป็นทางการ (Clean & Professional) [สำเร็จ]

---

## 3. การจัดกลุ่มและผลการสำรวจ (Survey & Categorization)

| กลุ่ม | ตำแหน่งไฟล์ | ตัวอย่างอิโมจิที่พบ | แนวทางจัดการ | สถานะ |
|-------|------------|-------------------|-------------|-------|
| **Group 1: UI & Frontend Templates** | `templates/**/*.html`, `static/js/*.js` | Warning, Chart Icons | ปรับเป็นข้อความทางการและ FontAwesome | [x] เรียบร้อย |
| **Group 2: Backend & Email System** | `src/main/java/**/*.java` (Email, Scheduler, Alerts, Logs) | Rocket, Warning, Calendar, Check | ลบออกจาก Notification, Email และ Slf4j Logs | [x] เรียบร้อย |
| **Group 3: Official Form Checkboxes** | `doc_form_*.html`, `doc_preview.js`, `.docx` | `☐` (U+2610), `☑` (U+2611), `✓` (U+2713), `✗` (U+2717) | **คงไว้ (Preserve)** สัญลักษณ์ฟอร์มราชการ ก.พ.อ. | [x] ปลอดภัย |
| **Group 4: Documentation & Reports** | `README.md`, `docs/reports/*.html`, `docs/*.md` | Emoji ตกแต่ง | ปรับเป็นข้อความทางการ (Clean Markdown) | [x] เรียบร้อย |

---

## 4. สรุปรายละเอียดการดำเนินงาน (Completed Tasks)

### Phase 1: Frontend & UI Cleanup
- [x] แก้ไข `admin-storage.js`: ลบอิโมจิออกจากข้อความแจ้งเตือนยืนยันการลบไฟล์
- [x] แก้ไข `evaluation-report.html`: ปรับหัวข้อกราฟและสถิติให้เป็นข้อความทางการ
- [x] แก้ไข `dashboard.html`: ปรับข้อความแจ้งเตือนหมดอายุของผลประเมินการสอน
- [x] แก้ไข `user_storage.html` และ `file_manager.html`: ลบอิโมจิออกจาก Dialogs
- [x] แก้ไข `document_1_form.html`: ลบอิโมจิออกจาก JS Alerts

### Phase 2: Java Backend & Email Notifications
- [x] แก้ไข `EmailTemplateHelper.java`: ลบอิโมจิออกจาก Template ข้อเสนอแนะและแจ้งเตือนระบบ
- [x] แก้ไข `EvaluationExpiryScheduler.java`: ลบอิโมจิออกจากหัวข้อ In-App Notification
- [x] แก้ไข `SignatureNotifier.java`: ลบอิโมจิออกจากหัวข้อกำหนดวันลงนาม
- [x] แก้ไข `AcademicEmailService.java`: ลบอิโมจิออกจากหัวข้อรายละเอียดการนัดหมาย
- [x] แก้ไข `SystemAlertService.java`: ปรับ Level enum ให้ไม่มีอิโมจิ
- [x] แก้ไข `ImageSyncAuditService.java` & `DataRetentionService.java`: ลบอิโมจิออกจาก Logs
- [x] แก้ไข `SendAllTestEmailsTest.java`: ลบอิโมจิออกจาก Test Strings และ Preview HTML

### Phase 3: Project Documentation & Reports
- [x] ปรับปรุง `README.md` เป็น Clean Technical Documentation
- [x] ปรับปรุง `docs/reports/workflow-report.html` เป็น Clean HTML Report
- [x] ปรับปรุงไฟล์เอกสาร Markdown ทั้งหมดใน `docs/`

---

## 5. Phase X: ผลการทดสอบและตรวจสอบความปลอดภัย (Verification Results)

- [x] **Compilation & Build:** รัน `./mvnw test-compile` สำเร็จ (221 source files + 138 test files) `BUILD SUCCESS`
- [x] **Document Generation Integrity:** สัญลักษณ์กล่องกาเครื่องหมายราชการ (`☐`/`☑`/`✓`/`✗`) ในแบบฟอร์ม ก.พ.อ. คงอยู่สมบูรณ์
- [x] **Email & Notification Check:** อีเมลและแจ้งเตือนแสดงผลข้อความถูกต้อง เป็นทางการ
- [x] **Emoji Scan Verification:** สแกนทั้งโปรเจกต์พบอิโมจิคงเหลือ 0 ตัว (Total findings: 0)
