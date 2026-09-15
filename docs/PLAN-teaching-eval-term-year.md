# แผนการดำเนินงาน: ปรับฟอร์แมตการระบุปีการศึกษาในการยื่นประเมินการสอนเป็น "ภาค/ปีการศึกษา" (เช่น 1/2569)

**ไฟล์แผนงาน:** `docs/PLAN-teaching-eval-term-year.md`  
**สถานะ:** ดำเนินการเสร็จสมบูรณ์ (Completed)  
**ผู้รับผิดชอบหลัก:** `project-planner`, `frontend-specialist`, `backend-specialist`

---

## 1. ที่มาและเป้าหมาย (Context & Goals)

### 1.1 ปัญหาและความต้องการเดิม
ในการยื่นคำร้องขอรับการประเมินผลการสอน (เอกสารที่ 1: บันทึกข้อความ ขอรับการประเมินผลการสอน):
- ปัจจุบันช่องข้อมูลมีป้ายกำกับว่า **"ปีการศึกษา"** และมี placeholder ตัวอย่างเป็น `"เช่น 2568"` ซึ่งผู้ยื่นจะกรอกเฉพาะตัวเลขปี พ.ศ. 4 หลัก
- **ความต้องการใหม่:** ต้องการให้อาจารย์กรอกในฟอร์แมตที่มีทั้งภาคการศึกษา (เทอม) และปีการศึกษา เช่น `1/2569` (โดย `1` คือเทอม และ `2569` คือปีการศึกษา)

### 1.2 วัตถุประสงค์
1. ปรับปรุง UI ของแบบฟอร์มเอกสารที่ 1 (`document_1_form.html`) ให้มี Label, Placeholder และข้อความช่วยเหลือ (Helper text) ที่ชัดเจนสำหรับรูปแบบ `เทอม/ปีการศึกษา` (เช่น `1/2569`)
2. กำหนด Validation (ทั้ง Client-side และ Server-side) เพื่อป้องกันการกรอกผิดรูปแบบ
3. ปรับปรุงการนำค่าไปใช้งานต่อในเอกสารทางการ (DOCX Template `doc_1.docx`) และการดึงข้อมูลอัตโนมัติ (Autofill) ไปยังคำขอตำแหน่งทางวิชาการ (Phase 2 / ก.พ.ว. มข. 03) ให้รองรับและแยกเทอม/ปีได้อย่างถูกต้อง

---

## 2. การวิเคราะห์ผลกระทบ (Impact Analysis)

| จุดที่เกี่ยวข้อง | ไฟล์ | พฤติกรรมเดิม | การปรับปรุงที่เสนอ |
|---|---|---|---|
| **ฟอร์มผู้ยื่นคำร้อง (Doc 1)** | `templates/academic/applicant/document_1_form.html` | Label: "ปีการศึกษา", Placeholder: "เช่น 2568" | ปรับ Label เป็น "ภาค/ปีการศึกษา", Placeholder เป็น "เช่น 1/2569", เพิ่ม Helper Text แนะนำ, เพิ่ม Validation รูปแบบ `^\d/\d{4}$` |
| **ฟอร์ม Admin (Doc 1 Fragment)** | `templates/academic/admin/doc_fragments/1.html` | Label: "ปีการศึกษา", ไม่มี placeholder | ปรับ Label เป็น "ภาค/ปีการศึกษา", Placeholder "เช่น 1/2569" |
| **หน้าแสดงผลคำร้อง (Applicant & Admin)** | `applicant/request_detail.html`, `applicant/dashboard.html`, `admin/requests.html`, `admin/request_detail.html` | แสดงคำว่า "ปีการศึกษา {academic_year}" | ปรับข้อความนำหน้าเป็น "ภาค/ปีการศึกษา {academic_year}" เพื่อให้เข้ากับค่า "1/2569" |
| **การแทนค่าลง DOCX (`doc_1.docx`)** | `DocumentGenerationService.java` & `doc_1.docx` | Placeholder `{{academic_year}}` เดิมวางต่อท้ายคำว่า "ปีการศึกษา" | รองรับค่า `1/2569` (แสดงเป็น "ปีการศึกษา 1/2569" หรือปรับพรีโพรเซสให้เป็นทางการ) |
| **การสรุปผลและส่งต่อข้อมูล (Autofill)** | `AcademicRequestService.java`, `DocumentDataAutoFillHelper.java` | ดึงปีจาก `academic_year` ไปใส่ `teaching_eval_academic_year` และ `semester` | `AcademicRequestService.summarize` มีฟังก์ชัน `yearOf(text)` ที่ดึงเลข 4 หลักหลังสุดอยู่แล้ว ทำให้ดึงปี "2569" ได้อย่างถูกต้อง และสามารถสกัดเลขเทอม "1" ไปใส่ `semester` ได้โดยอัตโนมัติ |

---

## 3. แผนการดำเนินงาน (Task Breakdown)

### Task 1: ปรับปรุงแบบฟอร์มเอกสารที่ 1 ฝั่งผู้ยื่นคำร้อง (`frontend-specialist`)
- **ไฟล์:** `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_1_form.html`
- **การเปลี่ยนแปลง:**
  1. แก้ไข `<label>` จาก `ปีการศึกษา` เป็น `ภาค/ปีการศึกษา`
  2. แก้ไข `placeholder` จาก `เช่น 2568` เป็น `เช่น 1/2569`
  3. เพิ่มข้อความกำกับ `<small class="text-muted">รูปแบบ: [ภาคเรียน]/[ปีการศึกษา] เช่น 1/2569 หรือ 2/2569</small>`
  4. เพิ่ม HTML pattern: `pattern="^[1-3]/25[0-9]{2}$"` (หรือ `pattern="^[1-3]/[0-9]{4}$"`)
  5. ปรับปรุง JavaScript Validation ในฟอร์มตรวจจับฟอร์แมตและแจ้งเตือนข้อผิดพลาดก่อน Submit
- **INPUT:** โค้ดเดิมที่รับเฉพาะปี
- **OUTPUT:** ฟอร์มรองรับฟอร์แมต `1/2569` พร้อมระบบตรวจสอบความถูกต้อง
- **VERIFY:** เปิดหน้าฟอร์ม ทดสอบกรอกค่าที่ไม่ตรงรูปแบบ (เช่น `2568`, `abc`) ต้องแสดงข้อความเตือน และกรอก `1/2569` ผ่านได้

### Task 2: ปรับปรุงแบบฟอร์มฝั่งผู้ดูแลระบบ (`frontend-specialist`)
- **ไฟล์:** `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/doc_fragments/1.html`
- **การเปลี่ยนแปลง:**
  1. ปรับ Label เป็น `ภาค/ปีการศึกษา`
  2. ปรับ `placeholder="เช่น 1/2569"`
- **INPUT:** ฟอร์มแอดมินเดิม
- **OUTPUT:** ฟอร์มแอดมินสอดคล้องกับฟอร์มผู้ยื่น
- **VERIFY:** แอดมินสามารถเปิดดูและแก้ไขข้อมูลเอกสาร 1 เป็น `1/2569` ได้อย่างถูกต้อง

### Task 3: ปรับปรุงหน้าแสดงผลและตารางสรุปคำร้อง (`frontend-specialist`)
- **ไฟล์:**
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/request_detail.html`
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/dashboard.html`
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/requests.html`
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/request_detail.html`
- **การเปลี่ยนแปลง:**
  - เปลี่ยนป้ายกำกับจาก `ปีการศึกษา` เป็น `ภาค/ปีการศึกษา` ให้สอดคล้องกันทั้งหมด
- **INPUT:** ป้ายกำกับเดิม
- **OUTPUT:** แสดงผล "ภาค/ปีการศึกษา 1/2569" สวยงามและสื่อความหมายชัดเจน
- **VERIFY:** ตรวจสอบหน้า Dashboard และหน้ารายละเอียดคำร้องทั้งฝั่งผู้ยื่นและแอดมิน

### Task 4: ตรวจสอบและปรับปรุงการสร้างเอกสาร DOCX และ Autofill (`backend-specialist`)
- **ไฟล์:**
  - `HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/DocumentGenerationService.java`
  - `HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/AcademicRequestService.java`
- **การเปลี่ยนแปลง:**
  - ตรวจสอบการแทนค่า `{{academic_year}}` ใน `doc_1.docx`
  - ปรับปรุง `AcademicRequestService.summarize()` ให้หาก `academic_year` มีรูปแบบ `semester/year` (เช่น `1/2569`) จะแยก `semester` ("1") และ `academicYear` ("2569") นำไปใช้ใน `EvaluationSummary` ได้อย่างสมบูรณ์แบบ
- **INPUT:** สตริง `1/2569` จาก doc1Data
- **OUTPUT:** เอกสาร DOCX แสดงผลถูกต้อง และระบบคำขอตำแหน่งทางวิชาการ (Phase 2) ได้รับค่าทั้งภาคการศึกษาและปีการศึกษาโดยไม่ต้องกรอกซ้ำ
- **VERIFY:** สร้างไฟล์พรีวิว DOCX ของเอกสาร 1 และทดสอบ Autofill ไปยังคำขอตำแหน่ง

### Task 5: อัปเดตและรันชุดทดสอบ (Automated Unit & Integration Tests) (`test-engineer`)
- **ไฟล์:**
  - `HRCP-KKU-Academic/src/test/java/com/ecom/academic/EvaluationAutoFillTest.java`
  - `HRCP-KKU-Academic/src/test/java/com/ecom/academic/service/DocumentDataAutoFillHelperTest.java`
- **การเปลี่ยนแปลง:**
  - เพิ่มเคสทดสอบสำหรับฟอร์แมต `1/2569`
  - ยืนยันว่าการแยกปีการศึกษาและภาคเรียนทำงานได้อย่างถูกต้อง
- **INPUT:** ข้อมูลทดสอบแบบเดิม ("2568") และแบบใหม่ ("1/2569")
- **OUTPUT:** Tests รันผ่าน 100% (Backward compatible)
- **VERIFY:** รันคำสั่ง `./mvnw test` หรือ `mvn test` ผ่านทั้งหมด

---

## 4. ข้อพิจารณาและคำตอบที่ยืนยันแล้ว (Confirmed Decisions)

1. **การแสดงผลในเอกสาร Word (`doc_1.docx`):**
   - **ตัวเลือก A (ยืนยันแล้ว):** แสดงเป็น `... ปีการศึกษา 1/2569` (นำค่าที่กรอกแทนที่ลงใน placeholder `{{academic_year}}` โดยตรง ไม่แก้ไขไฟล์เทมเพลต DOCX)
2. **ขอบเขตของภาคเรียนที่อนุญาตให้กรอก:**
   - **ยืนยันแล้ว:** เปิดกว้างเป็นตัวเลขใดๆ คั่นด้วยเครื่องหมาย `/` (Regex: `^[0-9]+/[0-9]+$` เช่น `1/2569`, `2/2569`, `3/2569`)
3. **ป้ายกำกับหน้าจอ (UI Labels):**
   - ปรับชื่อช่องจาก **"ปีการศึกษา *"** เป็น **"ภาค/ปีการศึกษา *"** พร้อม Helper text ในทุกหน้าจอที่เกี่ยวข้อง (ทั้งฝั่งอาจารย์และแอดมิน)

---

## ✅ PHASE X COMPLETE
- Frontend Forms & UI: ✅ เรียบร้อย (Label, Placeholder, Helper text, JS Validation `^[0-9]+/[0-9]+$`)
- Backend & Autofill Integration: ✅ เรียบร้อย (`AcademicRequestService.summarize` แยกเทอมและปีถูกต้อง)
- Automated Tests: ✅ Pass 100% (`EvaluationAutoFillTest`, `DocumentDataAutoFillHelperTest`)
- Date: 2026-09-15

