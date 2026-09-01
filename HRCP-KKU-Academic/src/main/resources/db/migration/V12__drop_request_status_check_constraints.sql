-- ============================================================
-- Migration: Drop the enum CHECK constraints on the request tables
--
-- Same fault V7 and V11 dealt with, on the tables they did not cover. Hibernate
-- emitted a CHECK for every @Enumerated(EnumType.STRING) column when ddl-auto
-- first created these tables, freezing the value list at whatever the enum held
-- that day. ddl-auto=update adds columns but never rewrites an existing
-- constraint, so any enum constant added later is rejected by the database.
--
-- This is what stands between the code and the two statuses the flow document
-- requires and the system has never had:
--   • COLLEGE_ENDORSED   — ข้อ 9-10, กรรมการประจำวิทยาลัยฯ รับรองผลประเมินการสอน
--   • REVISION_SUBMITTED — ข้อ 13, ผู้ขอกำหนดตำแหน่งส่งเอกสารที่แก้แล้วกลับมา
--
-- The history tables matter as much as the request tables and are easy to miss:
-- request_status_history.new_status is written on every single transition, so a
-- check there rejects the new value just as surely — and it would do so from
-- inside the same transaction that was updating the request, rolling back a
-- status change that had appeared to work.
--
-- Membership of an enum belongs to the Java enum, which is the only place it can
-- be kept in step with the code.
--
-- Note for whoever adds the next enum value: `columnDefinition = "varchar(50)"`
-- on the entity does NOT prevent this. Hibernate honours it for the column type
-- and still emits the check alongside it.
-- ============================================================

-- ---- Phase 1: teaching evaluation ----
ALTER TABLE academic_request
    DROP CONSTRAINT IF EXISTS academic_request_current_status_check;

ALTER TABLE request_status_history
    DROP CONSTRAINT IF EXISTS request_status_history_old_status_check;

ALTER TABLE request_status_history
    DROP CONSTRAINT IF EXISTS request_status_history_new_status_check;

-- ---- Phase 2: position request ----
ALTER TABLE position_request
    DROP CONSTRAINT IF EXISTS position_request_current_status_check;

ALTER TABLE position_status_history
    DROP CONSTRAINT IF EXISTS position_status_history_old_status_check;

ALTER TABLE position_status_history
    DROP CONSTRAINT IF EXISTS position_status_history_new_status_check;

-- ---- Document edit logs: EditAction gains values as the UI grows ----
ALTER TABLE academic_document_edit_log
    DROP CONSTRAINT IF EXISTS academic_document_edit_log_action_check;

ALTER TABLE position_document_edit_log
    DROP CONSTRAINT IF EXISTS position_document_edit_log_action_check;

-- ---- Committee directory: CommitteeType gains values as new panels appear ----
ALTER TABLE academic_committee_member
    DROP CONSTRAINT IF EXISTS academic_committee_member_committee_type_check;
