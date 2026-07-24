# TraceTick E2E tests

Playwright-based end-to-end tests for the TraceTick stack. This folder contains
exactly one happy-path spec that drives the full UI flow a `TECHNICIAN` user
follows from sign-in to a resolved Ticket.

## What runs

```
npm run test:e2e
```

This command invokes Playwright against `frontend/e2e/happy-path.spec.ts`,
which exercises:

1. login as the bootstrap admin user (`TECHNICIAN` role)
2. create a `HUMAN`-origin Ticket via the new-ticket form
3. post a comment on the Ticket
4. transition the Ticket `OPEN → IN_PROGRESS` via the UI
5. transition the Ticket `IN_PROGRESS → RESOLVED` via the UI

The stack (Postgres + Spring Boot backend + Vite dev server) is brought up by
Playwright's `webServer` array — see `frontend/playwright.config.ts`. The
backend's `BootstrapInitializer` (`CommandLineRunner`) seeds the single
`Customer` row and the admin `User` on first boot, so no manual SQL seed is
required.

## Bootstrap admin credentials (dev only)

| Field    | Value                  |
| -------- | ---------------------- |
| email    | `admin@tracetick.local` |
| password | `changeme`             |
| role     | `TECHNICIAN`           |

Override via environment variables when launching the backend:

- `TRACETICK_BOOTSTRAP_ADMIN_EMAIL`
- `TRACETICK_BOOTSTRAP_ADMIN_PASSWORD`

These defaults exist **for local development and E2E tests only**. Never deploy
them.

## Prereqs

- Node.js 20+ (frontend dev server + Playwright runner)
- Docker (for `postgres` service in `docker-compose.yml`)
- Java 21 (Spring Boot backend via Gradle)

## First-time setup

Install Playwright's Chromium binary. On a developer machine with sudo:

```
cd frontend
npx playwright install --with-deps chromium
```

In restricted environments where `--with-deps` cannot install system packages:

```
npx playwright install chromium
```

## Resetting the database between runs

The single happy-path spec does not require a DB reset between runs — the
backend's `BootstrapInitializer` is idempotent (it only inserts the admin user
if missing), and the spec creates a fresh Ticket each run.

For a fully clean state (e.g., after schema changes or to wipe manually-created
data), reset Postgres from the repo root:

```
docker compose down -v
docker compose up -d postgres
```

This drops the `tracetick-postgres-data` named volume and recreates an empty
`tracetick` database. The backend's Liquibase changelogs re-apply on the next
`bootRun`, and the bootstrap admin is recreated.

## Configuration overrides

The global setup and the spec use these environment variables (all optional):

- `E2E_API_BASE` — API base URL for the smoke-check login. Defaults to
  `http://localhost:8080/api/v1`.
- `E2E_ADMIN_EMAIL` — admin email for the smoke-check login. Defaults to
  `admin@tracetick.local`.
- `E2E_ADMIN_PASSWORD` — admin password for the smoke-check login. Defaults to
  `changeme`.
- `SKIP_E2E_IN_SANDBOX` — if set to `1`, `global-setup.ts` skips the admin
  login smoke check (useful in sandboxes where the backend cannot be reached).
  The spec itself will still attempt to drive the UI and fail if the stack is
  not actually running.

## Running a single spec or filtering

```
npx playwright test happy-path.spec.ts                # one spec
npx playwright test --headed                           # visible browser
npx playwright test --project=chromium                 # explicit project
npx playwright test --grep "resolve"                   # name filter
```

## Sandbox execution

In environments where Docker, the browser, or the JDK are unavailable, the
stack cannot come up and `npm run test:e2e` will fail. Set
`SKIP_E2E_IN_SANDBOX=1` to suppress the global-setup smoke check; the spec
itself will still attempt to drive the browser and report the missing stack
cleanly via Playwright's normal failure surface.

## CI

CI should run the test against a fresh Docker Compose stack. Recommended
order:

```
docker compose up -d postgres
./backend/gradlew bootRun &    # or build an image
cd frontend && npm run dev &
cd frontend && npm run test:e2e
```

The `webServer` array in `playwright.config.ts` already handles this lifecycle
for local development; CI pipelines can either rely on it or start the stack
out-of-band.