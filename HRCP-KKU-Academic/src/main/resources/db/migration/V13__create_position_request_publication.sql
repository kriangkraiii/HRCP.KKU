-- ============================================================
-- Migration: ตารางผูก "คำร้องขอตำแหน่ง ↔ ผลงานวิจัยจาก Scopus"
--
-- Until now nothing recorded which publication a request puts forward. The
-- Scopus picker asked for a formatted citation, wrote the text into an ordinary
-- input and dropped the id in the browser (GAP-11) — so the rule everyone agreed
-- on, that work submitted once cannot be submitted again (GAP-12), had nothing
-- to check against.
--
-- ddl-auto is 'validate' on production, so a new table has to arrive here.
--
-- No foreign key to scopus_publication, deliberately: that table mirrors an
-- upstream feed and the sync may delete and recreate rows, which a foreign key
-- would turn into a failed sync. The index on publication_id carries the lookup
-- instead. The reference to position_request is a real one — deleting a request
-- should take its links with it.
-- ============================================================

CREATE TABLE IF NOT EXISTS position_request_publication (
    id              BIGSERIAL PRIMARY KEY,
    request_id      BIGINT NOT NULL REFERENCES position_request(id) ON DELETE CASCADE,
    publication_id  BIGINT NOT NULL,
    document_type   INTEGER NOT NULL,
    slot_index      INTEGER,
    created_at      TIMESTAMP,
    CONSTRAINT uq_pos_req_pub UNIQUE (request_id, publication_id)
);

-- Reading the other way round — "is this publication spent?" — is what the
-- picker asks on every open.
CREATE INDEX IF NOT EXISTS idx_pos_req_pub_publication
    ON position_request_publication (publication_id);
