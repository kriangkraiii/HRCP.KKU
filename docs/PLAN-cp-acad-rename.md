# Project Plan: ปรับเปลี่ยนชื่อระบบเป็น CP ACAD (CP Academic Evaluation & Promotion System)

> **เอกสารแผนงาน:** `docs/PLAN-cp-acad-rename.md`  
> **ผู้รับผิดชอบ:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]` และ `@[backend-specialist]`  
> **สถานะ:** วางแผนงาน (Planning - Ready for Approval)  
> **ชื่อระบบใหม่:**  
> - **ชื่อย่อ / ชื่อแบรนด์หลัก:** `CP ACAD`  
> - **ชื่อทางการภาษาอังกฤษ:** `CP Academic Evaluation & Promotion System`  
> - **ชื่อทางการภาษาไทย:** `ระบบประเมินผลการสอนและขอกำหนดตำแหน่งทางวิชาการ วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น`

---

## 1. ที่มาและวัตถุประสงค์ (Rationale & Objective)

1. **การรีแบรนด์ระบบ (System Rebranding):**
   - เดิมระบบใช้ชื่อชั่วคราวว่า `HRCP.KKU` หรือ `ระบบเอกสารและการติดตามผลการยื่นขอกำหนดตำแหน่งทางวิชาการ`
   - มีการคัดเลือกชื่อทางการใหม่ที่สั้น กระชับ มีความหมายครบถ้วน และเป็นสากลในระดับมหาวิทยาลัย (สอดคล้องกับ `KKU Exam` / `KKU Reg`)
   - ได้ข้อสรุปชื่อระบบอย่างเป็นทางการคือ **`CP ACAD`** ซึ่งย่อมาจาก **`CP Academic Evaluation & Promotion System`**
2. **ขอบเขตการเปลี่ยนแปลง (Scope of Changes):**
   - ปรับปรุงข้อความ UI, หัวเว็บ (`<title>`), แถบนำทาง (Sidebar Brand), การ์ดหน้าเข้าสู่ระบบ (Guest / Sign-in pages), และส่วนท้ายหน้าเว็บ (Footer)
   - ปรับปรุงหัวเรื่องอีเมล (Email Subject), เทมเพลตอีเมลแจ้งเตือน (Notification Email Templates) และชื่อผู้ส่งของระบบ
   - ปรับปรุงข้อมูลโครงการใน `pom.xml` และ `application.properties`
   - **รักษาสถาปัตยกรรมภายใน (Preserve Internals):** ไม่แก้ไขชื่อ Package Java (`com.ecom.*`), ชื่อโฟลเดอร์ Root โปรเจกต์ และโครงสร้างตารางฐานข้อมูล เพื่อความเสถียรและป้องกันผลกระทบต่อ Build Pipeline

---

## 2. แผนการปรับปรุงตามส่วนประกอบ (Component Breakdown)

### 2.1 ส่วนติดต่อผู้ใช้ (Frontend UI & Templates)

| ไฟล์ / จุดที่แก้ไข | ข้อความเดิม | ข้อความใหม่ (CP ACAD) |
|---|---|---|
| **Sidebar Brand**<br>`academic/base_academic.html` | `<h6>ระบบเอกสารและการติดตามผลการยื่นขอกำหนดตำแหน่งทางวิชาการ</h6>` | `<h6>CP ACAD</h6>`<br>`<small>CP Academic Evaluation & Promotion System</small>` |
| **Page `<title>`**<br>`academic/base_academic.html` | `ระบบเอกสารและการติดตามผล... - วิทยาลัยการคอมพิวเตอร์` | `CP ACAD - ระบบประเมินผลการสอนและกำหนดตำแหน่งทางวิชาการ \| วิทยาลัยการคอมพิวเตอร์ มข.` |
| **Footer**<br>`academic/base_academic.html` | `... — ระบบเอกสารและการติดตามผลการยื่นขอกำหนดตำแหน่งทางวิชาการ` | `... — CP ACAD (CP Academic Evaluation & Promotion System)` |
| **Guest / Login Brand**<br>`guest/login.html` | `.guest-brand-title: ระบบเอกสารและการติดตามผล...` | `.guest-brand-title: CP ACAD`<br>`.guest-brand-sub: CP Academic Evaluation & Promotion System - วิทยาลัยการคอมพิวเตอร์ มข.` |
| **Guest Sub-pages**<br>`first_login.html`, `forgot_password.html`, `reset_password.html`, `verify_otp.html`, `verify_2fa.html`, `set_password.html` | หัวเรื่องและ `<title>` เดิม | เปลี่ยนเป็น `CP ACAD` และ `CP Academic Evaluation & Promotion System` ให้ตรงกันทุกหน้า |
| **Error Pages**<br>`error.html`, `403.html`, `404.html`, `500.html` | `<title>... \| HRCP-KKU</title>` | `<title>... \| CP ACAD</title>` |
| **E-Sign Pages**<br>`my_signatures.html`, `_signature_panel.html` | `...สำหรับการลงนามในระบบ KKU-HRCP` | `...สำหรับการลงนามในระบบ CP ACAD` |
| **Search Script**<br>`static/js/global-search.js` | `replace(' - HRCP KKU', '')` / `title !== 'HRCP.KKU'` | ปรับเงื่อนไขตัดคำและชื่อไตเติลให้รองรับ `CP ACAD` |

---

### 2.2 ระบบอีเมลและการแจ้งเตือน (Email & Notification System)

| ไฟล์ / คลาส | ข้อความเดิม | ข้อความใหม่ (CP ACAD) |
|---|---|---|
| **`EmailTemplateHelper.java`** | `ระบบบริหารจัดการตำแหน่งทางวิชาการ (HRCP.KKU)` | `CP ACAD (CP Academic Evaluation & Promotion System)` |
| **`EmailTemplateHelper.java`** | `อีเมลนี้เป็นการทดสอบ... ในระบบบริหารตำแหน่งทางวิชาการ (HRCP.KKU)` | `อีเมลนี้เป็นการทดสอบ... ในระบบ CP ACAD (วิทยาลัยการคอมพิวเตอร์ มข.)` |
| **`CommonUtil.java`** | `รีเซ็ตรหัสผ่าน - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)` | `รีเซ็ตรหัสผ่าน - ระบบ CP ACAD (วิทยาลัยการคอมพิวเตอร์ มข.)` |
| **`CommonUtil.java`** | `รหัส OTP สำหรับเข้าสู่ระบบครั้งแรก - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)` | `รหัส OTP สำหรับเข้าสู่ระบบครั้งแรก - ระบบ CP ACAD` |
| **`FeedbackService.java`** | `[HRCP.KKU] รายงาน: ...` | `[CP ACAD] รายงาน: ...` |
| **`SystemAlertService.java`** | `[HRCP.KKU] ...` | `[CP ACAD] ...` |
| **`EvaluationExpiryScheduler.java`** | `... - HRCP.KKU` | `... - CP ACAD` |
| **`AcademicSettingsController.java`** | `[HRCP-KKU] ทดสอบการส่งอีเมลผ่าน KKU SMTP Relay` | `[CP ACAD] ทดสอบการส่งอีเมลผ่าน KKU SMTP Relay` |
| **`static/js/notification.js`** | `ระบบสารสนเทศ KKU-HRCP` | `ระบบสารสนเทศ CP ACAD` |

---

### 2.3 การตั้งค่าโปรเจกต์ (Project Configuration & Metadata)

| ไฟล์ | รายการ | ค่าที่ปรับแก้ |
|---|---|---|
| **`pom.xml`** | `<name>` | `CP_ACAD` |
| **`pom.xml`** | `<description>` | `CP ACAD - CP Academic Evaluation & Promotion System` |
| **`application.properties`** | `spring.application.name` | `CP-ACAD` |
| **`application.properties`** | Header comment | `# CP ACAD — CP Academic Evaluation & Promotion System` |

---

## 3. สิ่งที่คงเดิมไว้เพื่อความปลอดภัยของระบบ (Safety Guardrails)

1. **ไม่เปลี่ยนชื่อ Package Java:** คง `package com.ecom...` ไว้ตามเดิม เนื่องจากหาก Refactor Package จะกระทบ Annotation Scan, JPA Entity mapping, Flyway Migration, และ Reflection ทั้งหมด
2. **ไม่เปลี่ยนชื่อโฟลเดอร์ Root โปรเจกต์:** โฟลเดอร์ `HRCP.KKU` และ `HRCP-KKU-Academic` จะคงชื่อเดิม เพื่อป้องกันไม่ให้ Workspace ของ IDE (Eclipse/VS Code) และ Git Path มีปัญหา
3. **ไม่แตะชื่อตารางฐานข้อมูล:** โครงสร้างตารางใน PostgreSQL ออกแบบเป็น Generic อยู่แล้ว (เช่น `academic_request`, `signature_request`, `staff_member`) ไม่มีผลกระทบใดๆ

---

## 4. แผนการตรวจสอบและทดสอบ (Verification Plan)

### 4.1 ตรวจสอบการ Compile (Automated Build Check)
- รัน `./mvnw test-compile` เพื่อตรวจสอบว่าไม่มี Syntax error หรือการเรียกใช้อ้างอิงผิดพลาด

### 4.2 ตรวจสอบการแสดงผลหน้าเว็บ (Visual UI Verification)
1. เข้าหน้า `/signin` ตรวจสอบ:
   - โลโก้ และข้อความ **CP ACAD**
   - ซับไตเติล **CP Academic Evaluation & Promotion System**
   - Title แท็บเบราว์เซอร์
2. เข้าหน้า `/user/academic/dashboard` และ `/admin/academic/requests`:
   - ตรวจสอบ Sidebar Brand และ Footer ด้านล่าง
   - ตรวจสอบ Title ของแต่ละหน้า
3. ตรวจสอบหน้า `/esign/my-signatures` และหน้ารอลงนาม `/esign/inbox`

### 4.3 ตรวจสอบระบบอีเมล (Email Diagnostic)
- ทดสอบส่งอีเมลผ่านหน้า Admin Settings (`/admin/academic/settings`)
- ตรวจสอบ Subject และ Header ในกล่องอีเมลว่าขึ้นเป็น `[CP ACAD]` ถูกต้อง
