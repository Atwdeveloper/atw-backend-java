-- V40__worker_remove_department.sql
-- department classification on a worker was never actually enforced (made nullable 2026-11-16 after
-- confirming legacy never enforced it either) and has no downstream consumer anywhere in this
-- codebase - confirmed before removal (PO-requested cleanup). The standalone Department masterdata
-- module/table/CRUD is untouched - this only removes WORKER's own reference to it.

ALTER TABLE worker DROP COLUMN department_id;
