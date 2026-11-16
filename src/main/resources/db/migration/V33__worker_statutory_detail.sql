-- V33__worker_statutory_detail.sql
-- A worker's UAN (EPFO) and ESIC number - same shape as kyc.worker_kyc_profile's Aadhaar/PAN:
-- encrypted at rest, plus a separate deterministic hash column per number for a duplicate check
-- across workers without ever decrypting to search. Legacy has these too (on separate
-- worker_statutory_details/worker_personal_details child tables, never on `workers` itself), never
-- hard-validated or verified there either - only soft-flagged for an "incomplete profile" admin
-- filter, which this rebuild does not replicate (no equivalent filter exists here yet).
CREATE TABLE worker_statutory_detail (
    id              UUID PRIMARY KEY,
    worker_id       UUID         NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    uan_number      VARCHAR(255),
    uan_number_hash VARCHAR(64),
    esic_number     VARCHAR(255),
    esic_number_hash VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100) DEFAULT 'system',
    updated_by      VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_statutory_detail_worker_id UNIQUE (worker_id)
);
CREATE UNIQUE INDEX uq_worker_statutory_detail_uan_hash
    ON worker_statutory_detail (uan_number_hash) WHERE uan_number_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_worker_statutory_detail_esic_hash
    ON worker_statutory_detail (esic_number_hash) WHERE esic_number_hash IS NOT NULL;
