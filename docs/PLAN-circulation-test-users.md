# Project Plan: เพิ่มบัญชีผู้ใช้สำหรับการทดสอบการเวียนเซ็น (Circulation Signing Test Accounts) ลงฐานข้อมูลโดยตรง

> **เอกสารแผนงาน:** `docs/PLAN-circulation-test-users.md`  
> **ผู้รับผิดชอบ:** `@[project-planner]` ร่วมกับ `@[backend-specialist]`  
> **สถานะ:** วางแผนงาน (Planning - Ready for Approval)  
> **ขอบเขต:** ดำเนินการเพิ่มข้อมูลลงใน PostgreSQL (`hr_db`) โดยตรง ไม่มีการแก้ไข Source Code แอปพลิเคชัน

---

## 1. ที่มาและวัตถุประสงค์ (Rationale & Objective)

1. **ความต้องการในการทดสอบการเวียนเซ็น (Circulation Workflow Testing):**
   - ระบบลงนามเอกสารอิเล็กทรอนิกส์ (`/esign/**`) ทั้งในส่วน **การประเมินผลการสอน (Academic Phase 1)** และ **การขอกำหนดตำแหน่งทางวิชาการ (Position Phase 2)** มีขั้นตอนการเวียนเอกสารให้ผู้มีอำนาจลงนามตามลำดับ (Sequential Signature Chain)
   - แต่ละขั้นตอนต้องการบทบาทที่แตกต่างกัน ได้แก่:
     - ผู้ขอรับการประเมิน / ผู้ยื่นคำขอ (`applicant`)
     - เจ้าหน้าที่ HR / ผู้ตรวจสอบคุณสมบัติ (`hr`)
     - หัวหน้าสาขาวิชา / ผู้บังคับบัญชาชั้นต้น (`head`)
     - รองคณบดี (`associate_dean`)
     - คณบดี (`dean`)
     - ประธานคณะกรรมการประเมิน / อนุกรรมการ (`committee_chair`)
     - ผู้ร่วมประพันธ์ (First Author / Corresponding Author ในแบบ ก.พ.อ.03 ส่วนที่ 2)
2. **ปัญหาปัจจุบัน:**
   - ข้อมูลผู้ใช้ในฐานข้อมูลปัจจุบันที่ดึงมาจาก Directory ยังไม่มีรหัสผ่านที่ทราบสำหรับทดสอบ Dev (เนื่องจากใช้ SSO ใน Production)
   - ผู้พัฒนาต้องการชุดบัญชีทดสอบที่พร้อมใช้งานทันที โดยมี **Email และ Password ที่ชัดเจน** เพื่อสลับล็อกอินทดสอบการเซ็นในแต่ละบทบาทได้สะดวก
3. **แนวทางดำเนินการ:**
   - เพิ่มข้อมูลผู้ใช้ทดสอบเข้าตาราง `user_dtls`, `staff_member` และ `user_signature` ในฐานข้อมูล PostgreSQL โดยตรง
   - **ไม่ต้องแก้ไข Code หรือสร้าง Entity ใหม่** ในระบบ

---

## 2. รายการบัญชีผู้ใช้ทดสอบ (Test Accounts Directory)

> 🔑 **รหัสผ่านสำหรับทุกบัญชีทดสอบ (Dev):** `test1234`  
> *(BCrypt Hash: `$2a$10$ouluw0aZ6VUduNyWpYVQyusQYS6Z4FsY1lm1znzPPO.x7ogPtS7kq`)*

| ลำดับ | บทบาทในระบบเวียนเซ็น | ช่องลงนาม (Slot) | Email | รหัสผ่าน | บทบาทระบบ (`role`) | บทบาทบุคลากร (`staff_role`) | ชื่อ-นามสกุล (สำหรับแสดงในเอกสาร) |
|---|---|---|---|---|---|---|---|
| 1 | **ผู้ยื่นคำขอ (Applicant)** | `applicant` | `test.applicant@kku.ac.th` | `test1234` | `ROLE_USER` | - | ผศ.ดร.สมชาย ทดสอบยื่น |
| 2 | **เจ้าหน้าที่ HR/ตรวจสอบ** | `hr` | `test.hr@kku.ac.th` | `test1234` | `ROLE_ADMIN` | `HR` | น.ส.สมหญิง สายตรวจการ |
| 3 | **หัวหน้าสาขาวิชา** | `head` | `test.head@kku.ac.th` | `test1234` | `ROLE_USER` | `HEAD` | รศ.ดร.สมศักดิ์ หัวหน้าสาขา |
| 4 | **รองคณบดี** | `associate_dean` | `test.assocdean@kku.ac.th` | `test1234` | `ROLE_USER` | `DEAN` | ศ.ดร.วิชัย รองคณบดีฝ่ายวิชาการ |
| 5 | **คณบดี** | `dean` | `test.dean@kku.ac.th` | `test1234` | `ROLE_ADMIN` | `DEAN` | ศ.ดร.ประสิทธิ์ คณบดีวิทยาลัยฯ |
| 6 | **ประธานคณะกรรมการ** | `committee_chair` | `test.committee@kku.ac.th` | `test1234` | `ROLE_USER` | `COMMITTEE` | ภก.ดร.บุญมี ประธานกรรมการ |
| 7 | **ผู้ประพันธ์อันดับแรก** | `first_author` | `test.author1@kku.ac.th` | `test1234` | `ROLE_USER` | - | อ.ดร.กิตติ ร่วมวิจัยหนึ่ง |
| 8 | **ผู้ประพันธ์บรรณกิจ** | `corresponding_author` | `test.author2@kku.ac.th` | `test1234` | `ROLE_USER` | - | ดร.สุภาวดี ผู้ประพันธ์บรรณกิจ |

---

## 3. รายละเอียดการออกแบบฐานข้อมูล (Database Mapping & Schema)

### 3.1 ตาราง `user_dtls` (บัญชีล็อกอินและสิทธิ์ระบบ)
- `email`: อีเมลสำหรับเข้าสู่ระบบ (เช่น `test.head@kku.ac.th`)
- `password`: รหัสผ่านเข้ารหัส BCrypt (`test1234`)
- `role`: `ROLE_USER` หรือ `ROLE_ADMIN`
- `applicant_id`: รหัสผู้ยื่น (สำหรับ Applicant) เช่น `APP-20260922-9001`
- `is_enable = true`, `account_non_locked = true`, `failed_attempt = 0`, `is_first_login = false`
- `two_factor_enabled = false` (เพื่อความสะดวกในการทดสอบ Dev ไม่ต้องรอ OTP)
- `email_verified = true`

### 3.2 ตาราง `staff_member` (ข้อมูลบุคลากรและบทบาทตำแหน่งเวียนเซ็น)
- สำหรับบัญชีที่เป็นกรรมการหรือผู้บริหาร (ลำดับ 2 - 6):
  - เชื่อมโยง foreign key `user_id` ไปยัง `user_dtls.id`
  - กำหนด `staff_role`:
    - `HR` สำหรับเจ้าหน้าที่
    - `HEAD` สำหรับหัวหน้าสาขา
    - `DEAN` สำหรับรองคณบดี และคณบดี
    - `COMMITTEE` สำหรับประธานกรรมการ
  - `is_active = true`
  - `fs_user_id = null` (เพื่อป้องกันระบบ sync ประจำวันมาเขียนทับ)

### 3.3 ตาราง `user_signature` (ลายมือชื่อเริ่มต้นสำหรับทดสอบเซ็นทันที)
- สร้างลายมือชื่อประเภท `TYPE` (`kind = 'TYPE'`, `is_default = true`, `is_deleted = false`) ให้กับทุกบัญชี
- ใช้ฟอนต์ `'Sarabun', sans-serif` พร้อมข้อความชื่อภาษาไทย
- ทำให้เมื่อล็อกอินเข้าสู่ระบบ สามารถกดปุ่ม **"ลงนาม (Sign)"** ได้ทันทีโดยไม่ต้องวาดหรืออัปโหลดรูปลายเซ็นใหม่ก่อน

---

## 4. แผนงานการดำเนินการ (Implementation Plan)

### ขั้นตอนที่ 1: เตรียมคำสั่ง SQL (Direct DB Script)
- เขียน Script ตรวจสอบการมีอยู่ของข้อมูล (`ON CONFLICT (email) DO NOTHING` หรือ `DELETE` บัญชีทดสอบเก่าหากต้องการ Reset)
- จัดลำดับการ Insert:
  1. `user_dtls` -> รับ `id`
  2. `staff_member` -> เชื่อมโยง `user_id`
  3. `user_signature` -> เชื่อมโยง `user_id`

### ขั้นตอนที่ 2: รันสคริปต์เข้าฐานข้อมูล PostgreSQL
- ดำเนินการผ่านเครื่องมือ `psql` เชื่อมต่อไปยัง Host: `10.198.200.84:5432`, Database: `hr_db`
- ยืนยันการบันทึกสำเร็จ (Commit Transaction)

### ขั้นตอนที่ 3: ตรวจสอบและทดสอบผล (Verification)
1. **ตรวจสอบความถูกต้องใน DB:**
   - เช็คความสัมพันธ์ระหว่าง `user_dtls` และ `staff_member`
   - ตรวจสอบว่า `staffMemberService.findByRoleWithAccountStatus("...")` มองเห็นบัญชีเหล่านี้
2. **ทดสอบการล็อกอิน (Authentication Test):**
   - ทดสอบล็อกอินผ่านหน้า `/signin` ด้วย Email และ Password `test1234`
3. **ทดสอบระบบเวียนเซ็น (Circulation Workflow Test):**
   - ตรวจสอบว่าในขั้นตอนการเลือกผู้ลงนาม (Signer Picker) ปรากฏชื่อและอีเมลของบัญชีทดสอบในแต่ละบทบาท
   - เมื่อส่งเวียนเซ็นแล้ว ตรวจสอบกล่องข้อความ `/esign/inbox` ของผู้ลงนามว่าเอกสารปรากฏถูกต้องและสามารถกดเซ็นได้ทันที

---

## 5. การ Rollback (หากต้องการลบข้อมูลทดสอบออก)
- มีคำสั่ง SQL สำหรับลบบัญชีทดสอบออกอย่างสะอาด:
  ```sql
  DELETE FROM user_signature WHERE user_id IN (SELECT id FROM user_dtls WHERE email LIKE 'test.%@kku.ac.th');
  DELETE FROM staff_member WHERE user_id IN (SELECT id FROM user_dtls WHERE email LIKE 'test.%@kku.ac.th');
  DELETE FROM user_dtls WHERE email LIKE 'test.%@kku.ac.th';
  ```
