-- V29: สำเนาไฟล์ลายเซ็นและใบรับรอง .p12 เก็บในฐานข้อมูล
--
-- เดิมรูปลายเซ็นและไฟล์ .p12 อยู่บนดิสก์ของเครื่องที่รับอัปโหลดเท่านั้น แต่แถวที่ชี้ไปหาไฟล์
-- อยู่ในฐานข้อมูลที่ทุกเครื่องใช้ร่วมกัน สองเครื่องต่อฐานเดียวกัน (ตอนเทส) จึงเห็นใบรับรองของอีก
-- เครื่องเป็น "ไม่พบไฟล์" และอัปโหลดใหม่ทีไรก็ปิดใบของอีกเครื่องทุกที
--
-- ไฟล์บนดิสก์ยังเป็นตัวหลักเหมือนเดิม ตารางนี้เป็นสำเนาให้เครื่องที่ไม่มีไฟล์อ่านได้ (BlobMirror)
-- ไฟล์เดิมที่มีอยู่แล้วถูกคัดลอกเข้ามาตอนแอปเริ่ม (StoredBlobBackfill) ไม่ต้องย้ายข้อมูลด้วยมือ
-- blob_key = kind + '/' + ชื่อไฟล์ ตรงกับค่าที่เก็บใน user_signature.image_path,
-- signature_step.image_path_snapshot และ user_digital_certificate.certificate_path

CREATE TABLE IF NOT EXISTS stored_blob (
    blob_key   VARCHAR(300) PRIMARY KEY,
    kind       VARCHAR(30)  NOT NULL,
    content    BYTEA        NOT NULL,
    size_bytes BIGINT       NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
