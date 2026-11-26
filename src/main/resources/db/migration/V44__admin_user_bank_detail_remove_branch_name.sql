-- V44__admin_user_bank_detail_remove_branch_name.sql
-- Free-text bank branch name removed from staff bank detail (PO-requested cleanup) - same reasoning
-- and precedent as V41__worker_bank_detail_remove_branch_name.sql: redundant with ifsc_code, which
-- alone uniquely identifies the branch in India's banking system.

ALTER TABLE admin_user_bank_detail DROP COLUMN branch_name;
