-- V19__masterdata_workforce_reference_data.sql
-- Department/industry/designation/skill reference data, owned by the masterdata module alongside
-- state/city (V17). Real relationships verified against the legacy schema rather than assumed:
-- skill belongs to designation (NOT skill_category - skill_category is minimum-wage-only config,
-- deliberately out of scope here), designation is many-to-many with industry, department is
-- unrelated to the other three (only ever referenced by admin staff / agent in legacy).
--
-- Status is a single consistent ACTIVE/INACTIVE enum column across all four tables, replacing
-- legacy's four different, inconsistent status representations (missing entirely on department;
-- int/TEXT/tinyint on the other three). No hard-delete endpoints exist for any of these - only
-- deactivate - so no delete-guard logic is needed yet; FK actions below still use RESTRICT as a
-- data-integrity backstop for whenever company/worker/vacancy start referencing these.

CREATE TABLE department (
    id         UUID PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_department_name UNIQUE (name)
);

CREATE TABLE industry (
    id                UUID PRIMARY KEY,
    name              VARCHAR(150) NOT NULL,
    code              VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    image_document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100) DEFAULT 'system',
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_industry_name UNIQUE (name),
    CONSTRAINT uq_industry_code UNIQUE (code)
);

CREATE TABLE designation (
    id                UUID PRIMARY KEY,
    name              VARCHAR(150) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    image_document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100) DEFAULT 'system',
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_designation_name UNIQUE (name)
);

CREATE TABLE designation_industry (
    designation_id UUID NOT NULL REFERENCES designation (id) ON DELETE CASCADE,
    industry_id    UUID NOT NULL REFERENCES industry (id) ON DELETE RESTRICT,
    PRIMARY KEY (designation_id, industry_id)
);

CREATE INDEX idx_designation_industry_industry_id ON designation_industry (industry_id);

CREATE TABLE skill (
    id                UUID PRIMARY KEY,
    designation_id    UUID         NOT NULL REFERENCES designation (id) ON DELETE RESTRICT,
    name              VARCHAR(150) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    image_document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100) DEFAULT 'system',
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_skill_designation_name UNIQUE (designation_id, name)
);

-- No separate index on designation_id: uq_skill_designation_name above already provides one as the
-- leading column of its composite unique index, which fully serves
-- SkillRepository.findByDesignationIdOrderByName's WHERE designation_id = ? filter.

-- Permission catalog additions (identity module owns the `admin_permission` table - renamed from
-- `permission` in V16 - this migration just seeds rows into it, same as the table's original seed
-- in V4; no Java code outside identity touches the AdminPermission entity, so module ownership at
-- the code level is unaffected).
--
-- Only `department.*` is new here: V4 already pre-seeded the full legacy permission catalog,
-- including `industry.*`/`designation.*`/`skill.*` (view/create/edit/delete/toggle_status, plus
-- skill.export), well before these tables/controllers existed - inserting them again would violate
-- the unique constraint on `code`. `department` was never in that legacy catalog (it had no RBAC
-- module there either, see the V19 table comment above), so its four codes are genuinely new. The
-- unused `*.delete`/`skill.export` codes already in the catalog are left alone - no endpoint here
-- checks for them, and pruning pre-existing identity-owned rows isn't this migration's job.
INSERT INTO admin_permission (id, code, module, action, label) VALUES
    (gen_random_uuid(), 'department.view', 'department', 'view', 'View departments'),
    (gen_random_uuid(), 'department.create', 'department', 'create', 'Create departments'),
    (gen_random_uuid(), 'department.edit', 'department', 'edit', 'Edit departments'),
    (gen_random_uuid(), 'department.toggle_status', 'department', 'toggle_status', 'Activate/deactivate departments');
