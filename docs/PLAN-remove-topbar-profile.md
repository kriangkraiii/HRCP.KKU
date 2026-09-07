# Project Plan: นำโปรไฟล์ออกจาก Topbar (แถบด้านบน) ให้คงเหลือเฉพาะใน Sidebar (แถบด้านข้าง)

> **เอกสารแผนงาน:** `docs/PLAN-remove-topbar-profile.md`  
> **ผู้รับผิดชอบหลัก:** `@[frontend-specialist]` ร่วมกับ `@[project-planner]`  
> **โหมดการทำงาน:** PLANNING ONLY (ไม่มีการแก้ไขโค้ดจนกว่าจะได้รับการอนุมัติ)  
> **สถานะ:** รอยืนยันเพื่อเริ่มดำเนินการ (Awaiting User Approval)

---

## 1. ที่มาและความต้องการ (Objectives & Background)

ผู้ใช้งานต้องการปรับปรุงหน้าจอส่วนหัว (Topbar/Header) ของระบบ โดยมีรายละเอียดดังนี้:
- **ปัญหา/ความต้องการ:** นำปุ่มโปรไฟล์และรูป Avatar ที่อยู่มุมขวาบนของแถบด้านบน (Topbar) ออก
- **เป้าหมาย:** ให้เข้าถึงหน้าโปรไฟล์ได้จากจุดเดียว คือส่วนข้อมูลผู้ใช้ที่อยู่บริเวณด้านล่างของแถบเมนูด้านข้าง (Sidebar Footer / Slide bar) เท่านั้น เพื่อลดความซ้ำซ้อนและทำให้แถบ Topbar สะอาดตาขึ้น

---

## 2. การวิเคราะห์โครงสร้างและผลกระทบ (Technical Analysis)

### 2.1 ตำแหน่งของปุ่มโปรไฟล์ในปัจจุบัน
1. **Topbar (แถบด้านบน):**
   - ไฟล์: `src/main/resources/templates/academic/base_academic.html` (บรรทัดที่ 807–822)
   - ประกอบด้วยแท็ก `<a class="topbar-avatar-link">` พร้อมไอคอน/รูปภาพ Avatar เชื่อมโยงไปยัง `/admin/profile` หรือ `/user/profile` อยู่ถัดจากปุ่มคู่มือการใช้งาน (`.topbar-icon-btn`)
   - คลาส CSS ที่เกี่ยวข้องใน `style.css` และ `dark-theme.css`: `.topbar-avatar-link`, `.topbar-avatar`, `.topbar-avatar img`

2. **Sidebar / Slide bar (แถบด้านข้าง):**
   - ไฟล์: `src/main/resources/templates/academic/base_academic.html` (บรรทัดที่ 649–677)
   - มีบล็อก `.sidebar-footer` ที่แสดงรูป Avatar, ชื่อ-นามสกุล, ตำแหน่ง/Role และลิงก์ไปยังหน้า Profile (`/admin/profile` หรือ `/user/profile`) พร้อมปุ่มออกจากระบบ (Logout) ไว้อยู่แล้วอย่างสมบูรณ์
   - ส่วนนี้จะยังคงอยู่และทำงานตามปกติ 100%

### 2.2 การตรวจสอบกรณีการใช้งาน (Edge Cases & Responsiveness)
- **เมื่อ Sidebar ถูกพับ (Collapsed on Desktop):** ผู้ใช้สามารถคลิกปุ่ม Toggle (`#sidebarToggleBtn`) ที่มุมซ้ายบนเพื่อเปิด Sidebar และคลิกดูโปรไฟล์ได้สะดวก
- **บนอุปกรณ์พกพา (Mobile / Tablet):** ผู้ใช้สามารถกดปุ่ม Hamburger เพื่อเปิด Drawer Menu จากด้านข้าง ซึ่งจะมีโปรไฟล์ของผู้ใช้ปรากฏอยู่ด้านล่างเสมอ
- **การจัดวางปุ่มใน Topbar (Flex Alignment):** คอนเทนเนอร์ `.topbar-right` ใช้ `d-flex align-items-center gap-3 justify-content-end` เมื่อนำ `.topbar-avatar-link` ออก ปุ่มขวาสุดจะเป็นไอคอนคู่มือการใช้งาน (`far fa-question-circle`) โดยระยะ Margin/Gap ยังคงสวยงามสมดุล

---

## 3. แผนการดำเนินงาน (Task Breakdown)

| Task ID | รายการงาน | ผู้รับผิดชอบ & ทักษะ | ไฟล์เป้าหมาย | รายละเอียดการทำงาน (INPUT → OUTPUT → VERIFY) |
|:---:|:---|:---|:---|:---|
| **TASK-1** | **นำบล็อก Profile Avatar ออกจาก Topbar** | `frontend-specialist`<br>`frontend-design` | `src/main/resources/templates/academic/base_academic.html` | **INPUT:** ลบแท็ก `<!-- Profile Avatar -->` (`.topbar-avatar-link`) ออกจาก `.topbar-right`<br>**OUTPUT:** แถบ Topbar ไม่มีปุ่มโปรไฟล์ เหลือเพียงช่องค้นหา, ตัวสลับภาษา, ปุ่มแจ้งเตือน และปุ่มคู่มือ<br>**VERIFY:** ตรวจสอบโครงสร้าง HTML ไม่แตก และแท็กปิดถูกต้อง |
| **TASK-2** | **ทำความสะอาด CSS / Dark Theme** | `frontend-specialist`<br>`clean-code` | `src/main/resources/static/css/style.css`<br>`src/main/resources/static/css/dark-theme.css` | **INPUT:** ตรวจสอบและคงสไตล์พื้นฐานหรือปรับ Clean-up คลาสที่ไม่จำเป็น<br>**OUTPUT:** CSS สะอาดตา ไม่มี Warning หรือ Style ซ้ำซ้อน<br>**VERIFY:** ตรวจสอบการแสดงผลทั้งโหมด Light และ Dark |
| **TASK-3** | **ทดสอบการทำงานของ Template และหน้าโปรไฟล์** | `frontend-specialist`<br>`testing-patterns` | Automated Test Suite | **INPUT:** รันคำสั่งทดสอบการ Render Template<br>**OUTPUT:** เทมเพลตคอมไพล์ผ่านสมบูรณ์ และลิงก์โปรไฟล์ใน Sidebar ทำงานถูกต้อง<br>**VERIFY:** `./mvnw test -Dtest="SigningPageRenderTest"` ผ่านฉลุย |

---

## 4. แผนการตรวจสอบความถูกต้อง (Phase X: Verification Plan)

### 4.1 Automated Build & Template Test
```bash
cd HRCP-KKU-Academic && ./mvnw test-compile
```

### 4.2 Manual & Visual Inspection
1. **Light Mode & Dark Mode:** ตรวจสอบมุมขวาบนของ Topbar พบว่าไอคอนรูปโปรไฟล์หายไปแล้ว โดยปุ่มคู่มือและปุ่มกระดิ่งแจ้งเตือนยังคงจัดตำแหน่งเรียบร้อย ไม่เบี้ยว
2. **Sidebar Verification:** ตรวจสอบแถบเมนูด้านข้าง (Slide bar) พบว่าส่วนโปรไฟล์ด้านล่าง (รูป Avatar, ชื่อ, บทบาท) ยังคงแสดงผลและสามารถคลิกเข้าสู่หน้าโปรไฟล์ (`/admin/profile` หรือ `/user/profile`) ได้ตามปกติ
3. **Mobile Screen (< 992px):** ทดสอบย่อหน้าจอ กดปุ่มเปิด Sidebar ตรวจสอบว่าโปรไฟล์ใน Slide bar ใช้งานได้ปกติ
