# Project Plan: กระโดดตรงไปยังข้อมูลและไฮไลต์เมื่อคลิกผลการค้นหา (Search Deep Linking & Highlight)

> **เอกสารแผนงาน:** `docs/PLAN-search-deep-link.md`  
> **ผู้รับผิดชอบหลัก:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]` และ `@[backend-specialist]`  
> **สถานะ:** รอยืนยันเพื่อเริ่มพัฒนา (Planning Phase — NO CODE MODIFIED YET)

---

## 1. ที่มาและความต้องการ (Objectives & Background)

ผู้ใช้งานต้องการปรับปรุงพฤติกรรมเมื่อคลิกผลการค้นหาจาก Omnibox (Global Search):
> *"เวลากดผลจากการค้นหาจะไปที่ข้อมูลนั้น มันไปหน้าที่มีข้อมูลก็จริงแต่มันต้องเลื่อนหาข้อมูลนั้นทำให้แบบไม่ต้องเลื่อนหาหน่อย แบบกดค้นไปก็อยู่ตรงข้อมูลนั้นเลย"*

### สภาพปัจจุบัน:
- เมื่อค้นหาเจอข้อมูล เช่น บุคลากร (`StaffMember`) "ศาสตราจารย์ ศาสตรา วงศ์ธนวสุ" หรือผู้ใช้ (`UserDtls`)
- ลิงก์ที่เปิดจะพาไปยังหน้ารายการใหญ่ เช่น `/admin/academic/staff` หรือ `/admin/users?type=1`
- ในหน้านั้นมีตารางรายชื่อบุคลากรหรือผู้ใช้จำนวนมาก แต่หน้าเว็บโหลดขึ้นมาที่ด้านบนสุดของตาราง (Scroll top) ทำให้ผู้ใช้ต้องกวาดสายตาหรือเลื่อน Scrollbar เพื่อตามหาแถวข้อมูลที่ตนเองเพิ่งคลิกมา

### สิ่งที่ต้องการ:
1. เมื่อคลิกลิงก์จากผลการค้นหา ให้เบราว์เซอร์ **เลื่อนหน้าจอ (Auto-scroll) ตรงไปยังแถวข้อมูลนั้นทันที** ให้อยู่กึ่งกลางสายตา (Center of viewport) โดยไม่ต้องเลื่อนหาเอง
2. **ไฮไลต์แถวข้อมูลนั้นชั่วคราว (Visual Pulse/Glow Highlight)** เป็นเวลาประมาณ 2.5 - 3 วินาที เพื่อให้สายตาผู้ใช้โฟกัสข้อมูลที่ค้นหาได้อย่างชัดเจน
3. รองรับกรณีที่ผู้ใช้อยู่ในหน้านั้นอยู่แล้ว แล้วค้นหาข้อมูลอื่นในหน้าเดียวกัน ให้เลื่อนไปยังรายการใหม่อย่างราบรื่น (Smooth Scroll on Hash Change)

---

## 2. การวิเคราะห์สาเหตุเชิงลึกและแนวทางแก้ไข (Technical Analysis)

### 2.1 โครงสร้าง URL และการระบุตำแหน่ง (Deep Link Anchors)
- **ปัญหา:** URL ที่สร้างจาก Search Engine และ Controller ปัจจุบัน ส่งมาเพียง Path หลัก (เช่น `/admin/academic/staff`) โดยไม่มี Anchor Hash (`#staff-12`) หรือ Parameter ระบุตัวตนของรายการ
- **แนวทางแก้ไข:**
  1. **ฝั่ง JavaScript (`global-search.js`):**
     - ในข้อมูล `SearchHit` ที่ได้รับจาก API `/api/global-search` มีฟิลด์ `entityType` และ `entityId` ส่งมาอยู่แล้ว
     - สามารถเพิ่ม Anchor ต่อท้าย URL โดยอัตโนมัติ เช่น:
       - `STAFF_MEMBER` → `/admin/academic/staff#staff-{entityId}`
       - `SYSTEM_USER` → `/admin/users?type=1#user-{entityId}` (หรือ `type=2`)
       - `COMMITTEE_MEMBER` → `/admin/academic/committee#committee-{entityId}`
       - `ACADEMIC_REQUEST` → `/admin/academic/requests?type=evaluation#req-{entityId}`
       - `POSITION_REQUEST` → `/admin/academic/requests?type=position#req-{entityId}`
       - `ADMIN_FILE` → `/admin/file-manager#file-{entityId}`
       - `PUBLICATION` → `/admin/publications#pub-{entityId}`
  2. **ฝั่ง Backend (`SearchEntityType.java` และ `GlobalSearchService.java`):**
     - ปรับปรุง Template URL ให้มี `#anchor` ครบถ้วน เพื่อให้ทั้งการคลิกจากหน้าผลการค้นหาแบบเต็ม (`/search?q=...`) และ Omnibox ได้ผลลัพธ์ที่ตรงกัน

### 2.2 การระบุ Element ID ในหน้าตารางปลายทาง (HTML ID Attribute)
- **ปัญหา:** แท็ก `<tr>` หรือการ์ดข้อมูลในหน้าปลายทางยังไม่มีแอตทริบิวต์ `id` ที่ตรงกับ Anchor
- **แนวทางแก้ไข:**
  - `staff_list.html`: เพิ่ม `th:id="'staff-' + ${s.id}"` ที่แถว `<tr>`
  - `users.html`: เพิ่ม `th:id="'user-' + ${u.id}"` ที่แถว `<tr>`
  - `committee.html`: เพิ่ม `th:id="'committee-' + ${m.id}"` ที่แถว `<tr>`
  - `requests.html`: เพิ่ม `th:id="'req-' + ${req.id}"` ที่แถว `<tr>`
  - `publications.html`: เพิ่ม `th:id="'pub-' + ${p.id}"` ที่แถว `<tr>`
  - `file_manager.html`: ตรวจสอบและรองรับ `th:id="'file-' + ${file.fileId}"`

### 2.3 การเลื่อนหน้าจออัตโนมัติ & การทำไฮไลต์ (Smooth Scroll & Pulse Animation)
- **CSS (`style.css`):**
  - กำหนด `:target` และคลาส `.search-target-highlight`:
    - `scroll-margin-top: 100px;` (เพื่อไม่ให้ถูก Topbar บัง)
    - CSS Animation: ค่อยๆ กะพริบเน้นสีพื้นหลัง (เช่น สีเหลืองอำพันอ่อน `rgba(255, 224, 130, 0.6)`) พร้อมขอบนุ่มนวล แล้วค่อยๆ จางหายไปใน 2.5 วินาที
    - รองรับ Dark mode ให้อ่านง่าย สบายตา
- **JavaScript ทั่วไประบบ (`base_academic.html` / `main.js`):**
  - ฟังก์ชันตรวจจับ Hash เมื่อโหลดหน้า (`DOMContentLoaded`) หรือเมื่อ Hash เปลี่ยน (`hashchange`):
    - ค้นหา Element ตาม `window.location.hash`
    - เรียก `element.scrollIntoView({ behavior: 'smooth', block: 'center' });`
    - แปะคลาส `.search-target-highlight` และเอาออกเมื่อแอนิเมชันสิ้นสุด

---

## 3. แผนการดำเนินงานและไฟล์เป้าหมาย (Task Breakdown)

| ลำดับ | รายการงาน | ไฟล์เป้าหมาย | รายละเอียดการแก้ไข |
|:---:|:---|:---|:---|
| **Phase 1** | **กำหนด Anchor ให้กับผลการค้นหา** | `global-search.js`, `SearchEntityType.java`, `GlobalSearchService.java` | 1. เพิ่มฟังก์ชันแนบ Anchor ตาม `entityType` + `entityId` ใน `global-search.js`<br>2. อัปเดต Template URL ใน `SearchEntityType` และ `GlobalSearchService` |
| **Phase 2** | **เพิ่ม ID ให้กับแถวตารางในหน้าเป้าหมาย** | `staff_list.html`, `users.html`, `committee.html`, `requests.html`, `publications.html` | 1. ใส่ `th:id` ให้กับ `<tr>` ในแต่ละหน้า (เช่น `staff-{id}`, `user-{id}`, `committee-{id}`)<br>2. ตรวจสอบให้ ID ไม่ซ้ำซ้อน |
| **Phase 3** | **สร้าง Effect เลื่อนนุ่มนวลและไฮไลต์** | `style.css`, `base_academic.html` | 1. เพิ่ม CSS Animation สำหรับแถวที่ถูก Target (`:target`, `.search-target-highlight`)<br>2. เพิ่มสคริปต์กลางใน `base_academic.html` ดักจับ Hash แล้วสั่ง `scrollIntoView({ block: 'center' })` |
| **Phase 4** | **การทดสอบและตรวจรับ (Verification)** | Browser & Tests | 1. ทดสอบค้นหา "ศาสตรา" -> คลิกผลการค้นหา -> หน้า staff โหลดและเลื่อนไปหาแถวศาสตราจารย์ทันทีพร้อมไฮไลต์สีสว่าง<br>2. ทดสอบค้นหา User ทั่วไป -> เลื่อนไปที่แถวในหน้า Users<br>3. ทดสอบการค้นหาขณะที่อยู่ในหน้านั้นอยู่แล้ว (Same-page Hash Change) |

---

## 4. แผนการทดสอบและเกณฑ์การตรวจรับ (Verification Criteria)

- [ ] เมื่อพิมพ์ค้นหาชื่อบุคลากร แล้วคลิกรายการใน Omnibox ระบบจะเปิดหน้าจัดการบุคลากร และ **เลื่อนหน้าจออัตโนมัติมายังแถวของบุคคลนั้น** โดยแถวนั้นอยู่บริเวณกึ่งกลางหน้าจอ
- [ ] แถวที่ค้นหาจะแสดง **Effect ไฮไลต์สีสว่าง (Pulse animation)** เพื่อให้ผู้ใช้มองเห็นได้ทันที
- [ ] หากผู้ใช้อยู่ที่หน้า `/admin/academic/staff` อยู่แล้ว แล้วพิมพ์ค้นหาบุคลากรอีกคนในช่องค้นหาด่วน หน้าจอจะ Scroll ไปยังบุคลากรคนใหม่อย่างราบรื่นโดยไม่ต้องกดรีเฟรชหน้า
- [ ] ใช้งานได้สมบูรณ์ทั้งใน Light Mode และ Dark Mode
