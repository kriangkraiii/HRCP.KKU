# แผนงาน: แก้ไขปัญหาปุ่มย้อนกลับลอยทับแถบแจ้งเตือนสำเร็จ (Alert & Floating Back Button Overlap Fix)

> เอกสารแผนงานตามคำสั่ง `/plan` สำหรับการแก้ไขปัญหาปุ่ม `← กลับ` ลอยทับข้อความแจ้งเตือนสีเขียว ("อัพเดทโปรไฟล์สำเร็จ") บนหน้าข้อมูลส่วนตัวและหน้าอื่นๆ ในระบบ

---

## 1. วัตถุประสงค์ (Objective)

1. **แก้ไขปุ่ม `← กลับ` ลอยทับข้อความแจ้งเตือน:**
   - ในหน้าข้อมูลส่วนตัว (`/user/profile` และ `/admin/profile`) ปุ่ม "กลับ" ต้องแสดงผลเป็นปุ่มปกติในเนื้อหา (Inline) ใต้แถบแจ้งเตือน ไม่ลอยขึ้นมาเป็น Floating Button บดบังข้อความแจ้งเตือน
2. **แก้ไข Selector ของระบบ Floating Buttons ไม่ให้ตรวจจับหน้าฟอร์มทั่วไปผิดพลาด:**
   - ตัวตรวจจับใน `base_academic.html` ใช้ `form[id$="Form"]` ซึ่งกว้างเกินไปและจับคู่กับ `profileForm`, `feedbackForm`, `uploadForm` ฯลฯ ทำให้หน้าตั้งค่าทั่วไปถูกแปลงปุ่มกลับเป็นปุ่มลอยโดยไม่ตั้งใจ
3. **ป้องกันการบดบังแถบแจ้งเตือนบนหน้าเอกสารจริง (Defensive Styling):**
   - สำหรับหน้าฟอร์มเอกสารทางวิชาการที่มีปุ่มลอยถูกต้อง หากมีการ redirect กลับมาพร้อมแถบแจ้งเตือน (`succMsg`, `errorMsg`, `warnMsg`) แถบแจ้งเตือนจะต้องเว้นระยะไม่ให้ปุ่มลอย `← กลับ` และ `👁 ดูตัวอย่าง` มาทับซ้อน

---

## 2. การวิเคราะห์สาเหตุเชิงลึก (Root Cause Analysis)

1. **สาเหตุหลัก (Selector ทับซ้อน):**
   - ใน [base_academic.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html) บรรทัดที่ 1026 มีโค้ดตรวจจับหน้าเอกสาร:
     ```javascript
     var docForm = document.querySelector('form[id^="doc"], form[id$="Form"], form[data-auto-draft], form[action*="/document"], .doc-form-wrapper, .card-doc-form');
     ```
   - เงื่อนไข `form[id$="Form"]` ทำให้ `<form id="profileForm">` บนหน้าข้อมูลส่วนตัวถูกนับเป็น "ฟอร์มเอกสารวิชาการ"
   - สคริปต์จึงหยิบปุ่ม `<a class="btn-back-pill">กลับ</a>` ไปแปลงเป็น `.floating-doc-btn-back` ซึ่งมีสไตล์ `position: fixed; top: 70px;`
2. **สาเหตุรอง (โครงสร้าง Layout ของ Flash Message):**
   - แถบแจ้งเตือนกลาง (`succMsg` / `errorMsg`) วางอยู่ต่อจาก `<header>` โดยตรง (ความสูง Navbar = 54px, แถบแจ้งเตือนอยู่ที่ 54px - 105px)
   - เมื่อมีปุ่มลอยที่ `top: 70px` ปุ่มจึงลอยทับครึ่งบนของแถบแจ้งเตือนและบังข้อความ "อัพเดท..." จนเหลือเพียง "...โปรไฟล์สำเร็จ" ตามภาพที่ผู้ใช้ส่งมา

---

## 3. รายละเอียดแผนการแก้ไข (Technical Implementation Plan)

### ส่วนที่ 1: ปรับแก้เงื่อนไขการสร้างปุ่มลอยใน `base_academic.html`
- นำ `form[id$="Form"]` ออกจากตัวเลือก `docForm` ในบรรทัดที่ 1026:
  - คงไว้เฉพาะ: `form[id^="doc"], form[data-auto-draft], form[action*="/document"], .doc-form-wrapper, .card-doc-form`
  - ฟอร์มเอกสารวิชาการทั้งหมด (Doc 1, Doc 2, Position Doc 1-6) ยังคงทำงานและมีปุ่มลอยตามปกติ 100%
  - หน้าที่ไม่ใช่เอกสาร เช่น `profileForm`, `feedbackForm`, `uploadForm` จะไม่ถูกแปลงเป็นปุ่มลอยอีกต่อไป
- กำหนดคลาส `no-floating-back` ใน [user/profile.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/user/profile.html) และ [admin/profile.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/admin/profile.html) เพื่อเป็นเกราะป้องกันสองชั้น (Defense-in-depth)

### ส่วนที่ 2: ป้องกันการทับซ้อนกรณีหน้าเอกสารมี Flash Message
- ใน [base_academic.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html):
  - เพิ่มคลาสระบุตัวตน `global-flash-alert` ให้กับคอนเทนเนอร์ข้อความแจ้งเตือน:
    ```html
    <div class="global-flash-alert px-4 pt-3" th:if="${succMsg != null or errorMsg != null or warnMsg != null}">
    ```
- ใน [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css):
  - เพิ่มกฎ CSS:
    ```css
    body:has(.floating-doc-btn) .global-flash-alert {
      padding-top: 56px;
    }
    body:has(.floating-doc-btn):has(.global-flash-alert) .content-wrapper {
      padding-top: 16px;
    }
    ```
  - ผลลัพธ์: หากหน้าใดมีทั้งปุ่มลอยและแถบแจ้งเตือน แถบแจ้งเตือนจะถูกขยับลงมาอยู่ใต้ปุ่มลอยอย่างเรียบร้อย ไม่เกิดการซ้อนทับกันไม่ว่าจะในกรณีใด

---

## 4. แผนการทดสอบความถูกต้อง (Verification Plan)

1. **ทดสอบหน้าแก้ไขข้อมูลส่วนตัว (`/user/profile`):**
   - อัปเดตข้อมูลหรือเปลี่ยนรูปโปรไฟล์ -> ระบบบันทึกและแสดงแถบแจ้งเตือนสีเขียว "อัพเดทโปรไฟล์สำเร็จ"
   - ตรวจสอบว่าปุ่ม `← กลับ` แสดงผลแบบ Inline ปกติอยู่ในตำแหน่งเดิมใต้แถบแจ้งเตือน
   - ตัวหนังสือ "อัพเดทโปรไฟล์สำเร็จ" ชัดเจน 100% ไม่มีปุ่มใดมาบัง
2. **ทดสอบหน้าแก้ไขข้อมูลส่วนตัวของแอดมิน (`/admin/profile`):**
   - ตรวจสอบพฤติกรรมถูกต้องเหมือนหน้าผู้ใช้งาน
3. **ทดสอบหน้าเอกสารวิชาการ (Evaluation & Position Doc Forms):**
   - เปิดหน้า Document 1 หรือ Document 2
   - ตรวจสอบว่าปุ่มลอย `← กลับ` และ `👁 ดูตัวอย่าง` ยังคงแสดงผลและลอยอยู่ด้านบนตามปกติ
   - หากมีการแสดงผลแจ้งเตือน แถบแจ้งเตือนจะอยู่ใต้ปุ่มลอยอย่างสวยงาม ไม่ซ้อนทับ
