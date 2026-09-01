-- 1. Ensure base table exists (for fresh test/production databases)
CREATE TABLE IF NOT EXISTS scopus_publication (
    id                      BIGSERIAL PRIMARY KEY,
    fs_user_id              BIGINT,
    eid                     VARCHAR(255),
    title                   VARCHAR(2000),
    publication_name        VARCHAR(512),
    publication_year        INTEGER,
    cover_date              TIMESTAMPTZ,
    doi                     VARCHAR(512),
    cited_by                INTEGER,
    author_names            TEXT,
    abstract_text           TEXT,
    issn                    VARCHAR(16),
    eissn                   VARCHAR(16),
    volume                  VARCHAR(64),
    issue                   VARCHAR(64),
    page_range              VARCHAR(64),
    article_number          VARCHAR(64),
    scopus_url              VARCHAR(1024),
    raw_json                TEXT,
    synced_at               TIMESTAMP DEFAULT NOW() NOT NULL
);

-- 2. Source discrimination + dedup support on the table
ALTER TABLE scopus_publication
    ADD COLUMN IF NOT EXISTS data_source       VARCHAR(32) DEFAULT 'SCOPUS' NOT NULL,
    ADD COLUMN IF NOT EXISTS external_id       VARCHAR(512),
    ADD COLUMN IF NOT EXISTS openalex_id       VARCHAR(128),
    ADD COLUMN IF NOT EXISTS crossref_score    DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS sjr_quartile      VARCHAR(8),
    ADD COLUMN IF NOT EXISTS tci_tier          VARCHAR(8),
    ADD COLUMN IF NOT EXISTS dedup_hash        VARCHAR(128),
    ADD COLUMN IF NOT EXISTS raw_source_metadata TEXT,
    ADD COLUMN IF NOT EXISTS language          VARCHAR(8);

CREATE INDEX IF NOT EXISTS idx_scopus_pub_dedup_hash   ON scopus_publication (dedup_hash);
CREATE INDEX IF NOT EXISTS idx_scopus_pub_data_source  ON scopus_publication (data_source);
CREATE INDEX IF NOT EXISTS idx_scopus_pub_external_id  ON scopus_publication (external_id);
CREATE INDEX IF NOT EXISTS idx_scopus_pub_openalex_id  ON scopus_publication (openalex_id);

-- 2. pg_trgm for Level 2.5 fuzzy title matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_scopus_pub_title_trgm
    ON scopus_publication USING gin (LOWER(title) gin_trgm_ops);

-- 3. External Author Mapping (DBLP PID, ORCID, Researcher ID)
CREATE TABLE IF NOT EXISTS external_author_mapping (
    id              BIGSERIAL PRIMARY KEY,
    fs_user_id      BIGINT       NOT NULL,
    provider        VARCHAR(32)  NOT NULL,
    external_pid    VARCHAR(512) NOT NULL,
    display_name    VARCHAR(512),
    verified        BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (fs_user_id, provider, external_pid)
);
CREATE INDEX IF NOT EXISTS idx_ext_author_provider
    ON external_author_mapping (provider, external_pid);

-- 4. Journal Tier Lookup (SJR + TCI)
CREATE TABLE IF NOT EXISTS journal_tier (
    id          BIGSERIAL PRIMARY KEY,
    issn        VARCHAR(16),
    eissn       VARCHAR(16),
    title       VARCHAR(1024) NOT NULL,
    provider    VARCHAR(16)   NOT NULL,
    tier        VARCHAR(8)    NOT NULL,
    tier_year   INTEGER       NOT NULL,
    sjr_score   DOUBLE PRECISION,
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (issn, provider, tier_year)
);
CREATE INDEX IF NOT EXISTS idx_journal_tier_issn  ON journal_tier (issn);
CREATE INDEX IF NOT EXISTS idx_journal_tier_eissn ON journal_tier (eissn);
