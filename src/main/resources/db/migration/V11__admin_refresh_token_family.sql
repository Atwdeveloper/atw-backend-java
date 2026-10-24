-- V11__admin_refresh_token_family.sql
-- Refresh-token reuse detection (RFC 6819 §5.2.2.3): rotation already exists (RefreshTokenService),
-- but replaying an already-rotated-away token was only ever rejected for itself - nothing acted on
-- the signal that reuse is a strong indicator of token theft. family_id groups every token
-- descended from one original login; when a dead (already-replaced) token is replayed,
-- RefreshTokenService now revokes the whole family, not just that one token.
--
-- Backfill: existing rows get family_id = their own id (each treated as its own one-token family).
-- This cannot reconstruct any true pre-existing chain from replaced_by_id alone in a single cheap
-- statement, but with at most a handful of rows in any environment at this stage, that's fine - it
-- only affects whether a reuse of a token issued before this migration revokes its (then-nonexistent)
-- successors too, which is a non-issue for tokens that predate this migration.

ALTER TABLE admin_refresh_token ADD COLUMN family_id UUID;
UPDATE admin_refresh_token SET family_id = id WHERE family_id IS NULL;
ALTER TABLE admin_refresh_token ALTER COLUMN family_id SET NOT NULL;
CREATE INDEX idx_admin_refresh_token_family ON admin_refresh_token (family_id);
