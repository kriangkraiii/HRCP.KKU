-- V45: เอกสารหนึ่งฉบับมีซองที่กำลังเวียนลงนาม (IN_PROGRESS) ได้ซองเดียว
--
-- createEnvelope ตรวจว่าเอกสารล็อกอยู่หรือไม่แล้วจึง insert โดยไม่ล็อกแถว กดส่งซ้ำเร็ว ๆ หรือส่งจากสองแท็บพร้อมกัน
-- จึงผ่านด่านทั้งคู่ ได้สองซองที่เวียนคู่กัน ผู้ลงนามเห็นการ์ดซ้ำในหน้ารอลงนามและลงนามซ้ำสองรอบ
--
-- 1. ซองที่ซ้ำอยู่แล้ว: เก็บซองที่ลงนามไปมากที่สุด (เท่ากันเก็บซองแรก) ที่เหลือยกเลิก พร้อมบันทึก audit
-- 2. unique index เฉพาะซอง IN_PROGRESS — ครอบคลุมทุกทางที่เขียนสถานะ รวมถึง SQL ตรงใน AdminInitializer
--    การ insert ซองที่สองที่มาพร้อมกันจะรอซองแรก commit แล้วล้มด้วย unique violation

CREATE TEMP TABLE v45_duplicate_envelopes ON COMMIT DROP AS
SELECT id
FROM (
    SELECT r.id,
           ROW_NUMBER() OVER (
               PARTITION BY r.module, r.request_id, r.document_type
               ORDER BY (SELECT COUNT(*) FROM signature_step s
                         WHERE s.signature_request_id = r.id AND s.status = 'SIGNED') DESC,
                        r.id ASC
           ) AS keep_rank
    FROM signature_request r
    WHERE r.status = 'IN_PROGRESS'
) ranked
WHERE keep_rank > 1;

UPDATE signature_step SET status = 'SKIPPED'
WHERE signature_request_id IN (SELECT id FROM v45_duplicate_envelopes)
  AND status IN ('WAITING', 'ACTIVE');

UPDATE signature_request
SET status = 'CANCELLED', cancelled_at = NOW(),
    cancel_reason = 'ซองซ้ำจากการส่งไปลงนามซ้ำ — ใช้ซองที่ลงนามไปมากกว่า'
WHERE id IN (SELECT id FROM v45_duplicate_envelopes);

INSERT INTO signature_audit_event (signature_request_id, event_type, detail, created_at)
SELECT id, 'CANCELLED', 'ระบบยกเลิกซองซ้ำ (V45) — เอกสารนี้มีซองที่กำลังเวียนอยู่แล้ว', NOW()
FROM v45_duplicate_envelopes;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sig_req_one_open_per_document
    ON signature_request (module, request_id, document_type)
    WHERE status = 'IN_PROGRESS';
