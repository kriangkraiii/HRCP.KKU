-- ============================================================
-- Migration: Create academic_document_edit_log table & indexes
-- Purpose: Track document edit history for Teaching Evaluation (Academic Requests)
-- ============================================================

CREATE TABLE IF NOT EXISTS academic_document_edit_log (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    document_type INTEGER NOT NULL,
    document_label VARCHAR(255),
    action VARCHAR(30) NOT NULL,
    edited_by INTEGER,
    edited_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_acad_doc_log_req FOREIGN KEY (request_id) REFERENCES academic_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_acad_doc_log_user FOREIGN KEY (edited_by) REFERENCES user_dtls(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_acad_doc_log_req_date 
ON academic_document_edit_log (request_id, edited_at DESC);
