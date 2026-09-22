# แผนงาน: ซ่อนและเคลียร์แบบร่างที่ว่างเปล่า (Hide & Clean Empty Drafts)

> เอกสารแผนงานตามคำสั่ง `/plan` สำหรับการจัดการแบบร่างที่ยังไม่มีการกรอกข้อมูลหรืออัปโหลดไฟล์ ไม่ให้แสดงผลเป็น "คำร้องฉบับร่าง" บนหน้าแดชบอร์ด

---

## 1. วัตถุประสงค์ (Objective)
แก้ไขปัญหาเมื่อผู้ใช้กดเข้าหน้า "ยื่นคำร้องใหม่" (`/user/academic/new-request`) แต่ยังไม่ได้กรอกข้อมูลใดๆ ในเอกสาร และยังไม่ได้อัปโหลดไฟล์ใดๆ เมื่อกดกลับมาหน้าแดชบอร์ด ระบบกลับแสดงแถบเตือนสีเหลืองว่า **"คำร้องฉบับร่าง: กรุณากรอกเอกสารให้ครบแล้วส่งคำร้อง"** ทำให้เกิดความสับสนและเป็นขยะข้อมูลค้างในระบบ

**เป้าหมาย:**
- หากคำร้องฉบับร่างยังคงเป็น **"แบบร่างว่างเปล่า" (Empty Draft)** คือ ยังไม่มีข้อมูลเอกสาร, ไม่มีไฟล์แนบ, ไม่มีการลงนาม, และไม่ใช่คำร้องที่ถูกส่งกลับมาแก้ไข จะต้อง **ไม่แสดงแถบฉบับร่างบนหน้าแดชบอร์ด** และดำเนินการเคลียร์แบบร่างว่างเปล่านั้นทิ้งอัตโนมัติ
- หากผู้ใช้มีการเริ่มกรอกข้อมูล (ไม่ว่าจะผ่าน Auto-Draft หรือกดบันทึก) หรือมีการอัปโหลดไฟล์แนบแล้ว แถบฉบับร่างจึงจะแสดงผลตามปกติเพื่อให้ดำเนินการต่อได้

---

## 2. การวิเคราะห์สาเหตุและโครงสร้างเดิม (Root Cause Analysis)

### 2.1 สาเหตุที่เกิดปัญหา
1. **การสร้าง Draft ทันทีเมื่อเปิดหน้าใหม่ (`AcademicApplicantController.java`):**
   ```java
   AcademicRequest draftRequest = requestService.findDraftByApplicant(user.getId());
   if (draftRequest == null) {
       draftRequest = requestService.createDraftRequest(user); // บันทึกลง DB ทันที
   }
   ```
   ทันทีที่ผู้ใช้คลิกลิงก์ "ยื่นคำร้องขอการประเมินการสอน" ระบบจะ `INSERT` เรคอร์ดใหม่ลงตาราง `academic_requests` ด้วยสถานะ `DRAFT` และสร้างเลข `KKU-ACAD-YYYY-NNNN` ทันที แม้ผู้ใช้จะยังไม่ได้เริ่มพิมพ์อะไรเลย
2. **การดึงข้อมูลบนหน้าแดชบอร์ด (`dashboard` method):**
   ```java
   AcademicRequest draftRequest = allRequests.stream()
           .filter(r -> r.getCurrentStatus() == RequestStatus.DRAFT)
           .findFirst().orElse(null);
   model.addAttribute("draftRequest", draftRequest);
   ```
   หน้าแดชบอร์ดตรวจสอบเพียง `draftRequest != null` ถ้าพบว่ามีเรคอร์ดสถานะ `DRAFT` อยู่ใน DB จะแสดงแบนเนอร์แจ้งเตือนทันที

---

## 3. นิยามของ "แบบร่างว่างเปล่า" (Definition of Empty Draft)

คำร้อง `AcademicRequest` ที่มีสถานะ `DRAFT` จะถือว่าเป็น **"แบบร่างว่างเปล่า" (Empty)** ก็ต่อเมื่อตรงตามเงื่อนไขทั้งหมดดังต่อไปนี้:
1. **ไม่มีการกรอกข้อมูลในเอกสารใดๆ:**
   - รายการ `AcademicDocument` ของคำร้องนั้นว่างเปล่า หรือ
   - ข้อมูล `jsonData` ของทุกเอกสารเป็น `null`, ค่าว่าง `""` หรือ JSON เปล่า `{}`
2. **ไม่มีไฟล์แนบใดๆ:**
   - รายการ `AcademicAttachment` ของคำร้องนั้นมีจำนวนเท่ากับ 0
3. **ไม่มีลายเซ็นของผู้ยื่น:**
   - ยังไม่มีการลงนามอิเล็กทรอนิกส์ในเอกสารใดๆ
4. **ไม่ใช่คำร้องที่ถูกส่งกลับมาแก้ไข (Crucial Protection):**
   - `requestService.getReturnNote(requestId) == null`
   *(หากเป็นคำร้องที่ถูกคณะกรรมการหรือคณบดีส่งกลับมาให้แก้ไข จะมีสถานะเป็น DRAFT แต่มี `returnNote` กำกับอยู่ ต้องคงไว้และแสดงผลตามปกติเสมอ)*

---

## 4. แนวทางการแก้ไขที่เสนอ (Proposed Strategy)

### ส่วนที่ 1: เพิ่มเมธอดตรวจสอบความว่างเปล่าใน Service Layer
- **ไฟล์:** `com.ecom.academic.service.AcademicRequestService.java`
- สร้างเมธอด:
  ```java
  public boolean isDraftEmpty(AcademicRequest request)
  ```
  ตรวจสอบเงื่อนไข 4 ข้อข้างต้นอย่างรัดกุม

### ส่วนที่ 2: จัดการ Empty Draft ในหน้าแดชบอร์ด (`AcademicApplicantController.java`)
- ใน `dashboard(Principal principal, Model model)`:
  - เมื่อค้นพบ `draftRequest`:
    ```java
    if (draftRequest != null && requestService.isDraftEmpty(draftRequest)) {
        // เคลียร์แบบร่างที่ว่างเปล่าออกจากฐานข้อมูล
        requestService.deleteDraftRequest(draftRequest.getId(), user.getId());
        draftRequest = null;
    }
    ```
  - ส่ง `draftRequest` (ซึ่งจะเป็น `null` ถ้าเป็นร่างว่างเปล่า) ไปยังโมเดล
  - หน้า `academic/applicant/dashboard.html` จะไม่แสดงแบนเนอร์คำร้องฉบับร่าง และแสดงปุ่ม "ยื่นคำร้องขอการประเมินการสอน" ได้ตามปกติ

### ส่วนที่ 3: ตรวจสอบความสอดคล้องกับ Position Request (Phase 2)
- นำหลักการเดียวกันไปตรวจสอบและประยุกต์ใช้กับ `PositionApplicantController` และ `PositionRequestService` หากพบพฤติกรรมสร้างแบบร่างว่างเปล่าในลักษณะเดียวกัน

---

## 5. แผนการดำเนินงาน (Task Breakdown)

| ลำดับ | รายการงาน | ไฟล์ที่เกี่ยวข้อง |
|---|---|---|
| 1 | เพิ่มฟังก์ชันตรวจสอบ `isDraftEmpty` ใน `AcademicRequestService` | `AcademicRequestService.java` |
| 2 | อัปเดต Controller แดชบอร์ดให้ตรวจสอบและเคลียร์ Draft ว่างเปล่า | `AcademicApplicantController.java` |
| 3 | ตรวจสอบกรณีคำร้องขอตำแหน่ง (Position Request) | `PositionRequestService.java`, `PositionApplicantController.java` |
| 4 | ทดสอบทุก Use Case และ Edge Cases | Manual & Test Runner |

---

## 6. กรณีทดสอบและเงื่อนไขขอบเขต (Edge Cases & Verification)

- [ ] **Case 1 (เคสผู้ใช้แจ้ง):** กดเข้าหน้า "ยื่นคำร้องใหม่" แล้วกดปุ่ม "กลับ" ทันทีโดยไม่กรอกอะไร
  - **ผลลัพธ์ที่คาดหวัง:** หน้าแดชบอร์ดต้องไม่มีแบนเนอร์ "คำร้องฉบับร่าง" ปรากฏ
- [ ] **Case 2 (มีการกรอกข้อมูล):** กดเข้าหน้ายื่นคำร้อง เข้าไปกรอกเอกสารที่ 1 แล้วกดกลับ
  - **ผลลัพธ์ที่คาดหวัง:** หน้าแดชบอร์ดต้องแสดงแบนเนอร์ "คำร้องฉบับร่าง" พร้อมปุ่ม "ดำเนินการต่อ"
- [ ] **Case 3 (มีการอัปโหลดไฟล์แนบ):** กดเข้าหน้ายื่นคำร้อง อัปโหลดไฟล์แนบแล้วกดกลับ
  - **ผลลัพธ์ที่คาดหวัง:** หน้าแดชบอร์ดต้องแสดงแบนเนอร์ "คำร้องฉบับร่าง" เพราะมีไฟล์แนบแล้ว
- [ ] **Case 4 (คำร้องถูกส่งกลับจากแอดมิน):** คำร้องที่สถานะเป็น DRAFT แต่มี `returnNote`
  - **ผลลัพธ์ที่คาดหวัง:** ต้องไม่ถูกลบ และแสดงแบนเนอร์เตือนให้แก้ไขตามปกติ
