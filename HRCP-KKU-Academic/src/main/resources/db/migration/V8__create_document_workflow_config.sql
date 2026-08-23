-- ============================================================
-- Migration: V8__create_document_workflow_config.sql
-- Description: Creates document_workflow_config table for custom
--              signing slots, ordering, and default assigned signers.
-- ============================================================

CREATE TABLE IF NOT EXISTS document_workflow_config (
    id BIGSERIAL PRIMARY KEY,
    module VARCHAR(20) NOT NULL,
    document_type INT NOT NULL,
    slot_key VARCHAR(50) NOT NULL,
    role_label VARCHAR(150) NOT NULL,
    anchor_placeholder VARCHAR(100) NOT NULL,
    default_staff_role VARCHAR(50),
    step_order INT NOT NULL DEFAULT 1,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_signer_user_id INT REFERENCES user_dtls(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_doc_workflow_slot UNIQUE (module, document_type, slot_key)
);

CREATE INDEX IF NOT EXISTS idx_doc_workflow_lookup 
    ON document_workflow_config (module, document_type);
