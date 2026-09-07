-- ============================================================
-- Migration: Create search_document — the denormalised search index
-- Purpose: One row per indexable thing, so global search is a single ranked,
--          permission-scoped query instead of a fan-out of unbounded LIKEs
--          across a dozen repositories.
-- ============================================================
--
-- WHY TRIGRAMS ARE THE PRIMARY MATCHER
--
-- Thai is written without spaces, so no tokeniser PostgreSQL ships with can
-- split a Thai phrase into words: the default parser turns คำร้องขอประเมินผลการสอน
-- into a single token. Trigrams do not care about word boundaries, which is
-- exactly what this corpus needs.
--
-- Measured on postgres:16-alpine, server_encoding UTF8, before writing this:
--
--   SELECT array_length(show_trgm('ประเมิน'), 1);                       -> 8
--   SELECT array_length(show_trgm('คำร้องขอกำหนดตำแหน่งทางวิชาการ'), 1);      -> 31
--
-- 31 trigrams from 30 characters is the n+1 you would get from Latin text, so
-- pg_trgm treats Thai vowel and tone marks as ordinary letters rather than
-- splitting on them. The index is real, not a decoration:
--
--   EXPLAIN SELECT count(*) FROM t WHERE body LIKE '%ศาสตราจารย์%';
--     -> Bitmap Index Scan on t_trgm
--
-- (A term matching nearly every row still gets a sequential scan, correctly.)
--
-- WHY word_similarity AND NOT similarity, for the typo-tolerant tier:
--
--   word_similarity('ประเมิน', 'คำร้องขอประเมินผลการสอน')  -> 0.625
--   word_similarity('ประเมิณ', 'คำร้องขอประเมินผลการสอน')  -> 0.500   (one wrong letter)
--   similarity     ('ประเมิน', 'คำร้องขอประเมินผลการสอน')  -> 0.185
--
-- similarity() normalises over the union of both trigram sets, so a short query
-- against a long body scores near zero no matter how well it matches. Only
-- word_similarity, which scores against the best matching extent of the text,
-- survives a 5 KB body.
--
-- The tsvector is kept for English. to_tsvector('simple', ...) leaves a Thai
-- phrase as one token but still splits ASCII runs out of mixed text:
--   'คำร้อง Machine Learning ขอประเมิน' -> 'learning':3 'machine':2 'ขอประเมิน':4 'คำร้อง':1
--
-- NO FOREIGN KEYS, DELIBERATELY
--
-- This table points at rows in a dozen others. Foreign keys would make the
-- bare-schema fixture in MigrationOnPostgresTest need all twelve, and — worse —
-- would delete index rows behind the reconciler's back, hiding drift instead of
-- letting SearchReconciler find and report it. Orphans are swept nightly.
--
-- H2 NOTE: this file never runs on H2 (spring.flyway.enabled=false in the test
-- profile; the schema there comes from the entity). search_text and tsv are
-- deliberately left unmapped on SearchDocument, so ddl-auto=validate ignores
-- them here and H2 simply does not have them. Do not map tsv as a read-only
-- column: Hibernate would validate its type, tsvector has no JDBC type, and
-- production would refuse to start.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS search_document (
    id                  BIGSERIAL PRIMARY KEY,

    -- Identity of the indexed thing. doc_part lets one source row produce
    -- several documents (a request and each of its forms) without colliding.
    entity_type         VARCHAR(40)   NOT NULL,
    entity_id           BIGINT        NOT NULL,
    doc_part            VARCHAR(48)   NOT NULL DEFAULT 'MAIN',

    -- Searchable text, most important first. The ranking formula weights these
    -- differently, which is why they stay in separate columns.
    title               VARCHAR(1000) NOT NULL,
    subtitle            VARCHAR(1000),
    keywords            VARCHAR(2000),
    body                TEXT,

    -- How the hit is presented. Kept on the row so rendering a result page
    -- needs no joins back to a dozen source tables.
    category            VARCHAR(120)  NOT NULL,
    url                 VARCHAR(2048) NOT NULL,
    admin_url           VARCHAR(2048),
    icon                VARCHAR(80),
    badge               VARCHAR(120),
    badge_class         VARCHAR(120),
    is_external         BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Who may see it. One predicate over these two columns is the whole
    -- authorisation check, so it cannot drift away from the matching.
    visibility          VARCHAR(20)   NOT NULL,
    owner_user_id       INTEGER,

    -- Facets and ranking inputs.
    status              VARCHAR(50),
    weight              REAL          NOT NULL DEFAULT 1.0,
    occurred_at         TIMESTAMP WITHOUT TIME ZONE,

    -- Index bookkeeping. content_hash lets the writer skip an UPDATE when
    -- nothing changed, so the nightly reconcile does not rewrite every row and
    -- regenerate every tsvector.
    source_updated_at   TIMESTAMP WITHOUT TIME ZONE,
    indexed_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_hash        VARCHAR(64),
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Queue for pulling text out of uploaded DOCX/PDF files. NONE for rows
    -- with no file behind them, which is most of them.
    extraction_state    VARCHAR(16)   NOT NULL DEFAULT 'NONE',
    file_path           VARCHAR(2048),
    file_fingerprint    VARCHAR(80),
    extraction_error    VARCHAR(500),
    extracted_at        TIMESTAMP WITHOUT TIME ZONE,

    -- Lower-cased once, at write time. Queries then use LIKE against a pattern
    -- that is already lower-cased, never ILIKE — ILIKE cannot use this index.
    search_text TEXT GENERATED ALWAYS AS (
        lower(coalesce(title, '')    || ' ' ||
              coalesce(keywords, '') || ' ' ||
              coalesce(subtitle, '') || ' ' ||
              coalesce(body, ''))
    ) STORED,

    -- Rebuilds the concatenation rather than reading search_text: PostgreSQL
    -- forbids one generated column from referencing another. The two-argument
    -- to_tsvector is IMMUTABLE only because the config is a literal; the
    -- one-argument form is STABLE and would be rejected here.
    tsv tsvector GENERATED ALWAYS AS (
           setweight(to_tsvector('simple', coalesce(title, '')),    'A')
        || setweight(to_tsvector('simple', coalesce(keywords, '')), 'B')
        || setweight(to_tsvector('simple', coalesce(subtitle, '')), 'C')
        || setweight(to_tsvector('simple', coalesce(body, '')),     'D')
    ) STORED
);

-- The indexer upserts on this key.
CREATE UNIQUE INDEX IF NOT EXISTS ux_search_doc_entity
    ON search_document (entity_type, entity_id, doc_part);

-- The two matchers.
CREATE INDEX IF NOT EXISTS idx_search_doc_trgm
    ON search_document USING gin (search_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_search_doc_tsv
    ON search_document USING gin (tsv);

-- Scoping, facets and ordering. Partial, because a deleted row is never a hit.
CREATE INDEX IF NOT EXISTS idx_search_doc_scope
    ON search_document (visibility, owner_user_id) WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_search_doc_facet
    ON search_document (entity_type, status) WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_search_doc_recent
    ON search_document (occurred_at DESC) WHERE is_deleted = FALSE;

-- Housekeeping paths: the nightly reconcile and the file-extraction queue.
CREATE INDEX IF NOT EXISTS idx_search_doc_reconcile
    ON search_document (entity_type, source_updated_at);

CREATE INDEX IF NOT EXISTS idx_search_doc_extract
    ON search_document (extraction_state, id) WHERE extraction_state = 'PENDING';
