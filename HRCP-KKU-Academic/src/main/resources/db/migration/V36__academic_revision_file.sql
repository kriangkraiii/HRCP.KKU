-- V36: ฉบับแก้ไขที่ผู้ยื่นส่งกลับมา (Flow ข้อ 13) — ส่งได้หลายไฟล์/ลิงก์ต่อรอบ และเก็บทุกรอบ
--
-- เดิมเก็บไว้ที่ academic_request.revision_file_path ได้ไฟล์เดียว และรอบใหม่ลบไฟล์รอบก่อนทิ้ง
-- คอลัมน์เดิมยังอยู่สำหรับคำร้องเก่าที่ส่งฉบับแก้ไว้ก่อนมีตารางนี้

CREATE TABLE IF NOT EXISTS academic_revision_file (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES academic_request(id) ON DELETE CASCADE,
    revision_round INT NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    stored_path VARCHAR(2048) NOT NULL,
    file_type VARCHAR(20),
    file_size BIGINT,
    uploaded_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_acad_rev_req_round ON academic_revision_file (request_id, revision_round);
