# แผนงาน: ย้ายปุ่มบันทึกฉบับร่างมาไว้ข้างปุ่มบันทึกและส่งลงนามทั้งโครงการ (PLAN-reposition-draft-button.md)

> **สถานะ**: ร่างแผนงาน (รอการยืนยันก่อนเริ่มสร้างโค้ดตามแนวทาง `/plan`)  
> **ผู้รับผิดชอบหลัก**: `@[project-planner]` & `@[frontend-specialist]`  
> **เป้าหมาย**: ปรับตำแหน่งปุ่ม "บันทึกฉบับร่าง" ให้มาอยู่เคียงข้างปุ่ม "บันทึกและส่งลงนาม" (หรือ "ยืนยันความถูกต้องและส่งเวียนลงนาม" สำหรับเจ้าหน้าที่) ในแผงลงนามอิเล็กทรอนิกส์ (`_signature_panel.html`) ในทุกหน้าเอกสารของทั้งระบบ (Academic & Position) เพื่อให้ UX เป็นหนึ่งเดียวและผู้ใช้ตัดสินใจได้ชัดเจนในจุดเดียว

---

## 1. บริบทและปัญหาปัจจุบัน (Context & Problem Statement)

1. **ปัญหาปัจจุบัน**:
   - ปัจจุบันในหน้ากรอกเอกสารทุกแบบฟอร์ม (ทั้งแบบประเมินผลการสอนและแบบคำขอกำหนดตำแหน่งทางวิชาการ):
     - ปุ่ม **"บันทึกฉบับร่าง"** (หรือ "บันทึกไว้ก่อน") วางอยู่ด้านล่างสุดของ `<form>` ข้อมูลเอกสาร เคียงข้างปุ่ม "ดูตัวอย่างเอกสาร" และ "กลับ"
     - แผงลงนามอิเล็กทรอนิกส์ (`_signature_panel.html`) ถูกวางต่อท้ายด้านล่างของเอกสาร ซึ่งมีปุ่มหลักคือ **"บันทึกและส่งลงนามในส่วนของผู้ยื่นคำร้อง"** (สำหรับผู้ยื่น) หรือ **"ยืนยันความถูกต้องและส่งเวียนลงนาม"** (สำหรับเจ้าหน้าที่)
   - การแยกปุ่มบันทึกเอกสารออกจากปุ่มส่งลงนาม ทำให้ผู้ใช้สับสนว่าต้องกดปุ่มไหนก่อน และเมื่อเลื่อนลงมาตรวจสอบแผงลงนามที่ด้านล่าง จะไม่มีปุ่มให้เลือก "แค่บันทึกแบบร่างไว้ก่อน ยังไม่ส่งเวียน" อยู่ตรงจุดตัดสินใจสุดท้าย

2. **เป้าหมายการปรับปรุง**:
   - ย้ายปุ่ม **"💾 บันทึกฉบับร่าง"** มาวางคู่กับปุ่ม **"✈️ บันทึกและส่งลงนาม..."** ที่แผงลงนาม (`_signature_panel.html`)
   - นำปุ่ม "บันทึกฉบับร่าง" ซ้ำซ้อนเดิมออกจากท้ายฟอร์มเอกสารทุกหน้า (คงเหลือปุ่ม "ดูตัวอย่างเอกสาร" และปุ่ม "กลับ" ไว้)
   - วางระบบกลไก JavaScript ให้ปุ่มบันทึกฉบับร่างที่อยู่ในแผงลงนามสามารถสั่งบันทึกฟอร์มเอกสาร (Parent Form) ได้อย่างถูกต้อง พร้อมรองรับ Validation ของแต่ละเอกสาร (เช่น เอกสารที่ 2 ที่ต้องติ๊ก 5 ข้อ)

---

## 2. ขอบเขตไฟล์ที่เกี่ยวข้องในโปรเจกต์ (Impact Analysis)

### ก. ศูนย์กลางหลัก (Single Source of Truth)
- `HRCP-KKU-Academic/src/main/resources/templates/academic/esign/_signature_panel.html`:
  - บรรทัด 499-503: เพิ่มปุ่ม `[💾 บันทึกฉบับร่าง]` ข้างปุ่ม `[✈️ บันทึกและส่งลงนาม...]` ภายใน `<form th:unless="${signaturePanel.locked}" ...>`
- `HRCP-KKU-Academic/src/main/resources/static/js/sign_presave.js` (หรือ Script ช่วยเหลือในแผงลงนาม):
  - เพิ่ม Event Handler สำหรับปุ่มบันทึกฉบับร่าง เพื่อค้นหาฟอร์มเอกสารและทำการ submit พร้อมส่ง parameter `action=submit`

### ข. เอกสารประเมินผลการสอน (Academic Evaluation - ฝั่งผู้ยื่น)
- `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_1_form.html`:
  - ลบปุ่ม `#doc1SubmitBtn` ออกจากท้ายฟอร์มเอกสาร (เหลือปุ่มดูตัวอย่างเอกสาร)
- `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_2_form.html`:
  - ลบปุ่ม `#doc2SubmitBtn` ออกจากท้ายฟอร์มเอกสาร
  - ปรับปรุง JavaScript เช็คกล่อง 5 ข้อ ให้เปิด/ปิดสถานะ `disabled` ของปุ่มบันทึกฉบับร่างในแผงลงนามด้วย

### ค. เอกสารประเมินผลการสอน (Academic Evaluation - ฝั่งเจ้าหน้าที่/Admin)
- `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/doc_fragments/_common.html`:
  - ปรับ `th:fragment="buttons"` โดยนำ `#btnSubmitDoc` ออกจากแถบปุ่มเดิม (เหลือดูตัวอย่างเอกสารและปุ่มกลับ)
  - ทั้ง 9 เอกสาร (`doc_fragments/1.html` ถึง `9.html`) ใช้ fragment นี้ร่วมกันอยู่แล้ว จึงได้รับการอัปเดตพร้อมกันทันที

### ง. เอกสารกำหนดตำแหน่งทางวิชาการ (Academic Position - ฝั่งผู้ยื่น)
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_1.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_2.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_3.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_4.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_5.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_6.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_9.html`
  - ลบปุ่ม `[บันทึกฉบับร่าง]` ที่เดิมอยู่ข้างปุ่มดูตัวอย่างและกลับ

### จ. เอกสารกำหนดตำแหน่งทางวิชาการ (Academic Position - ฝั่งเจ้าหน้าที่/Admin)
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_1.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_2.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_3.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_4.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_5.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_6.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_7.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_8.html`
- `HRCP-KKU-Academic/src/main/resources/templates/academic/position/admin/doc_form_9.html`
  - ลบปุ่ม `[บันทึกฉบับร่าง]` ที่เดิมอยู่ข้างปุ่มดูตัวอย่างและกลับ

---

## 3. สถาปัตยกรรมการทำงานและโครงสร้าง HTML/JS (Solutioning Architecture)

### 3.1 โครงสร้างปุ่มในแผงลงนาม (`_signature_panel.html`)
```html
<div class="d-flex flex-wrap gap-2 align-items-center mt-3">
    <!-- ปุ่มรอง: บันทึกฉบับร่าง (Secondary Button) -->
    <button type="button" class="btn btn-outline-secondary px-4 py-2" id="panelSaveDraftBtn" data-action="save-doc-draft">
        <i class="fas fa-save me-1"></i> บันทึกฉบับร่าง
    </button>

    <!-- ปุ่มหลัก: ส่งลงนาม (Primary Button) -->
    <button type="submit" class="btn btn-academic px-4 py-2">
        <i class="fas fa-paper-plane me-1"></i>
        <span th:if="${!signaturePanel.isAdminViewer}">บันทึกและส่งลงนามในส่วนของผู้ยื่นคำร้อง</span>
        <span th:if="${signaturePanel.isAdminViewer}">ยืนยันความถูกต้องและส่งเวียนลงนาม</span>
    </button>
</div>
```

### 3.2 กลไกการ Trigger บันทึกฟอร์มเอกสาร
เนื่องจากปุ่มอยู่ใน `<form th:action="@{/esign/envelope/create}">` ของแผงลงนาม:
1. เมื่อผู้ใช้คลิก `[บันทึกฉบับร่าง]` (`#panelSaveDraftBtn`):
   - ค้นหาฟอร์มเอกสารหลักบนหน้าผ่าน Selector:
     `var docForm = document.querySelector('form[id^="doc"], form[data-auto-draft], form[action*="/document"], form[action*="/doc/"]')`
   - ตรวจสอบความถูกต้องของข้อมูล (Form Validation / reportValidity)
   - กำหนดค่า `action=submit` เพื่อให้ Controller บันทึกข้อมูลและสร้างไฟล์เอกสาร
   - ทำการ submit `docForm` ตรง ๆ โดยไม่ส่งซองเอกสารไปลงนาม

---

## 4. แผนการดำเนินงานเป็นลำดับขั้น (Task Breakdown)

- [ ] **Task 1: อัปเดตแผงลงนามอิเล็กทรอนิกส์ (`_signature_panel.html`)**
  - เพิ่มปุ่มบันทึกฉบับร่างเคียงข้างปุ่มส่งลงนาม
  - เพิ่ม JavaScript Handler ให้รองรับการกดบันทึกเอกสารหลัก
- [ ] **Task 2: ปรับปรุงแบบฟอร์มเอกสาร Academic Applicant (Doc 1 & Doc 2)**
  - ลบปุ่มบันทึกเดิมออกจากท้ายฟอร์ม
  - ซิงค์สถานะความถูกต้องของ Doc 2 (Checkbox 5 ข้อ) กับปุ่มบันทึกฉบับร่างใหม่
- [ ] **Task 3: ปรับปรุงแบบฟอร์ม Academic Admin (`_common.html`)**
  - นำปุ่ม `#btnSubmitDoc` ออกจาก `_common.html` fragment
- [ ] **Task 4: ปรับปรุงแบบฟอร์ม Position Applicant (Doc 1 ถึง 6, 9)**
  - ลบปุ่มบันทึกฉบับร่างเดิมออกทั้งหมด
- [ ] **Task 5: ปรับปรุงแบบฟอร์ม Position Admin (Doc 1 ถึง 9)**
  - ลบปุ่มบันทึกฉบับร่างเดิมออกทั้งหมด
- [ ] **Task 6: การทดสอบและการตรวจสอบ (Verification)**
  - ทดสอบการกด "บันทึกฉบับร่าง" ว่าบันทึกข้อมูลเข้าฐานข้อมูลและอัปเดตไฟล์จริง
  - ทดสอบการกด "บันทึกและส่งลงนาม..." ว่าทำงานตามกระบวนการเวียนลงนามปกติ
  - ทดสอบการแสดงผลบนหน้าจอทั้ง Desktop และ Mobile ว่าปุ่มจัดวางสวยงาม ไม่ทับซ้อน

---

## 5. การตรวจสอบความปลอดภัยและมาตรฐาน Clean Code
- ไม่มีผลกระทบต่อ CSRF Token เนื่องจากฟอร์มหลักส่ง Token ของตนเอง
- ปฏิบัติตามมาตรฐาน Thai Typography และ Icon FontAwesome 6
- ไม่มีการใช้สีต้องห้าม (ม่วง) และใช้ชุดสีมาตรฐานของระบบ (`btn-academic`, `btn-outline-secondary`)
