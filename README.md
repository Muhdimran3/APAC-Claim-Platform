# APAC Claims Platform

Local-only claims management platform (take-home assessment). Claimants and claims staff use one Angular app, backed by a Spring Boot service with PostgreSQL and Kafka.

> Runs locally only. No public hosting, no third-party branding.

## Layout

| Path | What |
|---|---|
| `docker-compose.yml` | PostgreSQL 16 + Kafka 4.1 (KRaft, single node); optional kafka-ui |
| `backend/` | Spring Boot 4.2 service (`claims-service`), Java 17 target |
| `frontend/` | Angular app (later step) |
| `AI_JOURNAL.md` | Log of AI prompts, what was accepted, challenged and overridden |

## Prerequisites

- Docker Desktop (running)
- JDK 17+ (tested on Temurin 25). `JAVA_HOME` must point at a real JDK.
- Maven is **not** required, because the backend ships with the Maven Wrapper.

## Run

```powershell
# 1. Infrastructure
docker compose up -d --wait
# optional: Kafka UI on http://localhost:8090
docker compose --profile tools up -d

# 2. Backend
cd backend
.\mvnw.cmd spring-boot:run
```

| URL | |
|---|---|
| http://localhost:8080/actuator/health | health (db status included) |
| http://localhost:8080/swagger-ui.html | API docs / try-it |
| http://localhost:8080/v3/api-docs | OpenAPI JSON (source for frontend client types) |

## Test

```powershell
cd backend
.\mvnw.cmd verify    # uses Testcontainers (own Postgres + Kafka), so compose doesn't need to be running
```

## Configuration

These environment variables override the local defaults in `backend/src/main/resources/application.yml`:
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP`.

## Architecture (summary)

Modular monolith: one deployable with package-per-module (`claim`, `workload`, `exposure`).
- **REST**: commands and queries where the caller needs an immediate answer or validation (submit, assign, transition, decide, list).
- **Kafka (`claim-events`, keyed by claimId)**: lifecycle events consumed by the workload and exposure read models. Modules share only the event contracts, so they can be split into services later.
- **Flyway** owns the schema, and Hibernate only validates it.
