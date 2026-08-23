-- ============================================================
-- Migration: Create user_signature table
-- Purpose: A reusable signature library per person — drawn, uploaded, or typed.
--          Signing a document then means picking a saved signature rather than
--          producing a new one every time.
--
-- image_path holds a generated filename only, never a client-supplied one, and
-- the file lives outside the statically-served uploads path: a signature image
-- is personal data and is handed out only by an ownership-checked controller.
-- ============================================================

CREATE TABLE IF NOT EXISTS user_signature (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    name VARCHAR(100),
    kind VARCHAR(20) NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    typed_text VARCHAR(255),
    typed_font VARCHAR(100),
    width_px INTEGER,
    height_px INTEGER,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_user_signature_user FOREIGN KEY (user_id)
        REFERENCES user_dtls(id) ON DELETE CASCADE
);

-- The signature picker always asks "my signatures, not deleted".
CREATE INDEX IF NOT EXISTS idx_user_signature_owner
    ON user_signature (user_id, is_deleted);

-- At most one default per person. Partial so the many non-default rows are
-- unconstrained, and so soft-deleted rows never block a new default.
--
-- PostgreSQL-only syntax. H2 supports neither partial nor expression indexes, so
-- FlywayMigrationRunTest stops at V4 — see the note there. Kept in this form
-- because production is PostgreSQL and this is the correct constraint; bending
-- it to suit a test harness would weaken the real database for no gain.
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_signature_one_default
    ON user_signature (user_id) WHERE is_default = TRUE AND is_deleted = FALSE;
