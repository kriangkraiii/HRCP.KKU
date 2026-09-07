# Project Plan: แก้ไขปัญหาระบบกะพริบเน้นสีข้อมูลอัตโนมัติ (Search Pulse Highlight Fix v2)

> **เอกสารแผนงาน:** `docs/PLAN-search-pulse-fix.md`  
> **ผู้รับผิดชอบหลัก:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]` และ `@[debugger]`  
> **โหมดการทำงาน:** PLANNING ONLY (ห้ามแก้ไขโค้ดจนกว่าจะได้รับอนุมัติ)

---

## 1. ผลการวิเคราะห์เชิงลึกและสาเหตุที่แท้จริง (Root Cause Deep Analysis)

จากการตรวจสอบการทำงานจริงของโค้ดและทดสอบจำลองกระบวนการทั้งหมด (Browser Scroll, CSS Cascade, DOM ID, และ Web Animations API) พบสาเหตุสำคัญ 5 ประการที่ทำให้ผู้ใช้มองไม่เห็นการกะพริบเน้นสี:

### 1. แอนิเมชันเดิมไม่ใช่การ "กะพริบ (Blink/Pulse)" แต่เป็นการ "ค่อยๆ จางหาย (Monotonic Fade)"
- **ปัญหา:** แอนิเมชันเดิมใน `@keyframes` และ `cell.animate()` กำหนดให้สีเริ่มที่เหลืองอ่อน (0%) แล้วค่อยๆ จางลงเป็นโปร่งใส (100%) เพียงจังหวะเดียว
- **ผลกระทบ:** เมื่อผู้ใช้คลิกผลค้นหา เบราว์เซอร์จะทำ **Smooth Scroll** เลื่อนหน้าจอลงมาหาข้อมูล ซึ่งกินเวลา 500-800ms ในจังหวะที่หน้าจอกำลังเลื่อนลงมา สีเหลืองอ่อนได้จางหายไปเกินครึ่งแล้ว เมื่อสายตาผู้ใช้หยุดมองที่แถวข้อมูล สีได้กลายเป็นโปร่งใสหมดแล้ว ทำให้ผู้ใช้รู้สึกว่า **"ไม่เห็นกะพริบเลย"**
- **วิธีแก้:** ต้องเปลี่ยนเป็น **True Multi-Pulse Blink (กะพริบสว่าง-หรี่-สว่าง-หรี่-สว่าง 3 จังหวะชัดเจน)** พร้อมสีทองอำพันสว่างคมชัด (`#f59e0b` / `#fbbf24`) นาน 3.5 วินาที เพื่อให้ไม่ว่าจะเลื่อนหน้าจอนานแค่ไหน สายตาก็จะเห็นแถวกะพริบสว่างวาบสะดุดตาทันที

---

### 2. ID ไม่ตรงกันในหน้าคำร้อง (`requests.html`)
- **ปัญหา:** ใน `requests.html` บรรทัดที่ 204 (มุมมองรอการดำเนินการเริ่มต้น `activeStatus == null`):
  ```html
  <tr th:id="'pending-req-' + ${req.id}">
  ```
  แต่เมื่อผลการค้นหาจาก Omnibox ส่ง URL มาเป็น:
  `/admin/academic/requests?type=evaluation#req-123`
- **ผลกระทบ:** สคริปต์ค้นหา `#req-123` ใน DOM ไม่พบ (เพราะแถวมี id เป็น `pending-req-123`) ทำให้เลิกทำงาน ไม่เลื่อนหน้าจอ และไม่เกิดการไฮไลต์ใดๆ
- **วิธีแก้:** 
  1. ปรับ `requests.html` ให้แถวมี `th:id="'req-' + ${req.id}"` (และรองรับ alias `pending-req-`)
  2. ปรับ `global-search.js` ให้มี **Multi-Strategy Element Resolver** หากหา `#req-X` ไม่พบ ให้ตรวจหา `#pending-req-X`, `[data-id="X"]` อัตโนมัติ

---

### 3. ตารางของระบบใช้ `.admin-table` ซึ่งระบุสีขาวใน CSS ทับพื้นหลังของแถว
- **ปัญหา:** ใน `style.css` บรรทัดที่ 3075:
  ```css
  .admin-table tbody td {
      background-color: var(--color-white);
  }
  ```
  ความจำเพาะ (Specificity) ของ `.admin-table tbody td` สูงกว่า `.search-target-highlight > td` และตัวรีเซ็ต Bootstrap ก่อนหน้านี้มีเฉพาะ `.table > tbody > tr...` ซึ่งไม่ครอบคลุม `.admin-table`
- **วิธีแก้:** 
  1. เพิ่มกฎ CSS ให้ครอบคลุม `.admin-table tbody tr.search-target-highlight td` และ `:target`
  2. ใส่ `outline: 3px solid #f59e0b; outline-offset: -2px;` บน `<tr>` และทำแอนิเมชันกะพริบที่ตัว `<tr>` ควบคู่ไปกับ `<td>` ทุกช่อง เพื่อการันตีว่ามองเห็นชัดเจน 100% ไม่ว่าเซลล์จะมีสีพื้นหลังใดก็ตาม

---

### 4. ปัญหาผลงานตีพิมพ์ (`publications.html`) ข้ามหน้า Pagination
- **ปัญหา:** หน้า `/admin/publications` แบ่งหน้าละ 25 รายการ หากผลงานชิ้นที่ค้นพบอยู่หน้าที่ 2 หรือ 3 การเปิด `/admin/publications#pub-123` จะเข้าหน้าที่ 1 ซึ่งไม่มีแถวนั้นใน DOM
- **วิธีแก้:** ปรับ URL ของผลงานตีพิมพ์ให้ชี้ไปยังหน้ารายละเอียด `/admin/publications/{id}` โดยตรง ซึ่งระบบมีหน้านี้รองรับอยู่แล้ว และหากผู้ใช้อยู่ที่หน้าตารางรวมอยู่แล้ว ก็ยังเลื่อนหาและกะพริบได้ตามปกติ

---

### 5. ข้อผิดพลาดชั่วคราวจากการ Reload ของ Spring Boot DevTools
- **ปัญหา:** ผู้ใช้พบข้อความ:
  `Parameter 0 of constructor in com.ecom.config.GlobalModelAdvice required a bean of type 'com.ecom.service.UserService' that could not be found.`
- **สาเหตุ:** เกิดขึ้นชั่วคราวในจังหวะที่ DevTools กำลังรีสตาร์ตขณะที่ไฟล์ใน `target/classes` ยังคอมไพล์ไม่เสร็จสมบูรณ์
- **การยืนยัน:** ได้ทำการรัน `./mvnw test -Dtest=HrcpKkuApplicationTests` และ `GlobalSearchApiTest` แล้ว ผลลัพธ์ผ่านทั้งหมด 100% (BUILD SUCCESS) ไม่มีปัญหา Bean หายในซอร์สโค้ด

---

## 2. แผนงานการแก้ไข (Implementation Steps)

### Phase 1: ปรับแต่งแอนิเมชันให้เป็น True Multi-Pulse (กะพริบ 3 จังหวะจริง)
- **ไฟล์เป้าหมาย:**
  - `HRCP-KKU-Academic/src/main/resources/static/css/style.css`
  - `HRCP-KKU-Academic/src/main/resources/static/css/dark-theme.css`
  - `HRCP-KKU-Academic/src/main/resources/static/js/global-search.js`
- **รายละเอียด:**
  - **Keyframes จังหวะกะพริบ (3.5s):**
    - `0%`: สว่างวาบสีทองอำพัน (`rgba(245, 158, 11, 0.75)` / ขอบ 3px / glow 14px)
    - `15%`: หรี่ลงเล็กน้อย (`rgba(254, 243, 199, 0.25)`)
    - `30%`: สว่างวาบครั้งที่ 2 (`rgba(245, 158, 11, 0.75)` / ขอบ 3px / glow 14px)
    - `45%`: หรี่ลงครั้งที่ 2 (`rgba(254, 243, 199, 0.25)`)
    - `60%`: สว่างวาบครั้งที่ 3 (`rgba(245, 158, 11, 0.65)`)
    - `80%`: เรืองแสงนวลตา (`rgba(254, 243, 199, 0.35)`)
    - `100%`: จางหายสู่สถานะปกติอย่างนุ่มนวล
  - ใส่ทั้งใน CSS Keyframes และใน Web Animations API (`cell.animate(...)` + `target.animate(...)`)

### Phase 2: แก้ไข ID แถวข้อมูลใน `requests.html`
- **ไฟล์เป้าหมาย:**
  - `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/requests.html`
- **รายละเอียด:**
  - เปลี่ยนบรรทัด 204 จาก `th:id="'pending-req-' + ${req.id}"` ให้เป็น `th:id="'req-' + ${req.id}"` เพื่อให้ตรงกับ Anchor `#req-{id}` เสมอ

### Phase 3: เสริมพลัง Multi-Strategy Target Resolver ใน `global-search.js`
- **ไฟล์เป้าหมาย:**
  - `HRCP-KKU-Academic/src/main/resources/static/js/global-search.js`
- **รายละเอียด:**
  - เพิ่มฟังก์ชันค้นหา Target ยืดหยุ่น: หากค้นหา `#req-X` ไม่พบ ให้ค้นหา `#pending-req-X` หรือ `tr[id*="req-X"]`
  - เพิ่มการบังคับไฮไลต์ทันทีเมื่อกดคลิกในหน้าเดียวกันโดยไม่ต้องรอ `hashchange`

### Phase 4: ปรับ URL ผลงานตีพิมพ์เป็นหน้ารายละเอียด
- **ไฟล์เป้าหมาย:**
  - `HRCP-KKU-Academic/src/main/java/com/ecom/search/model/SearchEntityType.java`
  - `HRCP-KKU-Academic/src/main/java/com/ecom/search/dto/SearchHit.java`
- **รายละเอียด:**
  - กำหนด Admin URL ของผลงานตีพิมพ์ให้เปิด `/admin/publications/{id}`

---

## 3. แผนการทดสอบและการตรวจสอบ (Verification Plan)

1. **ทดสอบแอนิเมชันกะพริบ 3 จังหวะ:**
   - ทดสอบเปิด `/admin/academic/staff#staff-1` -> ต้องเห็นแถวสว่างวาบกะพริบ 3 ครั้งอย่างชัดเจนเป็นสีทองอำพัน
2. **ทดสอบการค้นหาคำร้องที่รอดำเนินการ:**
   - ค้นหาคำร้องแล้วกดลิงก์ -> ต้องเลื่อนตรงมาที่แถวนั้นและกะพริบ 3 จังหวะ แม้จะเป็นคำร้องที่อยู่ในสถานะรอการดำเนินการ
3. **ทดสอบผลงานตีพิมพ์:**
   - ค้นหาผลงานและกดดู -> นำทางไปยังผลงานได้อย่างถูกต้อง
4. **ทดสอบสร้างและคอมไพล์ระบบ:**
   - รัน `./mvnw test-compile` และรัน Unit/Integration Tests ทั้งหมด

---

## 4. Socratic Gate (คำถามเพื่อยืนยันก่อนเริ่มพัฒนา)

1. **ระดับความสว่างและความถี่ของการกะพริบ:**
   - ต้องการให้กะพริบ 3 จังหวะในเวลา 3.5 วินาที พร้อมเส้นขอบเรืองแสงสีทองอำพัน หรือต้องการให้กะพริบถี่กว่านี้/นานกว่านี้?
2. **การคลิกผลงานตีพิมพ์:**
   - ยืนยันให้การคลิกผลงานตีพิมพ์จากช่องค้นหาเปิดเข้าไปที่หน้ารายละเอียด `/admin/publications/{id}` โดยตรงเลยใช่หรือไม่ (เพื่อแก้ปัญหาผลงานอยู่คนละหน้าของ Pagination)?
