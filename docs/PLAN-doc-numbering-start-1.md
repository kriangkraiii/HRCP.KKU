# Project Plan: ปรับปรุงลำดับเลขที่เอกสารทั้งสองเฟสให้เริ่มต้นที่ 1 (Document Numbering Re-indexing 1-9)

> **เอกสารแผนงาน:** `docs/PLAN-doc-numbering-start-1.md`  
> **ผู้รับผิดชอบหลัก:** `@[project-planner]` ร่วมกับ `@[backend-specialist]` และ `@[frontend-specialist]`  
> **สถานะ:** รอยืนยันเพื่อเริ่มพัฒนา (Planning Phase — NO CODE MODIFIED YET)

---

## 1. ที่มาและวัตถุประสงค์ (Problem Statement & Background)

ในระบบปัจจุบัน กระบวนการขอกำหนดตำแหน่งทางวิชาการแบ่งออกเป็น 2 เฟส (Phase 1 และ Phase 2) โดยมีโครงสร้างลำดับเลขที่เอกสารดังนี้:
1. **Phase 1 (การประเมินผลการสอน):** ปัจจุบันเริ่มต้นที่ **เอกสารที่ 0** ไปจนถึง **เอกสารที่ 8** (รวม 9 ฉบับ: `doc_0` ถึง `doc_8`)
2. **Phase 2 (การขอกำหนดตำแหน่งทางวิชาการ):** ปัจจุบันเริ่มต้นที่ **เอกสารที่ 1** ไปจนถึง **เอกสารที่ 9** (รวม 9 ฉบับ: `p2doc_1` ถึง `p2doc_9`)

**ปัญหาและความต้องการของผู้ใช้:**
- การที่ Phase 1 เริ่มต้นที่ `0` (Zero-indexed ในระดับ Business/UI) ในขณะที่ Phase 2 เริ่มต้นที่ `1` (One-indexed) ทำให้เกิดความสับสนต่อทั้งผู้ยื่นคำร้อง คณะกรรมการ และผู้ดูแลระบบ ทั้งในแง่ของข้อความในหน้าจอคู่มือ แบบฟอร์ม บันทึกข้อความราชการ และการอ้างอิงระหว่างระบบ
- **ความต้องการ:** ปรับให้ **ทั้งสองเฟสมีเลขที่เอกสารเริ่มต้นที่ 1 เหมือนกันทั้งหมด**
  - **Phase 1:** เปลี่ยนจาก `0–8` เป็น `1–9` (เลื่อนลำดับขึ้น +1 ทั้งระบบ)
  - **Phase 2:** ยืนยันและรักษามาตรฐานเริ่มต้นที่ `1–9` พร้อมปรับการเชื่อมโยงข้อมูลข้ามเฟส (เดิมดึงผลประเมินจากเอกสารที่ 8 ของ Phase 1 ให้เปลี่ยนเป็นดึงจากเอกสารที่ 9 ของ Phase 1)

---

## 2. ขอบเขตและตารางเทียบการเปลี่ยนแปลง (Document Mapping)

### 2.1 Phase 1: การประเมินผลการสอน (ปรับเปลี่ยนจาก 0–8 เป็น 1–9)

| ลำดับเดิม | ลำดับใหม่ | ชื่อเอกสารราชการ | ผู้รับผิดชอบ | เทมเพลต DOCX เดิม | เทมเพลต DOCX ใหม่ |
|:---:|:---:|:---|:---:|:---:|:---:|
| **0** | **1** | บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน | ผู้ยื่นคำร้อง | `doc_0.docx` | `doc_1.docx` |
| **1** | **2** | แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน | ผู้ยื่นคำร้อง | `doc_1.docx` | `doc_2.docx` |
| **2** | **3** | การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ | ผู้ดูแลระบบ | `doc_2.docx` | `doc_3.docx` |
| **3** | **4** | คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน | ผู้ดูแลระบบ | `doc_3.docx` | `doc_4.docx` |
| **4** | **5** | บันทึกข้อความ ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ | ผู้ดูแลระบบ | `doc_4.docx` | `doc_5.docx` |
| **5** | **6** | ข้อเสนอแนะจากคณะอนุกรรมการ | ผู้ดูแลระบบ | `doc_5.docx` | `doc_6.docx` |
| **6** | **7** | แบบฟอร์มประเมินการสอน ตามประกาศ มข. 1607-66 | ผู้ดูแลระบบ | `doc_6.docx` | `doc_7.docx` |
| **7** | **8** | ส่วนที่ 3 แบบประเมินผลการสอน | ผู้ดูแลระบบ | `doc_7.docx` | `doc_8.docx` |
| **8** | **9** | บันทึกข้อความ แจ้งผลการประเมินผลการสอน | ผู้ดูแลระบบ | `doc_8.docx` | `doc_9.docx` |

### 2.2 Phase 2: การขอกำหนดตำแหน่งทางวิชาการ (คงเดิม 1–9 และปรับจุดเชื่อมโยง)

| ลำดับเอกสาร | ชื่อเอกสารราชการ | ผู้รับผิดชอบ | เทมเพลต DOCX | สถานะการเปลี่ยนแปลง |
|:---:|:---|:---:|:---:|:---|
| **1** | แบบ ก.พ.ว. มข. 03 (ประวัติและผลงาน) | ผู้ยื่นคำร้อง | `p2doc_1.docx` | คงเดิม (เริ่มที่ 1) |
| **2** | หนังสือแจ้งความประสงค์เรื่องการรับรู้ข้อมูล | ผู้ยื่นคำร้อง | `p2doc_2.docx` | คงเดิม |
| **3** | แบบรับรองจริยธรรมและจรรยาบรรณ | ผู้ยื่นคำร้อง | `p2doc_3.docx` | คงเดิม |
| **4** | บันทึกรับรองผลงานทางวิชาการ (วิทยานิพนธ์) | ผู้ยื่นคำร้อง | `p2doc_4.docx` | คงเดิม |
| **5** | แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา | ผู้ดูแลระบบ | `p2doc_5.docx` | คงเดิม |
| **6** | บันทึกข้อความจริยธรรมการวิจัย (Exemption) | ผู้ยื่นคำร้อง | `p2doc_6.docx` | คงเดิม |
| **7** | แบบฟอร์มตรวจสอบคุณสมบัติ (Checklist) | ผู้ยื่น/จนท. | `p2doc_7.docx` | คงเดิม |
| **8** | แบบสรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ | ผู้ดูแลระบบ | `p2doc_8.docx` | คงเดิม |
| **9** | ลักษณะการมีส่วนร่วมในผลงาน | ผู้ยื่นคำร้อง | `p2doc_9.docx` | คงเดิม |

> [!IMPORTANT]
> **จุดเชื่อมโยงระหว่าง Phase 1 และ Phase 2:**
> ในหน้า Dashboard และหน้ารายละเอียดของ Phase 2 เดิมจะดึงผลการประเมินการสอนและวันที่หมดอายุจาก **เอกสารที่ 8** ของ Phase 1 ระบบจะต้องปรับให้ดึงจาก **เอกสารที่ 9** ของ Phase 1 แทน

---

## 3. การวิเคราะห์ผลกระทบเชิงลึก (In-Depth Impact Analysis)

การเปลี่ยน `documentType` ของ Phase 1 มีผลกระทบครอบคลุม 6 ส่วนสำคัญ:

1. **ฐานข้อมูลและการ Migrate ข้อมูลเดิม (Database & Data Migration):**
   - ตาราง `academic_documents`: คอลัมน์ `document_type` ปัจจุบันเก็บค่า `0` ถึง `8` ต้องทำ Migration script ให้เลื่อนค่าขึ้น +1 (เรียงอัปเดตจาก 8 ลงมา 0 เพื่อเลี่ยงชน Unique Constraint หากมี)
   - ตาราง `document_revisions`, `document_edit_logs`, `signature_envelopes`: ตารางที่ผูกกับ `module = 'ACADEMIC'` และ `document_type` ต้องปรับเลื่อน +1
   - สร้าง Flyway migration: `V22__shift_phase1_doc_numbers_to_start_at_1.sql`

2. **ไฟล์เทมเพลตต้นแบบและไฟล์แนบในระบบ (File System & Templates):**
   - เทมเพลต `src/main/resources/templates/docx/`: เปลี่ยนชื่อไฟล์ `doc_8.docx` -> `doc_9.docx` ... ไล่ลงมาจนถึง `doc_0.docx` -> `doc_1.docx`
   - ไฟล์เอกสารที่สร้างแล้วใน `uploads/academic/{id}/`: ต้องมีสคริปต์เปลี่ยนชื่อไฟล์เดิม `doc_{x}.docx` เป็น `doc_{x+1}.docx`

3. **Backend Logic & Controllers (`com.ecom.academic`):**
   - `AcademicRequestService.java`:
     - ปรับปรุง Map `DOC_LABELS` ให้มี Key `1..9`
     - กำหนด `APPLICANT_DOCS = [1, 2]` (เดิม `[0, 1]`)
     - กำหนด `ADMIN_DOCS = [3, 4, 5, 6, 7, 8, 9]` (เดิม `[2..8]`)
   - `AcademicApplicantController.java`:
     - อัปเดต Endpoint URL: `/applicant/academic/request/{id}/doc/1` (เดิม 0) และ `/doc/2` (เดิม 1)
     - อัปเดตการตรวจสอบความสมบูรณ์ก่อนส่งคำร้อง (ตรวจสอบ doc 1 และ doc 2)
   - `AcademicAdminController.java`:
     - อัปเดต Map `DOC_LABELS` (1..9)
     - อัปเดตตรรกะ Auto-fill: ข้อมูลรายวิชาและผู้ขอจาก **doc 1** (เดิม doc 0) ถูกส่งต่อไปยัง **doc 4, 7, 8, 9** (เดิม doc 3, 6, 7, 8)
   - `DocumentGenerationService.java`:
     - ปรับการเรียกเทมเพลต `doc_` + `documentType` + `.docx`
     - ปรับฟังก์ชันดึงข้อมูลรายวิชาจาก doc 1
   - `PositionRequestService.java` & `PositionApplicantController.java`:
     - ปรับการดึงผลประเมินผลการสอนจาก Phase 1 ให้ชี้ไปที่ doc 9 แทน doc 8

4. **ระบบลงนามดิจิทัล (E-Signature Integration):**
   - `SignedDocumentRenderer.java`: ปรับการระบุชื่อไฟล์และตำแหน่งการประทับตราสำหรับ `doc_1` ถึง `doc_9`
   - `DigitalSignatureService.java` และ `SignatureEnvelope`: การส่งซองลงนามสำหรับเอกสารคำร้อง Phase 1 ให้ระบุ `docType` 1..9

5. **Frontend Thymeleaf Templates:**
   - เปลี่ยนชื่อไฟล์:
     - `templates/academic/applicant/document_0_form.html` -> `document_1_form.html`
     - `templates/academic/applicant/document_1_form.html` -> `document_2_form.html`
     - `templates/academic/admin/doc_fragments/`: ปรับชื่อไฟล์ fragment จาก `0.html..8.html` เป็น `1.html..9.html`
   - ปรับปรุงข้อความและปุ่มใน:
     - `applicant/new_request.html`
     - `applicant/dashboard.html`
     - `applicant/request_detail.html`
     - `admin/requests.html` (ตารางรายการคำร้อง และ `doc0DataMap` -> `doc1DataMap`)
     - `admin/request_detail.html` (รายการแท็บเอกสาร 1–9)
     - `guide_user.html` และ `guide_admin.html` (คู่มือการใช้งาน)

6. **ชุดการทดสอบ (Automated Tests):**
   - ปรับปรุง Assertions และ Mock Data ใน:
     - `FullJourneyMockMvcTest.java`
     - `UAT_AcademicRequestWorkflowTest.java`
     - `UAT_AcademicApplicantTest.java`
     - `UAT_DocumentGenerationTest.java`
     - `EveryPageRendersTest.java`
     - `SignatureStampingTest.java`

---

## 4. แผนการดำเนินงานเป็นขั้นตอน (Task Breakdown)

| ลำดับงาน | รายละเอียดงาน | Agent / Skills | INPUT & OUTPUT | VERIFICATION |
|:---:|:---|:---:|:---|:---|
| **Phase 1** | **จัดเตรียมฐานข้อมูลและ Migration Script**<br>- สร้าง Flyway script `V22__shift_phase1_doc_numbers_to_start_at_1.sql`<br>- อัปเดต `document_type` ของคำร้องเดิมในตารางที่เกี่ยวข้อง | `backend-specialist`<br>`database-design` | **INPUT:** ข้อมูล doc 0–8 เดิม<br>**OUTPUT:** สคริปต์ SQL ขยับเลขเป็น 1–9 | ทดสอบรัน Migration กับฐานข้อมูลทดสอบ ตรวจสอบว่าไม่มี record ใดเป็น 0 |
| **Phase 2** | **เปลี่ยนชื่อเทมเพลต DOCX และไฟล์บนดิสก์**<br>- เปลี่ยนชื่อไฟล์ใน `templates/docx/`<br>- สคริปต์อัปเดตไฟล์ใน `uploads/academic/` | `backend-specialist`<br>`clean-code` | **INPUT:** `doc_0.docx`..`doc_8.docx`<br>**OUTPUT:** `doc_1.docx`..`doc_9.docx` | ตรวจสอบความถูกต้องของไฟล์เทมเพลตครบ 9 ฉบับ |
| **Phase 3** | **แก้ไข Service & Controller ฝั่ง Backend**<br>- `AcademicRequestService`<br>- `AcademicApplicantController`<br>- `AcademicAdminController`<br>- `DocumentGenerationService`<br>- `SignedDocumentRenderer`<br>- `PositionRequestService` (จุดเชื่อม Phase 1) | `backend-specialist`<br>`java-pro` | **INPUT:** โค้ดเดิมที่อ้างอิงเลข 0<br>**OUTPUT:** โค้ดที่รองรับ 1–9 และ auto-fill ถูกต้อง | คอมไพล์ Java ผ่าน (`mvn compile`) ไม่มีข้อผิดพลาด |
| **Phase 4** | **ปรับปรุงหน้ากาก UI & Thymeleaf Templates**<br>- เปลี่ยนชื่อไฟล์ฟอร์มผู้ยื่นคำร้อง<br>- ปรับ fragment แอดมิน<br>- แก้ไขข้อความในแดชบอร์ด รายละเอียดคำร้อง และคู่มือ | `frontend-specialist`<br>`frontend-design` | **INPUT:** Templates เดิม<br>**OUTPUT:** หน้าจอแสดง "เอกสารที่ 1" ถึง "เอกสารที่ 9" | `EveryPageRendersTest` ผ่าน 100% |
| **Phase 5** | **อัปเดตชุดทดสอบและทดสอบระบบครบวงจร**<br>- ปรับปรุง MockMvc และ UAT Tests ทั้งหมด<br>- รัน End-to-End Workflow | `test-engineer`<br>`testing-patterns` | **INPUT:** แผนงานทดสอบ<br>**OUTPUT:** รายงานผลการรัน Test สำเร็จ | รัน `./mvnw test` ผ่านทุกชุด |

---

## 5. เกณฑ์การตรวจรับและตรวจทาน (Verification & Definition of Done)

- [ ] ใน Phase 1 ไม่ปรากฏคำว่า "เอกสารที่ 0" ในหน้าจอของผู้ยื่นคำร้องและผู้ดูแลระบบ
- [ ] แบบบันทึกข้อความขอรับการประเมินผลการสอน แสดงเป็น "เอกสารที่ 1"
- [ ] แบบตรวจสอบเบื้องต้น แสดงเป็น "เอกสารที่ 2"
- [ ] แบบแจ้งผลการประเมินการสอน แสดงเป็น "เอกสารที่ 9"
- [ ] ใน Phase 2 เอกสารยังคงเริ่มต้นที่ "เอกสารที่ 1: แบบ ก.พ.ว. มข. 03" ถึง "เอกสารที่ 9: ลักษณะการมีส่วนร่วมในผลงาน" อย่างถูกต้อง
- [ ] Phase 2 ดึงผลการประเมินที่ผ่านและวันหมดอายุจาก Phase 1 เอกสารที่ 9 ได้อย่างถูกต้อง
- [ ] เทมเพลต DOCX ถูกสร้างและดาวน์โหลดได้ครบทั้ง 9 ฉบับของ Phase 1 และ 9 ฉบับของ Phase 2
- [ ] ระบบลงนามอิเล็กทรอนิกส์สำหรับเอกสาร 1 และ 2 ของผู้ยื่นทำงานได้ตามปกติ
- [ ] การทดสอบระบบ `FullJourneyMockMvcTest` และ `EveryPageRendersTest` ผ่านทั้งหมด (0 Failures)
