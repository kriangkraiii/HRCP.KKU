# แผนงาน: ย้ายตำแหน่งชิปสถานะบันทึกร่างอัตโนมัติ (Auto-Draft Chip) ไปมุมล่างขวา (Option B)

> เอกสารแผนงานตามคำสั่ง `/plan` สำหรับการปรับย้ายตำแหน่งของ `.adb` (Auto-Draft Status Chip) ตามที่ผู้ใช้เลือก Option B โดยคงรูปแบบและสไตล์เดิมทั้งหมด ไม่แก้ไขส่วนอื่น

---

## 1. วัตถุประสงค์ (Objective)
ย้ายตำแหน่งแสดงผลของแถบสถานะการบันทึกร่างอัตโนมัติ (`.adb`) จากเดิมที่ลอยอยู่กึ่งกลางด้านบนจอ (`top: 70px; left: calc(50% + var(--sidebar-width) / 2)`) ซึ่งบดบังหัวเรื่องของหน้าเอกสาร (Page Title เช่น "ผลการประเมิน...") ไปไว้ที่ **มุมล่างขวาของหน้าจอ (Bottom-Right Floating)**

> ⚠️ **เงื่อนไขสำคัญจากผู้ใช้:** ย้ายเฉพาะตำแหน่งพิกัด (Position Coordinates) เท่านั้น ไม่แก้ไขสีสัน, ไม่แก้โครงสร้าง DOM, ไม่แก้ JavaScript Logic และไม่กระทบต่อสไตล์ส่วนอื่นใด

---

## 2. การวิเคราะห์จุดแก้ไข (Analysis)

### 2.1 ตำแหน่งเดิมใน [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css#L560-L566)
```css
.adb {
  position: fixed;
  top: 70px;
  left: calc(50% + var(--sidebar-width, 260px) / 2);
  transform: translateX(-50%);
  z-index: 1035;
  ...
}
```
- ลอยทับหัวข้อหลักของเอกสารโดยตรง

### 2.2 สิ่งที่จะปรับเปลี่ยน (Option B - เฉพาะตำแหน่งเท่านั้น)
1. **Desktop View (`.adb`):**
   - เปลี่ยนจาก `top: 70px` เป็น `bottom: 24px`
   - เปลี่ยนจาก `left: calc(50% + ...)` เป็น `right: 24px` และ `left: auto`
   - ปรับ `transform: none` (เนื่องจากไม่ต้อง translateX(-50%) เพื่อจัดกึ่งกลางแล้ว)
2. **Animation Keyframe (`@keyframes adbChipIn`):**
   - ปรับการเด้งเข้าเบาๆ ให้เข้ากับมุมล่างขวา (เลื่อนขึ้นจากด้านล่างเล็กน้อยแทนการเลื่อนลงมาจากด้านบน) โดยไม่มี `translateX(-50%)`
3. **Mobile View (`@media (max-width: 991px)`):**
   - คงเดิมไว้ที่กึ่งกลางขอบล่างตามที่มีอยู่แล้ว (`bottom: 16px; left: 50%; transform: translateX(-50%);`) ซึ่งเหมาะสมกับจอมือถืออยู่แล้ว

---

## 3. แผนการดำเนินงาน (Task Breakdown)

### ส่วนที่ 1: แก้ไขไฟล์ CSS เพียงจุดเดียว
- **ไฟล์:** `src/main/resources/static/css/style.css`
  - ปรับเฉพาะ Selector `.adb` และ `@keyframes adbChipIn`
  - คงค่า padding, border-radius, font, backdrop-filter, colors และ z-index เดิมทั้งหมด 100%

### ส่วนที่ 2: การตรวจสอบ (Verification)
- ตรวจสอบบนหน้าจอเบราว์เซอร์:
  - หัวเรื่องเอกสารด้านบน (เช่น "ผลการประเมิน...") โล่ง สะอาดตา ไม่มีอะไรบดบัง
  - ชิปสถานะ `.adb` แสดงผลที่มุมล่างขวาอย่างเรียบร้อย
  - การแสดงผลของสถานะบันทึก (idle, dirty, saving, saved, error) ยังคงแสดงผลและสีเดิมครบถ้วน
  - บนมือถือ (`max-width: 991px`) ยังคงจัดกึ่งกลางล่างอย่างถูกต้อง

---

## 4. ผู้รับผิดชอบ (Agent Assignments)
- **Project Planner:** จัดทำแผนงานและควบคุมขอบเขต (ไม่ให้แก้ส่วนอื่นเกินคำสั่ง)
- **Frontend Specialist:** ดำเนินการย้ายตำแหน่ง CSS ใน `style.css`

---

## 5. แผนการตรวจสอบความถูกต้อง (Verification Checklist)
- [ ] ตำแหน่ง `.adb` บน Desktop อยู่ที่มุมล่างขวา (`bottom: 24px; right: 24px;`)
- [ ] หัวข้อเอกสารด้านบนไม่มีชิปใดๆ มาบังตัวหนังสือ
- [ ] ไม่มีการแก้ไขโค้ด JavaScript หรือ HTML ใดๆ
- [ ] สี ธีมปกติ และ Dark Theme ของชิปยังคงแสดงผลเหมือนเดิมทุกประการ
