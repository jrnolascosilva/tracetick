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

The stack (E2E Postgres + Spring Boot backend + Vite dev server) is brought up
by Playwright's `webServer` array — see `frontend/playwright.config.ts`. The
backend's `BootstrapInitializer` (`CommandLineRunner`) seeds the single
`Customer` row and the admin `User` on first boot, so no manual SQL seed is
required.

## Test isolation — fresh DB per run

The e2e Postgres lives in its own dedicated compose file
(`docker-compose.e2e.yml` at the repo root) with its own container
(`tracetick-e2e-postgres`), its own host port (`5433`), and its own named
volume (`tracetick-e2e-postgres-data`). The developer's local dev DB on host
port 5432 (`docker-compose.yml`'s `tracetick-postgres`) is **never touched**
by the e2e run — both stacks can be up simultaneously without interfering.

Per-run isolation is automatic — no manual step:

1. The Postgres `webServer` entry's command chains
   `docker compose -f docker-compose.e2e.yml down -v --remove-orphans; docker compose -f docker-compose.e2e.yml up postgres`.
   The `down -v` is idempotent (no-op when nothing exists), so the
   webServer always brings Postgres up against a fresh, empty volume —
   even if a previous run crashed before reaching teardown.
   Pre-clean is intentionally **not** done in `globalSetup` because
   Playwright runs `globalSetup` concurrently with the `webServer` array,
   and a pre-clean there would race with the backend booting and drop
   the database out from under the already-connected JDBC pool.
2. The backend `webServer` entry overrides `SPRING_DATASOURCE_URL` to
   point at `localhost:5433` for the duration of the suite, so it talks
   to the e2e Postgres and never the developer's local dev DB on 5432.
3. `globalTeardown` runs `docker compose -f docker-compose.e2e.yml down -v --remove-orphans`
   after the suite, dropping the per-run volume so the next
   `npm run test:e2e` starts on an empty DB. Teardown failures are
   logged as warnings rather than thrown, so a green suite result is
   not masked by a teardown glitch.

Two consecutive `npm run test:e2e` invocations in the same shell session
each see an empty `tracetick` database at startup.

## Bootstrap admin credentials (dev only)

| Field    | Value                   |
| -------- | ----------------------- |
| email    | `admin@tracetick.local` |
| password | `changeme`              |
| role     | `TECHNICIAN`            |

Override via environment variables when launching the backend:

- `TRACETICK_BOOTSTRAP_ADMIN_EMAIL`
- `TRACETICK_BOOTSTRAP_ADMIN_PASSWORD`

These defaults exist **for local development and E2E tests only**. Never deploy
them.

## Prereqs

- Node.js 20+ (frontend dev server + Playwright runner)
- Docker (for the e2e Postgres service in `docker-compose.e2e.yml`)
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

## Configuration overrides

`frontend/e2e/test-config.ts` is the single source of truth for these
defaults. Both `global-setup.ts` (which logs in as a smoke check) and
`happy-path.spec.ts` (which drives the login form) import from it, so they
cannot drift apart.

- `E2E_API_BASE` — API base URL used by the smoke-check login. Defaults to
  `http://localhost:8080/api/v1`.
- `E2E_ADMIN_EMAIL` — admin email used by both the smoke check and the spec.
  Defaults to `admin@tracetick.local`.
- `E2E_ADMIN_PASSWORD` — admin password used by both the smoke check and the
  spec. Defaults to `changeme`.
- `SKIP_E2E_IN_SANDBOX` — if set to `1`, `global-setup.ts` skips the admin
  login smoke check (useful in sandboxes where the backend cannot be reached).
  The spec itself will still attempt to drive the UI and fail if the stack is
  not actually running.

Setting `E2E_ADMIN_EMAIL` and `E2E_ADMIN_PASSWORD` (alongside the matching
`TRACETICK_BOOTSTRAP_ADMIN_EMAIL` / `TRACETICK_BOOTSTRAP_ADMIN_PASSWORD` env
vars when starting the backend) lets the smoke check and the spec agree on
non-default credentials — without those overrides, the smoke check would
succeed but the browser login in the spec would silently fail.

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
docker compose -f docker-compose.e2e.yml up -d postgres
cd backend && ./gradlew bootRun &
cd frontend && npm run dev &
cd frontend && npm run test:e2e
```

The `webServer` array in `playwright.config.ts` already handles this lifecycle
for local development; CI pipelines can either rely on it or start the stack
out-of-band.