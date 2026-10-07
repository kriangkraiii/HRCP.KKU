-- V43: เอกสารที่ 9 เฟส 2 (แบบแสดงหลักฐานการมีส่วนร่วมในผลงานทางวิชาการ) แยกฉบับตามงานวิจัย
--
-- เอกสารแนบท้ายข้อบังคับ มข. พ.ศ. 2569 ข้อ 3.3 ให้ยื่นแบบนี้ทุกผลงาน แต่ละฉบับจึงมี document type ของตัวเอง
-- 900 + N (N = เลขแถวของงานวิจัยในเอกสารที่ 1) — ดู PositionDocTypes
-- ข้อมูลเดิมที่กรอกเป็นฉบับเดียวย้ายไปเป็นฉบับของงานวิจัยเรื่องแรก (901) พร้อมซองลงนามและประวัติการแก้ไข
-- การตั้งค่าผู้ลงนาม (document_workflow_config) ยังเก็บไว้ที่ 9 ระบบอ่านจากเอกสารที่ 9 ให้ทุกฉบับ

UPDATE position_document SET document_type = 901 WHERE document_type = 9;

UPDATE signature_request SET document_type = 901 WHERE module = 'POSITION' AND document_type = 9;

UPDATE position_document_edit_log SET document_type = 901 WHERE document_type = 9;
