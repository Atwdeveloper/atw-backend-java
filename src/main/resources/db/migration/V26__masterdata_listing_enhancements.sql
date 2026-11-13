-- V26__masterdata_listing_enhancements.sql
-- Backs the listing-API enhancement pass: multiselect filters, pagination, bulk operations,
-- delete-with-guards, and backend-only code generation for industry/skill_category.
--
-- No FK changes needed: every downstream reference that should block a parent delete is already
-- ON DELETE RESTRICT (worker.department_id/industry_id, designation_industry.industry_id,
-- worker_designation.designation_id, worker_skill.skill_id, designation.skill_category_id,
-- minimum_wage_rate.skill_category_id - all set correctly when those tables were first created).
-- This migration only adds what's genuinely missing: one permission, and two code sequences.

-- department never had a delete permission at all (legacy had no department module to inherit one
-- from) - every other masterdata entity's *.delete already exists, seeded from the legacy catalog in
-- V4__identity.sql, dormant until this pass wires it up.
INSERT INTO admin_permission (id, code, module, action, label) VALUES
    (gen_random_uuid(), 'department.delete', 'department', 'delete', 'Delete departments');

-- Backs server-generated industry/skill_category codes (same mechanism as worker_code_seq in
-- V22__worker.sql) - these were user-supplied request fields until now; CreateIndustryRequest/
-- CreateSkillCategoryRequest no longer accept a code at all.
CREATE SEQUENCE industry_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE skill_category_code_seq START WITH 1 INCREMENT BY 1;
