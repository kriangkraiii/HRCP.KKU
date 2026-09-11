-- ============================================================
-- Migration: V22__shift_phase1_doc_numbers_to_start_at_1.sql
-- Description: Re-index Phase 1 (Academic) document numbers
--              from 0..8 to 1..9 so both Phase 1 and Phase 2
--              start at document 1.
-- ============================================================

-- 1. Shift document_type in academic_document
-- Use +1000 buffer to avoid any potential transient index/constraint conflict
UPDATE academic_document
SET document_type = document_type + 1000
WHERE document_type >= 0 AND document_type <= 8;

UPDATE academic_document
SET document_type = document_type - 999
WHERE document_type >= 1000 AND document_type <= 1008;

-- Update generated_file_path in academic_document to match new filenames
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_8.docx', '/doc_9.docx') WHERE generated_file_path LIKE '%/doc_8.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_7.docx', '/doc_8.docx') WHERE generated_file_path LIKE '%/doc_7.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_6.docx', '/doc_7.docx') WHERE generated_file_path LIKE '%/doc_6.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_5.docx', '/doc_6.docx') WHERE generated_file_path LIKE '%/doc_5.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_4_copy_', '/doc_5_copy_') WHERE generated_file_path LIKE '%/doc_4_copy_%';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_4.docx', '/doc_5.docx') WHERE generated_file_path LIKE '%/doc_4.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_3.docx', '/doc_4.docx') WHERE generated_file_path LIKE '%/doc_3.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_2.docx', '/doc_3.docx') WHERE generated_file_path LIKE '%/doc_2.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_1.docx', '/doc_2.docx') WHERE generated_file_path LIKE '%/doc_1.docx';
UPDATE academic_document SET generated_file_path = REPLACE(generated_file_path, '/doc_0.docx', '/doc_1.docx') WHERE generated_file_path LIKE '%/doc_0.docx';

-- Update labels in academic_document
UPDATE academic_document SET document_label = 'บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน' WHERE document_type = 1;
UPDATE academic_document SET document_label = 'แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน' WHERE document_type = 2;
UPDATE academic_document SET document_label = 'การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ' WHERE document_type = 3;
UPDATE academic_document SET document_label = 'คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน' WHERE document_type = 4;
UPDATE academic_document SET document_label = 'บันทึกข้อความ ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ' WHERE document_type = 5;
UPDATE academic_document SET document_label = 'ข้อเสนอแนะจากคณะอนุกรรมการ' WHERE document_type = 6;
UPDATE academic_document SET document_label = 'แบบฟอร์มประเมินการสอน ตามประกาศ มข.1607-66' WHERE document_type = 7;
UPDATE academic_document SET document_label = 'ส่วนที่ 3 แบบประเมินผลการสอน' WHERE document_type = 8;
UPDATE academic_document SET document_label = 'บันทึกข้อความ แจ้งผลการประเมินผลการสอน' WHERE document_type = 9;

-- 2. Shift document_type in academic_document_edit_log
UPDATE academic_document_edit_log
SET document_type = document_type + 1000
WHERE document_type >= 0 AND document_type <= 8;

UPDATE academic_document_edit_log
SET document_type = document_type - 999
WHERE document_type >= 1000 AND document_type <= 1008;

-- 3. Shift document_type in document_workflow_config for ACADEMIC module
UPDATE document_workflow_config
SET document_type = document_type + 1000
WHERE module = 'ACADEMIC' AND document_type >= 0 AND document_type <= 8;

UPDATE document_workflow_config
SET document_type = document_type - 999
WHERE module = 'ACADEMIC' AND document_type >= 1000 AND document_type <= 1008;

-- 4. Shift document_type in signature_request for ACADEMIC module
UPDATE signature_request
SET document_type = document_type + 1000
WHERE module = 'ACADEMIC' AND document_type >= 0 AND document_type <= 8;

UPDATE signature_request
SET document_type = document_type - 999
WHERE module = 'ACADEMIC' AND document_type >= 1000 AND document_type <= 1008;
