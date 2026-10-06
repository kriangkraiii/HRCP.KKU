-- V39: ประกาศบนหน้าเข้าสู่ระบบ
--
-- แอดมินพิมพ์ข้อความเอง (เช่น แจ้งปิดปรับปรุงระบบวันที่/ช่วงเวลา) แล้วเลือกเปิดหรือปิดการแสดง
-- มีประกาศเดียวทั้งระบบ จึงเก็บแถวเดียว id = 1 — ปิดแสดงแล้วข้อความยังเก็บไว้ใช้รอบหน้าได้

CREATE TABLE IF NOT EXISTS login_announcement (
    id INTEGER PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    message TEXT,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

INSERT INTO login_announcement (id, enabled)
SELECT 1, FALSE
WHERE NOT EXISTS (SELECT 1 FROM login_announcement WHERE id = 1);
