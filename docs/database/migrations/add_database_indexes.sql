-- ============================================================================
-- HRCP.KKU Database Indexing Migration Script (PostgreSQL Compatible)
-- Purpose: Optimize lookup performance, foreign key joins, and tab queries
-- Safe to run on live production database: Uses IF NOT EXISTS / CONCURRENTLY
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Core Users Table (user_dtls)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_user_email ON user_dtls (email);
CREATE INDEX IF NOT EXISTS idx_user_reset_token ON user_dtls (reset_token);
CREATE INDEX IF NOT EXISTS idx_user_role ON user_dtls (role);
CREATE INDEX IF NOT EXISTS idx_user_applicant_id ON user_dtls (applicant_id);

-- ----------------------------------------------------------------------------
-- 2. Notifications Table (notifications)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_notif_recipient_active ON notifications (recipient_id, is_deleted, is_read);
CREATE INDEX IF NOT EXISTS idx_notif_recipient_created ON notifications (recipient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notif_recipient_starred ON notifications (recipient_id, is_deleted, is_starred);
CREATE INDEX IF NOT EXISTS idx_notif_recipient_important ON notifications (recipient_id, is_deleted, is_important);

-- ----------------------------------------------------------------------------
-- 3. Academic Request Evaluation (academic_request)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_acad_req_applicant ON academic_request (applicant_id);
CREATE INDEX IF NOT EXISTS idx_acad_req_status ON academic_request (current_status);
CREATE INDEX IF NOT EXISTS idx_acad_req_created ON academic_request (created_at DESC);

-- ----------------------------------------------------------------------------
-- 4. Position Request Evaluation (position_request)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pos_req_applicant ON position_request (applicant_id);
CREATE INDEX IF NOT EXISTS idx_pos_req_app_status ON position_request (applicant_id, current_status);
CREATE INDEX IF NOT EXISTS idx_pos_req_status ON position_request (current_status);
CREATE INDEX IF NOT EXISTS idx_pos_req_created ON position_request (created_at DESC);

-- ----------------------------------------------------------------------------
-- 5. Attachments (academic_attachment & position_attachment)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_acad_att_req_del ON academic_attachment (request_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_acad_att_req_upload ON academic_attachment (request_id, uploaded_at DESC);

CREATE INDEX IF NOT EXISTS idx_pos_att_req_del ON position_attachment (request_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_pos_att_req_upload ON position_attachment (request_id, uploaded_at DESC);

-- ----------------------------------------------------------------------------
-- 6. Documents & Forms (academic_document & position_document)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_acad_doc_req_type ON academic_document (request_id, document_type);
CREATE INDEX IF NOT EXISTS idx_acad_doc_req_draft ON academic_document (request_id, is_draft, is_deleted);

CREATE INDEX IF NOT EXISTS idx_pos_doc_req_type ON position_document (request_id, document_type);
CREATE INDEX IF NOT EXISTS idx_pos_doc_req_draft ON position_document (request_id, is_draft, is_deleted);

-- ----------------------------------------------------------------------------
-- 7. Request Status Timeline (request_status_history & position_status_history)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_req_hist_req_date ON request_status_history (request_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_pos_hist_req_date ON position_status_history (request_id, changed_at DESC);

-- ----------------------------------------------------------------------------
-- 8. Document Edit Log (position_document_edit_log)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pos_doc_log_req_date ON position_document_edit_log (request_id, edited_at DESC);

-- ----------------------------------------------------------------------------
-- 9. Staff Directory (staff_member)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_staff_name ON staff_member (first_name, last_name);
CREATE INDEX IF NOT EXISTS idx_staff_role_active ON staff_member (staff_role, is_active);

-- ----------------------------------------------------------------------------
-- 10. Petitions & Status History (petitions & petition_statuses)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_petition_user_created ON petitions (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_petition_status_pet_created ON petition_statuses (petition_id, created_at ASC);

-- ----------------------------------------------------------------------------
-- 11. File Management (user_folder, user_file, admin_folder, admin_file)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_userfolder_owner_parent ON user_folder (owner_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_userfile_owner_folder_del ON user_file (owner_id, folder_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_userfile_owner_del ON user_file (owner_id, is_deleted);

CREATE INDEX IF NOT EXISTS idx_adminfolder_parent ON admin_folder (parent_id);
CREATE INDEX IF NOT EXISTS idx_adminfile_folder_del ON admin_file (folder_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adminfile_del ON admin_file (is_deleted);
