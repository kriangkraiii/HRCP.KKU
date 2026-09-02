# แผนการพัฒนา: ปรับปรุงเส้นสถานะ (Progress Tracker), สีธีมสว่าง, และเพิ่ม Timeline คำร้องประเมินการสอน

## 1. วัตถุประสงค์ (Objectives)
1. **แก้ไขความคมชัดของ Badge สถานะในตารางรายการคำร้อง (Light Theme)**:
   - ปรับแต่ง `.at-status` และ `.status-badge` ในทุกตาราง (Table Views) ให้มีคอนทราสต์ชัดเจนในธีมสว่าง ตัวหนังสือไม่กลืนกับสีพื้นหลัง
2. **แก้ไขเส้นสถานะ (Progress Track Line) ไม่ให้เลยจุดแรกและจุดสุดท้าย**:
   - ปรับ CSS ของ `.detail-progress-line` และ `.detail-progress-fill` ให้เริ่มต้นที่กึ่งกลางของวงกลมสถานะแรก (Step 1) และสิ้นสุดที่กึ่งกลางของวงกลมสถานะสุดท้าย (Step N) พอดี โดยไม่ยื่นเลยออกไปด้านข้าง
3. **เพิ่ม Progress Tracker ในหน้าจัดการคำร้องประเมินการสอนของ Admin**:
   - เพิ่มแถบสถานะภาพรวมขั้นตอน (Visual Progress Tracker) ที่หน้ารายละเอียดคำร้อง `academic/admin/request_detail.html` เหมือนของฝั่งขอกำหนดตำแหน่ง
4. **ยกระดับไอคอนและสีสันให้ดูเป็นทางการ (Formal Academic Design & Rich Palette)**:
   - ปรับเปลี่ยนไอคอนของสถานะต่างๆ ให้ดูเป็นทางการระดับมหาวิทยาลัย (เช่น `fa-file-signature`, `fa-clipboard-check`, `fa-users-cog`, `fa-building-columns`, `fa-stamp`, `fa-award`)
   - เพิ่มระบบสีประจำสถานะ (Color Coding) พร้อมวงแหวนสี (Accent Ring) และ Glow Effect สำหรับสถานะปัจจุบันที่กำลังดำเนินการ

---

## 2. แผนการดำเนินงานและไฟล์ที่เกี่ยวข้อง (Implementation Plan)

### เฟส 1: ปรับแต่ง CSS สำหรับ Progress Tracker และ Status Badges
- **ไฟล์**: `HRCP-KKU-Academic/src/main/resources/static/css/style.css`
- **ไฟล์**: `HRCP-KKU-Academic/src/main/resources/static/css/dark-theme.css`
  1. คำนวณจุดเริ่มต้นและสิ้นสุดของเส้นสถานะ `.detail-progress-line` และ `.detail-progress-fill` ให้ตรงกับจุดกึ่งกลางของ Step แรกและ Step สุดท้าย
  2. เพิ่มคลาสสีและไอคอนประจำขั้นตอน (Step Colors):
     - Step 1 (รับคำร้อง): โทนน้ำเงินมิดไนท์ `#1565c0` / ไอคอน `fa-file-signature`
     - Step 2 (ตรวจสอบ/แต่งตั้ง): โทนส้มเข้ม `#e65100` / ไอคอน `fa-users-cog`
     - Step 3 (ประชุม/กลั่นกรอง): โทนม่วงเข้ม `#7b1fa2` / ไอคอน `fa-calendar-check`
     - Step 4 (แจ้งผล/รับรองมติ): โทนเขียวเอมเมอรัลด์ `#2e7d32` / ไอคอน `fa-stamp`
     - Step 5 (ส่งออก/เสร็จสิ้น): โทนเขียวเข้ม/เขียวหัวเป็ด `#1b5e20` / ไอคอน `fa-flag-checkered`
  3. ปรับ `.at-status` ในตารางให้มีสีขอบ (Border), เงา (Subtle Shadow), และตัวหนังสือเข้มชัดเจน ไม่กลืนในธีมสว่าง

### เฟส 2: ปรับปรุง Enum Icons & Labels
- **ไฟล์**: `com/ecom/academic/model/RequestStatus.java`
- **ไฟล์**: `com/ecom/academic/model/PositionRequestStatus.java`
  1. อัปเดตไอคอนใน Enum ให้เป็นไอคอนทางการ (Official Icons)
  2. จัดเตรียม `getProgressSteps()` สำหรับคำร้องประเมินการสอนให้แสดง 4-5 ขั้นตอนหลักที่ชัดเจน

### เฟส 3: เพิ่ม Progress Tracker Component ในหน้า Admin คำร้องประเมินการสอน
- **ไฟล์**: `HRCP-KKU-Academic/src/main/resources/templates/academic/admin/request_detail.html`
  1. วางการ์ด `.detail-progress-card` ด้านบนของหน้ารายละเอียดคำร้อง
  2. เชื่อมโยงสถานะปัจจุบัน `request.currentStatus` กับขั้นตอน Progress Step พร้อมคำนวณแถบความคืบหน้า (Progress Fill Percentage)
  3. รองรับสถานะผลลัพธ์พิเศษ (ผ่าน / ให้แก้ไข / ไม่ผ่าน / ไม่รับคำร้อง)

### เฟส 4: ตรวจสอบและทดสอบการแสดงผล (Verification)
  1. ตรวจสอบใน Light Theme และ Dark Theme ทุกจุด
  2. ตรวจสอบความถูกต้องของเส้นสถานะในทุกขนาดหน้าจอ (Responsive View)
  3. ตรวจสอบตารางคำร้องว่า Badge ทุกสถานะอ่านง่าย คมชัด 100%
