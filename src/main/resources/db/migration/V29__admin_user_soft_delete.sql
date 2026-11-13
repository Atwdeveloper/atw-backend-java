-- V29__admin_user_soft_delete.sql
-- Converts admin-staff deletion from a hard delete to a soft delete: AdminStaffService.delete() no
-- longer issues a real SQL DELETE, only an UPDATE that sets deleted_at. This preserves a deleted
-- admin's full history (admin_user_profile, admin_user_kyc, admin_user_bank_detail,
-- admin_user_address, admin_user_role) instead of losing it to the ON DELETE CASCADE chain those
-- tables already have (see V9__admin_staff_profile_kyc_bank.sql/V18__admin_user_address.sql) - an
-- audit finding (2026-11-11): this was the exact same risk already fixed for Worker this session
-- (V27__worker_soft_delete.sql), just not yet applied here. See AdminUser.java/AdminStaffService.java
-- for the full reasoning.
--
-- deleted_at is the single source of truth - nullable, no separate boolean flag: NULL means active,
-- NOT NULL means deleted. AdminUser.java's @SQLRestriction("deleted_at is null") transparently
-- excludes a deleted admin from every existing query path (findById,
-- findByUsernameIgnoreCaseOrEmailIgnoreCase, existsByUsernameIgnoreCase/existsByEmailIgnoreCase, the
-- pessimistic-lock JPQL in findByIdForUpdate) with no code changes needed anywhere else.
ALTER TABLE admin_user ADD COLUMN deleted_at TIMESTAMPTZ;

-- username/email must stay unique only among ACTIVE (non-deleted) admins - a deleted admin's
-- username/email becomes available again for a genuinely new hire to be created with, the same
-- reuse-after-delete decision already made for Worker's mobile_number (V27). Applied here to BOTH
-- columns for consistency with that precedent; flagged back to the developer as a
-- precedent-following default, not a separately re-confirmed decision for this specific table -
-- email reuse in particular may deserve a second look (a departed employee's email is commonly NOT
-- reassigned to a different person in many organizations, unlike a recycled phone number or
-- username), since this migration cannot easily be un-done once real data depends on it.
ALTER TABLE admin_user DROP CONSTRAINT admin_user_username_key;
ALTER TABLE admin_user DROP CONSTRAINT admin_user_email_key;
CREATE UNIQUE INDEX uq_admin_user_username_active ON admin_user (username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_admin_user_email_active ON admin_user (email) WHERE deleted_at IS NULL;

-- Supports a future "deleted admins" audit/compliance report without a sequential scan, if one is
-- ever built - cheap to add now (partial index, tiny while deletions stay rare), costs nothing today.
CREATE INDEX idx_admin_user_deleted_at ON admin_user (deleted_at) WHERE deleted_at IS NOT NULL;
