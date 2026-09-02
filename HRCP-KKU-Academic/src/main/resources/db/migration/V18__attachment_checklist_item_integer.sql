-- แก้ชนิดคอลัมน์ checklist_item ให้ตรงกับ entity
--
-- V16 สร้างคอลัมน์นี้เป็น SMALLINT แต่ AcademicAttachment.checklistItem
-- ประกาศเป็น Integer ซึ่ง Hibernate map เป็น INTEGER
--
-- ddl-auto ตั้งไว้เป็น validate แอปจึงไม่ start เลย ขึ้นข้อความว่า
--   wrong column type encountered in column [checklist_item]
--   found [int2 (Types#SMALLINT)], but expecting [integer (Types#INTEGER)]
--
-- เลือกขยายคอลัมน์เป็น integer แทนการเปลี่ยน entity เป็น Short เพราะ
-- โค้ดที่เรียกใช้ทั้งหมดใช้ Integer อยู่แล้ว การแก้ที่ฝั่งฐานข้อมูล
-- จึงกระทบน้อยกว่าและไม่ต้องไล่แก้ตามทุกจุด
--
-- ค่าที่เก็บจริงคือ 1-5 การขยายจาก smallint เป็น integer ไม่ทำให้ข้อมูลเสีย

ALTER TABLE academic_attachment
    ALTER COLUMN checklist_item TYPE integer;
