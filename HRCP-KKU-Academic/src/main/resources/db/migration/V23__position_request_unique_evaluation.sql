-- V23: ผลประเมินการสอน 1 ฉบับ ผูกกับคำร้องขอตำแหน่งได้ 1 คำร้อง (Flow ข้อ 30: เอกสารประเมินการสอน 1 ชุด)
--
-- linked_evaluation_id ยังเป็น NULL ได้ เพราะการขอตำแหน่งศาสตราจารย์ไม่ต้องใช้ผลประเมินการสอน
-- UNIQUE ของ PostgreSQL ยอมให้มี NULL ได้หลายแถว
--
-- ถ้ามีข้อมูลเดิมที่ผูกผลประเมินซ้ำ migration นี้จะล้ม ตรวจก่อน deploy ด้วย:
--   SELECT linked_evaluation_id, COUNT(*), STRING_AGG(request_code, ', ')
--   FROM position_request WHERE linked_evaluation_id IS NOT NULL
--   GROUP BY linked_evaluation_id HAVING COUNT(*) > 1;

ALTER TABLE position_request
    ADD CONSTRAINT uk_pos_req_linked_evaluation UNIQUE (linked_evaluation_id);
