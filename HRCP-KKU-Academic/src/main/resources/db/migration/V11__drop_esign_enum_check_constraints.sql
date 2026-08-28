-- ============================================================
-- Migration: Drop the enum CHECK constraints on the e-sign tables
--
-- These constraints were never written here. Hibernate emitted them for every
-- @Enumerated(EnumType.STRING) column back when ddl-auto first created the
-- tables, and PostgreSQL named them <table>_<column>_check. The value list was
-- frozen at whatever the enum held that day: ddl-auto=update adds columns but
-- never rewrites an existing constraint, so every enum constant added later is
-- rejected by the database.
--
-- That is what broke ส่งเวียนลงนามต่อ — SignatureAuditEventType.FORWARDED,
-- EXTENSION_REQUESTED and DUE_EXTENDED were all added after signature_audit_event
-- was created, so writing the audit row aborted the whole forwarding transaction.
--
-- Membership of an enum belongs to the Java enum, which is the only place it can
-- be kept in step with the code. Same fix as V7 did for notifications_type_check.
--
-- Note for whoever adds the next enum value: `columnDefinition = "varchar(n)"`
-- on the entity does NOT prevent this. Hibernate honours it for the column type
-- and still emits the check alongside it — the annotations on Notification.type
-- and AcademicRequest.current_status do not do what they look like they do.
-- Once dropped the constraint stays dropped (ddl-auto=update does not re-add
-- constraints to existing columns), but a database created fresh after this will
-- get a new check listing whatever the enum holds that day. If an enum grows
-- again, add another migration like this one.
-- ============================================================

ALTER TABLE signature_audit_event
    DROP CONSTRAINT IF EXISTS signature_audit_event_event_type_check;

ALTER TABLE signature_request
    DROP CONSTRAINT IF EXISTS signature_request_status_check;

ALTER TABLE signature_request
    DROP CONSTRAINT IF EXISTS signature_request_module_check;

ALTER TABLE signature_step
    DROP CONSTRAINT IF EXISTS signature_step_status_check;

ALTER TABLE user_signature
    DROP CONSTRAINT IF EXISTS user_signature_kind_check;

ALTER TABLE document_workflow_config
    DROP CONSTRAINT IF EXISTS document_workflow_config_module_check;
