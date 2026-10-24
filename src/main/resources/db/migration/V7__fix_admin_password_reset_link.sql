-- V7__fix_admin_password_reset_link.sql
-- ADMIN_PASSWORD_RESET's link used Thymeleaf's *text-inlining* syntax ([( )]) inside a plain
-- href attribute: href="[(${resetUrl})]". Inlining only processes text NODES (the content between
-- tags, e.g. the [[${name}]] greeting), never attribute VALUES, so this was never evaluated - every
-- password-reset email has been going out with a dead link containing that literal placeholder
-- instead of the real URL. Fixed with `th:href`, the attribute processor Thymeleaf actually
-- evaluates for attribute values.

UPDATE email_template
SET body_html = '<p>Hi [[${name}]],</p><p>We received a request to reset your password. Use the link below (valid for [[${minutes}]] minutes):</p><p><a th:href="${resetUrl}">Reset password</a></p><p>If you did not request this, you can ignore this email.</p>',
    updated_at = now(),
    updated_by = 'system',
    version    = version + 1
WHERE code = 'ADMIN_PASSWORD_RESET';
