-- V1__init.sql
-- Baseline migration for the ATW backend schema.
-- No domain tables yet; this establishes the Flyway history table and
-- enables commonly-needed PostgreSQL extensions. Add real DDL in V2+.

-- pgcrypto provides crypt(), digest(), and gen_random_bytes() helpers.
-- (gen_random_uuid() is already built into PostgreSQL core since v13.)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
