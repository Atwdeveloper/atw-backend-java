-- V28__kyc_pan_duplicate_check.sql
-- Audit finding (2026-11-11): worker_kyc_profile.pan_number had no duplicate check at all - unlike
-- aadhaar_number_hash (V23), there was no pan_number_hash column, so two workers could silently be
-- given the same PAN. Same fix as Aadhaar's: a deterministic HMAC-SHA256 hash column (computed in
-- Java by IdentityNumberHasher, the renamed/generalized AadhaarHasher - see KycServiceImpl.submitPan)
-- with a partial unique index, since the encrypted pan_number column itself is never searchable.

ALTER TABLE worker_kyc_profile
    ADD COLUMN pan_number_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_worker_kyc_profile_pan_hash
    ON worker_kyc_profile (pan_number_hash) WHERE pan_number_hash IS NOT NULL;
