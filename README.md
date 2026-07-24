# TraceTick

A self-hosted technical support ticketing and infrastructure incident tracking system.

See [`CONTEXT.md`](./CONTEXT.md) for the domain vocabulary (Customer, User, Ticket, Tag,
IngestionConfiguration, Event).

## Stack

- **Backend** — Spring Boot 3.4 on Java 21, built with Gradle (Kotlin DSL). PostgreSQL 16
  with Liquibase-managed schema. Session-cookie auth via Spring Security (wired up in T2).
- **Frontend** — Vite + React 19 + TypeScript, with React Router and TanStack React Query.
- **CI** — GitHub Actions, on every PR and push to `main`.

The v1 architectural decisions are recorded as ADRs under
[`docs/architecture/adrs/`](./docs/architecture/adrs/).

## Repository layout

```
.
├── AGENTS.md                   Notes for engineering agents (skills, conventions)
├── CONTEXT.md                  Domain glossary (single source of truth for terminology)
├── docs/
│   ├── agents/                 How agents interact with this repo
│   └── architecture/adrs/      Architectural decision records
├── backend/                    Spring Boot service
│   ├── src/main/java/com/tracetick/
│   │   ├── api/                REST controllers, DTOs, error mapping
│   │   ├── auth/               Spring Security wiring (placeholder until T2)
│   │   ├── domain/             Entities and core business invariants
│   │   ├── ingest/             Webhook receiver, HMAC verification, dedup
│   │   ├── notifications/      Outbound notification seam (interface only in v1)
│   │   ├── persistence/        Spring Data JPA repositories, Liquibase schema
│   │   └── TraceTickApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/changelog/       Liquibase changesets
│   └── src/test/...
├── frontend/                   Vite + React SPA
│   ├── src/
│   │   ├── components/
│   │   ├── lib/                API client, React Query setup
│   │   ├── pages/              Route components
│   │   ├── routes/             Router definition
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── index.html
│   ├── vite.config.ts
│   └── package.json
└── docker-compose.yml          Local Postgres 16
```

## Prerequisites

- **Java 21** (Temurin or any distribution). `JAVA_HOME` must point at it.
- **Node 24** and **npm 11**.
- **Docker** with the Compose plugin (for Postgres and for Testcontainers-based backend
  tests).
- **Gradle** is **not** required — the wrapper downloads its own Gradle.

## Local development

### 1. Start Postgres

```bash
docker compose up -d
```

This brings up Postgres 16 on `localhost:5432` with database `tracetick`, user `tracetick`,
password `tracetick`. Data is persisted in the `tracetick-postgres-data` named volume.

### 2. Start the backend

```bash
cd backend
./gradlew bootRun
```

The application starts on `http://localhost:8080`. It connects to the Postgres instance
above using the env vars `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` (or the defaults in `application.yml`).

Smoke-test:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

The HTTP API is auto-documented via springdoc-openapi:

- **OpenAPI 3 JSON** — `http://localhost:8080/v3/api-docs`
- **Swagger UI** — `http://localhost:8080/swagger-ui.html`

Both endpoints are reachable without auth in dev, since they only expose the API surface —
not data. Locking them down is a separate ticket (see ADR-0007).

Liquibase runs at startup and creates the `databasechangelog` and `databasechangeloglock`
tables. No entity tables exist yet — those land in T4.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The dev server starts on `http://localhost:5173`. API requests to `/api/*` are proxied to
`http://localhost:8080` (override with the `VITE_API_PROXY` env var).

The v1 routes are registered, but every page is a placeholder until its owning ticket
lands. Login, ticket list, and ticket detail UIs are not implemented in this foundation
ticket.

## Tests

### Backend

```bash
cd backend
./gradlew test
```

Tests use Testcontainers to spin up an ephemeral Postgres per run. They require Docker.
Test results land in `backend/build/reports/tests/test/`.

### Frontend

```bash
cd frontend
npm run lint
npm run typecheck
npm run build
```

The frontend ships with ESLint (flat config), TypeScript strict mode, and a production
build. Component tests and Playwright E2E are added in later tickets.

## CI

`.github/workflows/ci.yml` runs two jobs on every PR:

1. **Backend** — sets up JDK 21 with the Gradle cache, runs `./gradlew build`, uploads test
   results.
2. **Frontend** — sets up Node 24 with the npm cache, runs `npm ci`, then `lint`,
   `typecheck`, and `build`.

A Postgres service container backs the backend job.

## Tickets

Tickets are tracked as GitHub issues. The current plan lives in
[#1 (TraceTick v1 spec)](https://github.com/jrnolascosilva/tracetick/issues/1). Children:

- T1 — Foundation + v1 ADRs (this ticket).
- T2 — Auth + User admin.
- T3 — Password reset.
- T4 — Ticket domain + REST + filtering + role-scoping + events.
- T5 — Tags + watchers backend.
- T6 — Ticket list + detail UI.
- T7 — Ticket create UI.
- T8 — Comments UI.
- T9 — IngestionConfiguration backend.
- T10 — Webhook ingest (HMAC + dedup + extraction).
- T11 — Ingestion admin UI.
- T14 — OpenAPI documentation (springdoc).

## License

TBD.