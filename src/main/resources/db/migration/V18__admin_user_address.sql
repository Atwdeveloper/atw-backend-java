-- V18__admin_user_address.sql
-- Structured, encrypted address for an admin/staff account, replacing the free-text
-- address_line/pincode columns added on admin_user_profile in V9. Those columns are dropped rather
-- than backfilled: the project has no production data yet, and line1/line2 here are encrypted at
-- the application layer (EncryptedStringConverter) - a SQL-level backfill could not have produced
-- correctly encrypted data regardless.

CREATE TABLE admin_user_address (
    id                UUID PRIMARY KEY,
    admin_user_id     UUID         NOT NULL UNIQUE REFERENCES admin_user (id) ON DELETE CASCADE,
    line1             VARCHAR(255),
    line2             VARCHAR(255),
    state_id          UUID REFERENCES state (id) ON DELETE RESTRICT,
    city_id           UUID REFERENCES city (id) ON DELETE RESTRICT,
    pincode           VARCHAR(10),
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    formatted_address VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100) DEFAULT 'system',
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_user_address_state_id ON admin_user_address (state_id);
CREATE INDEX idx_admin_user_address_city_id ON admin_user_address (city_id);

ALTER TABLE admin_user_profile DROP COLUMN address_line;
ALTER TABLE admin_user_profile DROP COLUMN pincode;
