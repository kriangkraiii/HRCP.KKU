# แผนงาน: แก้ไขปัญหา Modal ขยายกำหนดเวลาจอมืดและคลิกไม่ได้ (Fix Modal Stacking Context & Backdrop Trap)

> เอกสารแผนงานตามคำสั่ง /plan สำหรับการแก้ไขปัญหา Modal แสดงผลอยู่ใต้ Backdrop (จอมืดและคลิกอะไรไม่ได้)

---

## 1. วัตถุประสงค์ (Objective)
แก้ไขปัญหาเมื่อผู้ใช้หรือผู้ดูแลระบบคลิกปุ่ม "ขยายกำหนดเวลา" (หรือปุ่มเปิด Modal ใดๆ ในแผงเวียนลงนาม) แล้วเกิดอาการหน้าจอมืดทั้งหมด และไม่สามารถคลิกพิมพ์ข้อมูลหรือกดปุ่มใดๆ ในหน้าต่าง Modal ได้ ("จอมืด แล้วกดไรไม่ได้") ให้สามารถเปิดหน้าต่าง Modal ขึ้นมาอยู่เหนือ Backdrop ได้อย่างถูกต้อง ชัดเจน และสามารถกรอกวันเวลาพร้อมกดบันทึกหรือยกเลิกได้ตามปกติ

---

## 2. การวิเคราะห์สาเหตุเชิงลึก (Root Cause Analysis)

### 2.1 ปัญหา Stacking Context ใน CSS (CSS Stacking Context Trap)
1. ในหน้าเว็บดังกล่าว โค้ดของ Modal (`modal-extend-due-${id}`) ถูกเขียนฝังอยู่ภายใน `academic/esign/_signature_panel.html`
2. แผง `_signature_panel.html` ถูกนำไปแทรก (Include) ไว้ภายในองค์ประกอบแม่หลายชั้น เช่น:
   `body` -> `.main-content` -> `.content-wrapper` -> `<section>` -> `.container` -> `.card-academic` -> `.card-body` -> `.signature-panel` -> `.modal`
3. ใน `style.css` องค์ประกอบแม่ `.card-academic` มีการกำหนดแอนิเมชัน:
   ```css
   .card-academic {
       ...
       animation: cardSlideIn var(--duration-slow) var(--ease-out-quart) both;
   }
   ```
4. ตามมาตรฐาน CSS (CSS Transforms Module Level 1): คุณสมบัติ `transform` หรือแอนิเมชันที่มีการแปลงค่า จะบังคับให้เบราว์เซอร์สร้าง **Stacking Context** แยกเฉพาะขึ้นมาสำหรับองค์ประกอบนั้นทันที
5. องค์ประกอบลูกทุกตัวที่อยู่ภายใน `.card-academic` (รวมถึง `.modal`) จะถูกจำกัดระดับ Layer ให้อยู่ภายใต้ Stacking Context ของ `.card-academic` เท่านั้น ไม่สามารถทะลุออกมาสู่ Root Document (`body`) ได้ แม้จะกำหนด `z-index` สูงเพียงใดก็ตาม

### 2.2 การทำงานของ Bootstrap 5 Modal Backdrop
1. เมื่อผู้ใช้คลิกปุ่มเปิด Modal ตัวไลบรารี Bootstrap 5 จะสร้างองค์ประกอบฉากหลังสีดำโปร่งแสง:
   `<div class="modal-backdrop fade show"></div>`
   และนำไปผูกไว้ที่ **`document.body` โดยตรง** ด้วยระดับ `z-index: 1050`
2. แต่ตัวหน้าต่าง `.modal` ยังคงติดอยู่ใน Stacking Context ของ `.card-academic` ซึ่งอยู่ที่ระดับชั้น Root Level 0
3. **ผลลัพธ์ที่เกิดขึ้น:**
   - ฉากหลัง `.modal-backdrop` (z-index 1050) จึงถูกวาดทับอยู่ด้านหน้าของ `.card-academic` และตัว Modal
   - ตัว Modal จึงตกไปอยู่ "ข้างหลัง" ฉากหลังสีดำ ทำให้หน้าจอมืดคลุมทับทั้งหน้าต่าง Modal
   - ฉากหลัง `.modal-backdrop` ทำหน้าที่ดักจับคลิกทั้งหมด (Pointer Events) ทำให้ผู้ใช้ไม่สามารถคลิกช่องกรอกวันเวลา หรือกดปุ่ม "ยกเลิก" / "บันทึกกำหนดเวลาใหม่" ได้เลย

---

## 3. แนวทางแก้ไขที่เสนอ (Proposed Solutions)

### แนวทางหลัก: Modal DOM Teleportation ไปยัง document.body (Universal & Bulletproof)
ตามแนวทางปฏิบัติมาตรฐานของ Bootstrap (Best Practices): หน้าต่าง Modal ควรเป็นลูกโดยตรงของ `document.body` เพื่อไม่ให้ถูกจำกัดโดย Stacking Context ของการ์ดหรือแอนิเมชัน
- เพิ่ม Global Event Listener ใน `base_academic.html`:
  ```javascript
  document.addEventListener('show.bs.modal', function (event) {
      const modal = event.target;
      if (modal && modal.classList && modal.classList.contains('modal')) {
          if (modal.parentElement !== document.body) {
              document.body.appendChild(modal);
          }
      }
  });
  ```
- **ข้อดี:**
  - แก้ไขปัญหาครอบคลุม Modal ทุกตัวในระบบ ทั้งในแผงลงนาม (`modal-extend-due`, `modal-resign`, `modal-revive`) และ Modal อื่นๆ ในอนาคต
  - โครงสร้าง `<form>` และโทเค็น CSRF ที่อยู่ภายใน `<div class="modal-content">` จะถูกย้ายไปพร้อมกันอย่างสมบูรณ์ ทำให้การส่งข้อมูล (Submit POST) ทำงานได้ปกติ 100%
  - หน้าต่าง Modal จะขึ้นมาอยู่ระดับ `body` ร่วมกับ Backdrop โดย Modal มี `z-index: 1055` และ Backdrop มี `z-index: 1050` ทำให้ Modal ลอยเด่นอยู่เหนือ Backdrop เสมอ สว่าง คมชัด และคลิกได้สมบูรณ์

### แนวทางการป้องกันเสริมใน CSS (CSS Reinforcement)
- ตรวจสอบและระบุ `z-index` ของ `.modal` (`1055 !important`) และ `.modal-backdrop` (`1050 !important`) ใน `style.css` อย่างชัดเจน
- ปรับปรุงการจัดการ Stacking Context ใน `.card-academic` เพื่อไม่ให้เกิดปัญหากับองค์ประกอบ Fixed อื่นๆ

---

## 4. แผนการดำเนินงาน (Task Breakdown)

### ส่วนที่ 1: การปรับปรุง JavaScript ใน `base_academic.html`
- เพิ่ม Global Modal Teleportation Handler ก่อนการปิดแท็ก `</body>`
- ตรวจสอบให้แน่ใจว่าเมื่อ Modal เปิดขึ้นมา จะถูกย้ายไปที่ `document.body` ทันที และคงการทำงานของปุ่ม ปิด/ยกเลิก และ Form Submit ไว้อย่างถูกต้อง

### ส่วนที่ 2: ตรวจสอบการทำงานของ Form และ CSRF ใน Modal
- ตรวจสอบ Modal ทั้ง 3 ตัวใน `_signature_panel.html`:
  1. `modal-extend-due-${id}` (ขยายกำหนดเวลาลงนาม)
  2. `modal-resign-${id}` (ส่งกลับให้แก้ไขและลงนามใหม่)
  3. `modal-revive-${id}` (เปิดการเวียนลงนามอีกครั้ง)
- ยืนยันว่าการส่งค่า POST ผ่าน Action `/esign/envelope/{id}/extend-due` มี CSRF token ครบถ้วนและทำงานได้สำเร็จ

### ส่วนที่ 3: การตรวจสอบและทดสอบ (Verification)
- ทดสอบคลิกปุ่ม "ขยายกำหนดเวลา" ในหน้า `/admin/academic/request/{id}/document/{docType}`
- ตรวจสอบว่า Modal ลอยอยู่เหนือ Backdrop ตัวหนังสือและช่องกรอกชัดเจน ไม่มืดทึบ
- ทดสอบพิมพ์วันเวลาใหม่ และคลิกปุ่ม "ยกเลิก" / "บันทึกกำหนดเวลาใหม่"
- ตรวจสอบว่าไม่มีผลกระทบข้างเคียงกับ Modal ตัวอื่นๆ ในระบบ

---

## 5. ผู้รับผิดชอบ (Agent Assignments)
- **ผู้วางแผนระบบ (Project Planner):** วิเคราะห์สาเหตุและจัดทำเอกสารแผนงาน
- **ผู้เชี่ยวชาญส่วนต่อประสาน (Frontend Specialist):** ปรับปรุง Script และ CSS ใน `base_academic.html` และทดสอบการทำงานของ Modal

---

## 6. แผนการตรวจสอบความถูกต้อง (Verification Checklist)
- [ ] เมื่อกดปุ่ม "ขยายกำหนดเวลา" หน้าต่าง Modal จะต้องปรากฏอยู่เหนือ Backdrop ไม่มืดทึบ
- [ ] สามารถคลิกเลือกวันเวลาลงนามใหม่ในช่อง datetime-local ได้อย่างลื่นไหล
- [ ] ปุ่ม "ยกเลิก" และปุ่ม "X" ปิดหน้าต่าง Modal และคืนค่าหน้าจอให้คลิกหน้าเว็บได้ปกติ
- [ ] ปุ่ม "บันทึกกำหนดเวลาใหม่" ส่งฟอร์มและบันทึกเวลาใหม่เข้าสู่ระบบได้ถูกต้อง
- [ ] Modal อื่นๆ ในแผงลงนาม (`modal-resign`, `modal-revive`) ทำงานได้ราบรื่น ไม่ติดปัญหาจอมืด
