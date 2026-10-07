# ATW Backend — Azure Deployment Handover

A minimal Spring Boot service used to **validate the Azure environment and pipeline** before the
real project. It exposes a tiny test API backed by PostgreSQL (via Flyway migrations) and Redis.
If this boots healthy on Azure, the environment is proven.

> ⚠️ **This is throwaway pipe-cleaner code** (the `testapi` package + `test_item` table). It will be
> removed before real feature work. Treat it only as a deployment smoke test.

---

## 1. Stack / runtime requirements

| Component | Version | Notes |
|---|---|---|
| Java | **25** | Build and runtime base image must be JDK/JRE 25 (Eclipse Temurin). |
| Spring Boot | 4.1.1 | |
| Build | Maven (via included `./mvnw` wrapper) | No local Maven needed; wrapper fetches 3.9.16. |
| PostgreSQL | 15+ (dev used 18) | Azure Database for PostgreSQL – Flexible Server. |
| Redis | 6+ (dev used 8) | Azure Cache for Redis. |

Packaging produces a single executable jar: `target/atw-backend-0.0.1-SNAPSHOT.jar`.

---

## 2. Azure resources to provision

1. **Azure Container Registry (ACR)** — to hold the image.
2. **Azure Database for PostgreSQL – Flexible Server** — create an empty database (e.g. `atw`).
   - The app user needs **DDL privileges** (Flyway creates tables on startup).
   - Require SSL (`sslmode=require`).
3. **Azure Cache for Redis** — note the host, port **6380 (TLS)**, and an **access key**.
4. **Azure Container Apps** (recommended) or **App Service for Containers** — to run the image.
5. **Networking** — the container must reach the DB and Cache: allow it through their firewalls
   (private endpoint / VNet integration, or "Allow Azure services" for a quick test).

---

## 3. Configuration — environment variables (the contract)

The app is **12-factor**: all connection info comes from environment variables. No code changes
are needed between local and Azure — only these variables differ.

| Variable | Required | Example (Azure) | Meaning |
|---|---|---|---|
| `DB_URL` | ✅ | `jdbc:postgresql://<srv>.postgres.database.azure.com:5432/atw?sslmode=require` | JDBC URL |
| `DB_USERNAME` | ✅ | `atwadmin` | DB user (needs DDL rights) |
| `DB_PASSWORD` | ✅ | *(secret)* | DB password |
| `REDIS_HOST` | ✅ | `<name>.redis.cache.windows.net` | Cache host |
| `REDIS_PORT` | ✅ | `6380` | TLS port on Azure |
| `REDIS_PASSWORD` | ✅ | *(access key)* | Cache access key |
| `REDIS_SSL` | ✅ | `true` | Enable TLS (Azure requires it) |
| `SERVER_PORT` | optional | `8080` | HTTP port (default 8080) |
| `JAVA_OPTS` | optional | `-XX:MaxRAMPercentage=75` | Extra JVM flags |

> Store `DB_PASSWORD` and `REDIS_PASSWORD` as **secrets** (Container Apps secrets / App Service
> app settings / Key Vault), never in the image.

Defaults for local dev (used when the vars are unset): `localhost` Postgres/Redis, no TLS, no
Redis password. So the same image runs locally too.

---

## 4. Build & run the container

```bash
# Build (run from the repo root)
docker build -t atw-backend:smoke .

# Push to ACR
az acr login --name <acrName>
docker tag atw-backend:smoke <acrName>.azurecr.io/atw-backend:smoke
docker push <acrName>.azurecr.io/atw-backend:smoke
```

Run it with the environment variables from section 3 (Container Apps example):

```bash
az containerapp create \
  --name atw-backend --resource-group <rg> --environment <cae> \
  --image <acrName>.azurecr.io/atw-backend:smoke \
  --target-port 8080 --ingress external \
  --env-vars DB_URL=... DB_USERNAME=... REDIS_HOST=... REDIS_PORT=6380 REDIS_SSL=true \
  --secrets dbpass=... redispass=... \
  --env-vars DB_PASSWORD=secretref:dbpass REDIS_PASSWORD=secretref:redispass
```

---

## 5. Health probes (for Azure)

| Probe | Path |
|---|---|
| Liveness | `GET /actuator/health/liveness` |
| Readiness | `GET /actuator/health/readiness` |
| Overall (shows db + redis when authorized) | `GET /actuator/health` |

`/actuator/health` returns `{"status":"UP"}` and is **open** (no auth) so probes work. The
detailed component view (db, redis) is shown only to authenticated callers.

---

## 6. Database migrations (Flyway)

- Flyway runs **automatically on startup** and applies `src/main/resources/db/migration/V*.sql`.
- It creates its own `flyway_schema_history` table and the `test_item` table.
- Therefore the DB must be **reachable at boot** and the user must have **DDL rights**.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate only validates; it never alters schema.

---

## 7. Validate the deployment (smoke test)

Once running, against the app's public URL `<BASE>`:

```bash
# Health
curl <BASE>/actuator/health            # -> {"status":"UP"}

# Create (POST)  -> 201
curl -X POST <BASE>/api/test/items -H "Content-Type: application/json" -d '{"name":"azure smoke"}'

# List (GET)
curl <BASE>/api/test/items

# Get by id
curl <BASE>/api/test/items/1
```

If POST persists a row and it survives a container restart, **Postgres connectivity is proven**.
If `/actuator/health` (authenticated) shows `redis: UP`, **Redis connectivity is proven**.

---

## 8. Notes / caveats

- **Security is intentionally permissive** for this smoke test (`/api/test/**` is open, CSRF off).
  This is fine for a throwaway validation; the real app will add proper auth.
- **Java 25 is new** — ensure the Temurin 25 base images are pullable in your environment.
- No HTTPS/TLS termination is handled by the app; terminate TLS at the Azure ingress.
