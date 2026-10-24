-- V14__admin_audit_log_actor_email_snapshot.sql
-- admin_audit_log.actor_admin_id is ON DELETE SET NULL (V4) - deliberately, so a log entry survives
-- the actor being removed. But until now there was no way to ever delete an admin_user row at all,
-- so that consequence was never actually reachable. StaffService.delete() (this slice) is the first
-- real path to deleting one, and a hard delete would silently strip attribution from every historical
-- action that admin ever took, which is exactly the audit trail CLAUDE.md requires for security/
-- financial reconciliation. actor_email is a point-in-time snapshot taken at write time (not a live
-- join to admin_user), so it survives the actor row being deleted - see AdminAuditService.

ALTER TABLE admin_audit_log ADD COLUMN actor_email VARCHAR(320);
