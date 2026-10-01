-- V34: ตำแหน่งที่พิมพ์ในเอกสาร และหน่วยงานของผู้ลงนามภายนอก
--
-- position_title: ตำแหน่งบริหาร/ตำแหน่งงาน เช่น "คณบดีวิทยาลัยการคอมพิวเตอร์" — ต่างจาก academic_position
-- (ตำแหน่งทางวิชาการ ผศ./รศ./ศ.) ใช้เติมช่อง "ตำแหน่ง" ข้างชื่อผู้ลงนามในแบบฟอร์มให้อัตโนมัติ
-- ไม่มีแหล่งข้อมูลภายนอกให้ซิงก์ ระบบเก็บจากค่าที่กรอกในเอกสารครั้งแรก และแก้เองได้ในโปรไฟล์
--
-- affiliation: หน่วยงานของบัญชีผู้ลงนามภายนอก (ROLE_EXTERNAL) เช่น ผู้ประพันธ์จากมหาวิทยาลัยอื่น
-- ดู docs/PLAN-signer-picker.md และ docs/PLAN-external-signer.md

ALTER TABLE user_dtls ADD COLUMN IF NOT EXISTS position_title VARCHAR(255);
ALTER TABLE user_dtls ADD COLUMN IF NOT EXISTS affiliation VARCHAR(255);
