-- V17__masterdata.sql
-- Canonical geography reference data (state, city) that company/worker/vacancy/admin addresses and
-- future state-based business rules (minimum wage, statutory, RM/agent territory) key off. Owned
-- exclusively by the masterdata module - every other module holds only a state_id/city_id, never a
-- direct relationship into these tables.
--
-- "city" is seeded at administrative-district granularity (the real locality stays free text on
-- the owning address), not every individual town/locality. Only the state list is seeded here -
-- city is intentionally left empty pending a real import from an authoritative dataset (e.g. India
-- Post's All India Pincode Directory) rather than hand-typed, which is exactly the mistake being
-- corrected (the legacy table had 25 of 36 states and collapsed distinct localities into one row).

CREATE TABLE state (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(10)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_state_code UNIQUE (code)
);

CREATE TABLE city (
    id         UUID PRIMARY KEY,
    state_id   UUID         NOT NULL REFERENCES state (id) ON DELETE RESTRICT,
    name       VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_city_state_name UNIQUE (state_id, name)
);

CREATE INDEX idx_city_state_id ON city (state_id);

INSERT INTO state (id, name, code) VALUES
    (gen_random_uuid(), 'Andhra Pradesh', 'AP'),
    (gen_random_uuid(), 'Arunachal Pradesh', 'AR'),
    (gen_random_uuid(), 'Assam', 'AS'),
    (gen_random_uuid(), 'Bihar', 'BR'),
    (gen_random_uuid(), 'Chhattisgarh', 'CG'),
    (gen_random_uuid(), 'Goa', 'GA'),
    (gen_random_uuid(), 'Gujarat', 'GJ'),
    (gen_random_uuid(), 'Haryana', 'HR'),
    (gen_random_uuid(), 'Himachal Pradesh', 'HP'),
    (gen_random_uuid(), 'Jharkhand', 'JH'),
    (gen_random_uuid(), 'Karnataka', 'KA'),
    (gen_random_uuid(), 'Kerala', 'KL'),
    (gen_random_uuid(), 'Madhya Pradesh', 'MP'),
    (gen_random_uuid(), 'Maharashtra', 'MH'),
    (gen_random_uuid(), 'Manipur', 'MN'),
    (gen_random_uuid(), 'Meghalaya', 'ML'),
    (gen_random_uuid(), 'Mizoram', 'MZ'),
    (gen_random_uuid(), 'Nagaland', 'NL'),
    (gen_random_uuid(), 'Odisha', 'OD'),
    (gen_random_uuid(), 'Punjab', 'PB'),
    (gen_random_uuid(), 'Rajasthan', 'RJ'),
    (gen_random_uuid(), 'Sikkim', 'SK'),
    (gen_random_uuid(), 'Tamil Nadu', 'TN'),
    (gen_random_uuid(), 'Telangana', 'TG'),
    (gen_random_uuid(), 'Tripura', 'TR'),
    (gen_random_uuid(), 'Uttar Pradesh', 'UP'),
    (gen_random_uuid(), 'Uttarakhand', 'UK'),
    (gen_random_uuid(), 'West Bengal', 'WB'),
    (gen_random_uuid(), 'Andaman and Nicobar Islands', 'AN'),
    (gen_random_uuid(), 'Chandigarh', 'CH'),
    (gen_random_uuid(), 'Dadra and Nagar Haveli and Daman and Diu', 'DN'),
    (gen_random_uuid(), 'Delhi', 'DL'),
    (gen_random_uuid(), 'Jammu and Kashmir', 'JK'),
    (gen_random_uuid(), 'Ladakh', 'LA'),
    (gen_random_uuid(), 'Lakshadweep', 'LD'),
    (gen_random_uuid(), 'Puducherry', 'PY');
