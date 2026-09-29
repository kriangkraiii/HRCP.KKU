-- V31: ผูกใบรับรองที่ใช้จริงไว้กับขั้นลงนาม และบันทึกประวัติการใช้ใบรับรอง
--
-- เดิมตอนสร้าง PDF ฉบับสุดท้าย ระบบหยิบใบรับรองที่ "ใช้งานอยู่ตอนนั้น" ของผู้ลงนามมาเซ็น
-- ถ้าผู้ลงนามเปลี่ยนใบระหว่างทาง PDF จะถูกเซ็นด้วยใบที่ไม่ใช่ใบที่ตรวจตอนกดลงนาม
-- สองคอลัมน์แรกเก็บใบที่ตรวจผ่านจริงตอนลงนาม
--
-- ตาราง digital_certificate_audit เก็บเหตุการณ์ของใบรับรอง (ติดตั้ง ปิดใช้ รหัสถูก/ผิด
-- ระบบใช้รหัสที่เก็บไว้เซ็นแทน เซ็น PDF ไม่สำเร็จ) ซึ่งเดิมมีแค่ใน log ของแอป
-- เพิ่มคอลัมน์และตารางใหม่เท่านั้น ไม่แตะข้อมูลเดิม

ALTER TABLE signature_step ADD COLUMN IF NOT EXISTS digital_certificate_id BIGINT;
ALTER TABLE signature_step ADD COLUMN IF NOT EXISTS cert_fingerprint_sha256 VARCHAR(64);

CREATE TABLE IF NOT EXISTS digital_certificate_audit (
    id             BIGSERIAL PRIMARY KEY,
    user_id        INTEGER,
    certificate_id BIGINT,
    event          VARCHAR(40)  NOT NULL,
    detail         VARCHAR(500),
    ip_address     VARCHAR(45),
    created_at     TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_digital_cert_audit_user
    ON digital_certificate_audit (user_id, created_at);
