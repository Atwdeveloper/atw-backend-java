# ATW Admin API — Authentication & Access (Slice 4a)

Reference for the admin frontend. Covers admin login (password + email OTP), JWT access/refresh,
self-service, and the permission catalog.

- **Base URL (local):** `http://localhost:8080`
- **API version:** every path below is under `/api/v1` (e.g. `/api/v1/admin/auth/login`) — see §7.
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
  stops working immediately). Replaying an already-rotated token is treated as a theft signal and
  revokes the whole session, not just that one token — see FRONTEND_HANDOVER.md §3.2.
- **`/api/v1/admin/auth/**` is public**; everything else requires a valid access token.
- `login`, `forgot-password`, `reset-password`, and `accept-invite` are rate-limited per IP
  (10 req/min by default) — `429 RATE_LIMITED` over the cap.

### Login sequence

```
1. POST /api/v1/admin/auth/login        {usernameOrEmail, password}   -> { otpRequired:true, challengeId }
2. (admin receives a 6-digit code by email)
3. POST /api/v1/admin/auth/verify-otp   {challengeId, otp}            -> { accessToken, refreshToken, admin, ... }
4. call protected APIs with  Authorization: Bearer <accessToken>
5. on 401 later:  POST /api/v1/admin/auth/refresh {refreshToken}      -> new { accessToken, refreshToken, ... }
6. POST /api/v1/admin/auth/logout       {refreshToken}                -> revokes the session
```

> If 2FA is disabled by config (`ADMIN_OTP_ENABLED=false`), step 1 returns `otpRequired:false` with the
> tokens directly and steps 2–3 are skipped. In production, OTP is on.

---

## 3. Endpoints

### 3.1 Public — `/api/v1/admin/auth`

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
> the list of permission codes from their roles (e.g. `["admin_staff.view","companies.edit",...]`).

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

#### POST `/accept-invite`
Request: `{ "token": "<from invite email link>", "password": "NewSecret@123" }` → `{ "success": true }`.
Completes a staff invite (see 3.4): sets the account's first real password (it was created with a
random, never-disclosed one) and clears `mustChangePassword`. Distinct from `/reset-password` — uses
a different token kind (`INVITE`, not `PASSWORD_RESET`) issued only by `POST /api/v1/admin/staff`.
Errors: `400 VALIDATION_ERROR` (invalid/expired/already-used invite link or weak password).

### 3.2 Authenticated — `/api/v1/admin/account` (send Bearer token)

#### GET `/me`
Response: the `admin` profile object (same shape as in `verify-otp`). Use it to render the user menu
and drive route guards.

#### GET `/my-permissions`
Response: `{ "success": true, "data": ["admin_staff.view","companies.edit", ...] }` — the current admin's
effective permission codes. For a super-admin it returns `["*"]` (treat as "all allowed").
Use this to show/hide UI; **the server still enforces every permission**.

#### POST `/change-password`
Request: `{ "currentPassword": "...", "newPassword": "NewSecret@123" }` → `{ "success": true }`.
Revokes all sessions (the user must log in again). Errors: `400 VALIDATION_ERROR` (wrong current password / weak new password).

### 3.3 Authenticated + permission — `/api/v1/admin/permissions`

#### GET `/` — requires permission `admin_roles.view`
Returns the full permission catalog grouped by module (for the role editor):
```json
{ "success": true, "data": [
    { "module": "companies", "permissions": [
        { "code": "companies.view", "action": "view", "label": "view" },
        { "code": "companies.edit", "action": "edit", "label": "edit" }
    ] },
    { "module": "admin_staff", "permissions": [ { "code": "admin_staff.view", "action": "view", "label": "view" } ] }
] }
```
Errors: `401 UNAUTHORIZED` (no token), `403 ACCESS_DENIED` (lacks `admin_roles.view`).

### 3.4 Staff — `/api/v1/admin/staff` (requires `admin_staff.*` permission)

| Method & path | Permission | Body | Notes |
|---|---|---|---|
| `GET /` | `admin_staff.view` | — (query: `page`, `size`, `sort`) | `PageResponse<AdminStaffDto>` |
| `GET /{id}` | `admin_staff.view` | — | `AdminStaffDto` |
| `POST /` | `admin_staff.create` | `{username?, email, fullName, phone?, roleIds[]}` | Creates the account (random, never-disclosed password) and emails an invite link — see 3.1 `/accept-invite`. No `superAdmin` field; that stays bootstrap-only. |
| `PUT /{id}` | `admin_staff.edit` | `{username?, fullName, phone?, roleIds[]}` | Full replace of roles. Email is not editable here. |
| `POST /{id}/status` | `admin_staff.toggle_status` | `{status: "ACTIVE"\|"DISABLED"}` | Explicit target status (not a blind toggle). Disabling revokes all of that admin's sessions immediately. Blocked against your own account and against super-admin accounts. |
| `DELETE /{id}` | `admin_staff.delete` | — | Blocked against your own account and against super-admin accounts. |

`AdminStaffDto`: `{id, username, email, fullName, phone, superAdmin, status, roles:[{id,name}], lastLoginAt, createdAt}`.

### 3.5 Roles — `/api/v1/admin/roles` (requires `admin_roles.*` permission)

| Method & path | Permission | Body | Notes |
|---|---|---|---|
| `GET /` | `admin_roles.view` | — | `AdminRoleDto[]` |
| `GET /{id}` | `admin_roles.view` | — | `AdminRoleDto` |
| `POST /` | `admin_roles.create` | `{name, description?, permissionCodes[]}` | `409 CONFLICT` on a duplicate name |
| `PUT /{id}` | `admin_roles.edit` | `{name, description?, permissionCodes[]}` | Full replace of permissions. `422 BUSINESS_RULE_VIOLATION` on a system role. |
| `DELETE /{id}` | `admin_roles.delete` | — | `422 BUSINESS_RULE_VIOLATION` on a system role; `409 CONFLICT` if staff are still assigned to it. |

`AdminRoleDto`: `{id, name, description, systemRole, permissionCodes[]}`. Unknown permission codes in a
create/update request return `400 VALIDATION_ERROR`.

### 3.6 Staff profile / KYC / bank detail — `/api/v1/admin/staff/{id}/{profile|kyc|bank-detail}`

Optional sub-resources, filled in progressively, each a plain get/replace pair gated by
`admin_staff.view`/`admin_staff.edit`. KYC numbers and the bank account number are encrypted at rest and
**masked in the response** (e.g. `"********9012"`) unless the caller also holds the narrower
`admin_staff.view_kyc` / `admin_staff.view_bank_details` permission (currently Senior/Junior Developer only,
same scoping as `workers.view_wallet`). Full request/response shapes: FRONTEND_HANDOVER.md §4
("Staff sub-resources").

---

## 4. Authorization model for the frontend

- Each protected endpoint requires a specific **permission code** (`module.action`, e.g. `admin_staff.create`).
- The frontend reads `authorities` (from the token/`/me`) or `/my-permissions` to decide what to show.
- **Super-admin** (`admin.superAdmin === true`, authority `SUPER_ADMIN`, `/my-permissions` = `["*"]`)
  passes every check — show the full UI.
- The server enforces permissions on every endpoint regardless of what the UI shows, so a hidden
  button that is somehow called still returns `403 ACCESS_DENIED`.

The permission catalog (modules → actions) is the authority list — fetch it from
`GET /api/v1/admin/permissions` to build the role editor.

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
| PII encryption key (KYC/bank, AES-256) | `PII_ENCRYPTION_KEY` | dev placeholder (set in Key Vault for real envs) |
| Rate limit on/off, cap, window | `ADMIN_RATE_LIMIT_ENABLED` / `_MAX_ATTEMPTS` / `_WINDOW` | `true` / `10` / `PT1M` |
| OTP-in-response test shortcut | `ADMIN_OTP_IN_RESPONSE` | `false` (local dev default: `true`; refused outside local/test) |

> Staff & role management CRUD, invite onboarding, and the profile/KYC/bank-detail sub-resources
> have all landed (§3.4, §3.5, §3.6, §3.1 `/accept-invite`). Still not yet built: admin documents,
> a "resend invite" endpoint, and an audit-log read API.
