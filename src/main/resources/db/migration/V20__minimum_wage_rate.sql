-- V20__minimum_wage_rate.sql
-- Skill category (a minimum-wage-only grouping, separate from the industry/designation/skill chain
-- - see SkillCategory's Javadoc) and the effective-dated minimum wage rate itself, keyed on
-- (state, skill_category). No permission rows to seed here: `skill_category.*` and `minimum_wage.*`
-- were already present in admin_permission from V4's original legacy-catalog seed, confirmed by
-- query before writing this migration (the same mistake that bit V19 the first time around).
--
-- minimum_wage_rate is effective-dated and immutable by design, not a flat current-value table like
-- legacy's: a rate change is a new row with its own effective_from, never an UPDATE of an existing
-- one (enforced in the Java entity - no setter exists for rate/effective_from/state/skill_category).
-- This preserves the history needed to recompute a past payroll period with the rate that was
-- actually in force then, and mirrors how real government minimum-wage notifications work (always
-- date-effective). Uniqueness is on the full (state, skill_category, effective_from) triple, not
-- just the pair, since multiple historical versions legitimately share a (state, skill_category) -
-- and it only applies to ACTIVE rows (a partial unique index below, not a plain UNIQUE constraint):
-- a mistaken entry gets deactivated, never edited, and without the partial qualifier its date could
-- never be reused by the corrected replacement row.

CREATE TABLE skill_category (
    id         UUID PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    code       VARCHAR(30)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_skill_category_name UNIQUE (name),
    CONSTRAINT uq_skill_category_code UNIQUE (code)
);

CREATE TABLE minimum_wage_rate (
    id                UUID PRIMARY KEY,
    state_id          UUID           NOT NULL REFERENCES state (id) ON DELETE RESTRICT,
    skill_category_id UUID           NOT NULL REFERENCES skill_category (id) ON DELETE RESTRICT,
    min_daily_rate    NUMERIC(10, 2) NOT NULL,
    effective_from    DATE           NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        VARCHAR(100)   DEFAULT 'system',
    updated_by        VARCHAR(100),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_minimum_wage_rate_positive CHECK (min_daily_rate > 0)
);

-- Partial unique index, not a plain UNIQUE constraint on the table (Postgres has no "WHERE" clause
-- on an inline CONSTRAINT UNIQUE) - see the comment above for why it's scoped to ACTIVE rows only.
-- Its (state_id, skill_category_id, effective_from) column order also serves two query patterns for
-- free: the "latest effective version" resolver (an index-only backward scan, B-trees scan either
-- direction equally well - no separate DESC index needed) and the full-history-for-this-pair listing.
CREATE UNIQUE INDEX uq_minimum_wage_rate_state_category_effective_active
    ON minimum_wage_rate (state_id, skill_category_id, effective_from)
    WHERE status = 'ACTIVE';

-- The state-only filter can still use the index above via its leading column (just without a fully
-- sorted result, at a scale small enough that an in-memory sort afterward is irrelevant). The
-- skill-category-only filter cannot use it at all - skill_category_id isn't the leading column - so
-- it needs its own index.
CREATE INDEX idx_minimum_wage_rate_skill_category ON minimum_wage_rate (skill_category_id, effective_from DESC);

-- Completes the link deferred when designation/skill were first built: a designation's minimum-wage
-- classification. Optional - not every designation needs one assigned immediately.
ALTER TABLE designation ADD COLUMN skill_category_id UUID REFERENCES skill_category (id) ON DELETE RESTRICT;

-- Every other FK column this module has added got one (city.state_id, designation_industry.industry_id,
-- skill.designation_id via its unique constraint) - this one was missed in the same pass that added it.
CREATE INDEX idx_designation_skill_category_id ON designation (skill_category_id);
