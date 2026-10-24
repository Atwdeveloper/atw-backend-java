-- V12__admin_user_case_insensitive_identifiers.sql
-- admin_user.email/username had a plain (case-sensitive) UNIQUE constraint, but every lookup
-- (login, existsByEmailIgnoreCase/existsByUsernameIgnoreCase used by StaffService before creating
-- an account) treats them case-insensitively. That mismatch meant "Riya@example.com" and
-- "riya@example.com" could both exist as separate rows: besides being a duplicate-account bug, two
-- such rows would make the case-insensitive login lookup match more than one row and fail outright
-- for both accounts. Fixed at the source: normalize to lowercase on write (AdminUser constructor /
-- updateProfile) and enforce it permanently with a CHECK constraint, so the existing plain UNIQUE
-- constraint is now correctly case-insensitive in effect, for every row regardless of how it's
-- inserted (not just rows that go through the entity layer).

UPDATE admin_user SET email = LOWER(email) WHERE email <> LOWER(email);
UPDATE admin_user SET username = LOWER(username) WHERE username IS NOT NULL AND username <> LOWER(username);

ALTER TABLE admin_user ADD CONSTRAINT chk_admin_user_email_lowercase CHECK (email = LOWER(email));
ALTER TABLE admin_user ADD CONSTRAINT chk_admin_user_username_lowercase CHECK (username IS NULL OR username = LOWER(username));
