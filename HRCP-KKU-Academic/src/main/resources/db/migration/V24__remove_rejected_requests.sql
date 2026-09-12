-- V24: ลบคำร้องที่เป็น REJECTED ทิ้ง เพราะสถานะนี้ถูกถอดออกจากระบบแล้ว
--
-- Flow การขอกำหนดตำแหน่งทางวิชาการไม่มีการปฏิเสธที่ทำให้คำร้องตกไป ทุกคำตอบ "NO" คือการตีกลับให้แก้
-- (ข้อ 2 คณบดีไม่เห็นชอบ → ส่งคืนเป็นแบบร่าง, ข้อ 12-15 / 22 / 27 → ส่งแก้ไข) เมื่อค่า REJECTED
-- หายไปจาก enum แล้ว แถวที่ยังเก็บค่านี้ไว้จะโหลดขึ้นมาไม่ได้ หน้าที่แตะมันจะพังทั้งหน้า
--
-- ⚠️ ลบแล้วย้อนไม่ได้ ตรวจก่อน deploy ว่าจะลบกี่แถว:
--   SELECT 'academic_request' t, COUNT(*) FROM academic_request WHERE current_status = 'REJECTED'
--   UNION ALL SELECT 'request_status_history', COUNT(*) FROM request_status_history
--       WHERE 'REJECTED' IN (old_status, new_status)
--   UNION ALL SELECT 'position_request', COUNT(*) FROM position_request WHERE current_status = 'REJECTED'
--   UNION ALL SELECT 'position_status_history', COUNT(*) FROM position_status_history
--       WHERE 'REJECTED' IN (old_status, new_status);
--
-- ⚠️ ไฟล์เอกสารบนดิสก์ (uploads/) ไม่ถูกลบ SQL ลบไฟล์ไม่ได้ จะเหลือเป็นไฟล์ที่ไม่มีคำร้องอ้างถึง
--
-- ลบตารางลูกเองทุกตัวโดยไม่พึ่ง ON DELETE CASCADE เพราะ schema ที่ Hibernate สร้าง (ที่ใช้ในเทสต์)
-- ไม่มี cascade เหมือน schema ของ production

-- ============================================================
-- เฟส 2: คำร้องขอตำแหน่งทางวิชาการ
-- ============================================================
DELETE FROM search_document
WHERE entity_type = 'POSITION_DOCUMENT'
  AND entity_id IN (SELECT id FROM position_document
                    WHERE request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED'));

DELETE FROM search_document
WHERE entity_type = 'POSITION_ATTACHMENT'
  AND entity_id IN (SELECT id FROM position_attachment
                    WHERE request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED'));

DELETE FROM search_document
WHERE entity_type = 'POSITION_REQUEST'
  AND entity_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED');

DELETE FROM notifications
WHERE link IN (SELECT '/user/position/request/' || CAST(id AS VARCHAR) FROM position_request
               WHERE current_status = 'REJECTED')
   OR link IN (SELECT '/admin/position/request/' || CAST(id AS VARCHAR) FROM position_request
               WHERE current_status = 'REJECTED');

DELETE FROM signature_audit_event
WHERE signature_request_id IN (
    SELECT id FROM signature_request
    WHERE module = 'POSITION'
      AND request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED'));

DELETE FROM signature_step
WHERE signature_request_id IN (
    SELECT id FROM signature_request
    WHERE module = 'POSITION'
      AND request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED'));

DELETE FROM signature_request
WHERE module = 'POSITION'
  AND request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED');

DELETE FROM position_document_edit_log
WHERE request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED');

DELETE FROM position_attachment
WHERE request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED');

DELETE FROM position_status_history
WHERE request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED');

DELETE FROM position_document
WHERE request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED');

DELETE FROM position_request_publication
WHERE request_id IN (SELECT id FROM position_request WHERE current_status = 'REJECTED');

DELETE FROM position_request WHERE current_status = 'REJECTED';

-- ============================================================
-- เฟส 1: คำร้องขอประเมินผลการสอน
-- ============================================================

-- คำร้องขอตำแหน่งที่ผูกผลประเมินฉบับที่จะลบ ยังอยู่ต่อได้ แต่ต้องปล่อยผลประเมินนั้นก่อน
UPDATE position_request
SET linked_evaluation_id = NULL
WHERE linked_evaluation_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED');

DELETE FROM search_document
WHERE entity_type = 'ACADEMIC_DOCUMENT'
  AND entity_id IN (SELECT id FROM academic_document
                    WHERE request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED'));

DELETE FROM search_document
WHERE entity_type = 'ACADEMIC_ATTACHMENT'
  AND entity_id IN (SELECT id FROM academic_attachment
                    WHERE request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED'));

DELETE FROM search_document
WHERE entity_type = 'ACADEMIC_REQUEST'
  AND entity_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED');

DELETE FROM notifications
WHERE link IN (SELECT '/user/academic/request/' || CAST(id AS VARCHAR) FROM academic_request
               WHERE current_status = 'REJECTED')
   OR link IN (SELECT '/admin/academic/request/' || CAST(id AS VARCHAR) FROM academic_request
               WHERE current_status = 'REJECTED');

DELETE FROM signature_audit_event
WHERE signature_request_id IN (
    SELECT id FROM signature_request
    WHERE module = 'ACADEMIC'
      AND request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED'));

DELETE FROM signature_step
WHERE signature_request_id IN (
    SELECT id FROM signature_request
    WHERE module = 'ACADEMIC'
      AND request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED'));

DELETE FROM signature_request
WHERE module = 'ACADEMIC'
  AND request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED');

DELETE FROM academic_document_edit_log
WHERE request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED');

DELETE FROM academic_attachment
WHERE request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED');

DELETE FROM request_status_history
WHERE request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED');

DELETE FROM academic_document
WHERE request_id IN (SELECT id FROM academic_request WHERE current_status = 'REJECTED');

DELETE FROM academic_request WHERE current_status = 'REJECTED';

-- ============================================================
-- คำร้องที่เคยเป็น REJECTED แล้วเจ้าหน้าที่ย้อนสถานะออกมา — ตัวคำร้องยังอยู่ แต่ประวัติยังเก็บค่าเดิมไว้
-- ============================================================
DELETE FROM request_status_history WHERE old_status = 'REJECTED' OR new_status = 'REJECTED';
DELETE FROM position_status_history WHERE old_status = 'REJECTED' OR new_status = 'REJECTED';
