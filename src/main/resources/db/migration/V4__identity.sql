-- V4__identity.sql
-- Admin identity & access: accounts, normalized RBAC (permissions + roles), auth tokens, audit.
-- Audit columns carry DB defaults so seed INSERTs below can omit them; the app (JPA auditing)
-- always supplies them explicitly at runtime.

CREATE TABLE admin_user (
    id                    UUID PRIMARY KEY,
    username              VARCHAR(100) UNIQUE,
    email                 VARCHAR(320) NOT NULL UNIQUE,
    password_hash         VARCHAR(100) NOT NULL,
    phone                 VARCHAR(20),
    full_name             VARCHAR(150) NOT NULL,
    is_super_admin        BOOLEAN      NOT NULL DEFAULT FALSE,
    status                VARCHAR(20)  NOT NULL,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(100) DEFAULT 'system',
    updated_by            VARCHAR(100),
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE permission (
    id         UUID PRIMARY KEY,
    code       VARCHAR(100) NOT NULL UNIQUE,
    module     VARCHAR(50)  NOT NULL,
    action     VARCHAR(50)  NOT NULL,
    label      VARCHAR(150),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100),
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_permission_module ON permission (module);

CREATE TABLE role (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(300),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100) DEFAULT 'system',
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE admin_user_role (
    admin_user_id UUID NOT NULL REFERENCES admin_user (id) ON DELETE CASCADE,
    role_id       UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    PRIMARY KEY (admin_user_id, role_id)
);

CREATE TABLE refresh_token (
    id             UUID PRIMARY KEY,
    admin_user_id  UUID        NOT NULL REFERENCES admin_user (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID,
    user_agent     VARCHAR(400),
    ip_address     VARCHAR(45),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     VARCHAR(100) DEFAULT 'system',
    updated_by     VARCHAR(100),
    version        BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_refresh_token_admin ON refresh_token (admin_user_id);

CREATE TABLE admin_otp (
    id            UUID PRIMARY KEY,
    admin_user_id UUID         NOT NULL REFERENCES admin_user (id) ON DELETE CASCADE,
    channel       VARCHAR(20)  NOT NULL,
    code_hash     VARCHAR(100) NOT NULL,
    purpose       VARCHAR(30)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ,
    attempts      INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(100) DEFAULT 'system',
    updated_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_admin_otp_admin ON admin_otp (admin_user_id);

CREATE TABLE auth_token (
    id            UUID PRIMARY KEY,
    admin_user_id UUID        NOT NULL REFERENCES admin_user (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    type          VARCHAR(30) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    VARCHAR(100) DEFAULT 'system',
    updated_by    VARCHAR(100),
    version       BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE admin_audit_log (
    id             UUID PRIMARY KEY,
    actor_admin_id UUID REFERENCES admin_user (id) ON DELETE SET NULL,
    actor_type     VARCHAR(20) NOT NULL,
    action         VARCHAR(60) NOT NULL,
    subject_type   VARCHAR(60),
    subject_id     VARCHAR(100),
    detail         TEXT,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(400),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     VARCHAR(100) DEFAULT 'system',
    updated_by     VARCHAR(100),
    version        BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_admin_audit_actor ON admin_audit_log (actor_admin_id);
CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at);

-- ---------------------------------------------------------------------------
-- Seed: permission catalog (ported from legacy config/admin_permissions.php, teams.* excluded).
-- ---------------------------------------------------------------------------
INSERT INTO permission (id, code, module, action, label) VALUES
(gen_random_uuid(), 'dashboard.view', 'dashboard', 'view', 'view'),
(gen_random_uuid(), 'companies.view', 'companies', 'view', 'view'),
(gen_random_uuid(), 'companies.create', 'companies', 'create', 'create'),
(gen_random_uuid(), 'companies.edit', 'companies', 'edit', 'edit'),
(gen_random_uuid(), 'companies.delete', 'companies', 'delete', 'delete'),
(gen_random_uuid(), 'companies.toggle_status', 'companies', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'companies.view_jobs', 'companies', 'view_jobs', 'view_jobs'),
(gen_random_uuid(), 'companies.view_documents', 'companies', 'view_documents', 'view_documents'),
(gen_random_uuid(), 'companies.manage_company_workers', 'companies', 'manage_company_workers', 'manage_company_workers'),
(gen_random_uuid(), 'companies.export', 'companies', 'export', 'export'),
(gen_random_uuid(), 'workers.view', 'workers', 'view', 'view'),
(gen_random_uuid(), 'workers.create', 'workers', 'create', 'create'),
(gen_random_uuid(), 'workers.edit', 'workers', 'edit', 'edit'),
(gen_random_uuid(), 'workers.delete', 'workers', 'delete', 'delete'),
(gen_random_uuid(), 'workers.toggle_status', 'workers', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'workers.view_documents', 'workers', 'view_documents', 'view_documents'),
(gen_random_uuid(), 'workers.view_comments', 'workers', 'view_comments', 'view_comments'),
(gen_random_uuid(), 'workers.view_wallet', 'workers', 'view_wallet', 'view_wallet'),
(gen_random_uuid(), 'workers.manage_wallet_hold', 'workers', 'manage_wallet_hold', 'manage_wallet_hold'),
(gen_random_uuid(), 'workers.withdraw_wallet', 'workers', 'withdraw_wallet', 'withdraw_wallet'),
(gen_random_uuid(), 'workers.export', 'workers', 'export', 'export'),
(gen_random_uuid(), 'face_enroll.view', 'face_enroll', 'view', 'view'),
(gen_random_uuid(), 'face_enroll.enroll', 'face_enroll', 'enroll', 'enroll'),
(gen_random_uuid(), 'configuration.view', 'configuration', 'view', 'view'),
(gen_random_uuid(), 'configuration.edit_username', 'configuration', 'edit_username', 'edit_username'),
(gen_random_uuid(), 'configuration.edit_email', 'configuration', 'edit_email', 'edit_email'),
(gen_random_uuid(), 'configuration.edit_site_settings', 'configuration', 'edit_site_settings', 'edit_site_settings'),
(gen_random_uuid(), 'configuration.manage_documents', 'configuration', 'manage_documents', 'manage_documents'),
(gen_random_uuid(), 'notifications.view', 'notifications', 'view', 'view'),
(gen_random_uuid(), 'jobs.view', 'jobs', 'view', 'view'),
(gen_random_uuid(), 'jobs.create', 'jobs', 'create', 'create'),
(gen_random_uuid(), 'jobs.edit', 'jobs', 'edit', 'edit'),
(gen_random_uuid(), 'jobs.delete', 'jobs', 'delete', 'delete'),
(gen_random_uuid(), 'jobs.toggle_status', 'jobs', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'reports.pf_reports', 'reports', 'pf_reports', 'pf_reports'),
(gen_random_uuid(), 'reports.esic_reports', 'reports', 'esic_reports', 'esic_reports'),
(gen_random_uuid(), 'reports.invoice_reports', 'reports', 'invoice_reports', 'invoice_reports'),
(gen_random_uuid(), 'reports.wallet_transactions', 'reports', 'wallet_transactions', 'wallet_transactions'),
(gen_random_uuid(), 'reports.wallet_entries', 'reports', 'wallet_entries', 'wallet_entries'),
(gen_random_uuid(), 'reports.worker_payouts', 'reports', 'worker_payouts', 'worker_payouts'),
(gen_random_uuid(), 'reports.job_vacancy_report', 'reports', 'job_vacancy_report', 'job_vacancy_report'),
(gen_random_uuid(), 'reports.earnings_report', 'reports', 'earnings_report', 'earnings_report'),
(gen_random_uuid(), 'reports.worker_registration_report', 'reports', 'worker_registration_report', 'worker_registration_report'),
(gen_random_uuid(), 'reports.company_registration_report', 'reports', 'company_registration_report', 'company_registration_report'),
(gen_random_uuid(), 'reports.salary_sheets', 'reports', 'salary_sheets', 'salary_sheets'),
(gen_random_uuid(), 'staff.view', 'staff', 'view', 'view'),
(gen_random_uuid(), 'staff.create', 'staff', 'create', 'create'),
(gen_random_uuid(), 'staff.edit', 'staff', 'edit', 'edit'),
(gen_random_uuid(), 'staff.delete', 'staff', 'delete', 'delete'),
(gen_random_uuid(), 'staff.toggle_status', 'staff', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'roles.view', 'roles', 'view', 'view'),
(gen_random_uuid(), 'roles.create', 'roles', 'create', 'create'),
(gen_random_uuid(), 'roles.edit', 'roles', 'edit', 'edit'),
(gen_random_uuid(), 'roles.delete', 'roles', 'delete', 'delete'),
(gen_random_uuid(), 'dress_orders.view', 'dress_orders', 'view', 'view'),
(gen_random_uuid(), 'dress_orders.create', 'dress_orders', 'create', 'create'),
(gen_random_uuid(), 'dress_orders.edit', 'dress_orders', 'edit', 'edit'),
(gen_random_uuid(), 'dress_orders.delete', 'dress_orders', 'delete', 'delete'),
(gen_random_uuid(), 'dress_orders.manage_status', 'dress_orders', 'manage_status', 'manage_status'),
(gen_random_uuid(), 'agent.view', 'agent', 'view', 'view'),
(gen_random_uuid(), 'agent.create', 'agent', 'create', 'create'),
(gen_random_uuid(), 'agent.edit', 'agent', 'edit', 'edit'),
(gen_random_uuid(), 'agent.delete', 'agent', 'delete', 'delete'),
(gen_random_uuid(), 'agent.toggle_status', 'agent', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'industry.view', 'industry', 'view', 'view'),
(gen_random_uuid(), 'industry.create', 'industry', 'create', 'create'),
(gen_random_uuid(), 'industry.edit', 'industry', 'edit', 'edit'),
(gen_random_uuid(), 'industry.delete', 'industry', 'delete', 'delete'),
(gen_random_uuid(), 'industry.toggle_status', 'industry', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'designation.view', 'designation', 'view', 'view'),
(gen_random_uuid(), 'designation.create', 'designation', 'create', 'create'),
(gen_random_uuid(), 'designation.edit', 'designation', 'edit', 'edit'),
(gen_random_uuid(), 'designation.delete', 'designation', 'delete', 'delete'),
(gen_random_uuid(), 'designation.toggle_status', 'designation', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'skill.view', 'skill', 'view', 'view'),
(gen_random_uuid(), 'skill.create', 'skill', 'create', 'create'),
(gen_random_uuid(), 'skill.edit', 'skill', 'edit', 'edit'),
(gen_random_uuid(), 'skill.delete', 'skill', 'delete', 'delete'),
(gen_random_uuid(), 'skill.toggle_status', 'skill', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'skill.export', 'skill', 'export', 'export'),
(gen_random_uuid(), 'minimum_wage.view', 'minimum_wage', 'view', 'view'),
(gen_random_uuid(), 'minimum_wage.create', 'minimum_wage', 'create', 'create'),
(gen_random_uuid(), 'minimum_wage.edit', 'minimum_wage', 'edit', 'edit'),
(gen_random_uuid(), 'minimum_wage.delete', 'minimum_wage', 'delete', 'delete'),
(gen_random_uuid(), 'minimum_wage.toggle_status', 'minimum_wage', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'skill_category.view', 'skill_category', 'view', 'view'),
(gen_random_uuid(), 'skill_category.create', 'skill_category', 'create', 'create'),
(gen_random_uuid(), 'skill_category.edit', 'skill_category', 'edit', 'edit'),
(gen_random_uuid(), 'skill_category.delete', 'skill_category', 'delete', 'delete'),
(gen_random_uuid(), 'skill_category.toggle_status', 'skill_category', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'vendor.view', 'vendor', 'view', 'view'),
(gen_random_uuid(), 'vendor.create', 'vendor', 'create', 'create'),
(gen_random_uuid(), 'vendor.edit', 'vendor', 'edit', 'edit'),
(gen_random_uuid(), 'vendor.delete', 'vendor', 'delete', 'delete'),
(gen_random_uuid(), 'vendor.toggle_status', 'vendor', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'vendor_orders.view', 'vendor_orders', 'view', 'view'),
(gen_random_uuid(), 'vendor_orders.update_status', 'vendor_orders', 'update_status', 'update_status'),
(gen_random_uuid(), 'payroll.view', 'payroll', 'view', 'view'),
(gen_random_uuid(), 'payroll.view_deductions', 'payroll', 'view_deductions', 'view_deductions'),
(gen_random_uuid(), 'payroll.manage_deductions', 'payroll', 'manage_deductions', 'manage_deductions'),
(gen_random_uuid(), 'payroll.view_salary_structures', 'payroll', 'view_salary_structures', 'view_salary_structures'),
(gen_random_uuid(), 'payroll.create_salary_structures', 'payroll', 'create_salary_structures', 'create_salary_structures'),
(gen_random_uuid(), 'payroll.edit_salary_structures', 'payroll', 'edit_salary_structures', 'edit_salary_structures'),
(gen_random_uuid(), 'payroll.delete_salary_structures', 'payroll', 'delete_salary_structures', 'delete_salary_structures'),
(gen_random_uuid(), 'referrals.view', 'referrals', 'view', 'view'),
(gen_random_uuid(), 'referrals.manage', 'referrals', 'manage', 'manage'),
(gen_random_uuid(), 'vacancy.view', 'vacancy', 'view', 'view'),
(gen_random_uuid(), 'vacancy.create', 'vacancy', 'create', 'create'),
(gen_random_uuid(), 'vacancy.edit', 'vacancy', 'edit', 'edit'),
(gen_random_uuid(), 'vacancy.delete', 'vacancy', 'delete', 'delete'),
(gen_random_uuid(), 'vacancy.toggle_status', 'vacancy', 'toggle_status', 'toggle_status'),
(gen_random_uuid(), 'vacancy.view_applied_workers', 'vacancy', 'view_applied_workers', 'view_applied_workers'),
(gen_random_uuid(), 'vacancy.add_worker', 'vacancy', 'add_worker', 'add_worker'),
(gen_random_uuid(), 'vacancy.manage_worker_status', 'vacancy', 'manage_worker_status', 'manage_worker_status'),
(gen_random_uuid(), 'vacancy.attendance', 'vacancy', 'attendance', 'attendance'),
(gen_random_uuid(), 'support.view', 'support', 'view', 'view');

-- ---------------------------------------------------------------------------
-- Seed: roles (legacy permission profiles, editable; super-admin is a user flag, not a role).
-- ---------------------------------------------------------------------------
INSERT INTO role (id, name, description, is_system) VALUES
(gen_random_uuid(), 'Sub Admin', 'Assists with daily operations: manage companies, workers, staff, vacancies with limited administrative access.', FALSE),
(gen_random_uuid(), 'Relationship Manager', 'Client retention and business development; manages companies, workers, vacancies.', FALSE),
(gen_random_uuid(), 'Senior Developer', 'Full operational access across modules including configuration, payroll, reports and face enrollment.', FALSE),
(gen_random_uuid(), 'Junior Developer', 'Full operational access across modules (excluding face enrollment).', FALSE);

-- Role -> permissions (by code), preserving the legacy profile ability sets (teams.* excluded).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
  'dashboard.view','companies.view','companies.create','companies.edit','companies.toggle_status',
  'workers.view','workers.create','workers.edit','workers.toggle_status','workers.view_comments',
  'notifications.view','jobs.view','jobs.create','jobs.edit','staff.view','staff.create','staff.edit',
  'roles.view','roles.create','dress_orders.view','dress_orders.create','dress_orders.edit',
  'agent.view','agent.create','agent.edit','industry.view','industry.create','industry.edit',
  'designation.view','designation.create','designation.edit','vendor.view','vendor.create','vendor.edit',
  'vendor_orders.view','vacancy.view','vacancy.create','vacancy.edit','vacancy.view_applied_workers',
  'vacancy.add_worker','support.view'
) WHERE r.name = 'Sub Admin';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
  'dashboard.view','companies.view','companies.create','companies.edit','workers.view','workers.create',
  'workers.edit','workers.view_documents','notifications.view','jobs.view','jobs.create','jobs.edit',
  'staff.view','staff.create','staff.edit','agent.view','agent.create','agent.edit','industry.view',
  'industry.create','industry.edit','designation.view','designation.create','designation.edit',
  'vendor.view','vendor.create','vendor.edit','vacancy.view','vacancy.create','vacancy.edit','support.view'
) WHERE r.name = 'Relationship Manager';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
  'dashboard.view','companies.view','companies.create','companies.edit','companies.delete','companies.toggle_status',
  'companies.view_jobs','companies.view_documents','companies.manage_company_workers','companies.export',
  'workers.view','workers.create','workers.edit','workers.delete','workers.toggle_status','workers.view_documents',
  'workers.view_comments','workers.view_wallet','workers.manage_wallet_hold','workers.withdraw_wallet','workers.export',
  'face_enroll.view','face_enroll.enroll','configuration.view','configuration.edit_username','configuration.edit_email',
  'configuration.edit_site_settings','configuration.manage_documents','notifications.view','jobs.view','jobs.create',
  'jobs.edit','jobs.delete','jobs.toggle_status','reports.pf_reports','reports.esic_reports','reports.invoice_reports',
  'reports.wallet_transactions','reports.wallet_entries','reports.worker_payouts','reports.job_vacancy_report',
  'reports.earnings_report','reports.worker_registration_report','reports.company_registration_report','reports.salary_sheets',
  'staff.view','staff.create','staff.edit','staff.delete','staff.toggle_status','roles.view','roles.create','roles.edit',
  'roles.delete','dress_orders.view','dress_orders.create','dress_orders.edit','dress_orders.delete','dress_orders.manage_status',
  'agent.view','agent.create','agent.edit','agent.delete','agent.toggle_status','industry.view','industry.create',
  'industry.edit','industry.delete','industry.toggle_status','designation.view','designation.create','designation.edit',
  'designation.delete','designation.toggle_status','skill.view','skill.create','skill.edit','skill.delete',
  'skill.toggle_status','skill.export','vendor.view','vendor.create','vendor.edit','vendor.delete','vendor.toggle_status',
  'vendor_orders.view','vendor_orders.update_status','payroll.view','payroll.view_deductions','payroll.manage_deductions',
  'payroll.view_salary_structures','payroll.create_salary_structures','payroll.edit_salary_structures',
  'payroll.delete_salary_structures','referrals.view','referrals.manage','vacancy.view','vacancy.create','vacancy.edit',
  'vacancy.delete','vacancy.toggle_status','vacancy.view_applied_workers','vacancy.add_worker','vacancy.manage_worker_status',
  'vacancy.attendance','support.view'
) WHERE r.name = 'Senior Developer';

-- Junior Developer = Senior Developer minus face_enroll.*
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
  'dashboard.view','companies.view','companies.create','companies.edit','companies.delete','companies.toggle_status',
  'companies.view_jobs','companies.view_documents','companies.manage_company_workers','companies.export',
  'workers.view','workers.create','workers.edit','workers.delete','workers.toggle_status','workers.view_documents',
  'workers.view_comments','workers.view_wallet','workers.manage_wallet_hold','workers.withdraw_wallet','workers.export',
  'configuration.view','configuration.edit_username','configuration.edit_email','configuration.edit_site_settings',
  'configuration.manage_documents','notifications.view','jobs.view','jobs.create','jobs.edit','jobs.delete','jobs.toggle_status',
  'reports.pf_reports','reports.esic_reports','reports.invoice_reports','reports.wallet_transactions','reports.wallet_entries',
  'reports.worker_payouts','reports.job_vacancy_report','reports.earnings_report','reports.worker_registration_report',
  'reports.company_registration_report','reports.salary_sheets','staff.view','staff.create','staff.edit','staff.delete',
  'staff.toggle_status','roles.view','roles.create','roles.edit','roles.delete','dress_orders.view','dress_orders.create',
  'dress_orders.edit','dress_orders.delete','dress_orders.manage_status','agent.view','agent.create','agent.edit',
  'agent.delete','agent.toggle_status','industry.view','industry.create','industry.edit','industry.delete',
  'industry.toggle_status','designation.view','designation.create','designation.edit','designation.delete',
  'designation.toggle_status','skill.view','skill.create','skill.edit','skill.delete','skill.toggle_status','skill.export',
  'vendor.view','vendor.create','vendor.edit','vendor.delete','vendor.toggle_status','vendor_orders.view',
  'vendor_orders.update_status','payroll.view','payroll.view_deductions','payroll.manage_deductions',
  'payroll.view_salary_structures','payroll.create_salary_structures','payroll.edit_salary_structures',
  'payroll.delete_salary_structures','referrals.view','referrals.manage','vacancy.view','vacancy.create','vacancy.edit',
  'vacancy.delete','vacancy.toggle_status','vacancy.view_applied_workers','vacancy.add_worker','vacancy.manage_worker_status',
  'vacancy.attendance','support.view'
) WHERE r.name = 'Junior Developer';

-- ---------------------------------------------------------------------------
-- Seed: admin auth email templates (Thymeleaf; rendered by the notification module).
-- ---------------------------------------------------------------------------
INSERT INTO email_template (id, code, subject, body_html, locale, active, description, created_at, updated_at, created_by, version)
VALUES
(gen_random_uuid(), 'ADMIN_LOGIN_OTP',
 'Your Anytime Work admin login code',
 '<p>Hi [[${name}]],</p><p>Your login verification code is <strong>[[${otp}]]</strong>. It expires in [[${minutes}]] minutes.</p><p>If you did not try to sign in, please ignore this email.</p>',
 'en', TRUE, 'Admin login 2FA one-time code', now(), now(), 'system', 0),
(gen_random_uuid(), 'ADMIN_PASSWORD_RESET',
 'Reset your Anytime Work admin password',
 '<p>Hi [[${name}]],</p><p>We received a request to reset your password. Use the link below (valid for [[${minutes}]] minutes):</p><p><a href="[(${resetUrl})]">Reset password</a></p><p>If you did not request this, you can ignore this email.</p>',
 'en', TRUE, 'Admin password reset link', now(), now(), 'system', 0),
(gen_random_uuid(), 'ADMIN_PASSWORD_CHANGED',
 'Your Anytime Work admin password was changed',
 '<p>Hi [[${name}]],</p><p>This is a confirmation that your admin account password was just changed. If this was not you, contact your administrator immediately.</p>',
 'en', TRUE, 'Admin password-changed security notice', now(), now(), 'system', 0);
