# ATW Admin API — Authentication & Access (Slice 4a)

Reference for the admin frontend. Covers admin login (password + email OTP), JWT access/refresh,
self-service, and the permission catalog.

- **Base URL (local):** `http://localhost:8080`
- **Live, interactive docs:** Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`
- **Content type:** `application/json` for all requests/responses

---

## 1. Response envelope

Every response uses the same wrapper. On success, `error` is omitted; on failure, `data` is omitted.

```jsonc
// success
{ "success": true, "data": { /* ... */ } }

// failure
{ "success": false, "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid credentials",
    "fieldErrors": [ { "field": "password", "message": "must not be blank" } ], // only for validation
    "timestamp": "2026-10-18T16:00:00Z",
    "traceId": "0f8c...-correlation-id"
} }
```

**Error codes** → HTTP status: `VALIDATION_ERROR` 400 · `UNAUTHORIZED` 401 · `ACCESS_DENIED` 403 ·
`RESOURCE_NOT_FOUND` 404 · `CONFLICT` 409 · `BUSINESS_RULE_VIOLATION` 422 · `INTERNAL_ERROR` 500.
Always show `error.message`; use `error.code` for logic and include `traceId` in bug reports.
Every response also carries an `X-Correlation-Id` header.

---

## 2. Auth model (read this first)

- Admins authenticate with **password + a one-time code emailed to them (2FA)**.
- On success the API returns a short-lived **access token (JWT, ~15 min)** and a longer-lived
  **refresh token (~7 days, rotating)**.
- Send the access token on every protected call: **`Authorization: Bearer <accessToken>`**.
- When the access token expires (401), call **`/refresh`** with the refresh token to get a new pair.
  Each refresh **rotates** the refresh token — store the new one and discard the old (the old one
  stops working immediately).
- **`/api/admin/auth/**` is public**; everything else requires a valid access token.

### Login sequence

```
1. POST /api/admin/auth/login        {usernameOrEmail, password}   -> { otpRequired:true, challengeId }
2. (admin receives a 6-digit code by email)
3. POST /api/admin/auth/verify-otp   {challengeId, otp}            -> { accessToken, refreshToken, admin, ... }
4. call protected APIs with  Authorization: Bearer <accessToken>
5. on 401 later:  POST /api/admin/auth/refresh {refreshToken}      -> new { accessToken, refreshToken, ... }
6. POST /api/admin/auth/logout       {refreshToken}                -> revokes the session
```

> If 2FA is disabled by config (`ADMIN_OTP_ENABLED=false`), step 1 returns `otpRequired:false` with the
> tokens directly and steps 2–3 are skipped. In production, OTP is on.

---

## 3. Endpoints

### 3.1 Public — `/api/admin/auth`

#### POST `/login`
Request:
```json
{ "usernameOrEmail": "superadmin", "password": "ChangeMe@12345" }
```
Response (OTP required):
```json
{ "success": true, "data": { "otpRequired": true, "challengeId": "7b1e...-uuid" } }
```
Errors: `401 UNAUTHORIZED` (invalid credentials / disabled / temporarily locked after 5 failed tries),
`400 VALIDATION_ERROR` (missing fields).

#### POST `/verify-otp`
Request:
```json
{ "challengeId": "7b1e...-uuid", "otp": "123456" }
```
Response:
```json
{ "success": true, "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshToken": "x7Qp...-opaque",
    "admin": {
      "id": "c0ffee...-uuid",
      "username": "superadmin",
      "email": "superadmin@anytimework.in",
      "fullName": "Platform Super Admin",
      "superAdmin": true,
      "roles": ["Senior Developer"],
      "authorities": ["SUPER_ADMIN"]
    }
} }
```
Errors: `400 VALIDATION_ERROR` (wrong/expired code), `422 BUSINESS_RULE_VIOLATION` (too many attempts),
`401 UNAUTHORIZED` (account disabled).

> `authorities` is `["SUPER_ADMIN"]` for super-admins (they can do everything); for normal admins it is
> the list of permission codes from their roles (e.g. `["staff.view","companies.edit",...]`).

#### POST `/resend-otp`
Request: `{ "challengeId": "7b1e...-uuid" }` → Response: `{ "success": true, "data": { "challengeId": "new-uuid" } }`
(Use the **new** `challengeId` for the next verify.) Throttled: `422 BUSINESS_RULE_VIOLATION` if requested within 60s.

#### POST `/refresh`
Request: `{ "refreshToken": "x7Qp...-opaque" }` → Response: same shape as `verify-otp` (new access + new refresh).
Errors: `401 UNAUTHORIZED` (expired/revoked/rotated token).

#### POST `/logout`
Request: `{ "refreshToken": "x7Qp...-opaque" }` → `{ "success": true }`. Revokes that refresh token.
(Access tokens are short-lived and expire on their own.)

#### POST `/forgot-password`
Request: `{ "email": "admin@anytimework.in" }` → **always** `{ "success": true }` (no account enumeration).
If the email exists, a reset link is emailed (valid 30 min).

#### POST `/reset-password`
Request: `{ "token": "<from email link>", "newPassword": "NewSecret@123" }` → `{ "success": true }`.
Resets the password and **revokes all existing sessions**. Errors: `400 VALIDATION_ERROR` (invalid/expired token or weak password; min 8 chars).

### 3.2 Authenticated — `/api/admin/account` (send Bearer token)

#### GET `/me`
Response: the `admin` profile object (same shape as in `verify-otp`). Use it to render the user menu
and drive route guards.

#### GET `/my-permissions`
Response: `{ "success": true, "data": ["staff.view","companies.edit", ...] }` — the current admin's
effective permission codes. For a super-admin it returns `["*"]` (treat as "all allowed").
Use this to show/hide UI; **the server still enforces every permission**.

#### POST `/change-password`
Request: `{ "currentPassword": "...", "newPassword": "NewSecret@123" }` → `{ "success": true }`.
Revokes all sessions (the user must log in again). Errors: `400 VALIDATION_ERROR` (wrong current password / weak new password).

### 3.3 Authenticated + permission — `/api/admin/permissions`

#### GET `/` — requires permission `roles.view`
Returns the full permission catalog grouped by module (for the role editor):
```json
{ "success": true, "data": [
    { "module": "companies", "permissions": [
        { "code": "companies.view", "action": "view", "label": "view" },
        { "code": "companies.edit", "action": "edit", "label": "edit" }
    ] },
    { "module": "staff", "permissions": [ { "code": "staff.view", "action": "view", "label": "view" } ] }
] }
```
Errors: `401 UNAUTHORIZED` (no token), `403 ACCESS_DENIED` (lacks `roles.view`).

---

## 4. Authorization model for the frontend

- Each protected endpoint requires a specific **permission code** (`module.action`, e.g. `staff.create`).
- The frontend reads `authorities` (from the token/`/me`) or `/my-permissions` to decide what to show.
- **Super-admin** (`admin.superAdmin === true`, authority `SUPER_ADMIN`, `/my-permissions` = `["*"]`)
  passes every check — show the full UI.
- The server enforces permissions on every endpoint regardless of what the UI shows, so a hidden
  button that is somehow called still returns `403 ACCESS_DENIED`.

The permission catalog (modules → actions) is the authority list — fetch it from
`GET /api/admin/permissions` to build the role editor.

---

## 5. Token handling checklist (frontend)

1. After `verify-otp`/`refresh`, store `accessToken` (memory or short-lived storage) and `refreshToken`
   (secure storage). Always replace **both** after a refresh.
2. Attach `Authorization: Bearer <accessToken>` to every non-`/auth` request.
3. On `401` with a valid refresh token → call `/refresh` once, retry the original request; if refresh
   also fails → send the user to login.
4. On `logout`, call `/logout` with the refresh token and clear stored tokens.
5. If `admin.mustChangePassword` is ever surfaced (bootstrap/invited accounts), route the user to a
   change-password screen first.

---

## 6. Config knobs (for environment/devops, FYI)

| Setting | Env var | Default |
|---|---|---|
| OTP 2FA on/off | `ADMIN_OTP_ENABLED` | `true` |
| Access token TTL | `JWT_ACCESS_TTL` | `PT15M` |
| Refresh token TTL | `JWT_REFRESH_TTL` | `P7D` |
| OTP validity | `ADMIN_OTP_TTL` | `PT10M` |
| Login lockout after N fails | `ADMIN_MAX_FAILED_LOGINS` | `5` |
| JWT signing secret | `JWT_SECRET` | dev placeholder (set in Key Vault for real envs) |

> Not yet in Slice 4a (coming in 4b/4c): admin & role management CRUD, invite onboarding, staff HR
> profile, admin documents.
