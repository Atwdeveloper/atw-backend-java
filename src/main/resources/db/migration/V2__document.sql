-- V2__document.sql
-- File metadata for the document module. Bytes live in blob storage (keyed by storage_key);
-- this table holds everything needed to find, serve, and audit a stored file.

CREATE TABLE documents (
    id                UUID         PRIMARY KEY,
    storage_key       VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(150) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256   VARCHAR(64),
    category          VARCHAR(50)  NOT NULL,
    owner_id          UUID,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL,
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key)
);

-- Lookups are typically "all documents for this owner in this category".
CREATE INDEX idx_documents_category_owner ON documents (category, owner_id);
