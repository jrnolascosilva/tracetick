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

1. `globalSetup` runs `docker compose -f docker-compose.e2e.yml down -v`
   *before* the webServer comes up. This drops any leftover volume from a
   previous run that crashed before reaching teardown.
2. `webServer` brings the e2e Postgres up against a fresh, empty volume.
   The backend's `SPRING_DATASOURCE_URL` is overridden by
   `frontend/playwright.config.ts` to point at `localhost:5433` for the
   duration of the suite.
3. `globalTeardown` runs `docker compose -f docker-compose.e2e.yml down -v`
   after the suite, dropping the per-run volume so the next
   `npm run test:e2e` starts on an empty DB.

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