import type { FullConfig } from '@playwright/test';

/**
 * Playwright global setup for the TraceTick happy-path E2E test.
 *
 * Runs once before any spec. The stack itself (Postgres, backend, frontend)
 * is brought up by Playwright's `webServer` array — this file only does a
 * post-boot sanity check and prints a status banner.
 *
 * The backend's `BootstrapInitializer` (`CommandLineRunner`) creates the single
 * Customer row and the admin User (TECHNICIAN role) on first boot, so there is
 * no separate SQL seed step to run here. The smoke check below simply confirms
 * the admin can log in, which proves Liquibase + the bootstrap succeeded.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use.baseURL ?? 'http://localhost:5173';
  const apiBase = process.env.E2E_API_BASE ?? 'http://localhost:8080/api/v1';
  const adminEmail = process.env.E2E_ADMIN_EMAIL ?? 'admin@tracetick.local';
  const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? 'changeme';

  console.log(`[e2e global-setup] baseURL=${baseURL} api=${apiBase}`);

  if (process.env.SKIP_E2E_IN_SANDBOX === '1') {
    console.log('[e2e global-setup] SKIP_E2E_IN_SANDBOX=1 — skipping admin login smoke check');
    return;
  }

  const response = await fetch(`${apiBase}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: adminEmail, password: adminPassword }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => '<no body>');
    throw new Error(
      `[e2e global-setup] Admin login failed (${response.status} ${response.statusText}): ${body}\n` +
        `Is the backend up with bootstrap enabled? Expected admin email=${adminEmail}.`,
    );
  }

  console.log(`[e2e global-setup] Admin login OK (${adminEmail})`);
}