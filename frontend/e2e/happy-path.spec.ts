import { expect, test } from '@playwright/test';

/**
 * Happy-path E2E for TraceTick.
 *
 * Drives the full UI loop that a TECHNICIAN user follows from sign-in to a
 * resolved Ticket:
 *   1. login as the bootstrap admin (TECHNICIAN role)
 *   2. create a new HUMAN-origin Ticket via the form
 *   3. post a comment on the Ticket
 *   4. transition OPEN → IN_PROGRESS via the UI
 *   5. transition IN_PROGRESS → RESOLVED via the UI
 *
 * The test relies on the backend's `BootstrapInitializer` having already
 * created the Customer row and the admin User on first boot (verified in
 * `frontend/e2e/global-setup.ts`). No per-test DB reset is needed because the
 * suite is exactly one spec; for fully clean re-runs see the README.
 */
test('happy path: login, create ticket, comment, and resolve', async ({ page }) => {
  // 1. Login as the bootstrap admin.
  await page.goto('/');
  await expect(page).toHaveURL(/\/login/);

  await page.getByLabel('Email').fill('admin@tracetick.local');
  await page.getByLabel('Password').fill('changeme');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('heading', { name: 'TraceTick' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Tickets' })).toBeVisible();
  await expect(page.getByText('admin@tracetick.local')).toBeVisible();

  // 2. Create a HUMAN-origin Ticket via the new-ticket form.
  await page.goto('/tickets/new');
  await expect(page.getByRole('heading', { name: 'New ticket' })).toBeVisible();

  const ticketTitle = `Login flow broken on staging ${Date.now()}`;
  const ticketDescription = 'Steps to reproduce:\n1. Visit /login\n2. Submit form\n3. Observe error.';
  await page.getByLabel('Title').fill(ticketTitle);
  await page.getByLabel('Description').fill(ticketDescription);

  await page.getByRole('button', { name: 'Create ticket' }).click();

  // We land on the detail page; the title is rendered as the h2.
  const detailHeading = page.getByRole('heading', { name: ticketTitle });
  await expect(detailHeading).toBeVisible();

  // A HUMAN-origin ticket is created in OPEN state. The badge text is the
  // state name with the underscore replaced by a space.
  await expect(page.getByText('OPEN').first()).toBeVisible();

  // 3. Post a comment.
  const commentBody = 'Triaging now — pulling recent logs.';
  await page.getByLabel('Add a comment').fill(commentBody);
  await page.getByRole('button', { name: 'Post comment' }).click();
  await expect(page.getByText(commentBody)).toBeVisible();

  // 4. Transition OPEN → IN_PROGRESS via the UI.
  await page.getByRole('button', { name: 'Mark in progress' }).click();
  // The badge updates to "IN PROGRESS"; the timeline gains a STATE_CHANGE entry.
  await expect(page.getByText('IN PROGRESS').first()).toBeVisible();
  await expect(page.getByText('OPEN → IN_PROGRESS')).toBeVisible();

  // 5. Transition IN_PROGRESS → RESOLVED via the UI.
  await page.getByRole('button', { name: 'Resolve' }).click();
  await expect(page.getByText('RESOLVED').first()).toBeVisible();
  await expect(page.getByText('IN_PROGRESS → RESOLVED')).toBeVisible();
});