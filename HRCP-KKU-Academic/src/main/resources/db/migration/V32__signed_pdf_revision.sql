-- V32: เอกสารลงนามแบบใส่ทับ (PAdES incremental) — ผู้ลงนามแต่ละคนเซ็นลงไฟล์ PDF ตอนกดลงนาม
--
-- ไฟล์ของซองหนึ่งคือสายของ revision: revision 0 คือเอกสารตั้งต้นที่มีช่องลงนามและช่องเลขที่/วันที่
-- จองไว้แล้ว แต่ละ revision ต่อท้ายไฟล์เดิมโดยไม่แตะไบต์เดิม (ลงนาม เติมช่อง หรือปิดเอกสาร)
-- ตารางนี้เก็บเฉพาะส่วนที่ต่อท้ายของแต่ละ revision; ไฟล์ของ revision N คือ delta 0..N ต่อกัน
--
-- ซองเดิมทั้งหมดเป็น LEGACY และยังเดินทางเดิมจนจบ เพิ่มคอลัมน์และตารางใหม่เท่านั้น ไม่แตะข้อมูลเดิม

CREATE TABLE IF NOT EXISTS signed_pdf_revision (
    id                   BIGSERIAL PRIMARY KEY,
    signature_request_id BIGINT       NOT NULL REFERENCES signature_request(id) ON DELETE CASCADE,
    revision_no          INTEGER      NOT NULL,
    kind                 VARCHAR(10)  NOT NULL,
    step_id              BIGINT,
    actor_user_id        INTEGER,
    delta                BYTEA        NOT NULL,
    total_length         BIGINT       NOT NULL,
    sha256               VARCHAR(64)  NOT NULL,
    cert_fingerprint     VARCHAR(64),
    created_at           TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_signed_pdf_revision UNIQUE (signature_request_id, revision_no)
);

ALTER TABLE signature_request ADD COLUMN IF NOT EXISTS pdf_mode VARCHAR(15) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE signature_request ADD COLUMN IF NOT EXISTS current_revision_no INTEGER;
ALTER TABLE signature_request ADD COLUMN IF NOT EXISTS field_layout_json TEXT;
ALTER TABLE signature_request ADD COLUMN IF NOT EXISTS pdf_locked_at TIMESTAMP(6);

ALTER TABLE signature_step ADD COLUMN IF NOT EXISTS pdf_revision_no INTEGER;
