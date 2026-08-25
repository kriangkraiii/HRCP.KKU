-- ผู้ยื่นแก้ไขเอกสารหลังส่งคำร้องไม่ได้อีกต่อไป
-- ประตูเดียวที่เปิดให้กลับมาแก้คือแอดมินกด "ขอให้แก้ไขและลงนามใหม่" (request-resign)
-- null = ล็อก (ค่าเริ่มต้นของคำร้องเดิมทุกใบ จึงล็อกทันทีที่ deploy)
ALTER TABLE academic_document
    ADD COLUMN IF NOT EXISTS revision_requested_at TIMESTAMP NULL;

-- เหตุผลที่แอดมินส่งกลับ แสดงให้ผู้ยื่นเห็นบนหน้าเอกสาร
ALTER TABLE academic_document
    ADD COLUMN IF NOT EXISTS revision_note VARCHAR(500) NULL;
