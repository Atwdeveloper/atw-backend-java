# ATW Backend — Azure Deployment Handover

The real ATW admin backend: authentication (password + email OTP 2FA), account self-service, and
role/permission management, backed by PostgreSQL (via Flyway) and Redis. The original throwaway
`testapi` pipe-cleaner code referenced in earlier versions of this doc is gone — this now documents
the real API and its real security requirements.

---

## 1. Stack / runtime requirements

| Component | Version | Notes |
|---|---|---|
| Java | **25** | Build and runtime base image must be JDK/JRE 25 (Eclipse Temurin). Confirmed consistent across `pom.xml`, `Dockerfile`, and this machine's local toolchain. |
| Spring Boot | 4.1.1 | |
| Build | Maven (via included `./mvnw` wrapper) | No local Maven needed; wrapper fetches 3.9.16. |
| PostgreSQL | 15+ (dev used 18) | Azure Database for PostgreSQL – Flexible Server. |
| Redis | 6+ (dev used 8) | Azure Cache for Redis. |

Packaging produces a single executable jar: `target/atw-backend-0.0.1-SNAPSHOT.jar`.

---

## 2. Azure resources to provision

1. **Azure Container Registry (ACR)** — to hold the image.
2. **Azure Database for PostgreSQL – Flexible Server** — create an empty database (e.g. `atw`).
   - The app user needs **DDL privileges** (Flyway creates/alters tables on startup).
   - Require SSL (`sslmode=require`).
3. **Azure Cache for Redis** — note the host, port **6380 (TLS)**, and an **access key**.
4. **Azure Container Apps** (recommended) or **App Service for Containers** — to run the image.
5. **Networking** — the container must reach the DB and Cache: allow it through their firewalls
   (private endpoint / VNet integration, or "Allow Azure services" for a quick test).
6. **(Optional) Azure Blob Storage** — for document uploads. Local dev uses Azurite; in Azure, set
   `AZURE_STORAGE_ENDPOINT` and leave the connection string empty so managed identity is used
   (`azure-identity` is already a dependency).
7. **(Optional) Azure Communication Services or SMTP** — for outbound email (OTP codes, password
   reset links, notifications). Without one configured, `MAIL_PROVIDER=log` just writes emails to the
   application log — fine for a first smoke test, not for real use.

---

## 3. Configuration — environment variables (the contract)

The app is **12-factor**: all environment-specific config comes from environment variables. No code
changes are needed between local and Azure — only these variables differ.

### Required outside a developer's own machine

| Variable | Required | Example (Azure) | Meaning |
|---|---|---|---|
| `DB_URL` | ✅ | `jdbc:postgresql://<srv>.postgres.database.azure.com:5432/atw?sslmode=require` | JDBC URL |
| `DB_USERNAME` | ✅ | `atwadmin` | DB user (needs DDL rights) |
| `DB_PASSWORD` | ✅ | *(secret)* | DB password |
| `REDIS_HOST` | ✅ | `<name>.redis.cache.windows.net` | Cache host |
| `REDIS_PORT` | ✅ | `6380` | TLS port on Azure |
| `REDIS_PASSWORD` | ✅ | *(access key)* | Cache access key |
| `REDIS_SSL` | ✅ | `true` | Enable TLS (Azure requires it) |
| `JWT_SECRET` | ✅ | *(secret, ≥32 bytes, unique per environment)* | HS256 signing key for admin access tokens. **There is no insecure fallback** — see §8. |
| `PII_ENCRYPTION_KEY` | ✅ | *(secret, base64, decodes to exactly 32 bytes, unique per environment)* | AES-256-GCM key encrypting staff KYC numbers and bank account numbers at rest. **No insecure fallback, same as `JWT_SECRET`** — see §8. |

> ⚠️ **Do not set `SPRING_PROFILES_ACTIVE` to `local` (or include `local` in a comma-list) anywhere
> outside a developer's own machine.** That profile layers in a publicly-committed dev JWT secret and
> a default bootstrap admin password (see §8) — it ships inside the image like any other classpath
> resource, and nothing stops it from activating except this one setting never containing `local`.
> Leave `SPRING_PROFILES_ACTIVE` unset in Azure.

### Required only for first boot (creating the first super-admin)

| Variable | Required | Example | Meaning |
|---|---|---|---|
| `ADMIN_BOOTSTRAP_ENABLED` | only to bootstrap | `true` | Off by default outside local/test. Set `true` for the *first* deploy only, then set back to `false` (or just leave it `true` — it's a no-op once any admin row exists). |
| `ADMIN_BOOTSTRAP_EMAIL` | if bootstrapping | `superadmin@anytimework.in` | First super-admin's email |
| `ADMIN_BOOTSTRAP_PASSWORD` | if bootstrapping | *(secret, strong, unique)* | First super-admin's password. Startup **refuses to boot** if this equals the known local-dev default — see §8. |
| `ADMIN_BOOTSTRAP_USERNAME` | optional | `superadmin` | |
| `ADMIN_BOOTSTRAP_FULL_NAME` | optional | `Platform Super Admin` | |

The bootstrap admin is created with `mustChangePassword = true` — the frontend forces a password
change before anything else is usable, and that first login's OTP + password-change flow is the
actual smoke test (§7).

### Optional (sensible defaults shown)

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `JAVA_OPTS` | *(none)* | Extra JVM flags, e.g. `-XX:MaxRAMPercentage=75` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated browser origins — **set this to the real frontend URL in Azure** |
| `FRONTEND_BASE_URL` | `http://localhost:3000` | Used to build the password-reset link emailed to admins — **set this to the real frontend URL in Azure** |
| `MAIL_PROVIDER` | `log` | `log` (writes to app log, no real email), `smtp`, or `azure` (Azure Communication Services) |
| `MAIL_FROM` / `MAIL_FROM_NAME` | `hello@anytimework.in` / `Anytime Work` | |
| `AZURE_COMMUNICATION_CONNECTION_STRING` / `AZURE_COMMUNICATION_ENDPOINT` | — | Only if `MAIL_PROVIDER=azure` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | — | Only if `MAIL_PROVIDER=smtp` |
| `AZURE_STORAGE_ENDPOINT` | — | Set this (and leave the connection string empty) to use managed identity in Azure |
| `AZURE_STORAGE_CONTAINER` | `atw-documents` | Blob container name |
| `ADMIN_OTP_ENABLED` | `true` | 2FA on login. Leave on. |
| `ADMIN_OTP_MAX_ATTEMPTS` | `5` | Wrong-code attempts before a challenge is locked out |
| `ADMIN_OTP_MAX_RESENDS` | `3` | Resends allowed per login attempt before it must restart |
| `ADMIN_MAX_FAILED_LOGINS` / `ADMIN_LOCKOUT_DURATION` | `5` / `PT15M` | Password-attempt lockout |
| `ADMIN_RATE_LIMIT_ENABLED` / `_MAX_ATTEMPTS` / `_WINDOW` | `true` / `10` / `PT1M` | Per-IP request cap on login/forgot-password/reset-password/accept-invite (Redis-backed, holds across replicas) |
| `ADMIN_OTP_IN_RESPONSE` | `false` | Test-only: puts the OTP directly in the login/resend-otp response instead of only emailing it. **Refused at startup outside local/test** — see §8. |
| `DOC_RECONCILE_STALE_AFTER` / `DOC_RECONCILE_INTERVAL` | `PT10M` / `PT5M` | How a background job self-heals a document upload that uploaded its blob but didn't finish committing |

> Store every secret (`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ADMIN_BOOTSTRAP_PASSWORD`, mail
> credentials) as a **Container Apps secret** / App Service app setting / Key Vault reference — never
> in the image or in plain env var definitions in a pipeline YAML.

---

## 4. Build & run the container

```bash
# Build (run from the repo root)
docker build -t atw-backend:latest .

# Push to ACR
az acr login --name <acrName>
docker tag atw-backend:latest <acrName>.azurecr.io/atw-backend:latest
docker push <acrName>.azurecr.io/atw-backend:latest
```

Run it with the environment variables from §3 (Container Apps example — first deploy, bootstrapping
the super-admin):

```bash
az containerapp create \
  --name atw-backend --resource-group <rg> --environment <cae> \
  --image <acrName>.azurecr.io/atw-backend:latest \
  --target-port 8080 --ingress external \
  --env-vars DB_URL=... DB_USERNAME=... REDIS_HOST=... REDIS_PORT=6380 REDIS_SSL=true \
             CORS_ALLOWED_ORIGINS=https://<your-frontend-domain> \
             FRONTEND_BASE_URL=https://<your-frontend-domain> \
             ADMIN_BOOTSTRAP_ENABLED=true ADMIN_BOOTSTRAP_EMAIL=superadmin@anytimework.in \
  --secrets dbpass=... redispass=... jwtsecret=... bootstrappass=... \
  --env-vars DB_PASSWORD=secretref:dbpass REDIS_PASSWORD=secretref:redispass \
             JWT_SECRET=secretref:jwtsecret ADMIN_BOOTSTRAP_PASSWORD=secretref:bootstrappass
```

Generate `JWT_SECRET` once with something like `openssl rand -base64 48` and store it as a secret —
treat it like any other credential (rotating it invalidates every currently-issued access token, by
design).

---

## 5. Health probes (for Azure)

| Probe | Path |
|---|---|
| Liveness | `GET /actuator/health/liveness` |
| Readiness | `GET /actuator/health/readiness` |
| Overall (shows db + redis when authorized) | `GET /actuator/health` |

`/actuator/health` returns `{"status":"UP"}` and is **open** (no auth) so probes work. The detailed
component view (db, redis, mail) is shown only to authenticated callers.

---

## 6. Database migrations (Flyway)

Flyway runs **automatically on startup** and applies every `src/main/resources/db/migration/V*.sql`
in order. The DB must be reachable at boot with **DDL rights**.
`spring.jpa.hibernate.ddl-auto=validate` — Hibernate only validates the schema; it never alters it.

Current migrations (V1–V16): admin identity, documents, notifications/email templates, RBAC
(permissions/roles/admin accounts), admin security hardening (OTP resend tracking, per-account
security-version for session revocation), document upload status tracking, a data-only fix to a
broken Thymeleaf attribute in the seeded password-reset email template, a rename of the admin
refresh-token/auth-token tables to `admin_refresh_token`/`admin_auth_token`, three staff
profile/KYC/bank-detail satellite tables, the seeded email template for the staff-invite flow,
refresh-token "family" tracking for reuse detection, case-insensitive-identifier enforcement on
`admin_user` (lowercase + a CHECK constraint), widening the KYC/bank-detail columns for encrypted
storage plus the `admin_staff.view_kyc` / `admin_staff.view_bank_details` permissions, an `actor_email`
snapshot column on `admin_audit_log` so deleting a staff account doesn't strip attribution from
their historical audit entries, renaming the `staff`/`roles` permission modules to
`admin_staff`/`admin_roles` (code/module columns only, in place — same permission ids, so every
existing role grant survives) to avoid a future collision with a company-side "staff"/"roles"
concept, and — the same reasoning taken one layer deeper — renaming the `role`, `permission`, and
`role_permission` tables themselves to `admin_role`/`admin_permission`/`admin_role_permission`
(plain `ALTER TABLE ... RENAME TO`, all data/ids/FK relationships preserved automatically). The
matching Java classes (`StaffService` → `AdminStaffService`, `Role` → `AdminRole`,
`Permission` → `AdminPermission`, `RoleRepository`/`PermissionRepository` → `AdminRoleRepository`/
`AdminPermissionRepository`, etc.) and permission-code literals were renamed in the same changes.
`admin_user*` and API URLs (`/api/v1/admin/staff`, `/api/v1/admin/roles`) were already unambiguous
and are unchanged.

---

## 7. Validate the deployment (smoke test)

Once running, against the app's public URL `<BASE>` (replace `<challengeId>`/`<otp>`/`<token>` with
real values returned by each step — see **FRONTEND_HANDOVER.md** for the full endpoint reference):

```bash
# 1. Health
curl <BASE>/actuator/health                       # -> {"status":"UP"}

# 2. Log in as the bootstrap super-admin (password step)
curl -X POST <BASE>/api/v1/admin/auth/login -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"<ADMIN_BOOTSTRAP_EMAIL>","password":"<ADMIN_BOOTSTRAP_PASSWORD>"}'
#   -> {"success":true,"data":{"otpRequired":true,"challengeId":"..."}}
#   The OTP is emailed via whatever MAIL_PROVIDER is configured (or printed to the app log if "log").

# 3. Verify the OTP
curl -X POST <BASE>/api/v1/admin/auth/verify-otp -H "Content-Type: application/json" \
  -d '{"challengeId":"<challengeId>","otp":"<otp>"}'
#   -> {"success":true,"data":{"accessToken":"...","admin":{"mustChangePassword":true,...}}}

# 4. Confirm the forced-password-change gate is active (expect 403 MUST_CHANGE_PASSWORD)
curl <BASE>/api/v1/admin/account/my-permissions -H "Authorization: Bearer <accessToken>"

# 5. Change the bootstrap password (now usable — anyone else with the bootstrap password can't log in anymore)
curl -X POST <BASE>/api/v1/admin/account/change-password -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"<ADMIN_BOOTSTRAP_PASSWORD>","newPassword":"<a-real-strong-password>"}'
```

If step 2–5 complete, Postgres connectivity, Redis connectivity (the OTP/session machinery uses
both), and the security hardening added in this handover (forced first password change,
immediate session revocation on password change) are all proven live. If `/actuator/health`
(authenticated) shows `redis: UP` and `db: UP`, that confirms both dependencies explicitly too.

**This exact sequence has been run against a real instance of this build** (locally, with a
dockerized Postgres/Redis/the app itself — see §9) and produces exactly the responses shown above.

---

## 8. Security notes (read this before your first real deploy)

- **No insecure fallback for `JWT_SECRET` or `PII_ENCRYPTION_KEY`.** Startup has a dedicated check
  (`StartupSecurityValidator`) that refuses to boot — throws and exits non-zero — if, outside the
  `local`/`test` Spring profiles: the JWT secret is missing, shorter than 32 bytes, or the
  publicly-committed local-dev literal; the PII key is missing, not valid base64, doesn't decode to
  exactly 32 bytes, or is the publicly-committed local-dev literal; or `ADMIN_OTP_IN_RESPONSE` is
  left on. Same check for `ADMIN_BOOTSTRAP_PASSWORD` when bootstrap is enabled. **A failed deploy
  here means a missing/weak secret (or a test-only flag left on), not a bug** — fix the env var,
  don't work around the check.
- **Staff KYC numbers and bank account numbers are encrypted at rest** (AES-256-GCM, a fresh random
  IV per value) and masked in every API response unless the caller holds the narrower
  `admin_staff.view_kyc` / `admin_staff.view_bank_details` permission.
- **Access tokens can be revoked before they expire.** Each admin carries a `securityVersion`; a
  password change/reset or account disable bumps it, and every authenticated request checks the
  token's version against the live value — so a stolen/leaked token stops working immediately, not
  just at its natural 15-minute expiry.
- **Refresh-token reuse is detected, not just rejected.** Replaying a token that was already rotated
  away (not merely expired) revokes every token in that session's rotation chain, not just the
  replayed one — see FRONTEND_HANDOVER.md §3.2.
- **OTP brute-force is rate-limited and the limit is actually enforced** (persists across concurrent
  attempts too) — 5 wrong codes locks the challenge; resends are cooldown- and count-limited. On top
  of that, `login`/`verify-otp`/`resend-otp`/`forgot-password`/`reset-password`/`accept-invite` now
  also have a general, Redis-backed per-IP request cap (`ADMIN_RATE_LIMIT_*`), independent of those
  per-challenge limits. **A Redis outage does not take auth down with it** — `RateLimiter.allow`
  catches `DataAccessException` and fails open (logs a warning, lets the request through) rather than
  propagating; this runs inside a `Filter`, ahead of the servlet dispatcher, so an uncaught exception
  there would have bypassed `GlobalExceptionHandler` entirely and surfaced as a raw error page on
  every login attempt during the outage. Covered by `RateLimiterRedisOutageTest` (simulated
  connection failure) alongside `RateLimiterTest` (real Redis, happy path).
- **The per-IP rate limit (and audit IP attribution) trusts exactly one proxy hop in `X-Forwarded-For`**
  (`HttpRequests.clientIp`, takes the last entry, not the first) — this assumes Azure Container
  Apps' own ingress is the only reverse proxy in front of the app. If a WAF, CDN, or Application
  Gateway is ever added in front of it, this needs to trust a different hop count or it starts
  trusting an attacker-controllable value again.
- **A caller can never grant a permission they don't themselves hold** — enforced on both role
  definition (`POST`/`PUT /api/v1/admin/roles`) and role assignment (`POST`/`PUT /api/v1/admin/staff`),
  so holding only `admin_staff.edit` or only `admin_roles.edit` can't be used to self-escalate via a custom role.
  Super-admins are exempt (they already hold everything implicitly).
- **`admin_user.email`/`username` are enforced lowercase** (normalized on write, plus a DB CHECK
  constraint) so a case-only duplicate (`Riya@x.com` vs `riya@x.com`) can't slip past the
  case-insensitive login lookup and the case-insensitive pre-create uniqueness check.
- **CSRF is disabled** — this is a stateless, bearer-token JSON API with no cookie-based session, so
  CSRF doesn't apply the way it would to a cookie-authenticated app. Don't add cookie-based auth
  without reintroducing CSRF protection.
- **Keep `ADMIN_BOOTSTRAP_ENABLED` off (the default) once a super-admin exists.** It's a no-op by then
  anyway (bootstrap only fires when the `admin_user` table is empty), but there's no reason to leave
  it on.

---

## 9. Run the full stack locally in Docker (recommended before a real handover/demo)

`compose.yaml` has a `full-stack` profile that runs Postgres, Redis, Azurite, **and the app itself**
all in containers — a self-contained environment that mirrors the Azure topology (containerized app +
managed Postgres/Redis) and deliberately does **not** set `SPRING_PROFILES_ACTIVE=local`, so it
exercises the exact same startup checks a real deployment would.

```bash
docker compose --profile full-stack up --build -d
# wait ~20-30s for the app to finish starting, then:
curl http://localhost:8081/actuator/health
# run the §7 smoke test against http://localhost:8081 instead of <BASE>

docker compose --profile full-stack down -v   # stop and wipe everything when done
```

(Ports are offset — app on `8081`, Postgres on `5433`, Redis on `6380` — so this doesn't collide with
anything you might already have running natively on the default ports.)

**Local troubleshooting note:** if `docker compose ... up` hangs or fails to pull images with DNS/
connection errors, and you're on a machine running **Cloudflare WARP** (or a similar VPN client), it
can block WSL2's outbound networking even after you "disconnect" it in the app — the underlying
Windows service needs to be fully stopped (`Stop-Service CloudflareWARP` as Administrator), and
sometimes a full reboot is needed to clear stale network state afterward. This is a local-machine
quirk, unrelated to the app or to how Azure's build pipeline will behave.

---

## 10. Notes / caveats

- **Java 25 is relatively new** — ensure the Temurin 25 base images are pullable in your registry/
  environment.
- No HTTPS/TLS termination is handled by the app; terminate TLS at the Azure ingress.
- See **FRONTEND_HANDOVER.md** for the complete API reference (every endpoint, every error code) and
  **postman/** for a ready-to-import Postman collection — both are kept current as the API evolves,
  ready for whenever the frontend handover happens; no frontend team has received them yet, so there
  is no live consumer depending on the pre-rename `staff.*`/`roles.*` permission codes.
