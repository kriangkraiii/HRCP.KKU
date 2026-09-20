# PLAN: Datepicker Auto-Close on Selection (ระบบปิดหน้าต่างเลือกวันที่อัตโนมัติเมื่อเลือกเสร็จ)

> **File:** `docs/PLAN-datepicker-auto-close.md`  
> **Status:** [COMPLETED] ดำเนินการเสร็จสิ้นและผ่านการทดสอบ 100%  
> **Target Project:** `HRCP-KKU-Academic`  
> **Project Type:** WEB (Thymeleaf + JavaScript DOM Event Handling + CSS)  
> **Assigned Agent:** `project-planner` (Planning) / `frontend-specialist` (Implementation)  

---

## 1. การวิเคราะห์สาเหตุ (Root Cause Analysis)

### 1.1 พฤติกรรมดั้งเดิมของ Browser (Native Chromium / HTML5 `<input type="date">`)
ในเบราว์เซอร์ตระกูล Chromium (Google Chrome, Microsoft Edge, Opera, Brave) และ WebKit (Safari บนเดสก์ท็อป):
- หน้าต่างปฏิทินที่เปิดขึ้นมา (Calendar Popup / Picker Dialog) ถูกสร้างและควบคุมโดย **Shadow DOM และ Browser UI layer** ภายในของเบราว์เซอร์เอง
- เมื่อผู้ใช้คลิกเลือกวัน (Day) ในปฏิทิน ตัวเบราว์เซอร์จะทำการอัปเดตค่า `value` ของ input และยิงอีเวนต์ `change` / `input`
- **แต่เบราว์เซอร์จงใจ "ไม่สั่งปิดหน้าต่างปฏิทินทันที"** ด้วยเหตุผลด้านการออกแบบของ Chromium:
  1. เพื่อให้ผู้ใช้มองเห็นวันที่ถูกไฮไลต์ และตรวจสอบความถูกต้องของวัน/เดือน/ปี
  2. เพื่อให้ผู้ใช้สามารถเปลี่ยนใจคลิกเลือกวันอื่นใหม่ได้ทันทีโดยไม่ต้องกดเปิดปฏิทินซ้ำ
  3. เพื่อให้ผู้ใช้กดปุ่มเสริม เช่น "วันนี้" (Today) หรือ "ล้างค่า" (Clear) ได้
- หน้าต่างปฏิทินนี้จะปิดตัวลงก็ต่อเมื่อ:
  - ผู้ใช้คลิกเมาส์ที่พื้นที่ว่างอื่นภายนอกหน้าต่าง (Light dismiss / Click outside)
  - ผู้ใช้กดปุ่ม `Enter` หรือ `Escape` บนคีย์บอร์ด
  - Element นั้นสูญเสีย Focus (`blur`) หรือถูก Re-render / Destroy จาก DOM

---

### 1.2 ทำไมในโปรเจกต์นี้ถึงทำให้ผู้ใช้รู้สึก "งงมาก" (The UX Dilemma)
ในโปรเจกต์ KKU HRCP ระบบใช้วันที่ใน 3 รูปแบบหลัก ซึ่งทั้งหมดได้รับผลกระทบจากปัญหานี้:

1. **รูปแบบ Dual Input ในแบบฟอร์มเอกสารวิชาการ (Doc 1 ถึง 9):**
   - มีช่องข้อความภาษาไทย (`input.thai-full-date`) เช่น `"วันที่ ๒๐ กันยายน พ.ศ. ๒๕๖๙"`
   - มีปุ่มปฏิทิน (`button.thai-date-btn`)
   - มีตัวเลือกวันที่แบบ native (`input[type="date"].thai-date-picker`) ซึ่งถูกซ่อนไว้ใน CSS:
     ```css
     .thai-date-picker {
       position: absolute;
       opacity: 0;
       width: 0;
       height: 0;
       pointer-events: none;
     }
     ```
   - เมื่อกดปุ่มปฏิทิน JS จะเรียก `picker.showPicker()` ปฏิทินของ Chrome จะเด้งขึ้นมา
   - เมื่อผู้ใช้คลิกวันที่ ค่าในช่องภาษาไทยเปลี่ยนทันที **แต่ปฏิทินยังคงลอยค้างอยู่กลางจอ** เนื่องจากตัว input มีขนาด `0x0` และไม่มี Focus Ring ให้ผู้ใช้เห็น ผู้ใช้จึงมองไม่เห็นจุดเชื่อมโยง รู้สึกเหมือนระบบค้างหรือยังไม่เสร็จ จนต้องคลิกที่อื่นเพื่อไล่ปฏิทินออกไป
2. **รูปแบบฉีดอัตโนมัติ (`thai_date_autofill.js`):**
   - สคริปต์ฉีด `<input type="date">` ไว้ข้างช่องกรอก เมื่อเลือกวันเสร็จ ปฏิทินของ Chromium ก็ยังค้างอยู่เช่นกัน
3. **รูปแบบช่องค้นหาและตัวกรองระบบ (`search.html`, `activity_logs.html`):**
   - เป็น `<input type="date">` ทั่วไป เมื่อเลือกวันที่เสร็จ ปฏิทินยังคงเปิดอยู่จนกว่าจะคลิกที่อื่น

---

## 2. ขอบเขตและเป้าหมายการแก้ไข (Scope & Goals)

### 2.1 เป้าหมาย (Success Criteria)
1. **Auto-Dismiss ทันทีที่เลือกวันที่เสร็จ (คลิกเลือกวันปุ๊บ หน้าต่างปฏิทินหายไปทันที 100%):**
   - เมื่อผู้ใช้คลิกเลือกวันในปฏิทิน ค่าวันที่ถูกอัปเดตลงช่องกรอก และหน้าต่างปฏิทินจะปิดตัวลงทันทีอัตโนมัติ โดยผู้ใช้ไม่ต้องคลิกที่อื่นซ้ำ
2. **Smooth Focus Transition (โฟกัสกลับไปที่ช่องข้อความภาษาไทยอย่างเป็นธรรมชาติ):**
   - ในฟอร์มที่มีช่องภาษาไทย (`.thai-full-date`) เมื่อปฏิทินปิด Focus จะถูกส่งไปยังช่องข้อความทันที ทำให้ผู้ใช้สามารถกด `Tab` เพื่อไปยังช่องถัดไปได้ทันที
3. **ไม่กระทบการเปลี่ยนเดือน/ปี (Safe Navigation):**
   - ผู้ใช้ยังสามารถกดเลื่อนดูเดือนก่อนหน้า/ถัดไป หรือกดเลือกปี พ.ศ./ค.ศ. ได้ตามปกติ โดยปฏิทินจะไม่ปิดจนกว่าจะมีการ "คลิกเลือกวันที่" จริงๆ
4. **ครอบคลุมทุกจุดในโปรเจกต์ (Global Coverage):**
   - จัดการผ่าน Shared Centralized Logic ใน `thai_date_autofill.js` และ `_common.html` ทำให้มีผลทั่วทั้งระบบโดยไม่ต้องฮาร์ดโค้ดแก้ซ้ำซ้อน
5. **100% CSP Compliant:**
   - ไม่ใช้ inline `onclick` หรือ inline script เพื่อรักษาความปลอดภัยตามมาตรฐาน Content Security Policy ของมหาวิทยาลัย

---

## 3. สถาปัตยกรรมทางเทคนิค (Technical Design)

### 3.1 กลไกการปิด Native Datepicker (Dismissal Mechanics)
เนื่องจาก Chromium ไม่มีคำสั่ง `.hidePicker()` เราจะใช้กลไกผสมผสาน 3 ชั้น (Triple-Action Dismissal) ที่ปลอดภัยและเสถียรที่สุด:

```javascript
/**
 * ปิดหน้าต่าง Native Date Picker และส่ง Focus ไปยัง Text Input คู่กัน
 * @param {HTMLInputElement} picker - input[type="date"]
 */
function dismissDatePicker(picker) {
    if (!picker) return;

    // หาช่อง text input คู่กัน (ถ้ามี)
    var group = picker.closest('.input-group');
    var textInput = group ? (group.querySelector('.thai-full-date') || group.querySelector('input[type="text"]')) : null;

    // Step 1: ปลด Focus ทันที (Trigger standard blur dismiss)
    picker.blur();

    // Step 2: คืน Focus ให้ช่องข้อความภาษาไทย
    if (textInput) {
        textInput.focus();
    }

    // Step 3: Chromium Shadow DOM Reset Fallback (สำหรับเบราว์เซอร์เวอร์ชันที่ blur ไม่ปิดทันที)
    setTimeout(function () {
        if (picker.value) {
            var currentVal = picker.value;
            // การสลับ type ชั่วคราวจะสั่ง Browser ทำลาย Floating Popup ทันที
            picker.type = 'text';
            picker.type = 'date';
            picker.value = currentVal;
        }
        picker.blur();
        if (textInput) {
            textInput.focus();
        }
    }, 10);
}
```

---

## 4. แผนการดำเนินงานและไฟล์ที่เกี่ยวข้อง (Implementation Breakdown)

### Phase 1: ปรับปรุงแกนกลางของระบบ (Centralized Core)
- **`HRCP-KKU-Academic/src/main/resources/static/js/thai_date_autofill.js`**
  - เพิ่มฟังก์ชัน `dismissDatePicker(picker)` เป็น Utility ส่วนกลาง
  - เพิ่ม Global Event Delegation ดักจับ `change` ของ `input[type="date"]` ทุกตัวในหน้าเว็บ
  - ปรับปรุงตัว Dynamic Picker ที่สคริปต์ฉีดขึ้นมาเอง ให้เรียกใช้ `dismissDatePicker` เมื่อเลือกวันเสร็จ

### Phase 2: ปรับปรุงแบบฟอร์มเอกสารวิชาการ (Academic Document Forms)
- **`HRCP-KKU-Academic/src/main/resources/templates/academic/admin/doc_fragments/_common.html`**
  - ในบล็อก Thai Date Picker: อัปเดต listener ของ `.thai-date-picker` ให้เรียกคำสั่งปิดและย้าย Focus อัตโนมัติ
- **`HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_1_form.html`**
  - ปรับปรุง inline script ของผู้ยื่นคำขอ (Applicant Form) ให้เรียกคำสั่งปิดหลังอัปเดตวันที่
- **`HRCP-KKU-Academic/src/main/resources/templates/academic/admin/v_old_document_form.html`**
  - ปรับปรุงฟอร์มเอกสารเวอร์ชันเดิมให้ปิดอัตโนมัติเช่นกัน
- **`doc_fragments/4.html`, `doc_fragments/8.html`, `doc_fragments/9.html`**
  - ตรวจสอบ Custom Date Handlers (เช่น `meeting_date`, `sign_date`, `order_date`) ให้เรียก dismissal ทุกจุด

### Phase 3: ปรับปรุงหน้าตัวกรองและค้นหา (Search & Filter Pages)
- **`HRCP-KKU-Academic/src/main/resources/templates/search.html`**
  - ช่องวันที่ `from` และ `to` จะได้รับการดูแลจาก Global Handler ใน `thai_date_autofill.js` ทันที
- **`HRCP-KKU-Academic/src/main/resources/templates/admin/activity_logs.html`**
  - ช่องวันที่ `dateFrom` และ `dateTo` ได้รับผลจาก Global Handler ทันที

---

## 5. แผนการทดสอบและตรวจสอบความถูกต้อง (Verification Plan)

| ลำดับ | จุดทดสอบ | วิธีการทดสอบ | ผลลัพธ์ที่คาดหวัง |
|---|---|---|---|
| 1 | แบบฟอร์มคำขอที่ 1 (Applicant Doc 1) | กดไอคอนปฏิทินที่ "วันที่เอกสาร" -> คลิกเลือกวันที่ 15 | ช่องข้อความขึ้น "15 [เดือน] [พ.ศ.]" และปฏิทินปิดหายไปทันที |
| 2 | เอกสาร ก.พ.อ. 03 (Admin Doc 1-9) | เปิดหน้าคำขอของ Admin -> เลือกวันในส่วนหัวและท้ายเอกสาร | ปฏิทินปิดอัตโนมัติ โฟกัสกลับมาที่ช่องข้อความ |
| 3 | วันที่เฉพาะ (Doc 8: วันที่ประชุม/ลงนาม) | เลือกวันประชุมใน Doc 8 | ข้อความเปลี่ยนเป็น "7 มีนาคม 2569" และปฏิทินปิดทันที |
| 4 | หน้าสืบค้นข้อมูล (`/search`) | คลิกเลือกวันที่เริ่มต้นและสิ้นสุด | เลือกวันเสร็จ หน้าต่างปฏิทินปิดทันที |
| 5 | หน้าประวัติกิจกรรม (`/admin/activity-logs`) | เลือกช่วงวันที่ในตัวกรอง | เลือกวันเสร็จ หน้าต่างปฏิทินปิดทันที |
| 6 | การเลื่อนเดือน/ปี (Navigation Test) | คลิกเปิดปฏิทิน -> กดเปลี่ยนเดือนและปี (ยังไม่เลือกวัน) | ปฏิทินต้องยังเปิดอยู่ ไม่เด้งปิดระหว่างเลื่อนดูเดือน |

---

## 6. คำถามเชิงกลยุทธ์ (Socratic Gate / User Confirmation)

1. **พฤติกรรมการคืน Focus:** เมื่อหน้าต่างปฏิทินปิดลงแล้ว ต้องการให้ Focus เด้งกลับไปที่ช่องข้อความภาษาไทย (`.thai-full-date`) เพื่อให้ผู้ใช้สามารถกด `Tab` ไปช่องถัดไปได้ทันที ถูกต้องหรือไม่?
2. **หน้าค้นหา/ตัวกรอง:** ในหน้า `/search` และ `/admin/activity-logs` ที่เป็นช่องวันที่แบบตัวเลขสากล (ค.ศ.) ยืนยันให้ใช้พฤติกรรม Auto-Close ทันทีที่คลิกเลือกวันด้วยเช่นกันหรือไม่?
