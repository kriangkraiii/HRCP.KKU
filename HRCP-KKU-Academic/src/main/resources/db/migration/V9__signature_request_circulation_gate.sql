-- เอกสารต้องรอเจ้าหน้าที่ตรวจก่อน จึงจะเวียนไปยังผู้ลงนามลำดับถัดไปได้
-- null = ยังไม่ได้ปล่อยเวียน (ผู้ยื่นลงนามส่วนตัวเองได้ตามปกติ)
ALTER TABLE signature_request
    ADD COLUMN IF NOT EXISTS circulation_started_at TIMESTAMP NULL;

-- ซองที่เวียนอยู่ก่อนมีประตูตรวจนี้ ถือว่าปล่อยเวียนไปแล้ว
-- ไม่งั้นเอกสารที่ค้างอยู่กลางทางจะหยุดนิ่งโดยไม่มีใครรู้
UPDATE signature_request
SET circulation_started_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE circulation_started_at IS NULL
  AND status IN ('IN_PROGRESS', 'COMPLETED');
