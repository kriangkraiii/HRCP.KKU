-- V46: ลิงก์เข้าสู่ระบบทางอีเมลของผู้ลงนามภายนอก มข. (docs/PLAN-external-magic-link.md)
--
-- คนนอก มข. ไม่มีบัญชี KKU SSO และหน้าเข้าสู่ระบบบน production มีแค่ปุ่ม SSO จึงเข้าระบบด้วยลิงก์
-- ในอีเมลแทน ลิงก์ใช้ได้ครั้งเดียวและมีอายุ เก็บแค่ SHA-256 ของ token ไม่เก็บค่าดิบ
-- user_id / step_id เป็นเลขเฉย ๆ ไม่ใช่ FK แถวจึงอยู่ต่อได้เป็นหลักฐานแม้บัญชีหรือซองถูกลบ
-- เพิ่มตารางใหม่เท่านั้น ไม่แตะข้อมูลเดิม

CREATE TABLE IF NOT EXISTS external_sign_in_link (
    id         BIGSERIAL PRIMARY KEY,
    user_id    INTEGER      NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    step_id    BIGINT,
    purpose    VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at    TIMESTAMP(6),
    used_ip    VARCHAR(45)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_external_sign_in_link_token
    ON external_sign_in_link (token_hash);

CREATE INDEX IF NOT EXISTS idx_external_sign_in_link_user
    ON external_sign_in_link (user_id, used_at);
