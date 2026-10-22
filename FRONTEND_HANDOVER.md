# ATW Backend — Frontend Integration Guide

Everything the admin-panel frontend needs to integrate with the backend: base URL, the auth flow
(including two new security behaviors you must handle), every endpoint with real request/response
examples, and the full error-code reference.

A ready-to-import Postman collection + environment live in `postman/` in this repo
(`ATW-Backend.postman_collection.json`, `ATW-Local.postman_environment.json`) — every request in it
matches this document exactly, and the Login/Verify OTP/Refresh requests auto-save tokens into the
environment via their Tests scripts, so you can click through the whole flow without copy-pasting
tokens by hand.

All examples below were captured against a real running instance of this exact codebase, not written
from the spec — the shapes are exact.

---

## 1. Base URL

| Environment | Base URL |
|---|---|
| Local dev | `http://localhost:8080` |
| Azure (once deployed) | *(ask backend/DevOps for the Container App's public URL)* |

Live, interactive API docs are also always available directly from the running server:
`GET /swagger-ui.html` and the raw OpenAPI spec at `GET /v3/api-docs`.

---

## 2. Response envelope — every endpoint, always

```json
// success
{ "success": true, "data": { /* payload, shape varies per endpoint */ } }

// failure
{ "success": false, "error": { "code": "VALIDATION_ERROR", "message": "...", "fieldErrors": [...], "timestamp": "...", "traceId": "..." } }
```

- `error.code` is the stable, machine-readable string to `switch` on — never parse `error.message`
  to decide behavior (it's for display only).
- `error.fieldErrors` is only present on `VALIDATION_ERROR` from a request body: an array of
  `{field, message}`.
- `error.traceId` — include this if a user reports a bug; it's in the server logs too.

---

## 3. Auth flow

### 3.1 Login is two steps (password, then email OTP)

```
POST /api/admin/auth/login  {usernameOrEmail, password}
     -> {otpRequired: true, challengeId}        (the normal case — 2FA is on by default)
     -> {otpRequired: false, tokens: {...}}      (only if the backend has OTP disabled)

POST /api/admin/auth/verify-otp  {challengeId, otp}
     -> {accessToken, tokenType, expiresIn, refreshToken, admin: {...}}
```

When `otpRequired` is `true`, show an OTP-entry screen and call `verify-otp` with the code the admin
received by email. **Check `admin.mustChangePassword` immediately** on the response from either
`verify-otp` or a direct login — see 3.3.

### 3.2 Using the access token

Attach it to every other request: `Authorization: Bearer <accessToken>`.

- Lifetime is short (**15 minutes** by default) — call `/api/admin/auth/refresh` when you get a 401
  `UNAUTHENTICATED` and the refresh token is still presumably live, then retry the original request
  once with the new access token.
- `refresh` **rotates**: it returns a *new* refresh token and immediately invalidates the one you
  sent. Always overwrite your stored refresh token with the new one — reusing an old one after it's
  been rotated returns 401 `UNAUTHORIZED` ("Refresh token has expired or been revoked").
- A refresh token lives 7 days by default, but logging out, a password change/reset, or an admin
  being disabled revokes it immediately regardless of that expiry.

### 3.3 `mustChangePassword` — a hard gate, not a suggestion

If `admin.mustChangePassword` is `true` (set for a newly-bootstrapped super-admin, and reusable for
future "force password change" cases), **every endpoint except these two returns 403
`MUST_CHANGE_PASSWORD`** until the password is changed:
- `GET /api/admin/account/me`
- `POST /api/admin/account/change-password`

Frontend behavior: on login/verify-otp, if `mustChangePassword` is true, redirect straight to a
change-password screen and block navigation elsewhere until `change-password` succeeds. Don't rely on
catching the 403 reactively everywhere — check the flag proactively right after auth.

### 3.4 `SESSION_REVOKED` — treat exactly like a 401, log the user out

A **valid, unexpired, correctly-signed** access token can still be rejected with 401
`SESSION_REVOKED` if, since it was issued: the admin changed/reset their password, or the admin's
account was disabled. This is deliberate — it's what lets a password change immediately invalidate
every other device's session instead of waiting up to 15 minutes. Handle it exactly like
`UNAUTHENTICATED`: clear local tokens and send the user to the login screen (do **not** try to
silently refresh — the refresh token was revoked too).

### 3.5 Forgot / reset password

```
POST /api/admin/auth/forgot-password  {email}
     -> always {success: true} (never reveals whether the email exists — don't message otherwise)

(admin clicks the emailed link: {FRONTEND_BASE_URL}/admin/reset-password?token=<token>)
Your route should read `token` from the query string and POST it below.

POST /api/admin/auth/reset-password  {token, newPassword}
     -> {success: true}
```

On success, every other session for that admin (every device's access + refresh token), every other
outstanding reset link, and any pending login-OTP for that admin are all invalidated too — not just
the one link used.

### 3.6 Logout

```
POST /api/admin/auth/logout  {refreshToken}
     -> always {success: true}, even if the token was already invalid
```

Also discard the access token client-side immediately — the backend can't force-expire it faster
than the `SESSION_REVOKED` mechanism above would (which logout doesn't trigger), so it stays
technically valid until it naturally expires (≤15 min). For admin-initiated logout this residual
window is accepted; it is not a concern for a user-initiated "log me out."

---

## 4. Endpoint reference

### Auth — `/api/admin/auth/**` (public, no token)

| Method & path | Body | Success (200) |
|---|---|---|
| `POST /login` | `{usernameOrEmail, password}` | `{otpRequired, challengeId?, tokens?}` |
| `POST /verify-otp` | `{challengeId, otp}` | `AuthTokens` (see 4.1) |
| `POST /resend-otp` | `{challengeId}` | `{challengeId}` — **new** challenge id; the old one stops working immediately |
| `POST /refresh` | `{refreshToken}` | `AuthTokens` |
| `POST /logout` | `{refreshToken}` | `{success:true}` |
| `POST /forgot-password` | `{email}` | `{success:true}` (always) |
| `POST /reset-password` | `{token, newPassword}` | `{success:true}` |

#### 4.1 `AuthTokens` shape (from `verify-otp` and `refresh`)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "9snFfxiOXlAuP1uh_zT664rYZT17xOoZ1KiSX8OYs8c",
  "admin": {
    "id": "72a4b2bf-b03e-43c7-ba99-6e042639b7e5",
    "username": "superadmin",
    "email": "superadmin@anytimework.in",
    "fullName": "Platform Super Admin",
    "superAdmin": true,
    "roles": [],
    "authorities": ["SUPER_ADMIN"],
    "mustChangePassword": false
  }
}
```

A non-super-admin's `authorities` is a real list of permission codes (e.g.
`["staff.view","roles.view",...]`) instead of `["SUPER_ADMIN"]`; `roles` lists their assigned role
names.

### Account — `/api/admin/account/**` (requires `Authorization: Bearer`)

| Method & path | Body | Success (200) | Notes |
|---|---|---|---|
| `GET /me` | — | `AdminProfile` (same shape as `AuthTokens.admin`) | Always allowed, even under `mustChangePassword` |
| `GET /my-permissions` | — | `string[]` | `["*"]` for a super-admin — treat as all-access, don't match it against a code list |
| `POST /change-password` | `{currentPassword, newPassword}` | `{success:true}` | **Invalidates the current access token immediately** — see 3.4. Re-authenticate right after. |

### Admin — permission-gated

| Method & path | Permission required | Body | Success (200) |
|---|---|---|---|
| `GET /api/admin/permissions` | `roles.view` | — | `[{module, permissions:[{code, action, label}]}]` — for building a role-editor matrix |

### Health — public

| Method & path | Notes |
|---|---|
| `GET /actuator/health` | `{"status":"UP"}`. Component detail (db/redis/mail) only shows to an authenticated caller. |

---

## 5. Error code reference

| HTTP | `error.code` | When | Frontend handling |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Bad request body (annotation or business validation) | Show `fieldErrors` inline next to the relevant field(s) |
| 400 | `MALFORMED_REQUEST` | Missing/unparseable JSON body | Bug on your side — check the request |
| 401 | `UNAUTHENTICATED` | Missing, malformed, expired, or bad-signature access token | Try `/refresh`; if that also fails, go to login |
| 401 | `UNAUTHORIZED` | Login credential failure, or a dead/revoked/reused refresh token | Show the message (safe to display); for refresh failures, go to login |
| 401 | `SESSION_REVOKED` | Token signature is fine, but the session was killed elsewhere (password changed/reset, account disabled) | **Log out immediately** (3.4) — do not try to refresh |
| 403 | `ACCESS_DENIED` | Authenticated but missing the required permission | Hide/disable the triggering UI; this means a UI bug if the user shouldn't have seen the action at all |
| 403 | `MUST_CHANGE_PASSWORD` | Must change password before using anything else | Redirect to change-password (3.3) |
| 404 | `RESOURCE_NOT_FOUND` | A referenced record doesn't exist | |
| 404 | `NOT_FOUND` | No such route | Likely a frontend URL typo |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method | |
| 409 | `CONFLICT` | Duplicate/conflicting state | |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Wrong/missing `Content-Type` | Always send `application/json` |
| 422 | `BUSINESS_RULE_VIOLATION` | A business rule, not raw validation (e.g. "too many invalid OTP attempts", "please wait before requesting another code", "too many resend attempts") | Show `error.message` directly — it's written to be user-facing |
| 500 | `INTERNAL_ERROR` | Unexpected server error | Generic "something went wrong," and report `traceId` if you have a bug-report channel |

### Worth knowing: OTP and password-reset limits (so you can design the UI around them)

- Wrong OTP codes are capped (**5 by default**) — the 6th+ attempt, even with the *correct* code,
  returns 422 `BUSINESS_RULE_VIOLATION` until the admin requests a brand-new code via resend or a
  fresh login.
- Resending an OTP has a cooldown (**60s by default**) and a cap on total resends per login attempt
  (**3 by default**) — both surface as 422 `BUSINESS_RULE_VIOLATION`. Disable the "resend" button for
  the cooldown period client-side as a UX nicety (the server enforces it regardless).
- A password-reset link is valid for **30 minutes** by default and single-use.

---

## 6. Things that changed recently (if you integrated against an earlier version)

- `admin.mustChangePassword` is a new field on every profile/token response — handle it (3.3).
- `SESSION_REVOKED` is a new 401 error code, distinct from `UNAUTHENTICATED`/`UNAUTHORIZED` — handle
  it the same way (log out), but it's worth distinguishing in logs/analytics since it means "killed
  elsewhere," not "token just expired."
- `resend-otp` now actually invalidates the previous challenge immediately (it used to still work
  briefly after a resend) and now has a total-resend cap it didn't have before.
