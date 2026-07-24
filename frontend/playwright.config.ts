import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for the TraceTick happy-path E2E test.
 *
 * Brings the full stack up via the `webServer` array (supported since Playwright
 * 1.30 — verified against @playwright/test 1.61):
 *   1. Postgres (port 5432) — `docker compose up -d postgres`
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
 *   - One happy-path test, so per-test isolation is unnecessary.
 *   - For a fully clean run between manual invocations, reset with
 *     `docker compose down -v && docker compose up -d postgres` from the repo
 *     root. See `frontend/e2e/README.md`.
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
      // Bring Postgres up before the backend tries to connect. We run the
      // compose command WITHOUT `-d` so the process stays in the foreground;
      // Playwright keeps the process alive while it polls port 5432, then
      // SIGTERMs it after the tests. `reuseExistingServer` lets a developer
      // who already ran `docker compose up -d postgres` locally skip the boot.
      command: 'docker compose up postgres',
      port: 5432,
      reuseExistingServer: true,
      timeout: 120_000,
      cwd: '..',
    },
    {
      // Spring Boot via Gradle. The health endpoint returns 200 once the
      // application context is up and Liquibase + the bootstrap admin seed have
      // finished. We don't gate on `db` health specifically — the readiness
      // probe goes 200 once the JDBC pool can hand out a connection.
      command: './gradlew bootRun',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: false,
      timeout: 240_000,
      cwd: '../backend',
      stdout: 'pipe',
      stderr: 'pipe',
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