-- V16__rename_role_permission_tables.sql
-- Renames the 'role', 'permission', and 'role_permission' tables to 'admin_role',
-- 'admin_permission', and 'admin_role_permission'. Follow-up to V15 (which renamed the staff/roles
-- *permission codes*): these three tables back the admin RBAC system itself and were the one piece
-- of that collision risk V15 didn't cover - a future company-side "role"/"permission" concept would
-- otherwise collide with these bare names at the database level, unlike admin_user* which was
-- already correctly prefixed. Matching Java entity/repository class renames (Role -> AdminRole,
-- RoleRepository -> AdminRoleRepository, Permission -> AdminPermission,
-- PermissionRepository -> AdminPermissionRepository) land in the same change.
--
-- Plain renames, not data migrations: row ids, all data, and every existing foreign-key
-- relationship (role_permission's two FKs, admin_user_role's FK to role, V15's permission.id
-- values) survive automatically - Postgres tracks FK targets by the referenced table's OID, not by
-- name, so admin_user_role.role_id still correctly points at the same rows after the rename below.
-- Column names (role_id, permission_id) are already unambiguous within their own join tables and
-- are left as-is.

ALTER TABLE role RENAME TO admin_role;
ALTER TABLE permission RENAME TO admin_permission;
ALTER TABLE role_permission RENAME TO admin_role_permission;

ALTER INDEX idx_permission_module RENAME TO idx_admin_permission_module;
