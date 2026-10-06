-- V40: วันที่ที่ใช้คำนวณตามประกาศ มข. ฉบับที่ 1670/2569 และข้อบังคับ มข. พ.ศ. 2569 ข้อ 35
--
-- college_resolution_date    วันที่คณะกรรมการประจำวิทยาลัยฯ มีมติเห็นชอบ — วันที่สภามหาวิทยาลัยรับเรื่อง (1670 ข้อ 6 (1))
-- corrections_received_date  มติให้แก้ไข: วันที่ได้รับเอกสารแก้ไขครบตามมติ ใช้แทนวันมติ (1670 ข้อ 6 (2))
-- council_resolution_date    วันที่สภามหาวิทยาลัยมีมติกำหนด/ไม่กำหนดตำแหน่ง
-- council_acknowledged_date  วันที่ผู้ขอรับทราบมติ — เริ่มนับ 90 วันของการขอทบทวน (ข้อ 35)
-- คำร้องเก่าไม่มีค่า (NULL)

ALTER TABLE position_request ADD COLUMN IF NOT EXISTS college_resolution_date DATE;
ALTER TABLE position_request ADD COLUMN IF NOT EXISTS corrections_received_date DATE;
ALTER TABLE position_request ADD COLUMN IF NOT EXISTS council_resolution_date DATE;
ALTER TABLE position_request ADD COLUMN IF NOT EXISTS council_acknowledged_date DATE;
