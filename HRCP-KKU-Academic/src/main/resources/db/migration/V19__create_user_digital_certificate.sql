-- ============================================================
-- Migration: Create user_digital_certificate table
-- Purpose: Holds PKCS#12 (.p12) digital certificate metadata for signers.
--          Enables cryptographic PAdES digital signing on PDFs without ugly table stamps.
-- ============================================================

CREATE TABLE IF NOT EXISTS user_digital_certificate (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    certificate_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    subject_dn VARCHAR(500) NOT NULL,
    issuer_dn VARCHAR(500) NOT NULL,
    serial_number VARCHAR(100),
    valid_from TIMESTAMP WITHOUT TIME ZONE,
    valid_to TIMESTAMP WITHOUT TIME ZONE,
    encrypted_pin VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_digital_cert_user FOREIGN KEY (user_id)
        REFERENCES user_dtls(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_digital_cert_user
    ON user_digital_certificate (user_id, is_active);

-- Link optional certificate audit to signature_step
ALTER TABLE signature_step
    ADD COLUMN IF NOT EXISTS digital_cert_subject VARCHAR(500);
