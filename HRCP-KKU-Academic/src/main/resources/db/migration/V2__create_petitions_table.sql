-- ============================================================
-- Migration: ตาราง petitions
--
-- เขียนใหม่ 2026-08-24: ของเดิมเป็น syntax MySQL ซึ่ง PostgreSQL รันไม่ได้
--
-- ตอนนี้เป็น no-op โดยเจตนา — V1 สร้าง petitions ไปแล้ว เพราะ petition_statuses
-- ใน V1 มี FK ชี้มาที่ตารางนี้ จึงต้องมีอยู่ก่อน
--
-- คงไฟล์ไว้ (ไม่ลบ ไม่เปลี่ยนเลขเวอร์ชัน) เพราะฐานข้อมูลที่เคยรัน migration ชุดนี้
-- สำเร็จจะมีเวอร์ชัน 2 บันทึกไว้ใน flyway_schema_history แล้ว การลบไฟล์จะทำให้
-- Flyway รายงานว่า migration หายไปและ validate ไม่ผ่าน
-- ============================================================

CREATE TABLE IF NOT EXISTS petitions (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_petition_user FOREIGN KEY (user_id)
        REFERENCES user_dtls(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_petition_user_id ON petitions (user_id);
CREATE INDEX IF NOT EXISTS idx_petition_created_at ON petitions (created_at);
