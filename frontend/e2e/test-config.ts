/**
 * Shared configuration for the TraceTick happy-path E2E test.
 *
 * Single source of truth for env-var reads so `global-setup.ts` (which logs
 * into the API as a smoke check) and `happy-path.spec.ts` (which drives the
 * login form in the browser) cannot drift. Defaults match the backend
 * `BootstrapInitializer` defaults in
 * `backend/src/main/java/com/tracetick/auth/BootstrapInitializer.java` and
 * `application.yml`.
 */

export const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8080/api/v1';

export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@tracetick.local';

export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'changeme';