-- V23__kyc.sql
-- Worker KYC: declared identity numbers (Aadhaar/PAN) plus a document upload/verification
-- workflow, owned by its own module so the same workflow can be reused (not this table itself -
-- see WorkerKycProfile's Javadoc) if/when company KYC is designed later.
--
-- worker_id is a real FK to worker.id (ON DELETE CASCADE), not a generic owner_type/owner_id pair -
-- a polymorphic reference can never carry a real foreign key, so the database could never guarantee
-- a KYC record belongs to an actual worker. Cross-module FK is already an established pattern in
-- this codebase (e.g. admin_user_address.state_id -> state.id); what's avoided here is a JPA
-- relationship, not database-level referential integrity.
--
-- aadhaar_number_hash gets a real partial-unique index (below), fixing a legacy gap: the old schema
-- had the same hash-for-lookup column but never actually enforced uniqueness on it.

CREATE TABLE worker_kyc_profile (
    id                  UUID PRIMARY KEY,
    worker_id           UUID         NOT NULL REFERENCES worker (id) ON DELETE CASCADE,
    aadhaar_number      VARCHAR(255),
    aadhaar_number_hash VARCHAR(64),
    pan_number          VARCHAR(255),
    overall_status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    verified_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(100) DEFAULT 'system',
    updated_by          VARCHAR(100),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_kyc_profile_worker_id UNIQUE (worker_id)
);
-- Real duplicate-Aadhaar prevention across all workers (partial: nullable until a number is submitted).
CREATE UNIQUE INDEX uq_worker_kyc_profile_aadhaar_hash
    ON worker_kyc_profile (aadhaar_number_hash) WHERE aadhaar_number_hash IS NOT NULL;
CREATE INDEX idx_worker_kyc_profile_overall_status ON worker_kyc_profile (overall_status);

-- One row per uploaded document. At most one "live" (non-rejected) row per (profile, document_type);
-- a rejected row stays as history, linked forward via superseded_by_document_id once a resubmission
-- lands - legacy had no such link, a rejected upload just vanished into a disconnected new row.
CREATE TABLE worker_kyc_document (
    id                       UUID PRIMARY KEY,
    worker_kyc_profile_id    UUID         NOT NULL REFERENCES worker_kyc_profile (id) ON DELETE CASCADE,
    document_type            VARCHAR(30)  NOT NULL,
    document_id              UUID         NOT NULL REFERENCES documents (id) ON DELETE RESTRICT,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by_admin_id     UUID REFERENCES admin_user (id) ON DELETE SET NULL,
    reviewed_by_email        VARCHAR(320),
    reviewed_at              TIMESTAMPTZ,
    rejection_reason         VARCHAR(500),
    superseded_by_document_id UUID REFERENCES worker_kyc_document (id) ON DELETE SET NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(100) DEFAULT 'system',
    updated_by               VARCHAR(100),
    version                  BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_worker_kyc_document_active_type
    ON worker_kyc_document (worker_kyc_profile_id, document_type) WHERE status <> 'REJECTED';
CREATE INDEX idx_worker_kyc_document_profile_id ON worker_kyc_document (worker_kyc_profile_id);

-- Permission catalog: a new `kyc` module, since no such module existed in the legacy catalog (KYC
-- document approve/reject rode on workers.view_documents there, with no distinct permission of its
-- own - a gap, not a pattern worth preserving). Four distinct actions, not reused from workers.*:
-- this intentionally allows a narrower "KYC reviewer" role later that can view/submit/review KYC
-- without needing general workers.view/edit rights - a real, useful separation of duties.
-- kyc.view_sensitive mirrors the existing admin_staff.view_kyc split: the base view only ever sees a
-- masked Aadhaar/PAN.
INSERT INTO admin_permission (id, code, module, action, label) VALUES
    (gen_random_uuid(), 'kyc.view', 'kyc', 'view', 'View worker KYC submissions'),
    (gen_random_uuid(), 'kyc.submit', 'kyc', 'submit', 'Submit worker KYC numbers and documents'),
    (gen_random_uuid(), 'kyc.review', 'kyc', 'review', 'Approve or reject worker KYC documents'),
    (gen_random_uuid(), 'kyc.view_sensitive', 'kyc', 'view_sensitive', 'View unmasked Aadhaar/PAN numbers');
