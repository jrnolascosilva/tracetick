import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import type { FullConfig } from '@playwright/test';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

/**
 * Playwright global teardown for the TraceTick happy-path E2E test.
 *
 * Runs once after all specs. The Playwright webServer cleanup has already
 * SIGTERMed the e2e Postgres container by the time this runs, but the
 * dedicated e2e volume (`tracetick-e2e-postgres-data`) survives a plain
 * `down`. We run `docker compose ... down -v` to drop the volume so the next
 * `npm run test:e2e` invocation starts on an empty DB.
 *
 * Safe to re-run: if no container/volume exists, `down -v` exits 0.
 */
export default async function globalTeardown(_config: FullConfig): Promise<void> {
  console.log('[e2e global-teardown] Dropping e2e Postgres volume');
  try {
    execFileSync(
      'docker',
      ['compose', '-f', 'docker-compose.e2e.yml', 'down', '-v', '--remove-orphans'],
      { cwd: repoRoot, stdio: 'inherit' },
    );
    console.log('[e2e global-teardown] e2e Postgres volume dropped');
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.warn(`[e2e global-teardown] docker compose down -v failed: ${message}`);
    // Do not throw — the suite already passed; surfacing this as an error
    // would mask the green result. The next run's globalSetup will re-clean.
  }
}