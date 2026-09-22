# แผนงาน: แก้ไขระบบภาษาของสถานะบันทึกร่าง และการซ้อนทับของเมนูเลือกภาษา (I18N & UI Collision Fix)

> เอกสารแผนงานตามคำสั่ง `/plan` สำหรับการแก้ไขปัญหา 2 จุด:
> 1. แถบสถานะบันทึกร่างอัตโนมัติ (`.adb`) ไม่แปลภาษาตามระบบเมื่อสลับเป็นภาษาอังกฤษ
> 2. เมนูเลือกภาษาของ Topbar ถูกปุ่มลอย "ดูตัวอย่าง" (`.floating-doc-btn-preview`) ลอยทับซ้อนกันจนมองและคลิกยาก

---

## 1. วัตถุประสงค์ (Objective)

1. **รองรับ 2 ภาษาในระบบ Auto-Draft (`auto_draft.js`):**
   - เมื่อผู้ใช้เลือกภาษาอังกฤษ (EN) ชิปสถานะบันทึกร่างและข้อความ mirror ท้ายหน้าจะต้องแสดงผลเป็นภาษาอังกฤษ เช่น "Auto-save is active", "Saving draft...", "Draft saved at 14:30" เป็นต้น
   - เมื่อเปลี่ยนภาษากลับเป็นไทย (TH) จะแสดงผลเป็นภาษาไทยตามปกติ
   - ป้องกันไม่ให้ Google Translate มาแทรกแซงหรือทำให้ข้อความ JavaScript เพี้ยนด้วยคลาส `notranslate`
2. **แก้ไขการทับซ้อนของเมนูเลือกภาษากับปุ่มดูตัวอย่าง:**
   - แก้ไขลำดับ Stacking Context ของ `.top-navbar` (`z-index: 1020` ต่ำกว่า `.floating-doc-btn` ที่เป็น `1040`)
   - ปรับให้ Navbar และเมนู Dropdown อยู่ด้านบนสุด (`z-index: 1045` เหนือปุ่มลอย) เพื่อให้เมนูเปิดออกมาลอยทับอย่างคมชัด ไม่ถูกปุ่มโปร่งใสทับซ้อนตัวหนังสือ

---

## 2. การวิเคราะห์สาเหตุเชิงลึก (Root Cause Analysis)

### 2.1 ปัญหาที่ 1: สถานะบันทึกร่างไม่แปลภาษา
- **สาเหตุ:** ใน [auto_draft.js](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/js/auto_draft.js) ข้อความสถานะทั้งหมด (เช่น `"ระบบบันทึกร่างอัตโนมัติทำงานอยู่"`, `"กำลังบันทึกร่าง"`, `"บันทึกร่างล่าสุด"`) ถูกกำหนดเป็น Hardcoded String ภาษาไทยผ่าน JavaScript DOM (`this.labelEl.textContent = text`)
- แม้ Google Translate จะแปลทั้งหน้า แต่เมื่อ JavaScript ทำงานหรือรันฟังก์ชัน `_show()` ข้อความจะถูกเขียนทับด้วยภาษาไทยเสมอ และ Google Translate มักไม่แปล Element ที่ถูกสร้างแบบไดนามิกหลังเพจโหลด

### 2.2 ปัญหาที่ 2: เมนูภาษาซ้อนทับกับปุ่ม "ดูตัวอย่าง"
- **สาเหตุ:**
  1. **Stacking Context Bug:** ใน [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css):
     - `.top-navbar` มี `z-index: 1020`
     - `.floating-doc-btn` มี `z-index: 1040`
     แม้ `.lang-dropdown-menu` จะมี `z-index: 1060` แต่อยู่ใต้ Stacking Context ของ `.top-navbar` (1020) ทำให้ปุ่ม "ดูตัวอย่าง" (1040) **ถูกวาดทับอยู่ด้านหน้าเมนูเลือกภาษา**
  2. **ตำแหน่งทับซ้อน:** ปุ่ม "ดูตัวอย่าง" ลอยอยู่ที่ `top: 70px; right: 24px` ขณะที่เมนูภาษาเปิดลงมาจากแถบบาร์ที่ `top: 54px` จึงชนกันตรงๆ และปุ่มตัวอย่างที่เป็นกระจกสีฟ้าโปร่งใสทำให้ตัวหนังสือ Thai/English ถูกทับจนอ่านยาก

---

## 3. รายละเอียดแผนการแก้ไข (Technical Implementation Plan)

### ส่วนที่ 1: ปรับแต่ง Auto-Draft รองรับ 2 ภาษา (`auto_draft.js`)
- **การตรวจจับภาษาปัจจุบัน (`getLang`):**
  - ตรวจสอบจาก Cookie `googtrans` (เช่น `/th/en`)
  - ตรวจสอบจากป้ายกำกับ `#langCurrentLabel` (TH / EN)
  - ตรวจสอบจาก `document.documentElement.lang` หรือคลาส `translated-ltr`
- **ตารางคำแปล (I18N Dictionary):**
  | สถานะ (State) | ภาษาไทย (TH) | ภาษาอังกฤษ (EN) |
  |---|---|---|
  | `idle` | ระบบบันทึกร่างอัตโนมัติทำงานอยู่ | Auto-save is active |
  | `idle_mirror` | ข้อมูลตรงกับที่บันทึกไว้ล่าสุด | All changes up to date |
  | `dirty` | มีการแก้ไขที่ยังไม่บันทึก | Unsaved changes |
  | `dirty_mirror` | มีการแก้ไขที่ยังไม่บันทึก — ระบบจะบันทึกให้ก่อนส่ง | Unsaved changes — system will save before submit |
  | `saving` | กำลังบันทึกร่าง | Saving draft |
  | `saved` | บันทึกร่างล่าสุด {time} | Draft saved {time} |
  | `error` | บันทึกร่างไม่สำเร็จ — กรุณาลองใหม่ | Draft save failed — please retry |
  | `disabled` | ปิดบันทึกร่างอัตโนมัติ | Auto-save disabled |
- **รูปแบบเวลา:**
  - ไทย: `14:30 น.`
  - อังกฤษ: `14:30`
- **การอัปเดตเมื่อสลับภาษา:**
  - เพิ่ม Event Listener ดักฟังการเปลี่ยนภาษา หรือ MutationObserver บน `document.documentElement` เพื่อให้ชิปเปลี่ยนภาษาทันทีโดยไม่ต้องรีโหลดหน้า
  - เพิ่มคลาส `notranslate` ให้กับ `.adb` เพื่อป้องกัน Google Translate แปลซ้อน

### ส่วนที่ 2: แก้ไขการทับซ้อนของเมนูเลือกภาษา (`style.css` & `base_academic.html`)
- **แก้ไข Z-Index ของ Topbar ใน [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css):**
  - เปลี่ยน `.top-navbar` จาก `z-index: 1020` เป็น `z-index: 1045`
  - ทำให้ Dropdown เมนูทั้งหมดจาก Navbar (เมนูภาษา, การแจ้งเตือน, ผลการค้นหา) ลอยอยู่ **เหนือ** ปุ่มลอยเอกสาร (`z-index: 1040`) เสมอ
- **ปรับแต่งเมนูภาษาใน [base_academic.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html):**
  - กำหนดพื้นหลังทึบ `var(--bg-card, #ffffff)` พร้อมเงา `box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15)` เพื่อให้เมื่อเปิดเมนู เมนูจะบดบังส่วนหลังได้อย่างเรียบร้อย ชัดเจน 100%
  - เมื่อผู้ใช้คลิกเลือกภาษา จะคลิกได้ตรงจุด ไม่โดนปุ่มดูตัวอย่างขวาง

---

## 4. แผนการตรวจสอบความถูกต้อง (Verification Checklist)

- [ ] **การแสดงผลสถานะ Auto-Draft:**
  - เมื่อหน้าเว็บเป็นภาษาไทย: แสดง "ระบบบันทึกร่างอัตโนมัติทำงานอยู่", "กำลังบันทึกร่าง", "บันทึกร่างล่าสุด 14:30 น."
  - เมื่อสลับหน้าเว็บเป็นภาษาอังกฤษ: แสดง "Auto-save is active", "Saving draft", "Draft saved 14:30"
- [ ] **การสลับภาษาแบบทันที:**
  - เมื่อคลิกเปลี่ยนภาษา TH <-> EN ชิปเปลี่ยนข้อความเป็นภาษาที่เลือกทันที
- [ ] **การแสดงผลเมนูเลือกภาษา:**
  - เมื่อกดปุ่มเลือกภาษา `[A文 TH]` เมนูตัวเลือก (Thai, English) เปิดลอยอยู่ **ด้านหน้า** ปุ่ม "ดูตัวอย่าง" อย่างชัดเจน
  - ตัวหนังสือ Thai และ English คมชัด ไม่มีปุ่มหรือตัวหนังสืออื่นมาทับซ้อน
  - สามารถคลิกเลือกเปลี่ยนภาษาได้อย่างราบรื่น
- [ ] **Dark Theme:**
  - ทั้งชิปและเมนูเลือกภาษารองรับและแสดงผลถูกต้องทั้งใน Light Mode และ Dark Mode
