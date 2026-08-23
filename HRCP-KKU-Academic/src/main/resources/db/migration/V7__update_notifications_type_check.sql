-- ============================================================
-- Migration: Update notifications type check constraint
--
-- Drops the old PostgreSQL check constraint on the notifications table
-- so that new enum values (SIGNATURE_REQUESTED, SIGNATURE_REMINDER,
-- SIGNATURE_COMPLETED, SIGNATURE_DECLINED) are accepted without failure.
-- ============================================================

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
