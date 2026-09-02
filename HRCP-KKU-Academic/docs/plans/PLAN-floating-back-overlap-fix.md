# แผนการพัฒนา (Project Plan): แก้ไขปัญหาปุ่ม "← กลับ" และการแจ้งเตือนแสดงซ้อนทับกัน (Floating Button & Alert Banner Overlap Fix)

## 📋 ข้อมูลเบื้องต้น (Overview & Context)
- **ปัญหาที่พบ (Issue):** เมื่อหน้าเว็บมีการแสดงข้อความแจ้งเตือน Flash Message (เช่น `succMsg` สีเขียว: *"ส่งเอกสารไปลงนามเรียบร้อยแล้ว ระบบได้แจ้งเตือนผู้ลงนามคนถัดไป"*) ปุ่มลอย **"← กลับ" (`.floating-doc-btn-back`)** จะลอยทับอยู่บนกล่องข้อความแจ้งเตือนและบดบังข้อความ ทำให้ผู้ใช้งานอ่านข้อความไม่สะดวกและดูไม่เรียบร้อย (ตามภาพที่ผู้ใช้แนบมา)
- **สาเหตุทางเทคนิค (Root Cause):**
  1. ในไฟล์ `src/main/resources/static/css/style.css` (บรรทัด 401-449), คลาส `.floating-doc-btn` ถูกตั้งค่าเป็น `position: fixed; top: 75px; bottom: auto !important;`
  2. ในไฟล์ `src/main/resources/templates/academic/base_academic.html` (บรรทัด 532-545), กล่องแจ้งเตือนกลาง (`succMsg`, `errorMsg`, `warnMsg`) ถูกเรนเดอร์อยู่ด้านบนสุดของเนื้อหาใต้ Topbar (ตำแหน่ง Y ประมาณ 65px - 130px)
  3. เนื่องจากปุ่มลอยถูกกำหนดตำแหน่งตายตัวที่ `top: 75px` ทำให้ปุ่มลอยไปทับกล่องแจ้งเตือนที่เพิ่งแสดงผลทันที

---

## 🎯 วัตถุประสงค์ (Objectives)
1. กำจัดปัญหาการแสดงผลซ้อนทับกันระหว่างปุ่มลอย (Floating Buttons) กับกล่องแจ้งเตือน (Alert Banners) และ Breadcrumbs/หัวข้อเอกสาร
2. จัดตำแหน่งปุ่มลอยสำหรับหน้าฟอร์มเอกสารให้เป็นมาตรฐาน Floating Action Button (FAB) บริเวณด้านล่างของหน้าจอ (`bottom: 24px; top: auto;`) เพื่อให้ผู้ใช้สามารถกด "← กลับ" และ "👁️ ดูตัวอย่าง" ได้สะดวกตลอดการกรอกเอกสาร โดยไม่บดบังเนื้อหาด้านบน
3. ตรวจสอบการแสดงผลทั้งในธีมปกติ (Light Theme) และธีมมืด (Dark Theme)

---

## 🏗️ รายละเอียดการปรับปรุง (Architecture & Changes)

### 1. ปรับปรุง CSS สำหรับ Floating Action Buttons
- **ไฟล์:** `src/main/resources/static/css/style.css`
  - ปรับปรุง `.floating-doc-btn`:
    ```css
    .floating-doc-btn {
      position: fixed;
      bottom: 24px !important;
      top: auto !important;
      z-index: 1040;
      border-radius: 9999px !important;
      padding: 10px 22px !important;
      font-size: 0.9rem !important;
      font-weight: 500;
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s ease;
    }
    .floating-doc-btn-back {
      left: calc(var(--sidebar-width, 260px) + 24px);
      bottom: 24px !important;
    }
    .floating-doc-btn-preview {
      right: 24px;
      bottom: 24px !important;
    }
    @media (max-width: 991px) {
      .floating-doc-btn-back {
        left: 16px;
      }
      .floating-doc-btn-preview {
        right: 16px;
      }
    }
    ```

### 2. ปรับปรุง Dark Theme CSS
- **ไฟล์:** `src/main/resources/static/css/dark-theme.css`
  - ตรวจสอบและอัปเดตสไตล์ `.floating-doc-btn` ในโหมด Dark Theme ให้มีพื้นหลังโปร่งแสงหรูหรา (`rgba(30, 41, 59, 0.85)`) ขอบมน และเงาที่กลมกลืน

### 3. ตรวจสอบและปรับปรุงสคริปต์ใน `base_academic.html`
- **ไฟล์:** `src/main/resources/templates/academic/base_academic.html`
  - ตรวจสอบฟังก์ชันสร้างปุ่มลอย เพื่อให้มั่นใจว่าเมื่อมีข้อความแจ้งเตือน (`alert`) การแสดงผลจะไม่เกิดการทับซ้อนกับองค์ประกอบใดๆ บนหน้าจอ

---

## 🧪 แผนการทดสอบ (Verification Plan)

### Manual Verification
1. เปิดหน้าที่มีการแสดงผล Flash Message (เช่น ส่งเอกสารไปลงนามเรียบร้อยแล้ว หรือบันทึกแบบร่างสำเร็จ)
2. ตรวจสอบว่ากล่อง Alert สีเขียว/แดง/เหลือง แสดงผลเด่นชัด ไม่ถูกปุ่มใดทับซ้อน
3. ตรวจสอบปุ่ม "← กลับ" และ "ดูตัวอย่าง" ว่าลอยอยู่ที่มุมซ้ายล่าง/ขวาล่างของหน้าจออย่างสวยงาม เมื่อเลื่อนหน้าจอ (Scroll) สามารถกดใช้งานได้ราบรื่น
4. ตรวจสอบ Responsive บนหน้าจอมือถือ (Mobile viewport < 991px) ว่าปุ่มลอยไม่ทับเนื้อหาสำคัญ

### Automated Tests
- รัน `./mvnw test` เพื่อตรวจสอบความสมบูรณ์ของระบบ

---

## 🚀 ลำดับขั้นตอนการดำเนินงาน (Execution Steps)
- [ ] **Phase 1:** อัปเดต `style.css` ย้ายตำแหน่ง `.floating-doc-btn` ไปที่ `bottom: 24px`
- [ ] **Phase 2:** อัปเดต `dark-theme.css` ให้สอดคล้องกัน
- [ ] **Phase 3:** ตรวจสอบและปรับปรุง `base_academic.html`
- [ ] **Phase 4:** รันชุดทดสอบ `./mvnw test`
