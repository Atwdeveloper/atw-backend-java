-- V31__permission_catalog_cleanup.sql
-- Removes permission codes for legacy modules that were never built in this rebuild (companies,
-- jobs/vacancy, payroll, reports, vendor, vendor_orders, dress_orders, agent, referrals, support,
-- configuration, notifications, dashboard - ported speculatively from legacy's full catalog in
-- V4__identity.sql, never wired to any controller since), plus dead codes inside modules that DO
-- exist: workers.view_documents/view_wallet/manage_wallet_hold/withdraw_wallet (the admin
-- document-review workflow and the wallet module were never built/were removed - see
-- V24__kyc_aadhaar_ekyc_intake.sql), kyc.review (same document-review removal), minimum_wage.edit/
-- delete (minimum wage rates are append-only by design - no such endpoints exist), and
-- face_enroll.view/enroll (superseded by faceauth.view/enroll, a leftover duplicate from before the
-- faceauth module existed). Verified dead by grepping every @authz.has(...)/permissionChecker.has(...)
-- call in the codebase - none of these codes are referenced anywhere, and none are currently granted
-- to any role, so this changes zero admin's actual effective access.
--
-- admin_role_permission.permission_id is ON DELETE CASCADE, so any (now-impossible) grants of these
-- codes would be cleaned up automatically; there are none to clean up here.
DELETE FROM admin_permission WHERE code IN (
    'dashboard.view',
    'companies.view', 'companies.create', 'companies.edit', 'companies.delete', 'companies.toggle_status',
    'companies.view_jobs', 'companies.view_documents', 'companies.manage_company_workers', 'companies.export',
    'face_enroll.view', 'face_enroll.enroll',
    'configuration.view', 'configuration.edit_username', 'configuration.edit_email',
    'configuration.edit_site_settings', 'configuration.manage_documents',
    'notifications.view',
    'jobs.view', 'jobs.create', 'jobs.edit', 'jobs.delete', 'jobs.toggle_status',
    'reports.pf_reports', 'reports.esic_reports', 'reports.invoice_reports', 'reports.wallet_transactions',
    'reports.wallet_entries', 'reports.worker_payouts', 'reports.job_vacancy_report', 'reports.earnings_report',
    'reports.worker_registration_report', 'reports.company_registration_report', 'reports.salary_sheets',
    'dress_orders.view', 'dress_orders.create', 'dress_orders.edit', 'dress_orders.delete', 'dress_orders.manage_status',
    'agent.view', 'agent.create', 'agent.edit', 'agent.delete', 'agent.toggle_status',
    'vendor.view', 'vendor.create', 'vendor.edit', 'vendor.delete', 'vendor.toggle_status',
    'vendor_orders.view', 'vendor_orders.update_status',
    'payroll.view', 'payroll.view_deductions', 'payroll.manage_deductions', 'payroll.view_salary_structures',
    'payroll.create_salary_structures', 'payroll.edit_salary_structures', 'payroll.delete_salary_structures',
    'referrals.view', 'referrals.manage',
    'vacancy.view', 'vacancy.create', 'vacancy.edit', 'vacancy.delete', 'vacancy.toggle_status',
    'vacancy.view_applied_workers', 'vacancy.add_worker', 'vacancy.manage_worker_status', 'vacancy.attendance',
    'support.view',
    'workers.view_documents', 'workers.view_wallet', 'workers.manage_wallet_hold', 'workers.withdraw_wallet',
    'kyc.review',
    'minimum_wage.edit', 'minimum_wage.delete'
);

-- New codes for this round's additions (see WorkerBankDetailService/AdminWorkerController and the
-- new face-identify endpoint). Deliberately NOT auto-granted to any existing role, matching how
-- kyc.view_sensitive/faceauth.* were introduced: a brand-new sensitive capability stays accessible
-- only to the super-admin bypass until a developer deliberately assigns it via the Roles screen.
INSERT INTO admin_permission (id, code, module, action, label) VALUES
    (gen_random_uuid(), 'workers.view_bank_details', 'workers', 'view_bank_details',
     'View unmasked worker bank account numbers'),
    (gen_random_uuid(), 'faceauth.identify', 'faceauth', 'identify',
     'Identify which worker a face photo belongs to (1:N search)');
