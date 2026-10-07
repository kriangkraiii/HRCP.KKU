-- V42: เอกสารจริยธรรมการวิจัยในมนุษย์ (เฟส 2 เอกสารที่ 6) มีผู้ขอลงนามเป็นลำดับแรกแล้วตามด้วยคณบดี
--
-- ช่องลงนามของผู้ขอมาจาก SignatureAnchorRegistry (ลำดับ 1) ถ้าแอดมินเคยบันทึกการตั้งค่าผู้ลงนาม
-- ของเอกสารนี้ไว้ แถวของคณบดียังเป็นลำดับ 1 และจะชนกับผู้ขอ — เลื่อนไปเป็นลำดับ 2

UPDATE document_workflow_config
   SET step_order = 2, updated_at = CURRENT_TIMESTAMP
 WHERE module = 'POSITION' AND document_type = 6 AND slot_key = 'dean' AND step_order < 2;
