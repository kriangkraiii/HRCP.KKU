-- ============================================================
-- Migration: ตาราง petitions และ petition_statuses (ระบบคำร้องทั่วไป)
--
-- เขียนใหม่ 2026-08-24: ของเดิมเป็น syntax MySQL (AUTO_INCREMENT, ENGINE=InnoDB,
-- INDEX ในคำสั่ง CREATE TABLE) ซึ่ง PostgreSQL parse ไม่ผ่าน ทำให้ Flyway ล้ม
-- ตั้งแต่ migration แรกและแอปสตาร์ตไม่ขึ้น
--
-- ไฟล์นี้สร้าง `petitions` ก่อน ทั้งที่ชื่อไฟล์บอกว่า petition_statuses เพราะ
-- petition_statuses มี FK ชี้ไป petitions แต่ V2 (ที่สร้าง petitions) รันทีหลัง
-- — ลำดับเดิมจึงเป็นไปไม่ได้ตั้งแต่แรก ส่วน V2 คงไว้และกลายเป็น no-op
--
-- ชนิดคอลัมน์อ้างอิงจาก entity เพราะ ddl-auto=validate จะตรวจให้ตรงกัน:
--   Petition.id / PetitionStatus.id = Long        -> BIGSERIAL
--   Petition.user -> UserDtls.id    = Integer     -> INTEGER (ของเดิมเป็น BIGINT ซึ่งผิด)
--   PetitionStatus.statusType       = enum STRING -> VARCHAR(50)
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

CREATE TABLE IF NOT EXISTS petition_statuses (
    id BIGSERIAL PRIMARY KEY,
    petition_id BIGINT NOT NULL,
    status_type VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_petition_status_petition FOREIGN KEY (petition_id)
        REFERENCES petitions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_petition_status_petition_id ON petition_statuses (petition_id);
CREATE INDEX IF NOT EXISTS idx_petition_status_created_at ON petition_statuses (created_at);
-- ใช้ตอนดึงสถานะล่าสุดของคำร้อง (เรียงตามเวลาภายในคำร้องเดียว)
CREATE INDEX IF NOT EXISTS idx_petition_status_composite ON petition_statuses (petition_id, created_at);
