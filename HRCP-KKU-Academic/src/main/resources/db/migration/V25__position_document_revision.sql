-- V25: เปิดประตูให้ผู้ยื่นแก้เอกสารเฟส 2 ได้เฉพาะตอนที่แอดมินส่งกลับมา
--
-- เฟส 1 มีประตูนี้มาตั้งแต่ต้น (academic_document.revision_requested_at) แต่เฟส 2 ไม่มี ผลคือ
-- PositionApplicantController ไม่เช็กอะไรเลยก่อนรับ POST — ผู้ยื่นแก้เอกสารของตัวเองได้ตลอดเวลา
-- แม้เอกสารจะลงนามครบไปแล้ว ซึ่งทำให้แฮชของซองลายเซ็นไม่ตรงและลายเซ็นเป็นโมฆะย้อนหลัง
--
-- เรื่องนี้กลายเป็นเรื่องคอขาดบาดตายเมื่อแอดมินแก้เอกสารของผู้ยื่นไม่ได้อีกต่อไป เพราะ
-- "ส่งกลับให้ผู้ยื่นแก้" กลายเป็นทางเดียวที่เหลือ มันจึงต้องเปิดสิทธิ์แก้ได้จริง
--
-- ทั้งสองคอลัมน์เป็น nullable โดยตั้งใจ: null = ยังไม่เคยถูกส่งกลับ = แก้ไม่ได้

ALTER TABLE position_document ADD COLUMN IF NOT EXISTS revision_requested_at TIMESTAMP;
ALTER TABLE position_document ADD COLUMN IF NOT EXISTS revision_note VARCHAR(500);

-- หาเอกสารที่รอผู้ยื่นแก้อยู่ ใช้บ่อยพอ ๆ กับ index คู่ของเฟส 1
CREATE INDEX IF NOT EXISTS idx_pos_doc_revision
    ON position_document (request_id, document_type, revision_requested_at);
