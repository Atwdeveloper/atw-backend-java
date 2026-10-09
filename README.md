# ATW Backend

Backend service for ATW, built with **Spring Boot 4.1** on **Java 25**, using **PostgreSQL**
(schema managed by **Flyway**) and **Redis**.

> **Current status:** this repo contains a small **smoke-test API** used to validate the Azure
> deployment environment before real feature work begins. The temporary bits (the `testapi`
> package and the `test_item` table) will be removed once the environment is proven.
> **Deploying to Azure? See [`HANDOVER.md`](HANDOVER.md).**

## Tech stack

| Area | Choice |
|---|---|
| Language / runtime | Java 25 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Actuator) |
| Database | PostgreSQL (Flyway migrations, Hibernate `validate`) |
| Cache | Redis |
| Build | Maven (via the `./mvnw` wrapper) |
| Container | Multi-stage `Dockerfile` (Temurin 25) |

## Run locally

**Prerequisites:** JDK 25, a PostgreSQL database, and a Redis server. Connection settings come from
environment variables (defaults target `localhost`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/atw` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `atw` / `atw` | DB credentials |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `REDIS_PASSWORD` / `REDIS_SSL` | *(empty)* / `false` | Redis auth/TLS (required on Azure) |
| `SERVER_PORT` | `8080` | HTTP port |

```bash
./mvnw spring-boot:run
```

Flyway applies migrations from `src/main/resources/db/migration` on startup.

## Smoke-test endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/actuator/health` | Health (open; shows db/redis detail when authenticated) |
| GET | `/api/test/items` | List test items |
| GET | `/api/test/items/{id}` | Get one (404 if missing) |
| POST | `/api/test/items` | Create — body `{"name":"..."}` |

```bash
curl -X POST http://localhost:8080/api/test/items \
  -H "Content-Type: application/json" -d '{"name":"hello"}'
curl http://localhost:8080/api/test/items
```

## Build a container image

```bash
docker build -t atw-backend:smoke .
```

See [`HANDOVER.md`](HANDOVER.md) for the full Azure deployment guide.
