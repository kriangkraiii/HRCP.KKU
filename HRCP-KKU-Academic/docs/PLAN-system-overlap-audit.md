# แผนงาน: ตรวจสอบและแก้ไขจุดซ้อนทับ/บดบังกันทั่วทั้งโปรเจกต์ (System-wide UI Collision Audit & Fix)

> เอกสารแผนงานตามคำสั่ง `/plan` เพื่อตรวจสอบ วิเคราะห์ และแก้ไขจุดที่มีโอกาสเกิดการลอยทับ ซ้อนทับ หรือบดบังกันของ UI ทั่วทั้งโปรเจกต์

---

## 1. ผลการสำรวจและตรวจสอบทั่วทั้งระบบ (Audit Findings)

จากการตรวจสอบ Stacking Context, Z-Index, Position Fixed/Sticky, และการทำงานของระบบ Floating Element ทั่วทั้งโปรเจกต์ พบจุดเสี่ยงที่มีโอกาสเกิดการซ้อนทับ/บดบังกันทั้งหมด **3 จุดสำคัญ** ดังนี้:

---

### จุดที่ 1: การแจ้งเตือนของ Scopus Picker ทับกับชิป Auto-Draft
- **ตำแหน่ง:** [scopus_picker.js](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/js/scopus_picker.js) บรรทัดที่ 448
- **อาการ:** เมื่อผู้ใช้อยู่ในหน้าเอกสารที่ 1 (`document_1_form.html`) แล้วกดเลือกผลงานวิชาการจาก Scopus ระบบจะแสดง Toast แจ้งเตือนสีเขียวที่ `bottom: 1rem; right: 1rem; z-index: 2000;` ซึ่งเป็นพิกัดมุมขวาล่างจุดเดียวกับชิปสถานะบันทึกร่าง `.adb` (`bottom: 24px; right: 24px;`) พอดี
- **ผลกระทบ:** Toast ของ Scopus ลอยทับถมลงบนชิป Auto-Draft ทำให้ผู้ใช้มองไม่เห็นสถานะการบันทึก
- **แนวทางแก้ไข:** ปรับตำแหน่ง Toast ของ Scopus ให้ลอยอยู่เหนือชิป Auto-Draft (`bottom: 76px; right: 24px;`) เพื่อให้เห็นทั้งสองอย่างพร้อมกันอย่างเป็นระเบียบ

---

### จุดที่ 2: แผ่นม่านดำบนมือถือ (`.sidebar-overlay`) ลอยอยู่ใต้แถบ Navbar
- **ตำแหน่ง:** [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css) บรรทัดที่ 1199
- **อาการ:**
  - `.top-navbar`: มี `z-index: 1045`
  - `.sidebar-overlay`: มี `z-index: 1040` *(ต่ำกว่า Navbar)*
  - `.sidebar`: มี `z-index: 1050`
  - บนหน้าจอมือถือ (ความกว้าง <= 991px) เมื่อกดเปิดเมนูด้านข้าง แผ่นม่านดำจะคลุมหน้าจอ แต่กลับอยู่ **ใต้** แถบ Navbar ด้านบน ทำให้ Navbar ไม่มืดลงและสว่างโดดออกมา เกิดการเหลื่อมซ้อนของมิติ
- **แนวทางแก้ไข:** ปรับ `.sidebar-overlay` เป็น `z-index: 1048` เพื่อให้ม่านดำคลุมทับ Navbar (1045) อย่างสมบูรณ์ แต่ยังคงอยู่ใต้ Sidebar (1050)

---

### จุดที่ 3: ปุ่มเมนูแฮมเบอร์เกอร์บนมือถือ (`.sidebar-toggle`) ลอยทะลุหน้าต่าง Modal
- **ตำแหน่ง:** [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css) บรรทัดที่ 1182
- **อาการ:**
  - `.sidebar-toggle`: มี `z-index: 1060`
  - Bootstrap Modal Backdrop: มี `z-index: 1050`
  - Bootstrap Modal Dialog: มี `z-index: 1055`
  - บนมือถือเมื่อเปิด Modal ใดๆ (เช่น หน้าต่างยืนยันการยกเลิกร่าง, หน้าต่างเปลี่ยนรหัสผ่าน, หน้าต่างลงนาม) ปุ่มแฮมเบอร์เกอร์สีน้ำเงิน (`top: 14px; left: 14px`) มี z-index สูงกว่า จึงลอยทะลุผ่านม่านดำและหน้าต่าง Modal ออกมาให้เห็น
- **แนวทางแก้ไข:** เพิ่มกฎ CSS เมื่อเปิด Modal (`body.modal-open .sidebar-toggle`) ให้ลด z-index เหลือ `1030` เพื่อให้ปุ่มแฮมเบอร์เกอร์อยู่ใต้ม่านดำและหน้าต่าง Modal อย่างถูกต้อง

---

## 2. แผนการดำเนินงาน (Implementation Plan)

### ส่วนที่ 1: แก้ไข Toast ของ Scopus Picker
- **ไฟล์:** [src/main/resources/static/js/scopus_picker.js](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/js/scopus_picker.js)
- ปรับ `box.style.cssText` จากเดิม `bottom:1rem;right:1rem;` เป็น `bottom:76px;right:24px;`
- จัดการให้ขอบมนและเงาสวยงาม สอดรับกับชิป Auto-Draft ด้านล่าง

### ส่วนที่ 2: แก้ไข Stacking Context บนหน้าจอมือถือ
- **ไฟล์:** [src/main/resources/static/css/style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css)
- ปรับ `.sidebar-overlay` ให้มี `z-index: 1048`
- เพิ่มกฎ CSS:
  ```css
  body.modal-open .sidebar-toggle {
    z-index: 1030 !important;
  }
  ```

---

## 3. แผนการตรวจสอบความถูกต้อง (Verification Plan)

1. **ทดสอบ Scopus Picker + Auto-Draft:**
   - เข้าหน้าเอกสารที่ 1 (`/user/academic/request/{id}/document-1`)
   - กดเลือกผลงานจาก Scopus
   - ตรวจสอบว่า Toast แจ้งเตือนสีเขียวปรากฏอยู่ **เหนือ** ชิป Auto-Draft ไม่ซ้อนทับกัน
2. **ทดสอบ Mobile Sidebar Drawer:**
   - ย่อหน้าจอเป็นขนาดมือถือ (ความกว้าง < 992px)
   - กดเปิดเมนูด้านข้าง
   - ตรวจสอบว่าม่านดำคลุมมืดทั้งหน้าจอรวมถึงแถบ Navbar ด้านบน โดยมี Sidebar ลอยอยู่ด้านบนสุด
3. **ทดสอบ Modal บนมือถือ:**
   - ในขนาดจอมือถือ กดเปิด Modal ใดๆ (เช่น กดยกเลิกแบบร่าง หรือเปิดดูตัวอย่าง)
   - ตรวจสอบว่าปุ่มแฮมเบอร์เกอร์สีน้ำเงิน (`.sidebar-toggle`) จมอยู่ใต้ม่านดำ ไม่ลอยทะลุออกมาทับ Modal
