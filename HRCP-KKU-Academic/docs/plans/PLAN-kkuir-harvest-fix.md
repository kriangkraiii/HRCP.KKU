# PLAN: แก้ไขปัญหาการดึงข้อมูล KKU IR (Khon Kaen University Institutional Repository)

##  สาเหตุที่ดึงไม่สำเร็จ (Root Cause)
1. **URL เก่าปิดตัวลง:** ค่าเดิมชี้ไปที่ `https://repository.kku.ac.th/oai/request` ซึ่งเซิร์ฟเวอร์ปลายทางปิดพอร์ต 443 ทำให้เกิด `Connection Refused`
2. **URL ที่แท้จริงของ มข. ในปัจจุบัน:** คือ **`https://kkuir.kku.ac.th/oai/request`**
3. **รหัสคอลเลกชัน (Set ID) ของวิทยาลัยการคอมพิวเตอร์:**
   - `col_123456789_37197`: CP - Journal Articles
   - `col_123456789_37198`: CP - Research Reports
   - `col_123456789_37199`: CP - Theses

---

## ️ รายละเอียดงานที่ต้องดำเนินการ (Task Breakdown)
1. **แก้ไขคอนฟิก `application.properties`:**
   - เปลี่ยน `harvest.kkuir.oai-endpoint` เป็น `https://kkuir.kku.ac.th/oai/request`
   - ตั้งค่า Set เริ่มต้นเป็น `col_123456789_37197`
2. **ปรับปรุง `KkuIrAdapter.java`:**
   - รองรับ OAI Date Granularity `YYYY-MM-DDThh:mm:ssZ`
   - จัดการกรณี `<error code="noRecordsMatch">` ให้ถือว่าดึงสำเร็จแต่ไม่มีรายการใหม่
   - วนลูปดึงข้อมูลทั้งจากคอลเลกชันบทความวิจัย และรายงานวิจัยของวิทยาลัยการคอมพิวเตอร์
3. **ทดสอบและยืนยันผล:**
   - รัน Unit Test & Integration Test
   - ทดสอบ Trigger บนหน้าจอ `/admin/external-sync`
