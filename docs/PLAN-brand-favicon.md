# Project Plan: เน้นตัวอักษร CP ACAD และเปลี่ยนโลโก้แท็บเบราว์เซอร์ (Favicon)

> **เอกสารแผนงาน:** `docs/PLAN-brand-favicon.md`  
> **ผู้รับผิดชอบ:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]`  
> **สถานะ:** วางแผนงาน (Planning - Ready for Approval)  
> **อ้างอิงความต้องการ:**  
> 1. ปรับเน้นตัวอักษร `CP ACAD` ให้เด่นชัด มีมิติ และสะดุดตาตามสไตล์มินิมอลทางการ  
> 2. เปลี่ยนโลโก้แท็บเบราว์เซอร์ (Favicon) จากโลโก้เก่า (ที่มีตัวอักษร HR สีส้ม) เป็นโลโก้ใหม่ที่เป็นทางการและสอดคล้องกับ CP ACAD  

---

## 1. บทวิเคราะห์ปัญหาและแนวทางแก้ไข (Analysis & Strategy)

### 1.1 ปัญหาเรื่องโลโก้แท็บเบราว์เซอร์ (Favicon Issue)
- ภาพตัวอย่างที่ส่งมาระบุชัดเจนว่าที่แท็บเบราว์เซอร์ยังคงแสดงโลโก้เดิม ซึ่งเป็นโลโก้ `cp HR KKU` ที่มีตัวอักษร `HR` สีส้ม ซึ่งตกค้างมาจากชื่อระบบเดิม
- ไฟล์ Favicon ในระบบที่ต้องเปลี่ยน:
  - `src/main/resources/static/favicon.ico`
  - `src/main/resources/static/favicon.png`
  - `src/main/resources/static/favicon.jpg`
  - `src/main/resources/static/img/favicon.ico`
  - `src/main/resources/static/img/favicon.png`
  - `src/main/resources/static/img/favicon_full.png`
- **ทางเลือกในการเปลี่ยน Favicon:**
  - **Option 1 (แนะนำ):** ใช้สัญลักษณ์ **CP วงกลมสีน้ำเงิน** (`favicon_cp.png` ขนาด 128x128) ซึ่งเป็นอัตลักษณ์ทางการของวิทยาลัยการคอมพิวเตอร์ที่คมชัด สะอาดตา ไม่มีตัวอักษร HR ปน
  - **Option 2:** สร้าง Favicon สไตล์ Dark Blue Denim ที่มีตัวย่อ `CP` พร้อมกรอบโมเดิร์น

### 1.2 การเน้นตัวอักษร CP ACAD (Brand Typography Emphasis)
- เพิ่มขนาดตัวอักษร (Scale) จากเดิม `2.6rem` ขึ้นเป็น `3.0rem - 3.2rem` เพื่อเป็นพระเอกหลักของ Brand Panel
- เพิ่มน้ำหนักตัวอักษร (Font Weight) เป็น `800` (Extra Bold) สำหรับ `CP` และ `700` สำหรับ `ACAD`
- ปรับสี Blue Denim ให้มีความสดสว่างและคอนทราสต์สูงขึ้น (`#4d8ee8` / `#5692e8`) ควบคู่กับแสงเงา (Text Shadow / Subtle Blue Glow)
- จัดช่องไฟ (Tracking/Letter Spacing) ให้มีความโมเดิร์น หนักแน่น มั่นคง สไตล์ Academic Tech

---

## 2. ขอบเขตไฟล์ที่ต้องดำเนินการ (Scope of Changes)

### 2.1 ไฟล์ทรัพยากร Favicon (Static Assets)
- คัดลอกและอัปเดตไฟล์ Favicon:
  - แทนที่ `static/img/favicon.png` ด้วย `favicon_cp.png` (วงกลม CP ไร้คำว่า HR)
  - อัปเดต `static/favicon.ico`, `static/favicon.png`, `static/favicon.jpg`
  - สร้างเวอร์ชันความละเอียดสูงสำหรับ Apple Touch Icon / Shortcut Icon
- อัปเดต `<link rel="icon">` ในหน้า Template ทุกหน้าเพื่อป้องกันปัญหา Browser Cache:
  ```html
  <link rel="icon" type="image/png" href="/img/favicon.png?v=2.0">
  <link rel="shortcut icon" href="/favicon.ico?v=2.0">
  ```

### 2.2 ไฟล์สไตล์สำหรับการเน้นตัวอักษร (CSS)
- **[guest.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/guest.css)**
  - ยกระดับ `.guest-brand-title`:
    - ปรับ `font-size: 3.1rem;`
    - เพิ่ม `font-weight: 800;`
    - เพิ่มรัศมีแสงเรืองรองบางเบา `text-shadow: 0 4px 20px rgba(91, 146, 229, 0.35), 0 2px 4px rgba(0,0,0,0.5);`
    - ปรับ `.brand-cp` ให้สว่างเด่นด้วยเฉด Denim คมชัด
- **[style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css)**
  - ขยายความเด่นชัดของ `.sidebar-brand h6` ในแถบเมนูด้านในระบบให้สอดคล้องกัน

---

## 3. แผนการตรวจสอบและทดสอบ (Verification Plan)

1. **ตรวจสอบ Favicon บนแท็บเบราว์เซอร์:**
   - เปิดหน้าเข้าสู่ระบบและหน้า Dashboard เพื่อยืนยันว่าไอคอนแท็บเบราว์เซอร์เปลี่ยนเป็นตราสัญลักษณ์ CP วงกลมเรียบร้อยแล้ว
2. **ตรวจสอบความเด่นชัดของตัวอักษร CP ACAD:**
   - ตรวจสอบขนาด ความหนา และระยะห่างของตัวอักษรในหน้าจอขนาดต่างๆ ทั้ง Desktop และ Mobile
3. **ตรวจสอบ Build:**
   - รัน `./mvnw test-compile` เพื่อให้แน่ใจว่าไม่มีข้อผิดพลาด
