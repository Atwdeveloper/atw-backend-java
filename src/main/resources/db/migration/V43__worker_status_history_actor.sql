-- V43__worker_status_history_actor.sql
-- Adds a real changed_by_admin_id to worker_status_history, mirroring company_status_history's
-- identical column (V37__company.sql) exactly - lets the response layer tell an admin-made change
-- apart from the automatic KYC-verified transition (no admin involved) instead of only ever showing
-- a raw JPA-auditing string that was never actually a display name.
--
-- Existing rows get NULL here (no way to know, after the fact, which of them were admin-made vs.
-- automatic) - they will display as "System" until a new transition is recorded for that worker,
-- which is an acceptable, honest gap: this migration does not retroactively invent an attribution it
-- cannot actually know.

ALTER TABLE worker_status_history
    ADD COLUMN changed_by_admin_id UUID REFERENCES admin_user (id) ON DELETE SET NULL;
