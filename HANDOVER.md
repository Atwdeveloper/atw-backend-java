# ATW Backend — Azure Deployment Handover

The real ATW admin backend: authentication (password + email OTP 2FA), account self-service,
role/permission management, staff address/KYC/bank-detail records, and workforce/geography reference
data (states, districts, departments, industries, designations, skills, skill categories, and
effective-dated statutory minimum wage rates) — backed by PostgreSQL (via Flyway), Redis, and Azure
Blob Storage. The original throwaway `testapi` pipe-cleaner code referenced in earlier versions of
this doc is gone — this now documents the real API and its real security requirements.

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
6. **Azure Blob Storage** — backs two features: staff KYC/profile document uploads (the `document`
   module) and masterdata icon uploads for industries/designations/skills. Building the blob client
   makes no network call, so **the app still boots without it reachable** — only those specific
   upload/download endpoints fail until it's configured. Local dev uses Azurite; in Azure, set
   `AZURE_STORAGE_ENDPOINT` and leave the connection string empty so managed identity is used
   (`azure-identity` is already a dependency) — grant the runtime identity the **Storage Blob Data
   Contributor** role on the storage account (needed for `generateUserDelegationSas`; there is no
   account key in this mode, so a narrower "reader" role is not enough).
7. **(Optional) Azure Communication Services or SMTP** — for outbound email (OTP codes, password
   reset links, notifications). Without one configured, `MAIL_PROVIDER=log` just writes emails to the
   application log — fine for a first smoke test, not for real use. **Admin login depends entirely on
   this** (OTP is mandatory 2FA, no password-only fallback) — see the `ADMIN_FIXED_OTP` escape hatch
   in §3 if real email isn't verified working yet on a dev-tier environment.
8. **Azure Key Vault and Azure App Configuration** — back the deploy pipeline's whole config/secrets
   story (see the "How a variable actually reaches the container" note in §3): every setting in §3's
   tables needs a matching `backend-<kebab-case>` entry in one of these two (plain values in App
   Configuration labeled `dev`, secrets in Key Vault), or it won't survive the next automated deploy.
9. **The runtime user-assigned managed identity** (`runtime_identity_resource_id` in the deploy
   pipeline) needs its own RBAC role assignments on the resources it touches — the pipeline *uses*
   these, it doesn't grant them: **`Key Vault Secrets User`** on the vault (to resolve `backend-*`
   secret references at container start), **`AcrPull`** on the registry (to pull the image —
   `containerapp registry set --identity` in the deploy action configures the Container App to use
   this identity for pulls, but the identity still needs the role itself), and, if/when Blob Storage
   or Azure Communication Services are switched to managed-identity mode instead of a connection
   string (§2.6/§3's `MAIL_PROVIDER=azure`), **`Storage Blob Data Contributor`** / the appropriate ACS
   role on those resources too. A deploy or a specific feature failing with an authorization/403 error
   despite correct configuration almost always means one of these role assignments is missing, not a
   code or config bug.
10. **Face recognition service (`faceauth` module)** — already hosted externally on Render
   (`atw-face-auth-service`, same codebase as `D:\Office_project\face_auth_model\face-auth` run
   locally) — **nothing to provision in Azure for this one**, just configuration (§3). Backs worker
   face enrollment, 1:N identify ("whose face is this"), and enrollment-time duplicate-face
   rejection. **Unlike every other integration above, leaving this unconfigured fails silently, not
   loudly**: the app boots fine and every enroll/identify call "succeeds" against a stub that returns
   random, photo-independent data — it will never actually recognize anyone or catch a real duplicate
   face, with no error anywhere to notice. Confirm `FACE_AUTH_PROVIDER=python` is actually set, not
   just assumed.
11. **Sandbox.co.in integrations (Aadhaar e-KYC, bank verification, GST verification)** — three
    separate SaaS products, **nothing to provision in Azure for any of them**, just configuration
    (§3). All three share face recognition's exact silent-failure shape above: each defaults to a
    stub adapter that always "succeeds" with fake, deterministic data when its provider flag is left
    unset, with no error anywhere to notice. **None of the three provider flags or their credential
    env vars have ever been in this document before today** — confirm all three are actually set to
    `sandbox` in the running Azure app, not just assumed.

---

## 3. Configuration — environment variables (the contract)

The app is **12-factor**: all environment-specific config comes from environment variables. No code
changes are needed between local and Azure — only these variables differ.

**How a variable actually reaches the container on this project's dev environment (updated
2026-11-17 — the automation below didn't exist when §4's manual example was first written, and that
example is now misleading if followed for an ongoing deploy):** `.github/workflows/deploy-backend.yml`
(push to `develop`, or manual dispatch) builds the jar, pushes the image to ACR, then
`.github/actions/backend-tasks` (`operation: deploy`) rebuilds the **entire** env-var set on every
run from two sources and pushes it with one `az containerapp update --set-env-vars`:
- **Plain (non-secret) values** → an **Azure App Configuration** key-value, key named
  `backend-<kebab-case>`, **label `dev`**, ordinary text content-type (no Key Vault references or
  feature flags allowed here — the action rejects those). `backend-face-auth-base-url` becomes the
  `FACE_AUTH_BASE_URL` environment variable.
- **Secrets** → an **enabled** Key Vault secret, same `backend-<kebab-case>` naming. The action wires
  it as a Container Apps secret that's itself a Key Vault reference resolved by the attached runtime
  managed identity at container start — the raw value never appears in the pipeline, its logs, or
  anywhere in this repo. `backend-face-auth-api-key` becomes `FACE_AUTH_API_KEY`.
- A setting must live in **exactly one** of the two stores — the action errors out if the same
  derived name exists in both, or if an App Configuration entry isn't a plain value.
- Practical effect: **every variable in the tables below needs a matching `backend-*` entry in App
  Configuration or Key Vault (labeled/scoped to this dev environment) to survive the next deploy** —
  anything set only via a one-off `az containerapp update --env-vars` (as in §4's bootstrap example)
  gets overwritten the next time this pipeline runs.

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
| `ADMIN_PII_ENCRYPTION_KEY` | ✅ | *(secret, base64, decodes to exactly 32 bytes, unique per environment)* | AES-256-GCM key encrypting staff/admin KYC numbers and bank account numbers at rest. **No insecure fallback, same as `JWT_SECRET`** — see §8. Renamed from `PII_ENCRYPTION_KEY` on 2026-11-23 for clarity alongside its siblings below. |
| `WORKER_PII_ENCRYPTION_KEY` | ✅ | *(secret, base64, decodes to exactly 32 bytes, unique per environment, DIFFERENT from `ADMIN_PII_ENCRYPTION_KEY`)* | AES-256-GCM key encrypting **worker** KYC/bank/statutory numbers at rest — deliberately a separate key from the staff one above so a compromise of one key doesn't also expose the other dataset. Same no-insecure-fallback rule — added when the `worker` module shipped; missing from this table until now. |
| `COMPANY_PII_ENCRYPTION_KEY` | ✅ | *(secret, base64, decodes to exactly 32 bytes, unique per environment, DIFFERENT from `ADMIN_PII_ENCRYPTION_KEY` and `WORKER_PII_ENCRYPTION_KEY`)* | AES-256-GCM key encrypting **company** GST/PAN numbers at rest — a third, independent key (bounds blast radius the same way the worker key does). Same no-insecure-fallback rule, enforced by `StartupSecurityValidator` since the `company` module shipped; missing from this table until now. |

> ⚠️ **Do not set `SPRING_PROFILES_ACTIVE` to `local` (or include `local` in a comma-list) anywhere
> outside a developer's own machine.** That profile layers in a publicly-committed dev JWT secret and
> a default bootstrap admin password (see §8) — it ships inside the image like any other classpath
> resource, and nothing stops it from activating except this one setting never containing `local`.
> Leave `SPRING_PROFILES_ACTIVE` unset in Azure.
>
> The one deliberate exception is `dev` — a separate, narrower profile for a pre-SMTP dev/staging
> slot. It does **not** relax `JWT_SECRET`/`ADMIN_PII_ENCRYPTION_KEY`/`ADMIN_BOOTSTRAP_PASSWORD` at all; it
> only unlocks `ADMIN_FIXED_OTP` (see below). Never set `dev` on a production app.

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

### Face recognition — required for real matching, but fails SILENTLY if skipped (see §2.8)

| Variable | Default | Example (Azure) | Meaning |
|---|---|---|---|
| `FACE_AUTH_PROVIDER` | `stub` | `python` | `stub` (default) returns random, photo-independent data for every photo — enroll/identify/duplicate-detection all return a plausible-looking response but never a real result, with no error. Set to `python` for the real integration. |
| `FACE_AUTH_BASE_URL` | *(empty)* | `https://face-auth-service-m2o7.onrender.com` | Only read once `FACE_AUTH_PROVIDER=python`. The already-hosted Render deployment — not an Azure resource. |
| `FACE_AUTH_API_KEY` | *(empty)* | *(secret)* | Checked via `X-API-Key` by the external service. Missing/wrong → every enroll/identify call fails loudly with `invalid_api_key` (unlike the silent stub fallback above). |
| `FACE_AUTH_TIMEOUT` | `PT75S` | — | Render's free tier cold-starts after idling; the default already accounts for it. |
| `FACE_AUTH_IDENTIFY_THRESHOLD` / `FACE_AUTH_IDENTIFY_MARGIN` | `0.5` / `0.05` | — | 1:N identify match confidence (legacy's own production-validated values, not yet recalibrated for this specific deployment). |
| `FACE_AUTH_DUPLICATE_THRESHOLD` | `0.5` | — | Enrollment-time duplicate-face rejection threshold. |

### Sandbox.co.in integrations — Aadhaar e-KYC, bank verification, GST verification: required for real verification, but fail SILENTLY if skipped (see §2.11)

Three separate products on the Sandbox.co.in vendor platform, each behind its own port/adapter pair
(a stub default and a real `Sandbox*Adapter`) switched by its own Spring property — mirrors face
recognition's pattern above, including its risk. **Leaving a provider flag unset silently activates
the stub**: it always "succeeds" with fake, deterministic data and makes no real vendor call — the
app boots fine, every call returns a plausible-looking response, and there is no error anywhere to
notice. (The opposite mistake — a typo'd value that is neither `sandbox` nor `stub` — fails the
opposite way: no adapter bean matches either condition and the app refuses to boot, so at least that
particular mistake is loud.) **None of the three provider flags or their credential env vars have
ever appeared in this document until today** — confirm each one below is actually `sandbox` in the
running Azure app, not just assumed, the same way the face-recognition table above already asks for
`FACE_AUTH_PROVIDER`.

**Aadhaar e-KYC** (`in.anytimework.atwbackend.kyc.infrastructure.SandboxAadhaarKycAdapter`, property `atw.aadhaar-kyc.provider`):

| Variable | Default | Example (Azure) | Meaning |
|---|---|---|---|
| `AADHAAR_KYC_PROVIDER` | `stub` | `sandbox` | Renamed from `ATW_AADHAAR_KYC_PROVIDER` on 2026-11-23 — now has a short alias just like `FACE_AUTH_PROVIDER`, instead of binding straight off the full `atw.aadhaar-kyc.provider` property path. `stub` (default) returns fake, fixed "verified" Aadhaar details for any OTP — never real UIDAI data. Set to `sandbox` for the real integration. |
| `SANDBOX_KYC_BASE_URL` | `https://api.sandbox.co.in` | — | Only read once the provider above is `sandbox`. Already correct by default; only override if Sandbox.co.in ever assigns a different host. |
| `SANDBOX_API_KEY` / `SANDBOX_API_SECRET` | *(empty)* | *(secret)* | Sandbox.co.in account credentials, exchanged for a bearer token via `/authenticate` on every call (no token caching, by design — see the adapter's Javadoc). **Shared with GST verification below** — the same two values, not two separate secrets. |
| `SANDBOX_KYC_TIMEOUT` | `PT20S` | — | HTTP client timeout for both the `/authenticate` and OTP send/verify calls. |

**Bank account verification** (`in.anytimework.atwbackend.worker.infrastructure.SandboxBankVerificationAdapter`, property `atw.bank-verification.provider`) — **its own, fully separate Sandbox.co.in account, never shared with the other two below**:

| Variable | Default | Example (Azure) | Meaning |
|---|---|---|---|
| `BANK_VERIFICATION_PROVIDER` | `stub` | `sandbox` | Renamed from `ATW_BANK_VERIFICATION_PROVIDER` on 2026-11-23, same reason as the Aadhaar one above. `stub` (default) returns a fake "account exists" penny-drop result for any account/IFSC — never a real bank check. Set to `sandbox` for the real integration. |
| `SANDBOX_BANK_BASE_URL` | `https://api.sandbox.co.in` | — | Logically independent of the other two base URLs even though it happens to default to the same host. |
| `SANDBOX_BANK_API_KEY` / `SANDBOX_BANK_API_SECRET` | *(empty)* | *(secret)* | **A separate Sandbox.co.in account/credential pair — do not reuse `SANDBOX_API_KEY`/`SANDBOX_API_SECRET` here.** This adapter's wire contract (endpoint path, field names) is a best-effort reading of Sandbox.co.in's public docs, not confirmed against a live instance and not ported from a working legacy reference (legacy used a different, dead vendor for this) — confirm against Sandbox's current docs before relying on it beyond a throwaway test call. |
| `SANDBOX_BANK_TIMEOUT` | `PT20S` | — | |

**GST verification** (`in.anytimework.atwbackend.company.infrastructure.SandboxGstVerificationAdapter`, property `atw.gst-verification.provider`) — **deliberately reuses the Aadhaar e-KYC credentials above, not a new secret**:

| Variable | Default | Example (Azure) | Meaning |
|---|---|---|---|
| `GST_VERIFICATION_PROVIDER` | `stub` | `sandbox` | Renamed from `ATW_GST_VERIFICATION_PROVIDER` on 2026-11-23, same reason as the Aadhaar one above. `stub` (default) returns a fake, fixed "active GSTIN" result for any GST number — never a real registry lookup. Set to `sandbox` for the real integration. |
| `SANDBOX_GST_BASE_URL` | `https://api.sandbox.co.in` | — | |
| `SANDBOX_API_KEY` / `SANDBOX_API_SECRET` | *(empty)* | *(secret)* | **The exact same two secrets as Aadhaar e-KYC's row above — not a separate credential.** GST verification is a second Sandbox.co.in product under the same already-wired account (confirmed against the legacy app's own PHP source), so nothing new needs adding to Key Vault here *if* those two secrets are already there — see the note below. |
| `SANDBOX_GST_TIMEOUT` | `PT20S` | — | |

> ⚠️ **Historical note:** this doc didn't mention any of the nine variables above, for any of the
> three integrations, until 2026-11-23 — before that date, all three may have silently been running
> the fake stub with no error anywhere to notice. Confirmed fixed and verified live in the dev
> environment the same day (all three correctly set to `sandbox`). On any new environment, check
> `az containerapp show --name <app> --resource-group <rg> --query
> "properties.template.containers[0].env"` (§4b) for `AADHAAR_KYC_PROVIDER` / `BANK_VERIFICATION_PROVIDER`
> / `GST_VERIFICATION_PROVIDER` before assuming any of the three is real.

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
| `ADMIN_MAX_FAILED_LOGINS` / `ADMIN_LOCKOUT_DURATION` | `5` / `PT15M` | **No longer enforced** — account-level lockout was removed in favor of the per-IP rate limit below + mandatory OTP. Safe to leave unset or set; it's inert either way. |
| `ADMIN_RATE_LIMIT_ENABLED` / `_MAX_ATTEMPTS` / `_WINDOW` | `true` / `10` / `PT1M` | Per-IP request cap on login/forgot-password/reset-password/accept-invite (Redis-backed, holds across replicas) |
| `ADMIN_OTP_IN_RESPONSE` | `false` | Test-only: puts the OTP directly in the login/resend-otp response instead of only emailing it. **Refused at startup outside local/test** — see §8. |
| `ADMIN_FIXED_OTP` | *(unset)* | Pre-SMTP dev-slot-only: every issued OTP (login + resend) is this fixed value instead of a random code, so you can log in by typing a known code instead of reading logs/email. Must be exactly `ADMIN_OTP_LENGTH` numeric digits. **Refused at startup unless `SPRING_PROFILES_ACTIVE` includes `dev`** — see §8. |
| `DOC_RECONCILE_STALE_AFTER` / `DOC_RECONCILE_INTERVAL` | `PT10M` / `PT5M` | How a background job self-heals a document upload that uploaded its blob but didn't finish committing |

> **No working email yet on a dev slot?** Set `SPRING_PROFILES_ACTIVE=dev` *and* `ADMIN_FIXED_OTP=<N
> digits>` together (both required — setting only one does nothing extra). Every admin then logs in
> with that same fixed code instead of a random one. This is meant to be temporary: unset
> `ADMIN_FIXED_OTP` the moment a real `MAIL_PROVIDER` is configured on that environment. Never set it
> anywhere `SPRING_PROFILES_ACTIVE` isn't exactly `dev` — startup refuses to boot if it is.

> Store every secret (`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ADMIN_BOOTSTRAP_PASSWORD`, mail
> credentials) as a **Container Apps secret** / App Service app setting / Key Vault reference — never
> in the image or in plain env var definitions in a pipeline YAML.

---

## 4. Build & run the container

**This section is for the one-time initial creation of the Container App resource itself (it doesn't
exist yet), or for a from-scratch manual deploy with no pipeline.** Once `.github/workflows/
deploy-backend.yml` is live against it, every ongoing config change goes through App Configuration /
Key Vault instead (§4b below) — anything set here directly gets overwritten by the next automated
deploy, since that pipeline rebuilds the full env-var set from those two stores every run.

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

### 4b. Adding/changing a setting once the pipeline manages this app (the normal path)

Pick App Configuration for a plain value, or Key Vault for a secret — never both for the same
setting, and never a Key Vault reference *inside* App Configuration (the deploy action rejects both).
Example: wiring the face-recognition service from §3 end-to-end:

```bash
# Plain values -> App Configuration, labeled "dev", one key-value per setting
az appconfig kv set --endpoint <app_config_endpoint> --auth-mode login --label dev \
  --key backend-face-auth-provider  --value python
az appconfig kv set --endpoint <app_config_endpoint> --auth-mode login --label dev \
  --key backend-face-auth-base-url --value https://face-auth-service-m2o7.onrender.com

# Secret -> Key Vault, enabled, same backend-<kebab-case> naming
az keyvault secret set --vault-name <key_vault_name> \
  --name backend-face-auth-api-key --value "<the real key>"
```

Example: wiring the three Sandbox.co.in integrations from §3 end-to-end (same pattern — provider
flag to App Configuration, secrets to Key Vault):

```bash
# Plain values -> App Configuration, labeled "dev" -- the three provider flags (none of these
# existed in this doc before; all three currently default to the stub if left unset)
az appconfig kv set --endpoint <app_config_endpoint> --auth-mode login --label dev \
  --key backend-aadhaar-kyc-provider --value sandbox
az appconfig kv set --endpoint <app_config_endpoint> --auth-mode login --label dev \
  --key backend-bank-verification-provider --value sandbox
az appconfig kv set --endpoint <app_config_endpoint> --auth-mode login --label dev \
  --key backend-gst-verification-provider --value sandbox

# Secrets -> Key Vault. Aadhaar e-KYC and GST verification deliberately share ONE Sandbox.co.in
# credential pair -- set it once and both integrations read the same two secrets (GST needs no
# command of its own here). VERIFY these two don't already exist first (they should have been added
# when Aadhaar e-KYC first shipped, but this doc never said to -- check with
# `az keyvault secret list --vault-name <key_vault_name> --query "[].name"` before assuming):
az keyvault secret set --vault-name <key_vault_name> \
  --name backend-sandbox-api-key --value "<the real Sandbox.co.in API key>"
az keyvault secret set --vault-name <key_vault_name> \
  --name backend-sandbox-api-secret --value "<the real Sandbox.co.in API secret>"

# Bank verification's Sandbox.co.in account is separately credentialed -- a different pair, never
# reused from the two commands above.
az keyvault secret set --vault-name <key_vault_name> \
  --name backend-sandbox-bank-api-key --value "<the real bank-verification API key>"
az keyvault secret set --vault-name <key_vault_name> \
  --name backend-sandbox-bank-api-secret --value "<the real bank-verification API secret>"
```

**No new Key Vault secret commands are needed for GST specifically** — it reuses
`backend-sandbox-api-key`/`backend-sandbox-api-secret` above rather than getting its own.

Nothing further to run by hand — the next push to `develop` (or a manual `workflow_dispatch`) picks
both up automatically and updates the Container App. To confirm before/without a full pipeline run,
`az containerapp show --name <app> --resource-group <rg> --query
"properties.template.containers[0].env"` lists the env vars the app actually has right now.

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

Migrations V17–V21 added the new `masterdata` module's reference data: `state`/`city` (V17 — all 36
states/UTs seeded; `city` left empty pending a real district import rather than hand-typed), a
structured, encrypted `admin_user_address` table replacing the free-text `address_line`/`pincode`
columns dropped from `admin_user_profile` (V18), `department`/`industry`/`designation`/`skill` plus
the `designation_industry` join table and new `department.*` permission rows — `industry.*`/
`designation.*`/`skill.*` already existed in the legacy permission seed, confirmed by query before
writing the migration to avoid a duplicate-insert (V19), `skill_category` and the effective-dated,
immutable `minimum_wage_rate` table plus the `designation.skill_category_id` link (V20), and a real
784-district seed of the `city` table sourced from `igod.gov.in` data, replacing the deliberately
empty V17 table (V21).

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

- **No insecure fallback for `JWT_SECRET`, `ADMIN_PII_ENCRYPTION_KEY`, `WORKER_PII_ENCRYPTION_KEY`, or
  `COMPANY_PII_ENCRYPTION_KEY`.** Startup has a dedicated check (`StartupSecurityValidator`) that
  refuses to boot — throws and exits non-zero — if, outside the `local`/`test` Spring profiles: the
  JWT secret is missing, shorter than 32 bytes, or the publicly-committed local-dev literal; **any**
  of the three PII keys (staff, worker, or company — same four checks, run independently for each) is
  missing, not valid base64, doesn't decode to exactly 32 bytes, or is the publicly-committed
  local-dev literal; or `ADMIN_OTP_IN_RESPONSE` is left on. Same check for `ADMIN_BOOTSTRAP_PASSWORD`
  when bootstrap is enabled. **A failed deploy here means a missing/weak secret (or a test-only flag
  left on), not a bug** — fix the env var, don't work around the check.
- **`ADMIN_FIXED_OTP` is gated the same strict way, plus a profile check.** It's refused at startup
  if set under any profile other than `dev` (a static, config-known OTP is a standing 2FA bypass for
  every admin — see §3), and refused if it isn't exactly `ADMIN_OTP_LENGTH` numeric digits. When it
  *is* active, startup logs a loud warning on every boot so it's never silently forgotten about.
- **Staff KYC numbers and bank account numbers are encrypted at rest** (AES-256-GCM, a fresh random
  IV per value) and masked in every API response unless the caller holds the narrower
  `admin_staff.view_kyc` / `admin_staff.view_bank_details` permission.
- **Masterdata icon uploads (industry/designation/skill) get the same scrutiny as any other file
  upload** — PNG/JPEG/WEBP only (SVG is deliberately excluded: it can embed `<script>` and would be
  served back to a browser that opens the image URL directly), a 2MB cap, and the actual file bytes
  are checked against a magic-byte signature rather than trusting the declared `Content-Type` header.
  Gated by the same per-resource RBAC as the rest of masterdata (`industry.edit` / `designation.edit`
  / `skill.edit`), not reachable by just any authenticated caller.
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
- **Business-date calculations (e.g. minimum-wage effective-dating) use `Asia/Kolkata` explicitly**
  (`ClockConfig`), independent of the container host's or JVM's default timezone — no `TZ` env var is
  needed for "today" to resolve correctly against Indian statutory dates.
- See **FRONTEND_HANDOVER.md** for the complete API reference (every endpoint, every error code) and
  **postman/** for eight ready-to-import Postman collections, split by module (Auth & Account, RBAC &
  Staff, Masterdata, Health, Worker, Worker-KYC, Worker-FaceAuth, and Company) rather than bundled
  into one file — both are kept current as the API evolves. The Company collection
  (`ATW-Company.postman_collection.json`) is new this same round, alongside GST verification. The
  frontend dev has already integrated against Auth & Account and RBAC & Staff; keep the pre-rename
  `staff.*`/`roles.*` permission codes in mind as a real, not just theoretical, compatibility concern
  if either of those is ever touched again.
