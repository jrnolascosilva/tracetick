import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for the TraceTick happy-path E2E test.
 *
 * Brings the full stack up via the `webServer` array (supported since Playwright
 * 1.30 — verified against @playwright/test 1.61):
 *   1. E2E Postgres (host port 5433) — `docker compose -f docker-compose.e2e.yml up postgres`
 *   2. Backend (Spring Boot, port 8080) — `./gradlew bootRun` from backend/
 *   3. Frontend (Vite dev, port 5173) — `npm run dev` from frontend/
 *
 * The backend's `BootstrapInitializer` (CommandLineRunner, see
 * `backend/src/main/java/com/tracetick/auth/BootstrapInitializer.java`) seeds the
 * single Customer row and the admin User (TECHNICIAN role) on first boot, so no
 * separate SQL seed step is required. Credentials come from
 * `TRACETICK_BOOTSTRAP_ADMIN_EMAIL` and `TRACETICK_BOOTSTRAP_ADMIN_PASSWORD`
 * (defaults `admin@tracetick.local` / `changeme`).
 *
 * Test isolation strategy:
 *   - The e2e Postgres lives in its own dedicated compose file
 *     (`docker-compose.e2e.yml`) on host port 5433 with a dedicated volume
 *     (`tracetick-e2e-postgres-data`). The developer's local dev DB on port
 *     5432 is never touched.
 *   - `globalSetup` pre-cleans any leftover container/volume from a previous
 *     crash that did not reach teardown.
 *   - `globalTeardown` always runs `docker compose ... down -v` so the next
 *     run starts on an empty DB. Two consecutive `npm run test:e2e`
 *     invocations each see a fresh database.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: process.env.CI ? 'line' : 'list',
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 10_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      // Bring the e2e Postgres up before the backend tries to connect. The
      // command chain `down -v` first to drop any leftover container/volume
      // from a previous run that crashed before reaching teardown, then
      // `up postgres` starts a fresh container against an empty volume. The
      // compose command stays in the foreground without `-d`, so Playwright
      // keeps the process alive while it polls host port 5433, then SIGTERMs
      // it after the tests. `reuseExistingServer: false` matters — if port
      // 5433 is already listening for another reason we'd otherwise attach
      // to a stale container whose volume still holds the previous run's
      // data. Surface that as a real failure instead.
      command:
        'docker compose -f docker-compose.e2e.yml down -v --remove-orphans; ' +
        'docker compose -f docker-compose.e2e.yml up postgres',
      port: 5433,
      reuseExistingServer: false,
      timeout: 120_000,
      cwd: '..',
    },
    {
      // Spring Boot via Gradle. The health endpoint returns 200 once the
      // application context is up and Liquibase + the bootstrap admin seed
      // have finished. We override SPRING_DATASOURCE_URL so the backend
      // points at the e2e Postgres on port 5433, not the developer's port
      // 5432 dev DB.
      command: './gradlew bootRun',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: false,
      timeout: 240_000,
      cwd: '../backend',
      stdout: 'pipe',
      stderr: 'pipe',
      env: {
        SPRING_DATASOURCE_URL: 'jdbc:postgresql://localhost:5433/tracetick',
        SPRING_DATASOURCE_USERNAME: 'tracetick',
        SPRING_DATASOURCE_PASSWORD: 'tracetick',
      },
    },
    {
      // Vite dev server. The /api proxy is configured to forward to
      // http://localhost:8080, so the backend must already be up before this
      // serves real traffic — Playwright starts them in array order.
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: false,
      timeout: 60_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
});