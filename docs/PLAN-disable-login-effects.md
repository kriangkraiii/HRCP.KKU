# Plan: ปิดเอฟเฟกต์อนุภาค (Particles Effect) ในหน้าเข้าสู่ระบบ (Disable Sign-in Particles Effect)

## 📌 Context & Overview
ปัจจุบันในหน้าเข้าสู่ระบบ (`/login` ผ่านเทมเพลต `src/main/resources/templates/guest/login.html`) มีการเรียกใช้งานสคริปต์ `js/signin-particles.js` เพื่อเรนเดอร์สนามอนุภาค (Particles Canvas) เคลื่อนไหวตามเคอร์เซอร์เมาส์และลอยไปมาทั่วทั้งฝั่งแบรนด์สีเข้ม (`.guest-brand`) และฝั่งฟอร์มล็อกอินสีขาว (`.guest-form`)

**ความต้องการของผู้ใช้:**
ต้องการปิดเอฟเฟกต์ดังกล่าวในหน้านี้ เพื่อให้หน้าจอเข้าสู่ระบบดูสะอาดตา (Clean, Professional, Minimal) ลดการรบกวนสายตาขณะกรอกข้อมูล และลดภาระการประมวลผลกราฟิกบนเบราว์เซอร์

---

## 🎯 Success Criteria
1. **Frontend Performance & Visuals:** หน้าเข้าสู่ระบบไม่มี Canvas ของ `signin-particles` ทำงาน ไม่มีขีดหรืออนุภาคสีลอยรบกวน
2. **Background Restoration:** พื้นหลังกลับสู่ดีไซน์มาตรฐานของระบบ (พื้นหลังไล่ระดับสีเข้มฝั่งซ้าย และพื้นขาวพร้อมเงาการ์ดฝั่งขวาอย่างชัดเจน สะอาดตา)
3. **Clean Code & Asset Management:** สคริปต์ `signin-particles.js` ถูกปิดการโหลดจากหน้า `login.html` อย่างเรียบร้อย ไม่ส่งผลกระทบต่อฟอร์มเข้าสู่ระบบและระบบรักษาความปลอดภัยอื่นๆ

---

## 🏗️ Architecture & Component Analysis

### 1. ไฟล์ที่เกี่ยวข้องโดยตรง
- **Template หน้าเข้าสู่ระบบ:** `HRCP-KKU-Academic/src/main/resources/templates/guest/login.html`
  - บรรทัด 105: Attribute `data-signin-particles data-particles-dark` บน `<div class="guest-brand">`
  - บรรทัด 112: Attribute `data-signin-particles` บน `<div class="guest-form">`
  - บรรทัด 395: `<script src="/js/signin-particles.js" defer></script>`
- **CSS สไตล์:** `HRCP-KKU-Academic/src/main/resources/static/css/guest.css`
  - บรรทัด 81–97: คลาส `.guest-particles`, `.guest-brand.has-particles`, `.guest-form.has-particles` (เมื่อไม่มี particles คลาส `.guest-brand` จะกลับมาแสดง grid pattern เดิมเบาๆ ตาม CSS ปกติ)
- **Script:** `HRCP-KKU-Academic/src/main/resources/static/js/signin-particles.js` (ยังคงเก็บไฟล์ไว้ในโปรเจกต์ ไม่ลบทิ้ง เพื่อความปลอดภัย)

---

## 📋 Task Breakdown

### Phase 1: Template Modification (`login.html`)
- [x] นำแท็กเรียกใช้งาน `<script src="/js/signin-particles.js" defer></script>` ออกจาก `login.html`
- [x] นำ attribute `data-signin-particles` และ `data-particles-dark` ออกจาก `.guest-brand` และ `.guest-form`
- [x] ตรวจสอบโครงสร้าง HTML และคลาสของ `.guest-card` ว่าแสดงผลได้ถูกต้องสวยงาม

### Phase 2: Verification & Visual Polish
- [x] ตรวจสอบว่าเบราว์เซอร์ไม่เกิด JavaScript Error หรือ 404
- [x] ตรวจสอบการแสดงผลหน้าล็อกอินทั้งในโหมดปกติและจอขนาดเล็ก (Responsive)
- [x] ยืนยันการทำงานของระบบล็อกอิน (Login Form, Remember Me, Alerts) ว่ายังทำงานได้ครบถ้วน 100%

---

## 🧪 Phase X: Verification Plan

### Automated & Build Checks
- [x] รันการคอมไพล์โปรเจกต์:
  ```bash
  ./mvnw compile -DskipTests
  ```
- [x] รันการทดสอบ Integration Tests:
  ```bash
  ./mvnw test -Dtest=SsoCallbackSurvivesAStaleSessionTest
  ```

### Manual Verification Checklist
- [x] เปิดหน้า `/login` หรือ `/signin`
- [x] ตรวจสอบว่าไม่มีอนุภาคสีลอยรอบเคอร์เซอร์เมาส์ หน้าจอดูสะอาดตาและเป็นระเบียบ
- [x] ตรวจสอบว่าคอนโซลไม่มี JavaScript Error
- [x] ทดสอบกรอกอีเมล/รหัสผ่านและกดเข้าสู่ระบบได้ตามปกติ

## ✅ PHASE X COMPLETE
- Compilation: ✅ Pass (./mvnw compile -DskipTests)
- Tests: ✅ Pass (9 tests run, 0 failures, 0 errors in SsoCallbackSurvivesAStaleSessionTest)
- Date: 2026-09-24
