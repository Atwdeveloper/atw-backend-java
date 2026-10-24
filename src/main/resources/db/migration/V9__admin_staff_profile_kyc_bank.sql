-- V9__admin_staff_profile_kyc_bank.sql
-- Staff demographic/KYC/bank data, split into three satellite tables by sensitivity tier rather
-- than added as columns on admin_user: ordinary PII, government-ID-grade KYC, and financial/bank
-- data each get their own permission boundary later (e.g. staff.view vs staff.view_kyc vs
-- staff.view_bank_details) and their own audit trail (created_by/updated_by/version), independent
-- of admin_user's credential/session audit trail. Each table is optional - no row until that data
-- is actually captured - and strictly 1:1 with admin_user via a UNIQUE foreign key.
--
-- Deliberately NOT handled here, pending a dedicated decision: encryption-at-rest for
-- aadhaar_number / pan_number / account_number. These are plain columns for now - this is a schema
-- foundation, not a finished KYC-compliance posture, and should not be treated as one.

CREATE TABLE admin_user_profile (
    id            UUID PRIMARY KEY,
    admin_user_id UUID         NOT NULL UNIQUE REFERENCES admin_user (id) ON DELETE CASCADE,
    date_of_birth DATE,
    gender        VARCHAR(20),
    address_line  VARCHAR(255),
    pincode       VARCHAR(10),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(100) DEFAULT 'system',
    updated_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE admin_user_kyc (
    id             UUID PRIMARY KEY,
    admin_user_id  UUID         NOT NULL UNIQUE REFERENCES admin_user (id) ON DELETE CASCADE,
    aadhaar_number VARCHAR(20),
    pan_number     VARCHAR(20),
    uan_number     VARCHAR(20),
    pf_number      VARCHAR(50),
    esic_number    VARCHAR(30),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(100) DEFAULT 'system',
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE admin_user_bank_detail (
    id                  UUID PRIMARY KEY,
    admin_user_id       UUID         NOT NULL UNIQUE REFERENCES admin_user (id) ON DELETE CASCADE,
    account_holder_name VARCHAR(150),
    bank_name           VARCHAR(150),
    account_number      VARCHAR(30),
    ifsc_code           VARCHAR(15),
    branch_name         VARCHAR(150),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(100) DEFAULT 'system',
    updated_by          VARCHAR(100),
    version             BIGINT       NOT NULL DEFAULT 0
);
