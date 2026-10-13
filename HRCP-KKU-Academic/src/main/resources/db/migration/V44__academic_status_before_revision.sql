-- V44: สถานะ "ส่งแก้ไขเอกสาร" (DOCUMENT_REVISION) ของคำร้องประเมินผลการสอน
--
-- status_before_revision  ขั้นที่เจ้าหน้าที่ส่งเอกสารกลับมาให้ผู้ยื่นแก้ — แก้และลงนามใหม่ครบแล้ว
--                         คำร้องกลับไปขั้นนี้เอง ค่าว่าง (NULL) เมื่อไม่ได้อยู่ระหว่างส่งแก้ไข
-- current_status เป็น varchar(50) ไม่มี CHECK (V12) สถานะใหม่จึงไม่ต้องแก้คอลัมน์

ALTER TABLE academic_request ADD COLUMN IF NOT EXISTS status_before_revision VARCHAR(50);
