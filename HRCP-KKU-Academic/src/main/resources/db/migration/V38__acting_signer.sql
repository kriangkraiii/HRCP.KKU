-- V38: ผู้รักษาการแทน
--
-- acting_signer: แอดมินตั้งว่าใครรักษาการแทนตำแหน่งลงนามไหน (คณบดี / รองคณบดี / หัวหน้าสาขาวิชา)
-- หนึ่งแถวต่อหนึ่งตำแหน่ง (slot_key ตรงกับ SignatureAnchorRegistry) เปิด/ปิดเองด้วย active
-- position_title เก็บตำแหน่งเต็มที่รักษาการแทน เช่น "คณบดีวิทยาลัยการคอมพิวเตอร์"
-- เอกสารพิมพ์เป็น "รักษาการแทน" + position_title ไม่ย่อ
--
-- signature_step.acting_position: ตำแหน่งที่พิมพ์ใต้ชื่อของขั้นที่มอบให้ผู้รักษาการแทน
-- จำไว้ ณ ตอนสร้างขั้น — ปิดการรักษาการแทนทีหลัง ซองที่ส่งไปแล้วไม่เปลี่ยน
-- ซองที่ส่งก่อนเปิดการรักษาการแทนก็ไม่เปลี่ยนเช่นกัน (NULL)

CREATE TABLE IF NOT EXISTS acting_signer (
    id BIGSERIAL PRIMARY KEY,
    slot_key VARCHAR(50) NOT NULL UNIQUE,
    acting_user_id INTEGER REFERENCES user_dtls(id),
    position_title VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by_user_id INTEGER REFERENCES user_dtls(id)
);

ALTER TABLE signature_step ADD COLUMN IF NOT EXISTS acting_position VARCHAR(300);
