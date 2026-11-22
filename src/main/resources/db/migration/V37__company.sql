-- V37__company.sql
-- New `company` module: the company record itself, its addresses, its approval-status history, and
-- a fully parallel company-user login/auth account chain (own user table, invite/reset tokens,
-- rotating refresh tokens, mandatory login OTP) - mirroring the identity module's admin auth tables
-- field-for-field, kept as its own independent chain per this codebase's locked "group by business
-- domain, never by user type" rule (identity owning every actor's accounts would violate it).
--
-- Deliberately out of scope this migration (confirmed with the developer): company_document /
-- document-review permissions (the document module is not integrated here at all), company
-- staff-user creation (only the OWNER row is created this round), custom per-company permission
-- profiles, multi-company-per-user. Those are future work, not a gap in this migration.
--
-- Dependency order below matches creation order: company -> company_address /
-- company_status_history -> company_user -> company_auth_token / company_refresh_token /
-- company_otp -> permission seed -> email template seed.

-- ---------------------------------------------------------------------------
-- 1. company
-- ---------------------------------------------------------------------------
-- industry_id is a plain UUID into masterdata.industry (validated via IndustryLookupService at the
-- service layer, exactly like worker.industry_id) but still gets a real DB-level FK, same precedent
-- as worker.industry_id in V22__worker.sql - "never a JPA relationship" is about the entity mapping,
-- not about giving up real referential integrity. NOT NULL (unlike worker's optional industry_id):
-- a company's industry is how it gets matched to vacancies/workers later and must always be known.
--
-- gst_number/pan_number are encrypted at rest (CompanyEncryptedStringConverter, its own
-- company-PII key - see PiiEncryptionConfig) so the plaintext column is sized generously for
-- base64(iv+ciphertext+tag) overhead, same convention as worker_kyc_profile.aadhaar_number/
-- pan_number. gst_number_hash/pan_number_hash are a separate deterministic HMAC (never decrypted to
-- search) used purely for the duplicate-number partial unique indexes below - same mechanism as
-- worker_kyc_profile.aadhaar_number_hash/pan_number_hash.
CREATE TABLE company (
    id                   UUID         PRIMARY KEY,
    company_code         VARCHAR(20)  NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    industry_id          UUID         NOT NULL REFERENCES industry (id) ON DELETE RESTRICT,
    gst_number           VARCHAR(255),
    gst_number_hash      VARCHAR(64),
    gst_verified_at      TIMESTAMPTZ,
    gst_verification_ref VARCHAR(255),
    pan_number           VARCHAR(255),
    pan_number_hash      VARCHAR(64),
    status               VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    status_reason        VARCHAR(500),
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(100) DEFAULT 'system',
    updated_by           VARCHAR(100),
    version              BIGINT       NOT NULL DEFAULT 0,
    -- Plain, non-partial unique constraint - company_code is a permanent business identifier, never
    -- reused after a soft delete, same precedent as worker_code (V22), NOT like mobile_number's
    -- deliberately-partial reuse-after-delete index (V27). No equivalent reuse decision was asked for
    -- here, so the safer "never reused" default is kept.
    CONSTRAINT uq_company_code UNIQUE (company_code)
);
CREATE INDEX idx_company_status ON company (status);
CREATE INDEX idx_company_industry_id ON company (industry_id);
CREATE INDEX idx_company_deleted_at ON company (deleted_at) WHERE deleted_at IS NOT NULL;

-- Real duplicate-GST/PAN prevention across all companies - partial: nullable until a number is
-- submitted, and scoped to non-deleted rows so a deleted company's old GST/PAN can genuinely be
-- re-registered by a fresh company record, same reasoning as admin_user's email reuse-after-delete
-- (V29). One real GSTIN/PAN = one company, enforced at the DB level, same tier of certainty as
-- worker's Aadhaar/PAN uniqueness (worker_kyc_profile, V23/V28).
CREATE UNIQUE INDEX uq_company_gst_number_hash_active
    ON company (gst_number_hash) WHERE deleted_at IS NULL AND gst_number_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_company_pan_number_hash_active
    ON company (pan_number_hash) WHERE deleted_at IS NULL AND pan_number_hash IS NOT NULL;

-- Backs Company.companyCode generation (CMP + 7-digit zero-padded sequence value, formatted in
-- CompanyService, not here) - same mechanism as worker_code_seq/industry_code_seq/skill_category_code_seq.
CREATE SEQUENCE company_code_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------
-- 2. company_address
-- ---------------------------------------------------------------------------
-- Unlike worker_address (exactly 2 fixed types, PERMANENT/CURRENT, one row each, no is_primary),
-- a company may hold an arbitrary number of addresses (its registered office, plus any number of
-- branches) with exactly one marked primary - the partial unique index below is the same mechanism
-- as worker_designation.is_primary (uq_worker_designation_primary in V22), not worker_address's
-- (worker_id, address_type) shape. Real satellite @Entity with its own UUID PK, never a generic
-- owner_type/owner_id pair - same reasoning as every other satellite table in this codebase
-- (worker_kyc_profile's class comment explains why a polymorphic pair can never carry a real FK).
CREATE TABLE company_address (
    id                UUID         PRIMARY KEY,
    company_id        UUID         NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    address_type      VARCHAR(30)  NOT NULL,
    is_primary        BOOLEAN      NOT NULL DEFAULT FALSE,
    line1             VARCHAR(255),
    line2             VARCHAR(255),
    state_id          UUID REFERENCES state (id) ON DELETE RESTRICT,
    city_id           UUID REFERENCES city (id) ON DELETE RESTRICT,
    pincode           VARCHAR(10),
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    formatted_address VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100) DEFAULT 'system',
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_company_address_primary ON company_address (company_id) WHERE is_primary = TRUE;
CREATE INDEX idx_company_address_company_id ON company_address (company_id);

-- ---------------------------------------------------------------------------
-- 3. company_status_history
-- ---------------------------------------------------------------------------
-- Append-only, mirrors worker_status_history exactly, plus a snapshot of which admin made the change
-- (changed_by_admin_id/changed_by_admin_email) - same ON DELETE SET NULL + email-snapshot reasoning
-- as admin_audit_log.actor_admin_id/actor_email (V4/V14): never lose the history fact just because
-- the admin who made the change later left.
CREATE TABLE company_status_history (
    id                     UUID        PRIMARY KEY,
    company_id             UUID        NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    from_status            VARCHAR(30),
    to_status              VARCHAR(30) NOT NULL,
    reason                 VARCHAR(500),
    changed_by_admin_id    UUID REFERENCES admin_user (id) ON DELETE SET NULL,
    changed_by_admin_email VARCHAR(320),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             VARCHAR(100) DEFAULT 'system',
    updated_by             VARCHAR(100),
    version                BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_company_status_history_company_id_created_at ON company_status_history (company_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 4. company_user
-- ---------------------------------------------------------------------------
-- A login account for one person acting on behalf of a company (role OWNER today; STAFF is modeled
-- now but never created this round - confirmed out of scope). email is unique only among active
-- (non-deleted) rows, lowercase-normalized at the source and DB-enforced via a CHECK constraint -
-- applying the full V12 (lowercase CHECK) + V29 (soft-delete + partial unique) admin_user lesson from
-- day one here, rather than needing two follow-up migrations the way admin_user did.
CREATE TABLE company_user (
    id                    UUID         PRIMARY KEY,
    company_id            UUID         NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    role                  VARCHAR(20)  NOT NULL,
    name                  VARCHAR(150) NOT NULL,
    email                 VARCHAR(320) NOT NULL,
    phone                 VARCHAR(20),
    password_hash         VARCHAR(100) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    security_version      INT          NOT NULL DEFAULT 1,
    last_login_at         TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(100) DEFAULT 'system',
    updated_by            VARCHAR(100),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_company_user_email_lowercase CHECK (email = LOWER(email))
);
CREATE UNIQUE INDEX uq_company_user_email_active ON company_user (email) WHERE deleted_at IS NULL;
CREATE INDEX idx_company_user_company_id ON company_user (company_id);

-- ---------------------------------------------------------------------------
-- 5. company_auth_token - mirrors admin_auth_token exactly (invite / password-reset link tokens).
-- ---------------------------------------------------------------------------
CREATE TABLE company_auth_token (
    id              UUID        PRIMARY KEY,
    company_user_id UUID        NOT NULL REFERENCES company_user (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    type            VARCHAR(30) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(100) DEFAULT 'system',
    updated_by      VARCHAR(100),
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_company_auth_token_company_user_id ON company_auth_token (company_user_id);

-- ---------------------------------------------------------------------------
-- 6. company_refresh_token - mirrors admin_refresh_token exactly (rotating refresh + family-based
--    reuse detection).
-- ---------------------------------------------------------------------------
CREATE TABLE company_refresh_token (
    id              UUID        PRIMARY KEY,
    company_user_id UUID        NOT NULL REFERENCES company_user (id) ON DELETE CASCADE,
    family_id       UUID        NOT NULL,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  UUID,
    user_agent      VARCHAR(400),
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(100) DEFAULT 'system',
    updated_by      VARCHAR(100),
    version         BIGINT      NOT NULL DEFAULT 0
);
-- Backs both normal per-user token lookups and the bulk "revoke every active token belonging to this
-- company's users" cascade CompanyService fires on suspend/reject/delete - see Company's Javadoc.
CREATE INDEX idx_company_refresh_token_company_user_id ON company_refresh_token (company_user_id);

-- ---------------------------------------------------------------------------
-- 7. company_otp - mirrors admin_otp exactly (mandatory email OTP as the 2nd factor on every login).
-- ---------------------------------------------------------------------------
CREATE TABLE company_otp (
    id              UUID         PRIMARY KEY,
    company_user_id UUID         NOT NULL REFERENCES company_user (id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL,
    code_hash       VARCHAR(100) NOT NULL,
    purpose         VARCHAR(30)  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    consumed_at     TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    resend_count    INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100) DEFAULT 'system',
    updated_by      VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_company_otp_company_user_id ON company_otp (company_user_id);

-- ---------------------------------------------------------------------------
-- Permission catalog: 7 new `company` codes, seeded here but deliberately NOT auto-granted to any
-- admin_role_permission row - same precedent as workers.view_bank_details/faceauth.identify (V31)
-- and workers.delete_comments (V35): super-admin bypass covers access until a human deliberately
-- assigns these via the Roles screen. GST verification (the /gst-verify action, part 2) reuses
-- company.edit rather than getting its own code - mirrors how bank-account verification for workers
-- reuses an existing code instead of adding a new one.
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (id, code, module, action, label) VALUES
    (gen_random_uuid(), 'company.view', 'company', 'view', 'View companies'),
    (gen_random_uuid(), 'company.create', 'company', 'create', 'Create a company and its owner account'),
    (gen_random_uuid(), 'company.edit', 'company', 'edit', 'Edit company profile, addresses, and verify GST'),
    (gen_random_uuid(), 'company.delete', 'company', 'delete', 'Delete a company'),
    (gen_random_uuid(), 'company.manage_status', 'company', 'manage_status', 'Approve, reject, suspend, or resume a company'),
    (gen_random_uuid(), 'company.view_sensitive', 'company', 'view_sensitive', 'View unmasked company GST/PAN numbers'),
    (gen_random_uuid(), 'company.export', 'company', 'export', 'Export the company list');

-- ---------------------------------------------------------------------------
-- Seed: COMPANY_OWNER_INVITE email template - exact same shape/columns as ADMIN_STAFF_INVITE (V10),
-- rendered by the notification module (Thymeleaf subject + HTML body). Sent via
-- NotificationService.enqueue (the async transactional-outbox path), not send() - see
-- CompanyService.create's Javadoc for why that is a deliberate deviation from the admin-staff mirror.
-- ---------------------------------------------------------------------------
INSERT INTO email_template (id, code, subject, body_html, locale, active, description, created_at, updated_at, created_by, version)
VALUES (
    gen_random_uuid(),
    'COMPANY_OWNER_INVITE',
    'You have been invited to manage your company on Anytime Work',
    '<p>Hi [[${name}]],</p><p>You have been added as the owner of <strong>[[${companyName}]]</strong> on Anytime Work. Use the link below (valid for [[${hours}]] hours) to set your password and sign in:</p><p><a th:href="${inviteUrl}">Set your password</a></p><p>If you were not expecting this, you can ignore this email.</p>',
    'en', TRUE, 'New company owner account invite — set initial password', now(), now(), 'system', 0
);
