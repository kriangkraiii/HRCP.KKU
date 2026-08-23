-- ============================================================
-- Migration: Signing envelopes, their ordered steps, and the audit trail
--
-- An "envelope" is one document sent out for signature. It freezes the form
-- data at the moment it is sent (frozen_json + frozen_hash) so that everyone
-- in the chain signs the same content, and so that a later edit can be detected
-- rather than silently inheriting signatures that were never given for it.
--
-- request_id is intentionally NOT a foreign key: it points into either
-- academic_request or position_request depending on `module`. The signing
-- machinery is identical for both, and two nullable FK columns would push that
-- distinction into every query for no benefit.
-- ============================================================

CREATE TABLE IF NOT EXISTS signature_request (
    id BIGSERIAL PRIMARY KEY,
    module VARCHAR(20) NOT NULL,
    request_id BIGINT NOT NULL,
    document_type INTEGER NOT NULL,
    document_label VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    initiated_by INTEGER,
    frozen_json TEXT NOT NULL,
    frozen_hash VARCHAR(64) NOT NULL,
    verification_code VARCHAR(32) NOT NULL,
    signed_docx_path VARCHAR(500),
    signed_pdf_path VARCHAR(500),
    due_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    cancel_reason VARCHAR(500),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_sig_req_initiator FOREIGN KEY (initiated_by)
        REFERENCES user_dtls(id) ON DELETE SET NULL,
    CONSTRAINT uq_sig_req_verification UNIQUE (verification_code)
);

-- "Is this document locked / already out for signature?" — asked on every
-- document form load and every save.
CREATE INDEX IF NOT EXISTS idx_sig_req_document
    ON signature_request (module, request_id, document_type);

-- Drives the reminder scheduler.
CREATE INDEX IF NOT EXISTS idx_sig_req_status_due
    ON signature_request (status, due_at);


CREATE TABLE IF NOT EXISTS signature_step (
    id BIGSERIAL PRIMARY KEY,
    signature_request_id BIGINT NOT NULL,
    step_order INTEGER NOT NULL,
    slot_key VARCHAR(50) NOT NULL,
    role_label VARCHAR(150),
    anchor_placeholder VARCHAR(100) NOT NULL,
    signer_user_id INTEGER,
    -- Name and position as they were when the request was sent. The account may
    -- be renamed or promoted later; the evidence must not change with it.
    signer_name_snapshot VARCHAR(255),
    signer_position_snapshot VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    notified_at TIMESTAMP WITHOUT TIME ZONE,
    reminded_at TIMESTAMP WITHOUT TIME ZONE,
    viewed_at TIMESTAMP WITHOUT TIME ZONE,
    signed_at TIMESTAMP WITHOUT TIME ZONE,
    declined_at TIMESTAMP WITHOUT TIME ZONE,
    decline_reason VARCHAR(500),
    user_signature_id BIGINT,
    -- The image file as used at signing time. Held separately from
    -- user_signature.image_path so that deleting a signature from one's library
    -- cannot rewrite a document that was already signed with it.
    image_path_snapshot VARCHAR(255),
    consent_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    consent_text_version VARCHAR(30),
    auth_method VARCHAR(30),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    doc_hash_signed VARCHAR(64),
    evidence_hmac VARCHAR(128),
    delegated_from_user_id INTEGER,
    delegate_reason VARCHAR(500),
    CONSTRAINT fk_sig_step_request FOREIGN KEY (signature_request_id)
        REFERENCES signature_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_sig_step_signer FOREIGN KEY (signer_user_id)
        REFERENCES user_dtls(id) ON DELETE SET NULL,
    CONSTRAINT fk_sig_step_signature FOREIGN KEY (user_signature_id)
        REFERENCES user_signature(id) ON DELETE SET NULL,
    CONSTRAINT fk_sig_step_delegator FOREIGN KEY (delegated_from_user_id)
        REFERENCES user_dtls(id) ON DELETE SET NULL
);

-- The "รอลงนาม" inbox: everything waiting on one person.
CREATE INDEX IF NOT EXISTS idx_sig_step_signer
    ON signature_step (signer_user_id, status);

-- Walking an envelope's chain in order.
CREATE INDEX IF NOT EXISTS idx_sig_step_order
    ON signature_step (signature_request_id, step_order);


CREATE TABLE IF NOT EXISTS signature_audit_event (
    id BIGSERIAL PRIMARY KEY,
    signature_request_id BIGINT NOT NULL,
    step_id BIGINT,
    event_type VARCHAR(30) NOT NULL,
    actor_user_id INTEGER,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    detail VARCHAR(1000),
    created_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_sig_audit_request FOREIGN KEY (signature_request_id)
        REFERENCES signature_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_sig_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES user_dtls(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_sig_audit_request
    ON signature_audit_event (signature_request_id, created_at);
