-- V15: Create table for synchronized KKU HR regulations and announcements
CREATE TABLE IF NOT EXISTS kku_regulation_docs (
    id              BIGSERIAL PRIMARY KEY,
    category        VARCHAR(120) NOT NULL,
    category_icon   VARCHAR(60),
    category_color  VARCHAR(60),
    title           VARCHAR(500) NOT NULL,
    file_url        VARCHAR(1000) NOT NULL,
    file_key        VARCHAR(500) NOT NULL UNIQUE,
    display_order   INTEGER DEFAULT 0,
    is_new          BOOLEAN DEFAULT FALSE,
    published_year  VARCHAR(20),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kku_doc_category ON kku_regulation_docs (category);
CREATE INDEX IF NOT EXISTS idx_kku_doc_file_key ON kku_regulation_docs (file_key);
