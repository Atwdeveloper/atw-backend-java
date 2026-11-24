-- V41__worker_bank_detail_remove_branch_name.sql
-- Free-text bank branch name removed (PO-requested cleanup) - redundant with ifsc_code, which alone
-- uniquely identifies the branch in India's banking system. Confirmed before removal: no verification
-- adapter (BankVerificationPort) reads this value, so nothing downstream depends on it.

ALTER TABLE worker_bank_detail DROP COLUMN branch_name;
