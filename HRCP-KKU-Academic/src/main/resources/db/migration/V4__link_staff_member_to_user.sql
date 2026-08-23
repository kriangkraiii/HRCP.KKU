-- ============================================================
-- Migration: Link staff_member to a real login account (user_dtls)
-- Purpose: Digital signature workflow needs to know WHICH ACCOUNT is the dean /
--          department head, so the system can notify that person and let them
--          sign in-app. Until now staff_member only held a name to print into a
--          document, with no way back to a user who could act on it.
--
-- Nullable on purpose: external committee members and anyone not yet onboarded
-- keep a NULL user_id. They still appear in documents as printed names; they
-- simply cannot be picked as an in-system signer.
-- ============================================================

ALTER TABLE staff_member ADD COLUMN IF NOT EXISTS user_id INTEGER;

ALTER TABLE staff_member DROP CONSTRAINT IF EXISTS fk_staff_user;
ALTER TABLE staff_member ADD CONSTRAINT fk_staff_user
    FOREIGN KEY (user_id) REFERENCES user_dtls(id) ON DELETE SET NULL;

-- One account may back at most one staff row. No WHERE clause needed: a unique
-- index in PostgreSQL already treats NULLs as distinct, so the many rows with no
-- linked account never collide with each other.
CREATE UNIQUE INDEX IF NOT EXISTS idx_staff_user_id
    ON staff_member (user_id);
