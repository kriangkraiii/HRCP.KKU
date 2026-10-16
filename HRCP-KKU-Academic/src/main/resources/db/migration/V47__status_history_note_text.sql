-- V47: หมายเหตุของประวัติสถานะยาวเกิน 255 ตัวอักษรได้
--
-- สองตารางนี้ถูกสร้างโดย Hibernate ddl-auto ก่อนมี migration คอลัมน์ note จึงเป็น varchar(255)
-- ขณะที่เหตุผลขอทบทวนผลรับได้ 2,000 ตัวอักษร และหมายเหตุของเจ้าหน้าที่ไม่จำกัด — เกินแล้วบันทึกไม่ได้ทั้งการเปลี่ยนสถานะ
-- เปลี่ยนเป็น TEXT อย่างเดียว ข้อมูลเดิมไม่เปลี่ยน

ALTER TABLE request_status_history ALTER COLUMN note TYPE TEXT;
ALTER TABLE position_status_history ALTER COLUMN note TYPE TEXT;
