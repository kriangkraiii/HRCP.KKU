# แผนงาน: แก้ไขปัญหาปุ่มกลับลอยทับแถบแจ้งเตือน และเอกสารแจ้งเตือนกรอกไม่ครบ (ขาดอีก 1 ช่อง)
# (PLAN-doc-complete-overlap.md)

> แผนการดำเนินงานตามคำสั่ง `/plan` สำหรับแก้ไข 2 ปัญหาหลักในระบบ CP-ACAD:
> 1. ปุ่มลอย `← กลับ` (`.floating-doc-btn-back`) ลอยทับแถบข้อความแจ้งเตือน Flash Alert (`.global-flash-alert`)
> 2. เมื่อกรอกข้อมูลในเอกสารครบถ้วนทุกช่องแล้ว แต่ระบบยังแจ้งเตือนว่า *"กรอกข้อมูลในเอกสารยังไม่ครบ (ขาดอีก 1 ช่อง) กรุณากรอกให้ครบแล้วบันทึกก่อนส่งไปลงนาม"*

---

## 1. การวิเคราะห์สาเหตุเชิงลึก (Root Cause Analysis)

### ปัญหาที่ 1: ปุ่ม `← กลับ` ลอยทับแถบแจ้งเตือนสีชมพู/แดง
1. **Bootstrap Utility Priority Overwrite:**
   - ใน [base_academic.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html) บรรทัด 832 คอนเทนเนอร์ของ Flash Alert กำหนดคลาสไว้เป็น:
     ```html
     <div class="global-flash-alert px-4 pt-3" th:if="${succMsg != null or errorMsg != null or warnMsg != null}">
     ```
   - คลาส `.pt-3` ของ Bootstrap 5 มีการระบุ `padding-top: 1rem !important;`
   - ในขณะที่ [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css) บรรทัด 568 กำหนดไว้ว่า:
     ```css
     body:has(.floating-doc-btn) .global-flash-alert {
       padding-top: 56px; /* ขาด !important */
     }
     ```
   - ส่งผลให้เบราว์เซอร์เพิกเฉยต่อ `padding-top: 56px` แล้วใช้ `1rem` (16px) ของ Bootstrap แทน
   - แถบแจ้งเตือนจึงขึ้นไปแสดงผลอยู่บริเวณความสูง `Y = 70px` พอดี ซึ่งเป็นพิกัดเดียวกับปุ่มลอย `top: 70px !important` ทำให้ปุ่มลอยทับบังข้อความแจ้งเตือน

### ปัญหาที่ 2: กรอกเอกสารครบแล้ว แต่ระบบบอกขาดอีก 1 ช่อง
1. **ความไม่สอดคล้องกันระหว่าง Front-end Radio และ Back-end Completeness:**
   - ในเอกสารที่ 1 (ประเมินการสอน - `document_1_form.html` และ `admin/doc_fragments/1.html`) ช่องเลือกตำแหน่งที่ขอ (ผศ. / รศ.) แสดงเป็น Radio button ให้ผู้ใช้เลือกได้เพียง 1 ตัวเลือก
   - แต่เบื้องหลังมีการซิงค์ค่าไปยัง `<input type="hidden" name="chk1">` และ `<input type="hidden" name="chk2">`:
     ```javascript
     if (r1.checked) {
         chk1.value = '✓';
         chk2.value = ''; // ว่างเปล่า
     } else if (r2.checked) {
         chk1.value = ''; // ว่างเปล่า
         chk2.value = '✓';
     }
     ```
   - เมื่อกดส่งเอกสาร `auto_draft.js` จะบันทึกฟอร์มลง JSON โดย `chk1` หรือ `chk2` ตัวที่ไม่ถูกเลือกจะมีค่าเป็น `""` (Empty string)
2. **การตรวจสอบของ `DocumentCompleteness.java`:**
   - ฟังก์ชัน `missingApplicantFields` วนลูปตรวจทุก Key ใน `data.entrySet()`
   - สำหรับโมดูล `ACADEMIC` เอกสาร 1 ไม่มีรายการใน `OPTIONAL_FIELDS`
   - และ `chk1` / `chk2` ไม่อยู่ใน `adminFields` และ `signerFields`
   - เมื่อพบว่า `chk2` (หรือ `chk1`) มีค่าเป็น `""` (`value.isBlank() == true`) ระบบจึงตัดสินว่า **"ยังกรอกไม่ครบ ขาดอีก 1 ช่อง"** เสมอ!
3. **ปัญหาแฝงในเอกสารอื่น (Defense-in-depth):**
   - เอกสารที่ 4 ของขอกำหนดตำแหน่ง (`doc_form_4.html`) มีช่อง `<input type="hidden" name="academic_paper_not_part_edu">`, `academic_paper_is_part_edu`, `research_not_part_edu`, `research_is_part_edu` ซึ่งทำงานเป็น Radio แบบเลือกข้อใดข้อหนึ่งเช่นเดียวกันและตั้งค่าว่าง `""` ไว้ และยังไม่ได้อยู่ใน `OPTIONAL_FIELDS` ของ Doc 4

---

## 2. ขอบเขตและเป้าหมาย (Scope & Objectives)

| ลำดับ | รายการ | ผลลัพธ์ที่ต้องการ |
|---|---|---|
| 1 | แก้ไข Layout Overlap | แถบ Flash Alert เว้นระยะห่างลงมาใต้ปุ่มลอย (`padding-top: 64px !important;`) ไม่ถูกบัง 100% |
| 2 | แก้ไข Academic Doc 1 Checkbox/Choice Logic | ผู้ใช้เลือก ผศ. หรือ รศ. อย่างใดอย่างหนึ่ง ถือว่ากรอกส่วนนี้ครบถ้วน ไม่ติดเงื่อนไขขาด 1 ช่อง |
| 3 | ปรับ Front-end Sync Script | ให้ช่องที่ไม่ถูกเลือกมีค่าเป็นสัญลักษณ์กล่องว่าง `'☐'` ตามมาตรฐาน DOCX Template แทนสตริงว่าง `''` |
| 4 | เพิ่มรายการ Optional Fields ใน `DocumentCompleteness.java` | ครอบคลุมช่องทางเลือกของเอกสาร 4 และเอกสารอื่นๆ ไม่ให้เกิด False Positive |

---

## 3. แผนการดำเนินงานและมอบหมายงาน (Task Breakdown)

### Task 1: แก้ไข Layout แถบแจ้งเตือนใน CSS และ Template
- **Agent:** `frontend-specialist` | **Skills:** `frontend-design`, `clean-code`
- **Priority:** P1
- **ไฟล์ที่เกี่ยวข้อง:**
  - `HRCP-KKU-Academic/src/main/resources/static/css/style.css`
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html`
- **รายละเอียด:**
  - ใน `base_academic.html`: นำคลาส `pt-3` ออกจาก `global-flash-alert` หรือเปลี่ยนเป็น `pt-0`
  - ใน `style.css`:
    ```css
    body:has(.floating-doc-btn) .global-flash-alert {
      padding-top: 64px !important;
    }
    @media (max-width: 991px) {
      body:has(.floating-doc-btn) .global-flash-alert {
        padding-top: 60px !important;
      }
    }
    ```
- **INPUT:** CSS class conflicts with Bootstrap `.pt-3`
- **OUTPUT:** แถบแจ้งเตือนถูกดันลงมาใต้ปุ่มลอยอย่างสวยงาม
- **VERIFY:** เปิดหน้าเอกสารที่มี Flash Alert ตรวจสอบว่าปุ่มลอยและข้อความแจ้งเตือนไม่ทับซ้อนกัน

### Task 2: แก้ไขกติกาความครบถ้วนใน `DocumentCompleteness.java`
- **Agent:** `backend-specialist` | **Skills:** `clean-code`, `testing-patterns`
- **Priority:** P0 (Critical Logic Blocker)
- **ไฟล์ที่เกี่ยวข้อง:**
  - `HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/DocumentCompleteness.java`
  - `HRCP-KKU-Academic/src/test/java/com/ecom/academic/service/DocumentCompletenessTest.java`
- **รายละเอียด:**
  - ใน `DocumentCompleteness.java`:
    1. สำหรับ `SignatureModule.ACADEMIC` เอกสารที่ 1: ช่อง `chk1`, `chk2`, `chk3` เป็นตัวเลือกตำแหน่งที่ขอประเมิน หากช่องใดช่องหนึ่งมีค่า `"✓"` หรือ `"✔"` ให้ถือว่าตัวเลือกอื่นไม่ขาด (Skip)
    2. สำหรับ `SignatureModule.POSITION` เอกสารที่ 4: เพิ่ม `academic_paper_not_part_edu`, `academic_paper_is_part_edu`, `research_not_part_edu`, `research_is_part_edu`, `paper_title_1`, `research_title_1`, `academic_paper_status`, `research_status`, `academic_paper_count`, `research_count` เข้าไปใน `OPTIONAL_FIELDS` ของ Doc 4
  - ใน `DocumentCompletenessTest.java`:
    - เพิ่ม Unit Test เคส Academic Doc 1: `data.put("chk1", "✓"); data.put("chk2", "");` ต้องได้ `missing.isEmpty() == true`
- **INPUT:** `missingApplicantFields` flagging empty mutually-exclusive radio hidden inputs
- **OUTPUT:** อนุญาตให้ส่งลงนามได้เมื่อผู้ยื่นเลือกตัวเลือกใดตัวเลือกหนึ่งแล้ว
- **VERIFY:** รัน `./mvnw test -Dtest=DocumentCompletenessTest` ผ่าน 100%

### Task 3: ปรับ Front-end Sync Script ของเอกสารที่ 1
- **Agent:** `frontend-specialist` | **Skills:** `javascript-pro`
- **Priority:** P1
- **ไฟล์ที่เกี่ยวข้อง:**
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_1_form.html`
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/doc_fragments/1.html`
- **รายละเอียด:**
  - ในฟังก์ชัน `syncTargetPos()`:
    - เมื่อ `r1.checked`: ตั้ง `chk1.value = '✓'` และ `chk2.value = '☐'`
    - เมื่อ `r2.checked`: ตั้ง `chk1.value = '☐'` และ `chk2.value = '✓'`
    - ป้องกันค่าว่างใน DOM ตั้งแต่ต้นทางเพื่อความสมบูรณ์ของ DOCX template
- **INPUT:** JavaScript function `syncTargetPos()` setting `""` on unchecked
- **OUTPUT:** ตั้งค่า `'☐'` ให้กับช่องที่ไม่ถูกเลือก
- **VERIFY:** ตรวจสอบค่า hidden input ใน DOM ก่อนและหลังเลือก Radio

---

## 4. แผนการตรวจสอบ (Verification Plan)

### Automated Tests
```bash
./mvnw test -Dtest=DocumentCompletenessTest
./mvnw test -Dtest=SignatureWorkflowServiceTest
```

### Manual Verification
1. **ทดสอบ UI แถบแจ้งเตือน:**
   - ทดสอบจำลอง flash message (`errorMsg`) ในหน้าเอกสาร
   - ตรวจสอบว่าปุ่ม `← กลับ` ลอยอยู่ที่มุมบนซ้าย และแถบสีชมพูอยู่ถัดลงมาด้านล่าง มองเห็นข้อความและไอคอนครบถ้วน ไม่ทับซ้อน
2. **ทดสอบการส่งลงนามเอกสารที่ 1:**
   - กรอกข้อมูลเอกสารที่ 1 ให้ครบ และเลือก "ผู้ช่วยศาสตราจารย์"
   - กดส่งไปลงนาม -> ระบบต้องไม่แจ้งเตือน "ขาดอีก 1 ช่อง" และสามารถเริ่มกระบวนการเวียนลงนามได้สำเร็จ
