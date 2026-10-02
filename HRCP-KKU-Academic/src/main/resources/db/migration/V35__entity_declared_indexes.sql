-- V35: สร้าง index ที่ entity ประกาศไว้ใน @Table(indexes = ...) ให้ครบบนทุกฐานข้อมูล
--
-- production รันด้วย ddl-auto=validate ซึ่งตรวจแค่ตารางกับคอลัมน์ ไม่สร้าง index ให้
-- index ที่ entity ประกาศจึงมีอยู่จริงก็ต่อเมื่อเคยบูตด้วย ddl-auto=update ในช่วงที่ประกาศไว้แล้ว
-- อันที่เพิ่มทีหลังไม่มีอะไรรับประกันว่ามี — ทั้งที่ query ที่ยิงทุกหน้าพึ่งมันอยู่ เช่น
-- นับแจ้งเตือนที่ยังไม่อ่าน (notifications), นับลายเซ็นที่รอ (signature_step),
-- และเอกสารของคำร้องตามประเภท (academic_document / position_document)
--
-- ใช้ชื่อและคอลัมน์เดียวกับใน entity และ IF NOT EXISTS ฐานที่มีอยู่แล้วจึงไม่เปลี่ยนอะไร
-- ข้ามตารางหรือคอลัมน์ที่ยังไม่มี (ฐานเปล่าใน MigrationOnPostgresTest มีแค่บางตาราง)
-- ไม่รวม index ที่ migration อื่นสร้างไว้แล้ว (kku_regulation_docs, position_request_publication)

DO $$
DECLARE
    spec TEXT[];
    col TEXT;
    ready BOOLEAN;
BEGIN
    FOREACH spec SLICE 1 IN ARRAY ARRAY[
        -- table, index, columns
        ['academic_attachment', 'idx_acad_att_req_del', 'request_id, is_deleted'],
        ['academic_attachment', 'idx_acad_att_req_upload', 'request_id, uploaded_at DESC'],
        ['academic_committee_member', 'idx_comm_email', 'email'],
        ['academic_committee_member', 'idx_comm_affiliation', 'affiliation'],
        ['academic_committee_member', 'idx_comm_type', 'committee_type'],
        ['academic_committee_member', 'idx_comm_active', 'is_active'],
        ['academic_document', 'idx_acad_doc_req_type', 'request_id, document_type'],
        ['academic_document', 'idx_acad_doc_req_draft', 'request_id, is_draft, is_deleted'],
        ['academic_document_edit_log', 'idx_acad_doc_log_req_date', 'request_id, edited_at DESC'],
        ['academic_request', 'idx_acad_req_applicant', 'applicant_id'],
        ['academic_request', 'idx_acad_req_status', 'current_status'],
        ['academic_request', 'idx_acad_req_created', 'created_at DESC'],
        ['admin_file', 'idx_adminfile_folder_del', 'folder_id, is_deleted'],
        ['admin_file', 'idx_adminfile_del', 'is_deleted'],
        ['admin_folder', 'idx_adminfolder_parent', 'parent_id'],
        ['admin_logs', 'idx_admin_logs_timestamp', 'timestamp'],
        ['admin_logs', 'idx_admin_logs_action_ts', 'action, timestamp'],
        ['admin_logs', 'idx_admin_logs_email', 'admin_email'],
        ['digital_certificate_audit', 'idx_digital_cert_audit_user', 'user_id, created_at'],
        ['fs_faculty', 'idx_fs_faculty_email', 'email'],
        ['fs_faculty', 'idx_fs_faculty_scopus_id', 'scopus_id'],
        ['fs_faculty', 'idx_fs_faculty_source_updated', 'source_updated_at'],
        ['fs_faculty_change', 'idx_fs_change_status_detected', 'status, detected_at'],
        ['fs_faculty_change', 'idx_fs_change_user_status', 'fs_user_id, status'],
        ['notifications', 'idx_notif_recipient_active', 'recipient_id, is_deleted, is_read'],
        ['notifications', 'idx_notif_recipient_created', 'recipient_id, created_at DESC'],
        ['notifications', 'idx_notif_recipient_starred', 'recipient_id, is_deleted, is_starred'],
        ['notifications', 'idx_notif_recipient_important', 'recipient_id, is_deleted, is_important'],
        ['position_attachment', 'idx_pos_att_req_del', 'request_id, is_deleted'],
        ['position_attachment', 'idx_pos_att_req_upload', 'request_id, uploaded_at DESC'],
        ['position_document', 'idx_pos_doc_req_type', 'request_id, document_type'],
        ['position_document', 'idx_pos_doc_req_draft', 'request_id, is_draft, is_deleted'],
        ['position_document_edit_log', 'idx_pos_doc_log_req_date', 'request_id, edited_at DESC'],
        ['position_request', 'idx_pos_req_applicant', 'applicant_id'],
        ['position_request', 'idx_pos_req_app_status', 'applicant_id, current_status'],
        ['position_request', 'idx_pos_req_status', 'current_status'],
        ['position_request', 'idx_pos_req_created', 'created_at DESC'],
        ['position_status_history', 'idx_pos_hist_req_date', 'request_id, changed_at DESC'],
        ['request_status_history', 'idx_req_hist_req_date', 'request_id, changed_at DESC'],
        ['scopus_publication', 'idx_scopus_pub_user_year', 'fs_user_id, publication_year'],
        ['scopus_publication', 'idx_scopus_pub_eid', 'eid'],
        ['scopus_publication', 'idx_scopus_pub_doi', 'doi'],
        ['scopus_publication', 'idx_scopus_pub_synced_at', 'synced_at'],
        ['signature_audit_event', 'idx_sig_audit_request', 'signature_request_id, created_at'],
        ['signature_request', 'idx_sig_req_document', 'module, request_id, document_type'],
        ['signature_request', 'idx_sig_req_status_due', 'status, due_at'],
        ['signature_step', 'idx_sig_step_signer', 'signer_user_id, status'],
        ['signature_step', 'idx_sig_step_order', 'signature_request_id, step_order'],
        ['staff_member', 'idx_staff_name', 'first_name, last_name'],
        ['staff_member', 'idx_staff_role_active', 'staff_role, is_active'],
        ['user_digital_certificate', 'idx_user_digital_cert_user', 'user_id, is_active'],
        ['user_dtls', 'idx_user_email', 'email'],
        ['user_dtls', 'idx_user_reset_token', 'reset_token'],
        ['user_dtls', 'idx_user_role', 'role'],
        ['user_dtls', 'idx_user_applicant_id', 'applicant_id'],
        ['user_signature', 'idx_user_signature_owner', 'user_id, is_deleted']
    ]
    LOOP
        ready := to_regclass('public.' || spec[1]) IS NOT NULL;
        IF ready THEN
            FOREACH col IN ARRAY string_to_array(spec[3], ',') LOOP
                col := split_part(trim(col), ' ', 1);
                IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_schema = 'public' AND table_name = spec[1] AND column_name = col) THEN
                    ready := FALSE;
                END IF;
            END LOOP;
        END IF;

        IF ready THEN
            -- "timestamp" เป็นคำสงวน จึง quote ทุกคอลัมน์ ส่วน DESC ต่อท้ายตามเดิม
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (%s)', spec[2], spec[1],
                    (SELECT string_agg(format('%I', split_part(trim(c), ' ', 1))
                                       || CASE WHEN trim(c) ILIKE '% desc' THEN ' DESC' ELSE '' END, ', ')
                     FROM unnest(string_to_array(spec[3], ',')) AS c));
        ELSE
            RAISE NOTICE 'V35: skip % on % — table or column not present', spec[2], spec[1];
        END IF;
    END LOOP;
END $$;
