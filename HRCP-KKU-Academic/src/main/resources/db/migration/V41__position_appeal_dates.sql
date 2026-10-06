-- V41: วันที่ของการขอทบทวนผลการพิจารณา ตามข้อบังคับ มข. พ.ศ. 2569 ข้อ 35 (รอบล่าสุด)
--
-- appeal_received_date  วันที่หน่วยงานของส่วนงานรับเรื่องขอทบทวน — ถือเป็นวันที่สภามหาวิทยาลัยรับเรื่อง
--                       และเป็นวันที่ใช้ตัดสินว่ายื่นภายใน 90 วันนับจากวันรับทราบมติหรือไม่
-- appeal_endorsed_date  วันที่คณะกรรมการประจำส่วนงานเห็นชอบให้เสนอขอทบทวนต่อมหาวิทยาลัย
-- คำร้องเก่าไม่มีค่า (NULL)

ALTER TABLE position_request ADD COLUMN IF NOT EXISTS appeal_received_date DATE;
ALTER TABLE position_request ADD COLUMN IF NOT EXISTS appeal_endorsed_date DATE;
