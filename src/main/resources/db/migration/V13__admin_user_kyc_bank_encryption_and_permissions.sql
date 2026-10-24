-- V13__admin_user_kyc_bank_encryption_and_permissions.sql
-- Prepares admin_user_kyc / admin_user_bank_detail for encrypted-at-rest storage, and adds the
-- fine-grained view permissions that gate seeing the unmasked values:
--
--   1. Widen the sensitive columns: AES-256-GCM ciphertext (12-byte IV + 16-byte auth tag
--      overhead, base64-encoded) is longer than the plaintext identifier it replaces. VARCHAR(255)
--      is comfortably more than any of these ever need, encrypted or not - no column currently
--      holds real data (V9 just landed, no staff KYC/bank API existed until now), so widening is
--      a plain type change, no data migration needed.
--   2. Seed staff.view_kyc / staff.view_bank_details and grant them to Senior Developer and Junior
--      Developer only - the same two roles (and only those two) already hold the equivalent
--      sensitive-data permissions for workers (workers.view_wallet, workers.manage_wallet_hold,
--      workers.withdraw_wallet). staff.view alone now only returns masked values for these two
--      satellite tables (see StaffKycService/StaffBankDetailService); staff.edit still governs
--      writing to them - no separate write permission introduced, to avoid an unused extra knob.

ALTER TABLE admin_user_kyc ALTER COLUMN aadhaar_number TYPE VARCHAR(255);
ALTER TABLE admin_user_kyc ALTER COLUMN pan_number TYPE VARCHAR(255);
ALTER TABLE admin_user_kyc ALTER COLUMN uan_number TYPE VARCHAR(255);
ALTER TABLE admin_user_kyc ALTER COLUMN pf_number TYPE VARCHAR(255);
ALTER TABLE admin_user_kyc ALTER COLUMN esic_number TYPE VARCHAR(255);
ALTER TABLE admin_user_bank_detail ALTER COLUMN account_number TYPE VARCHAR(255);

INSERT INTO permission (id, code, module, action, label) VALUES
(gen_random_uuid(), 'staff.view_kyc', 'staff', 'view_kyc', 'view_kyc'),
(gen_random_uuid(), 'staff.view_bank_details', 'staff', 'view_bank_details', 'view_bank_details');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN ('staff.view_kyc', 'staff.view_bank_details')
WHERE r.name IN ('Senior Developer', 'Junior Developer');
