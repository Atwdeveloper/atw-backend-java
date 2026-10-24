-- V8__rename_admin_auth_tables.sql
-- Gives the admin auth stack consistent, type-prefixed names ahead of Worker/Company identity
-- modules: each future identity type gets its own equivalent table family (e.g.
-- worker_refresh_token), never a shared table with these - only the issue/rotate/revoke code is
-- shared, via a common pattern, not a common table. This rename is step one of applying that to
-- what already exists for Admin.
--
-- auth_token becomes admin_auth_token (not admin_password_reset_token): it already serves two
-- token kinds via its `type` column - PASSWORD_RESET today, and INVITE for the staff-invite flow
-- being added next - see AuthTokenType and AdminAuthTokenRepository.invalidateActiveTokens. Naming it
-- after only one of its two purposes would be misleading.
--
-- Table data is untouched (RENAME preserves rows); only names change. Constraint/index names are
-- renamed explicitly too since PostgreSQL does not rename them automatically with their table.

ALTER TABLE refresh_token RENAME TO admin_refresh_token;
ALTER TABLE admin_refresh_token RENAME CONSTRAINT refresh_token_pkey TO admin_refresh_token_pkey;
ALTER TABLE admin_refresh_token RENAME CONSTRAINT refresh_token_token_hash_key TO admin_refresh_token_token_hash_key;
ALTER TABLE admin_refresh_token RENAME CONSTRAINT refresh_token_admin_user_id_fkey TO admin_refresh_token_admin_user_id_fkey;
ALTER INDEX idx_refresh_token_admin RENAME TO idx_admin_refresh_token_admin;

ALTER TABLE auth_token RENAME TO admin_auth_token;
ALTER TABLE admin_auth_token RENAME CONSTRAINT auth_token_pkey TO admin_auth_token_pkey;
ALTER TABLE admin_auth_token RENAME CONSTRAINT auth_token_token_hash_key TO admin_auth_token_token_hash_key;
ALTER TABLE admin_auth_token RENAME CONSTRAINT auth_token_admin_user_id_fkey TO admin_auth_token_admin_user_id_fkey;
