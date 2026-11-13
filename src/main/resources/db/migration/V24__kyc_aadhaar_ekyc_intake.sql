-- V24__kyc_aadhaar_ekyc_intake.sql
-- Replaces the KYC module's manual "upload a photo, admin reviews it" workflow with live Aadhaar
-- e-KYC OTP verification: a government-backed API call is itself sufficient proof, so there is
-- nothing left for an admin to manually review. Also adds the "add worker via Aadhaar" intake
-- wizard's two staging tables (mobile-number OTP, Aadhaar e-KYC verification session).
--
-- worker_kyc_document is dropped outright, not edited in place: V23 is an already-applied migration
-- (never alter an applied migration - see CLAUDE.md), so this correction must be a new forward
-- migration. Safe in this pre-production environment (no real worker documents uploaded yet); a
-- production rollout of a change like this would need a data-preserving plan instead.

DROP TABLE worker_kyc_document;

ALTER TABLE worker_kyc_profile
    ADD COLUMN verification_ref VARCHAR(255);

-- Proves control of a mobile number by SMS OTP - the intake wizard's first step. Independent of any
-- worker (none may exist yet) and of AdminOtp's own device-bound login 2FA; see WorkerMobileOtp's
-- Javadoc for why this isn't generalized from that mechanism instead.
CREATE TABLE worker_mobile_otp (
    id            UUID PRIMARY KEY,
    mobile_number VARCHAR(20)  NOT NULL,
    code_hash     VARCHAR(100) NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ,
    attempts      INT          NOT NULL DEFAULT 0,
    resend_count  INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(100) DEFAULT 'system',
    updated_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0
);

-- One Aadhaar e-KYC verification attempt - independent of any worker (none may exist yet; a new
-- worker is typically created FROM a successful verification). consumed_by_worker_id is populated
-- after the fact purely for traceability, hence ON DELETE SET NULL rather than CASCADE: deleting a
-- worker must never silently erase this audit trail. Verified PII (name/DOB/address) and the Aadhaar
-- number itself are encrypted at rest with the worker-PII key (enforced at the entity level via
-- WorkerEncryptedStringConverter) even though this is short-lived staging data, since it is still
-- genuine PII regardless of how briefly it is expected to live here.
CREATE TABLE worker_aadhaar_verification (
    id                          UUID PRIMARY KEY,
    aadhaar_number              VARCHAR(255) NOT NULL,
    aadhaar_number_hash         VARCHAR(64)  NOT NULL,
    provider_reference_id       VARCHAR(255) NOT NULL,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'OTP_SENT',
    expires_at                  TIMESTAMPTZ  NOT NULL,
    attempts                    INT          NOT NULL DEFAULT 0,
    verified_at                 TIMESTAMPTZ,
    consumed_at                 TIMESTAMPTZ,
    consumed_by_worker_id       UUID REFERENCES worker (id) ON DELETE SET NULL,
    consent_given_at            TIMESTAMPTZ  NOT NULL,
    consent_ip                  VARCHAR(45),
    verified_full_name          VARCHAR(255),
    verified_gender             VARCHAR(20),
    verified_date_of_birth      DATE,
    verified_address_line1      VARCHAR(255),
    verified_address_line2      VARCHAR(255),
    verified_pincode            VARCHAR(10),
    verified_state_name         VARCHAR(150),
    verified_district_name      VARCHAR(150),
    verified_photo_document_id  UUID REFERENCES documents (id) ON DELETE SET NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                  VARCHAR(100) DEFAULT 'system',
    updated_by                  VARCHAR(100),
    version                     BIGINT       NOT NULL DEFAULT 0
);
