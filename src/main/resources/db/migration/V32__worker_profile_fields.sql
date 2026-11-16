-- V32__worker_profile_fields.sql
-- Makes department optional on a worker (it was never actually enforced in the legacy app either -
-- absent from its own create validator, the update-resolution logic was commented out there, and
-- live legacy rows have department_id = NULL) and adds a plain, optional years-of-experience column
-- (legacy's own equivalent is a bare unvalidated free-text column on `workers` - this is the same
-- idea, just a real integer so it is genuinely filterable/sortable rather than an ambiguous string).
--
-- No new "registration date" column: legacy's own "registration date" filter is just created_at
-- under a different name (confirmed by reading its filter code directly) - this worker table already
-- has created_at, so only a query-side filter was added, not a column.
ALTER TABLE worker ALTER COLUMN department_id DROP NOT NULL;
ALTER TABLE worker ADD COLUMN work_experience_years INT;
