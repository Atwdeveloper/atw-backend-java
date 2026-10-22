-- V5__admin_security_hardening.sql
-- OTP brute-force & resend-abuse hardening.
--   admin_user.security_version - bumped whenever a security-sensitive admin event occurs
--                                  (e.g. password reset/change); lets a future per-request check
--                                  invalidate tokens/sessions minted under an older value.
--   admin_otp.resend_count      - number of resends already issued in an OTP chain, enforced by
--                                  OtpService against atw.admin.security.otp-max-resends.

ALTER TABLE admin_user ADD COLUMN security_version INT NOT NULL DEFAULT 1;
ALTER TABLE admin_otp ADD COLUMN resend_count INT NOT NULL DEFAULT 0;
