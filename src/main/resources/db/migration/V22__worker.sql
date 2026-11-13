-- V22__worker.sql
-- Worker management (admin side): core profile, address, bank detail, designation/skill
-- assignment, admin notes, and status-change history. KYC (identity numbers + document
-- verification) is a separate module - see V23__kyc.sql - reusable later for company KYC and FK'd
-- back to worker.id here rather than modeled generically, so the database can actually enforce that
-- a KYC record belongs to a real worker.
--
-- Deliberately NOT carried over from the legacy schema: a hard-delete path (workers.delete is kept
-- as a permission for a future narrow, guarded erasure flow - not built in this slice), DB-less
-- uniqueness on mobile number (real UNIQUE constraint here), designation_id/industry_id with no FK
-- (real FK here, RESTRICT, consistent with how masterdata's own tables already reference each
-- other), and a bare int status column (a named WorkerStatus enum + worker_status_history instead).
--
-- worker_code is a generated, display-only business identifier (see worker_code_seq below) - it is
-- never used as a join key anywhere in this schema; every relationship uses the UUID primary key.

CREATE TABLE worker (
    id                        UUID PRIMARY KEY,
    worker_code               VARCHAR(20)  NOT NULL,
    first_name                VARCHAR(100) NOT NULL,
    middle_name               VARCHAR(100),
    last_name                 VARCHAR(100) NOT NULL,
    gender                    VARCHAR(20)  NOT NULL,
    date_of_birth             DATE,
    mobile_number             VARCHAR(20)  NOT NULL,
    personal_email            VARCHAR(191),
    department_id             UUID         NOT NULL REFERENCES department (id) ON DELETE RESTRICT,
    industry_id               UUID REFERENCES industry (id) ON DELETE RESTRICT,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
    status_reason             VARCHAR(500),
    profile_photo_document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                VARCHAR(100) DEFAULT 'system',
    updated_by                VARCHAR(100),
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_code UNIQUE (worker_code),
    CONSTRAINT uq_worker_mobile_number UNIQUE (mobile_number)
);
CREATE INDEX idx_worker_status ON worker (status);
CREATE INDEX idx_worker_department_id ON worker (department_id);
CREATE INDEX idx_worker_industry_id ON worker (industry_id);

-- Backs Worker.workerCode generation (WRK + 7-digit zero-padded sequence value, formatted in
-- WorkerService, not here) - a plain sequence, not an identity column, so the application fully
-- controls formatting and the value can be read before the row is inserted.
CREATE SEQUENCE worker_code_seq START WITH 1 INCREMENT BY 1;

-- A worker has 0-2 addresses (permanent / current) - see shared.kernel.Address for the embedded
-- shape and why line1/line2 are encrypted while state/city/pincode stay plain.
CREATE TABLE worker_address (
    id          UUID PRIMARY KEY,
    worker_id   UUID        NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    address_type VARCHAR(20) NOT NULL,
    line1       VARCHAR(255),
    line2       VARCHAR(255),
    state_id    UUID REFERENCES state (id) ON DELETE RESTRICT,
    city_id     UUID REFERENCES city (id) ON DELETE RESTRICT,
    pincode     VARCHAR(10),
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    formatted_address VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100) DEFAULT 'system',
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_address_worker_type UNIQUE (worker_id, address_type)
);

-- One bank account per worker for now - see WorkerBankDetail's Javadoc for why this is a
-- deliberate simplification, not a dropped legacy feature.
CREATE TABLE worker_bank_detail (
    id                   UUID PRIMARY KEY,
    worker_id            UUID         NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    account_holder_name  VARCHAR(150),
    bank_name            VARCHAR(150),
    account_number       VARCHAR(255),
    ifsc_code            VARCHAR(15),
    branch_name          VARCHAR(150),
    is_verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(100) DEFAULT 'system',
    updated_by           VARCHAR(100),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_bank_detail_worker_id UNIQUE (worker_id)
);

-- A worker may hold up to 3 designations (app-enforced cap, see WorkerService), exactly one marked
-- primary - DB-enforced via the partial unique index below, fixing legacy's app-only enforcement of
-- the same rule. designation_id is a plain UUID into masterdata, not a JPA relationship - see
-- WorkerDesignation's Javadoc.
CREATE TABLE worker_designation (
    id             UUID PRIMARY KEY,
    worker_id      UUID         NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    designation_id UUID         NOT NULL REFERENCES designation (id) ON DELETE RESTRICT,
    is_primary     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(100) DEFAULT 'system',
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_designation UNIQUE (worker_id, designation_id)
);
CREATE UNIQUE INDEX uq_worker_designation_primary ON worker_designation (worker_id) WHERE is_primary = TRUE;
CREATE INDEX idx_worker_designation_designation_id ON worker_designation (designation_id);

-- A skill a worker has declared, validated (in WorkerService) to belong to one of the worker's
-- chosen designations above. skill_id is a plain UUID into masterdata, same reasoning.
CREATE TABLE worker_skill (
    id         UUID PRIMARY KEY,
    worker_id  UUID         NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    skill_id   UUID         NOT NULL REFERENCES skill (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_skill UNIQUE (worker_id, skill_id)
);
CREATE INDEX idx_worker_skill_skill_id ON worker_skill (skill_id);

-- Append-only admin remarks - never updated after creation, so created_by/created_at (inherited,
-- not repeated here) are "who wrote it and when".
CREATE TABLE worker_note (
    id         UUID PRIMARY KEY,
    worker_id  UUID         NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    note       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100),
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_worker_note_worker_id_created_at ON worker_note (worker_id, created_at DESC);

-- Append-only status-transition audit - from_status is null only for the very first (REGISTERED) row.
CREATE TABLE worker_status_history (
    id          UUID PRIMARY KEY,
    worker_id   UUID         NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    reason      VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100) DEFAULT 'system',
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_worker_status_history_worker_id_created_at ON worker_status_history (worker_id, created_at DESC);

-- No permission INSERTs in this migration: workers.view/create/edit/delete/toggle_status/
-- view_documents/view_comments/view_wallet/manage_wallet_hold/withdraw_wallet/export already exist
-- (pre-seeded in V4__identity.sql from the legacy catalog, well before this module existed) and are
-- reused as-is - inserting them again would violate the unique constraint on admin_permission.code.
-- workers.view_wallet/manage_wallet_hold/withdraw_wallet stay dormant until the wallet module
-- exists, same as department.* codes stayed dormant between V19 and this migration. A
-- workers.import permission will be added in the migration that actually builds bulk import
-- (a later slice) - not here, ahead of the feature it would gate.
