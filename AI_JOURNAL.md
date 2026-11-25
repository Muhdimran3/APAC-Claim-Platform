# AI Working Journal - APAC Claims Platform

## Guardrails & Scope
- **Repo:** apac-claims-platform (strictly local; no public hosting or Chubb branding/proprietary assets)
- **Core Split:** REST/HTTP for direct commands and queries; Kafka for claim lifecycle events, workload tracking, and liability exposure updates

## Log Entries
### Entry 1 - Repo & Journal Initialization
- **Prompt / Direction:** Initialized local repository inside C:\Users\Imran\Documents before writing backend or frontend code.
- **Accepted:** Neutral project naming and backend/frontend folder layout.
- **Challenged / Overrode:** Kept the repository strictly local and scoped to core workflows first.
- **Reasoning:** Prevents public exposure and keeps development focused on the core backend and UI requirements.

### Entry 2 - Infra (docker-compose) + Spring Boot skeleton
- **Prompt / Direction:** Asked AI to create `docker-compose.yml` (Postgres + Kafka) and a Spring Boot skeleton in `backend/`, then list the design decisions to confirm before domain logic. Planned first, approved the plan, then let it execute.
- **Accepted:**
  - Single-node Kafka in **KRaft** mode (`apache/kafka:4.1.0`), no ZooKeeper, so there's one fewer container and it's the current Kafka default.
  - Broker topic auto-create **off**. The app declares `claim-events` (3 partitions, keyed by claimId) via a `NewTopic` bean, so topology is versioned with code and events per claim stay ordered.
  - **Flyway owns schema**, `ddl-auto: validate`. No silent Hibernate schema drift.
  - Skeleton generated from start.spring.io, plus springdoc-openapi for a backend-owned API contract.
  - Testcontainers images pinned to the same versions as compose (generator defaulted to `:latest`).
  - Module boundaries recorded as `package-info.java` (claim / workload / exposure / common.events).
- **Challenged / Overrode:**
  - AI's plan said Spring Boot 3.3.x. The generator now defaults to **4.2.0**, and 3.3 is out of support, so I kept 4.2 (still targets Java 17).
  - AI first claimed a JDK 17 was installed. It had misread `JAVA_HOME` (which pointed to a non-existent `jdk-17` folder). The build failure exposed it, and I installed **Temurin JDK 25** myself. A stale user-level `JAVA_HOME` still overrides the correct machine-level one and needs fixing.
  - AI asked about Docker being missing. I installed Docker Desktop rather than accept an H2/embedded-Kafka fallback (prod-like infra > convenience).
  - Rejected Lombok. Records and plain Java mean less generated magic to defend.
- **Decision:** **Modular monolith** over multiple services. With the 2-3h budget, a single transactional boundary for claim state wins; Kafka between modules keeps the split option open.
- **Verified:** `docker compose up -d --wait` shows both healthy; `mvnw verify` passes (context loads with Flyway + Kafka via Testcontainers); app on compose stack gives `/actuator/health` UP, Swagger 200, `claim-events` topic created with 3 partitions.
