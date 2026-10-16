-- V50: รวมผลงานตีพิมพ์ซ้ำแบบนุ่ม (docs/PLAN-bughunt-decisions.md ข้อ 2b)
--
-- ผลงานชิ้นเดียวกันที่มีสองแถว (Scopus กับแหล่งอื่น ก่อนแก้การกรองซ้ำ) ทำให้ผู้ยื่นใช้ผลงานเดิมยื่นซ้ำได้
-- แถวที่ถูกรวมไม่ถูกลบ: id ที่อ้างไว้ใน JSON ของเอกสารยังหาแถวที่เหลือได้ผ่าน merged_into_id
-- เพิ่มคอลัมน์อย่างเดียว การรวมจริงทำจากหน้าแอดมินหลังตรวจรายการ

ALTER TABLE scopus_publication ADD COLUMN IF NOT EXISTS merged_into_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_scopus_pub_merged_into ON scopus_publication (merged_into_id);
