-- V10__admin_staff_invite_email_template.sql
-- Email sent when a new staff/admin account is created via the Staff administration API. The new
-- account is given a random, never-disclosed password at creation (see StaffService.create) and
-- must set its own via this link - the same AuthTokenType.INVITE lifecycle the identity module's
-- auth_token table already anticipated, consumed by POST /api/v1/admin/auth/accept-invite.

INSERT INTO email_template (id, code, subject, body_html, locale, active, description, created_at, updated_at, created_by, version)
VALUES (
    gen_random_uuid(),
    'ADMIN_STAFF_INVITE',
    'You have been invited to Anytime Work admin',
    '<p>Hi [[${name}]],</p><p>An administrator account has been created for you on Anytime Work. Use the link below (valid for [[${hours}]] hours) to set your password and sign in:</p><p><a th:href="${inviteUrl}">Set your password</a></p><p>If you were not expecting this, you can ignore this email.</p>',
    'en', TRUE, 'New staff/admin account invite — set initial password', now(), now(), 'system', 0
);
