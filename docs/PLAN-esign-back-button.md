# แผนงาน: ปรับตำแหน่งปุ่มย้อนกลับไปไว้ฝั่งซ้ายในหน้าการลงนามและหน้ารอลงนาม (PLAN-esign-back-button.md)

> **สถานะ**: ร่างแผนงาน (ผ่านการตรวจสอบ Socratic Gate เรียบร้อย พร้อมเริ่มดำเนินการ)  
> **ประเภทโครงการ (Project Type)**: WEB  
> **ผู้รับผิดชอบหลัก**: `@[project-planner]` (กำกับดูแลสถาปัตยกรรม) & `@[frontend-specialist]` (ออกแบบและปรับแต่ง UI/UX)  
> **ทักษะที่เกี่ยวข้อง**: `frontend-design`, `clean-code`, `webapp-testing`  
> **เป้าหมาย**: ปรับตำแหน่งปุ่ม "ย้อนกลับ" (`.btn-back-pill`) จากเดิมที่อยู่มุมขวาบนในหน้าลงนามเอกสาร (`/esign/sign/{id}`) ให้ย้ายมาอยู่ฝั่งซ้ายด้านบนเหนือหัวข้อเอกสารตามมาตรฐาน UI เดียวกับหน้าคำร้องและหน้าอื่น ๆ ทั้งระบบ พร้อมทั้งเพิ่มปุ่มย้อนกลับฝั่งซ้ายในหน้ารอลงนาม (`/esign/inbox`) เพื่อความสอดคล้อง (Consistency) และความสะดวกในการใช้งานของผู้ใช้

---

## 1. ภาพรวมและบริบท (Overview & Context)

### 1.1 ปัญหาปัจจุบัน (Current State)
1. **หน้าลงนามเอกสาร (`/esign/sign/{id}` - `sign.html`)**:
   - ปุ่มย้อนกลับ (`<a href="/esign/inbox" class="btn-back-pill"><i class="fas fa-arrow-left"></i> กลับ</a>`) ถูกจัดวางอยู่มุมขวาบนของแถบหัวข้อ ด้วยคลาส Bootstrap `d-flex justify-content-between align-items-start`
   - ในหน้าอื่น ๆ ทั้งหมดของระบบ (เช่น `applicant/request_detail.html`, `admin/request_detail.html`, `position/applicant/request_detail.html`, `esign/verify.html`) ปุ่มกลับจะวางอยู่ **ฝั่งซ้ายด้านบนสุดเหนือหัวข้อเอกสาร** เสมอ
   - ตำแหน่งมุมขวาบนทำให้เกิดความไม่สอดคล้องของ Visual Hierarchy ผู้ใช้งานที่คุ้นเคยกับปุ่มกลับฝั่งซ้ายต้องกวาดสายตาไปหามุมขวา
2. **หน้ารอลงนาม / กล่องเอกสารลงนาม (`/esign/inbox` - `inbox.html`)**:
   - ปัจจุบันยังไม่มีปุ่มย้อนกลับไปหน้าหลัก (Dashboard) ที่ด้านบนซ้าย ผู้ใช้ต้องกดเมนู Sidebar เพื่อนำทางกลับเท่านั้น

### 1.2 วัตถุประสงค์และการเปลี่ยนแปลง (Target State)
1. **หน้าลงนามเอกสาร (`sign.html`)**:
   - ย้ายปุ่ม `.btn-back-pill` ออกจากแถบ Header Flex ด้านขวา มาสร้างเป็นบล็อก `<div class="mb-3">` แยกไว้ด้านบนสุดฝั่งซ้าย เหนือหัวข้อเอกสาร
   - คงคุณสมบัติ `onclick="if(window.history.length > 1 && document.referrer) { history.back(); return false; }"` พร้อม fallback ไปที่ `/esign/inbox`
2. **หน้ารอลงนาม (`inbox.html`)**:
   - เพิ่มปุ่ม `.btn-back-pill` ฝั่งซ้ายด้านบนสุดเหนือหัวข้อ "กล่องเอกสารลงนามอิเล็กทรอนิกส์" โดยกำหนด fallback ให้ผู้ใช้ทั่วไปกลับไปที่ `/user/academic/dashboard` และผู้ดูแลระบบกลับไปที่ `/admin/academic/requests`
3. **หน้าลายเซ็นของฉัน (`my_signatures.html`) - เสริมความสอดคล้อง**:
   - เพิ่มปุ่ม `.btn-back-pill` ฝั่งซ้ายด้านบนสุด เพื่อให้ย้อนกลับไป `/esign/inbox` ได้อย่างราบรื่น

---

## 2. เกณฑ์ความสำเร็จที่วัดผลได้ (Success Criteria)

- [ ] **SC-1 (Layout Consistency)**: ปุ่มย้อนกลับ (`.btn-back-pill`) ปรากฏอยู่ฝั่งซ้ายด้านบนสุด เหนือหัวข้อเอกสาร ในหน้าลงนาม (`/esign/sign/{id}`) โดยไม่มีปุ่มกลับตกค้างอยู่มุมขวาบน
- [ ] **SC-2 (Inbox Back Navigation)**: หน้ารอลงนาม (`/esign/inbox`) มีปุ่มย้อนกลับฝั่งซ้ายด้านบน เชื่อมโยงกลับ Dashboard ได้ถูกต้องตาม Role
- [ ] **SC-3 (Responsive & Mobile UI)**: ในมุมมองหน้าจอขนาดเล็ก (Mobile/Tablet) ปุ่มย้อนกลับยังคงเรียงชิดซ้าย ไม่ดันแถบหัวข้อหรือตกขอบหน้าจอ
- [ ] **SC-4 (Browser History Support)**: เมื่อคลิกปุ่มกลับ ระบบสามารถเรียก `history.back()` เพื่อกลับไปหน้าก่อนหน้าได้ทันที หากมีประวัติการเข้าชม
- [ ] **SC-5 (Zero Regression)**: หน้าการทำงานหลักและการรันเทสต์ `SigningPageRenderTest` และ `EveryPageRendersTest` ผ่าน 100%

---

## 3. สถาปัตยกรรมและเทคโนโลยี (Tech Stack & Architecture)

- **Frontend Template**: Thymeleaf + HTML5
- **CSS Framework**: Bootstrap 5 + Liquid Glass custom design tokens (`style.css`, `dark-theme.css`)
- **Icon Set**: FontAwesome 6 Pro (`fa-solid fa-arrow-left`)
- **Style Compliance**: ปฏิบัติตามมาตรฐาน Clean Code และ CSP (ไม่มี inline style `style="..."`, ใช้ยูทิลิตี้คลาสและ CSS ที่กำหนดไว้)

---

## 4. โครงสร้างไฟล์ที่เกี่ยวข้อง (File Structure)

```text
HRCP-KKU-Academic/src/main/resources/templates/academic/esign/
├── sign.html           # [แก้ไขหลัก] ย้ายปุ่มกลับจากขวาบนมาไว้ฝั่งซ้ายบนเหนือหัวข้อ
├── inbox.html          # [แก้ไขหลัก] เพิ่มปุ่มกลับฝั่งซ้ายบนเหนือหัวข้อกล่องเอกสาร
└── my_signatures.html  # [แก้ไขเสริม] เพิ่มปุ่มกลับฝั่งซ้ายบนเหนือหัวข้อลายเซ็น
```

---

## 5. การแบ่งงานและขั้นตอนการดำเนินงาน (Task Breakdown)

### Task 1: ปรับปรุงปุ่มกลับในหน้าลงนามเอกสาร (`sign.html`)
- **Agent**: `@[frontend-specialist]`
- **Skills**: `frontend-design`, `clean-code`
- **Priority**: P0 (High)
- **Dependencies**: ไม่มี
- **INPUT**: โค้ดปัจจุบันของ `templates/academic/esign/sign.html` บรรทัดที่ 9-23
- **OUTPUT**:
  - สร้าง `<div class="mb-3">` บรรจุ `<a href="/esign/inbox" class="btn-back-pill" onclick="...">` ด้านบนสุด
  - ปรับบล็อกหัวข้อ `<h3>` และ `<p>` ให้เรียงตามธรรมชาติโดยไม่ใช้ `d-flex justify-content-between` ที่ดันปุ่มไปขวา
- **VERIFY**:
  - ตรวจสอบว่าปุ่มกลับแสดงผลฝั่งซ้ายเหนือหัวข้อเอกสาร
  - ไม่มีปุ่มกลับหลงเหลืออยู่มุมขวาของหัวข้อ

### Task 2: เพิ่มปุ่มกลับในหน้ารอลงนาม (`inbox.html`)
- **Agent**: `@[frontend-specialist]`
- **Skills**: `frontend-design`, `clean-code`
- **Priority**: P1 (Medium)
- **Dependencies**: Task 1
- **INPUT**: โค้ดปัจจุบันของ `templates/academic/esign/inbox.html` บรรทัดที่ 7-17
- **OUTPUT**:
  - เพิ่มปุ่มกลับด้านบนซ้ายเหนือ `Top Header & Fast Actions`:
    ```html
    <div class="mb-3">
        <a th:href="${isAdmin} ? '/admin/academic/requests' : '/user/academic/dashboard'"
           class="btn-back-pill"
           onclick="if(window.history.length > 1 && document.referrer) { history.back(); return false; }">
            <i class="fas fa-arrow-left"></i> กลับ
        </a>
    </div>
    ```
- **VERIFY**:
  - ตรวจสอบการแสดงผลปุ่มกลับด้านบนซ้ายของหน้ารอลงนาม
  - ตรวจสอบการนำทางกลับ Dashboard ถูกต้องตามสิทธิ์ผู้ใช้

### Task 1: ปรับปรุงปุ่มกลับในหน้าลงนามเอกสาร (`sign.html`) ✅
- **Agent**: `@[frontend-specialist]`
- **Skills**: `frontend-design`, `clean-code`
- **Priority**: P0 (High)
- **Status**: [x] Completed
- **INPUT**: โค้ดปัจจุบันของ `templates/academic/esign/sign.html` บรรทัดที่ 9-23
- **OUTPUT**:
  - ย้ายปุ่ม `.btn-back-pill` มาอยู่ใน `<div class="mb-3">` แยกไว้ด้านบนสุดฝั่งซ้าย เหนือหัวข้อเอกสาร
  - ปรับหัวข้อ `<h3>` และ `<p>` ให้เรียงตามธรรมชาติ

### Task 2: เพิ่มปุ่มกลับในหน้ารอลงนาม (`inbox.html`) ✅
- **Agent**: `@[frontend-specialist]`
- **Skills**: `frontend-design`, `clean-code`
- **Priority**: P1 (Medium)
- **Status**: [x] Completed
- **INPUT**: โค้ดปัจจุบันของ `templates/academic/esign/inbox.html`
- **OUTPUT**:
  - เพิ่มปุ่ม `.btn-back-pill` ด้านบนซ้ายเหนือ Top Header พร้อมเงื่อนไขปลายทางตาม Role (`/admin/academic/requests` หรือ `/user/academic/dashboard`)

### Task 3: เพิ่มปุ่มกลับในหน้าลายเซ็นของฉัน (`my_signatures.html`) ✅
- **Agent**: `@[frontend-specialist]`
- **Skills**: `frontend-design`, `clean-code`
- **Priority**: P2 (Low / Consistency)
- **Status**: [x] Completed
- **INPUT**: โค้ดปัจจุบันของ `templates/academic/esign/my_signatures.html`
- **OUTPUT**:
  - เพิ่มปุ่มกลับด้านบนซ้ายเหนือ Header เพื่อนำทางกลับไปยัง `/esign/inbox`

### Task 4: ตรวจสอบและทดสอบการเรนเดอร์ (Verification & Test Suite) ✅
- **Agent**: `@[orchestrator]`
- **Skills**: `testing-patterns`, `clean-code`
- **Priority**: P0 (High)
- **Status**: [x] Completed (Tests run: 78, Failures: 0, Errors: 0)

---

## 6. แผนการตรวจสอบและทดสอบ (Phase X: Final Verification)

- [x] **1. Template Syntax & CSP Check**:
  - ตรวจสอบว่าไม่มี inline `style=""`
  - ตรวจสอบแท็ก Thymeleaf `th:href` และ JavaScript inline attributes ถูกต้อง
- [x] **2. Automated Test Suite**:
  - คำสั่ง: `./mvnw test -Dtest=SigningPageRenderTest,EveryPageRendersTest`
  - ผลลัพธ์: Tests run: 78, Failures: 0, Errors: 0 (BUILD SUCCESS)
- [x] **3. UI/UX Verification**:
  - หน้า `/esign/sign/{id}`: ปุ่มกลับชิดซ้ายบน
  - หน้า `/esign/inbox`: ปุ่มกลับชิดซ้ายบน
  - หน้า `/esign/my-signatures`: ปุ่มกลับชิดซ้ายบน
  - Responsive check: ขนาดหน้าจอ Desktop (1440px), Laptop (1024px), Mobile (375px)
- [x] **4. Git Diff Inspection**:
  - ตรวจสอบความเรียบร้อยของโค้ดด้วย `git diff` สะอาด ปลอดภัย ไม่กระทบไฟล์อื่น

## ✅ PHASE X COMPLETE
- Render Tests: ✅ Pass (78/78 tests passed)
- CSP / Inline Style: ✅ Pass (No inline style)
- UI Consistency: ✅ Pass (Back button placed top-left across all esign views)
- Build: ✅ Success
- Date: 2026-09-25
