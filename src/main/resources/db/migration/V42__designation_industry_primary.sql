-- V42__designation_industry_primary.sql
-- Adds a PRIMARY industry concept to designation_industry (PO-requested; confirmed against the
-- legacy reference, which modeled this identically as a boolean on the pivot row, not a separate
-- column on designation itself - see legacy's add_is_primary_to_designation_industry_table
-- migration). designation_industry is a pure leaf pivot - nothing has an FK pointing into it - so
-- this converts it in place rather than create-new-table-and-copy.
--
-- Backfill rule (developer-confirmed): every designation ends up with exactly one primary
-- afterward - the row with the LOWEST industry_id, whether it already had exactly one industry or
-- several with no existing signal for which one should lead. Stricter than legacy, which never
-- enforced "exactly one primary" at all (legacy production data has designations with zero rows
-- marked primary).

ALTER TABLE designation_industry
    ADD COLUMN id UUID,
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by VARCHAR(100) DEFAULT 'system',
    ADD COLUMN updated_by VARCHAR(100),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE designation_industry SET id = gen_random_uuid();
ALTER TABLE designation_industry ALTER COLUMN id SET NOT NULL;

UPDATE designation_industry di SET is_primary = TRUE
WHERE di.id = (
    SELECT di2.id FROM designation_industry di2
    WHERE di2.designation_id = di.designation_id
    ORDER BY di2.industry_id
    LIMIT 1
);

ALTER TABLE designation_industry DROP CONSTRAINT designation_industry_pkey;
ALTER TABLE designation_industry ADD CONSTRAINT designation_industry_pkey PRIMARY KEY (id);
ALTER TABLE designation_industry ADD CONSTRAINT uq_designation_industry UNIQUE (designation_id, industry_id);

-- Enforces "exactly one primary per designation" at the DB level going forward, mirroring
-- uq_worker_designation_primary exactly.
CREATE UNIQUE INDEX uq_designation_industry_primary ON designation_industry (designation_id) WHERE is_primary = TRUE;
