import type { FullConfig } from '@playwright/test';

import { ADMIN_EMAIL, ADMIN_PASSWORD, API_BASE } from './test-config';

/**
 * Playwright global setup for the TraceTick happy-path E2E test.
 *
 * Runs once before any spec. The stack itself (Postgres, backend, frontend)
 * is brought up by Playwright's `webServer` array — including a fresh-volume
 * `docker compose down -v && docker compose up postgres` chain inside the
 * Postgres webServer command, so per-run isolation happens there, not here.
 *
 * Pre-cleaning inside globalSetup is intentionally NOT done: Playwright runs
 * globalSetup concurrently with webServer, so a pre-clean here would race
 * with the backend booting and could drop the database out from under the
 * already-connected JDBC pool.
 *
 * This file's only responsibility is the post-boot smoke check: log in as
 * the bootstrap admin to prove Liquibase and the bootstrap admin User both
 * succeeded.
 *
 * The backend's `BootstrapInitializer` (`CommandLineRunner`) creates the
 * single Customer row and the admin User (TECHNICIAN role) on first boot, so
 * no separate SQL seed step is required.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use.baseURL ?? 'http://localhost:5173';

  console.log(`[e2e global-setup] baseURL=${baseURL} api=${API_BASE}`);

  if (process.env.SKIP_E2E_IN_SANDBOX === '1') {
    console.log('[e2e global-setup] SKIP_E2E_IN_SANDBOX=1 — skipping admin login smoke check');
    return;
  }

  const response = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => '<no body>');
    throw new Error(
      `[e2e global-setup] Admin login failed (${response.status} ${response.statusText}): ${body}\n` +
        `Is the backend up with bootstrap enabled? Expected admin email=${ADMIN_EMAIL}.`,
    );
  }

  console.log(`[e2e global-setup] Admin login OK (${ADMIN_EMAIL})`);
}