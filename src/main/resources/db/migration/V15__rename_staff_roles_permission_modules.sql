-- V15__rename_staff_roles_permission_modules.sql
-- Renames the 'staff' and 'roles' permission modules (and their permission codes) to 'admin_staff'
-- and 'admin_roles'. These two modules are different in kind from the ~21 other business-entity
-- modules (companies, workers, vacancy, ...): they describe the admin identity/access-management
-- system managing itself, not a platform-wide business entity, so "staff"/"roles" alone are the
-- only module names realistically at risk of colliding with a future company-side "staff"/"roles"
-- concept. DB tables (admin_user*) and API paths (/api/v1/admin/staff, /api/v1/admin/roles) are
-- already unambiguous and are deliberately left unchanged - only these catalog values, and the
-- matching Java class/permission-code literals in the same commit, are renamed.
--
-- Renaming in place (not inserting new rows + deleting old ones) deliberately preserves each
-- permission's id, so every existing role_permission grant survives the rename automatically -
-- this is a pure rename, not a data migration.

UPDATE permission
SET code = 'admin_staff.' || split_part(code, '.', 2), module = 'admin_staff'
WHERE module = 'staff';

UPDATE permission
SET code = 'admin_roles.' || split_part(code, '.', 2), module = 'admin_roles'
WHERE module = 'roles';
