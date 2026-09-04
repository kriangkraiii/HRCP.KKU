# Project Plan: ปรับแต่ง Sidebar (แถบเมนูด้านข้าง) — ป้องกันการเลื่อนแนวนอน และเพิ่มระบบซ่อน/แสดง (Hide/Toggle)

> **เอกสารแผนงาน:** `docs/PLAN-sidebar-toggle.md`  
> **ผู้รับผิดชอบหลัก:** `@[frontend-specialist]` (UI/UX, Layout, CSS Transitions) ร่วมกับ `@[project-planner]`  
> **สถานะ:** รอยืนยันเพื่อเริ่มพัฒนา (Planning Phase — NO CODE MODIFIED YET)

---

## 1. ที่มาและความต้องการ (Objectives & Background)

ผู้ใช้งานต้องการปรับปรุงแถบเมนูด้านข้าง (Sidebar) ของระบบวิชาการ (`base_academic.html`):
1. **ป้องกันไม่ให้แถบเมนูด้านข้างเกิดการเลื่อนแนวนอน (No Horizontal Scroll):**
   - ในปัจจุบัน เมื่อหน้าจอหรือข้อความในเมนูยาว มี Badge หรือมีการตั้งค่า Flex layout ในบางเบราว์เซอร์ จะเกิดแถบเลื่อนแนวนอน (Horizontal Scrollbar) ด้านล่างของ Sidebar ทำให้ดูไม่สวยงามและใช้งานยาก
2. **เพิ่มความสามารถในการซ่อน/แสดง Sidebar (Hide / Collapse Toggle):**
   - ในปัจจุบันบนหน้าจอคอมพิวเตอร์ (Desktop) แถบ Sidebar จะถูกฟิกซ์ความกว้างไว้ที่ 260px ตลอดเวลา ไม่สามารถกดยุบหรือซ่อนได้ ทำให้พื้นที่ในการดูตารางข้อมูลคำร้อง รายการเอกสาร หรือแบบฟอร์มกว้างๆ ถูกจำกัด
   - ต้องการให้ผู้ใช้สามารถกดปุ่มเพื่อซ่อน (Hide) หรือเปิด (Show) แถบเมนูได้ เพื่อขยายพื้นที่การทำงานให้เต็มจอ (Full Width)

---

## 2. การวิเคราะห์สาเหตุเชิงลึกและแนวทางแก้ไข (Technical Analysis)

### 2.1 ปัญหาการเลื่อนแนวนอน (Horizontal Scrollbar):
- **สาเหตุ:**
  1. ใน CSS `.sidebar` มีการกำหนด `overflow-y: auto;` เพียงแกนเดียว ตามมาตรฐาน CSS เมื่อกำหนด `overflow-y: auto` แต่ไม่ได้ระบุ `overflow-x: hidden;` อย่างชัดเจน แกน X จะกลายเป็น `auto` ด้วย หากมี Child element ใดก็ตามที่กว้างเกิน 260px แม้เพียง 1px (เช่น Padding, Margin, Badges, หรือข้อความยาว) เบราว์เซอร์จะสร้าง Scrollbar แนวนอนทันที
  2. `.sidebar-link` เป็น Flexbox (`display: flex; gap: 12px;`) ซึ่งตามธรรมชาติของ Flexbox หากไม่ได้ใส่ `min-width: 0;` ข้อความหรือชื่อเมนูที่ยาวจะไม่ยอมหดตัว (No flex shrink below content size) ทำให้ล้นกรอบ
  3. ข้อความใน `.sidebar-brand` และส่วนโปรไฟล์ผู้ใช้ด้านล่าง (`.sidebar-footer .sidebar-user`) หากชื่อ-นามสกุล หรืออีเมลยาวเกินไป อาจดันให้กว้างเกินขอบ
- **วิธีแก้ไข:**
  1. กำหนด `overflow-x: hidden !important; overflow-y: auto;` ที่ `.sidebar`
  2. ปรับแต่ง Scrollbar ในแนวตั้งให้มีขนาดกะทัดรัด (บางลงและสีกลมกลืน) ด้วย `scrollbar-width: thin;` และ `::-webkit-scrollbar`
  3. กำหนด `min-width: 0; text-overflow: ellipsis; white-space: nowrap; overflow: hidden;` ให้กับข้อความใน `.sidebar-link` และ `.sidebar-user`
  4. กำหนด `word-break: break-word;` หรือ `overflow-wrap: break-word;` ให้กับหัวข้อและโลโก้ใน `.sidebar-brand`

---

### 2.2 ปัญหาระบบซ่อน/แสดง Sidebar (Desktop & Mobile Hide/Toggle):
- **สาเหตุ:**
  1. ปุ่ม `.sidebar-toggle` เดิมถูกตั้งค่า `@media (max-width: 991px)` ให้แสดงเฉพาะบนมือถือ ส่วนบน Desktop ถูกซ่อนไว้ (`display: none`)
  2. `.main-content` ถูกล็อกระยะ `margin-left: var(--sidebar-width);` ไว้ตายตัวบนหน้าจอขนาด >= 992px
- **วิธีแก้ไข:**
  1. **ปุ่ม Toggle บน Desktop:**
     - เพิ่มปุ่มกดสลับ Sidebar (Hamburger / Sidebar Toggle Button) ที่มุมซ้ายของ `.top-navbar` ข้างๆ Breadcrumb เพื่อให้ผู้ใช้สามารถคลิกซ่อน/แสดงได้ตลอดเวลา
     - ดีไซน์ปุ่มให้สวยงาม สไตล์ Minimalist เช่น ไอคอน `fas fa-bars-staggered` หรือ `fas fa-bars` พร้อม Tooltip `"ซ่อน/แสดงแถบเมนู (Sidebar)"`
  2. **สถานะการซ่อน (CSS Classes & Transitions):**
     - เมื่อผู้ใช้กดซ่อน: เพิ่มคลาส `sidebar-collapsed` (หรือ `sidebar-hidden`) ที่ `<body>` หรือ `<html>`
     - `.sidebar`: เลื่อนหลบไปทางซ้ายอย่างนุ่มนวล (`transform: translateX(-100%);` หรือ `margin-left: -260px; transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);`)
     - `.main-content`: ขยายเต็มหน้าจออัตโนมัติ (`margin-left: 0 !important; transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1);`)
     - ปรับระยะของปุ่มลอย (Floating buttons เช่น `.floating-doc-btn-back`): ให้ขยับมาที่ `left: 24px` อัตโนมัติเมื่อ Sidebar ถูกซ่อน
  3. **การจำสถานะ (Persistence via LocalStorage):**
     - เมื่อผู้ใช้กดย่อหรือขยาย ให้บันทึกค่าลง `localStorage.setItem('sidebar_collapsed', 'true' / 'false')`
     - เพิ่ม Anti-flash script ใน `<head>` เพื่ออ่านค่าจาก `localStorage` และแปะคลาสทันทีก่อนที่เบราว์เซอร์จะ Render เพื่อไม่ให้เกิดอาการหน้าจอกระตุกหรือกะพริบเวลากดเปลี่ยนหน้า
  4. **รองรับ Responsive Mobile & Tablet:**
     - บนหน้าจอขนาดเล็ก (< 992px) ปุ่มดังกล่าวและปุ่มเดิมจะยังคงทำหน้าที่เปิด/ปิด Drawer พร้อม Overlay สีดำแบบเดิมอย่างสมบูรณ์

---

## 3. แผนการดำเนินงานและไฟล์ที่เกี่ยวข้อง (Task Breakdown)

| ลำดับ | รายการงาน | ไฟล์เป้าหมาย | รายละเอียด |
|:---:|:---|:---|:---|
| **Phase 1** | **แก้ปัญหา Horizontal Scroll** | `src/main/resources/static/css/style.css` | - เพิ่ม `overflow-x: hidden !important;`<br>- เพิ่ม Text Truncation และ `min-width: 0` ในลิงก์เมนูและ Footer<br>- ตกแต่ง Slim scrollbar สำหรับแนวตั้ง |
| **Phase 2** | **เพิ่ม CSS สำหรับ Desktop Sidebar Collapse** | `src/main/resources/static/css/style.css`<br>`src/main/resources/static/css/dark-theme.css` | - เพิ่มกฎ `body.sidebar-collapsed .sidebar`<br>- เพิ่มกฎ `body.sidebar-collapsed .main-content`<br>- รองรับ Floating buttons และ Dark Mode |
| **Phase 3** | **เพิ่มปุ่ม Toggle และ Script ควบคุม** | `src/main/resources/templates/academic/base_academic.html` | - เพิ่มปุ่ม Toggle ใน `.top-navbar`<br>- เขียน JavaScript รองรับการคลิกสลับสถานะ และบันทึก `localStorage`<br>- เพิ่ม Anti-flash inline script ใน `<head>` |
| **Phase 4** | **การทดสอบและการตรวจสอบ (Verification)** | Automated Test Suite & Browser Rendering | - ตรวจสอบการ Render หน้าเว็บผ่าน `SigningPageRenderTest`<br>- ตรวจสอบว่าไม่เกิด Error กับ JavaScript อื่นๆ<br>- ตรวจสอบพฤติกรรมบนความกว้างหน้าจอต่างๆ (Desktop, Tablet, Mobile) |

---

## 4. แผนการตรวจสอบความถูกต้อง (Verification Plan)

1. **Automated Tests:**
   - รันการทดสอบ Unit & Integration Tests ของระบบเว็บ:
     ```bash
     ./mvnw test -Dtest="SigningPageRenderTest"
     ```
   - ตรวจสอบว่า Template Thymeleaf ทำงานปกติ ไม่มี Syntax Error
2. **Manual & Visual Inspection:**
   - ตรวจสอบว่า Sidebar ไม่มีแถบเลื่อนแนวนอนปรากฏขึ้นมาไม่ว่าจะเลื่อน Scroll ในตำแหน่งใด
   - กดปุ่ม Toggle ที่แถบด้านบน:
     - Sidebar เลื่อนเก็บเรียบร้อย
     - เนื้อหาหลักขยายเต็มความกว้างหน้าจออย่างนุ่มนวล
   - กดปุ่ม Toggle อีกครั้ง: Sidebar เลื่อนกลับมาแสดงผลปกติ
   - รีเฟรชหน้าเว็บ: สถานะที่เลือกไว้ (ซ่อนหรือแสดง) ยังคงถูกจดจำไว้เหมือนเดิม
