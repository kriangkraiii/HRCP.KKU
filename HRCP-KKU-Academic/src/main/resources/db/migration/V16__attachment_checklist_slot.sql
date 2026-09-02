-- V16: Add checklist_item slot column to academic_attachment
-- Each attachment can now be assigned to one of the 5 checklist items (1-5).
-- NULL = legacy/unassigned attachments (backward compatible).

ALTER TABLE academic_attachment
    ADD COLUMN IF NOT EXISTS checklist_item SMALLINT DEFAULT NULL;

-- Partial index for querying attachments by request + slot (excluding soft-deleted)
CREATE INDEX IF NOT EXISTS idx_acad_att_req_slot
    ON academic_attachment (request_id, checklist_item)
    WHERE is_deleted = false OR is_deleted IS NULL;
