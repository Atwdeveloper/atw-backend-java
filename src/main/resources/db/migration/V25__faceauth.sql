-- V25__faceauth.sql
-- Worker face-template enrollment - admin-triggered only, for now (see project memory for the fuller
-- legacy picture: company-side attendance verification and worker self-enrollment are deliberately
-- deferred until the company/vacancy/attendance modules that would consume them actually exist). A
-- new dedicated module, not folded into kyc: this is an independent fact about a worker (has a face
-- template on file or not), not a replacement for or addition to kyc's single aggregate Aadhaar/PAN
-- verification status.
--
-- embedding is a plain Postgres real[] (128 floats), not the pgvector extension's native vector type:
-- this phase only stores the template, it never searches across embeddings, and pgvector has no
-- straightforward Windows installer for this project's local dev Postgres. Reassess if/when a future
-- verify/identify feature needs indexed nearest-neighbor search at a scale where an application-level
-- comparison loop over candidates stops being fast enough.

CREATE TABLE worker_face_enrollment (
    id                        UUID PRIMARY KEY,
    worker_id                 UUID             NOT NULL UNIQUE REFERENCES worker (id) ON DELETE CASCADE,
    embedding                 REAL[]           NOT NULL,
    quality_snapshot          JSONB            NOT NULL,
    liveness_confidence       DOUBLE PRECISION NOT NULL,
    enroll_photo_document_id  UUID REFERENCES documents (id) ON DELETE SET NULL,
    enrolled_at               TIMESTAMPTZ      NOT NULL,
    consent_given_at          TIMESTAMPTZ      NOT NULL,
    consent_ip                VARCHAR(45),
    created_at                TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ      NOT NULL DEFAULT now(),
    created_by                VARCHAR(100) DEFAULT 'system',
    updated_by                VARCHAR(100),
    version                   BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT chk_worker_face_enrollment_embedding_dimensions CHECK (array_length(embedding, 1) = 128)
);

-- Permission catalog: a new `faceauth` module, matching legacy's own treatment of face_enroll as a
-- distinct permission domain from workers.* - a narrower "face enrollment operator" role can be
-- granted later without general worker-management rights.
INSERT INTO admin_permission (id, code, module, action, label) VALUES
    (gen_random_uuid(), 'faceauth.view', 'faceauth', 'view', 'View worker face-enrollment status'),
    (gen_random_uuid(), 'faceauth.enroll', 'faceauth', 'enroll', 'Enroll, re-enroll, or remove a worker''s face template');
