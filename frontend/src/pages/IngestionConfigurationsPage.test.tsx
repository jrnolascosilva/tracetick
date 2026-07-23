import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import {
  Outlet,
  createMemoryRouter,
  RouterProvider,
} from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/lib/auth';
import { IngestionConfigurationsPage } from '@/pages/IngestionConfigurationsPage';
import type {
  IngestionConfiguration,
  IngestionConfigurationWithSecret,
  Role,
  User,
} from '@/lib/types';
import { server } from '@/test/server';

type ClipboardSpy = { mockRestore: () => void };
type ClipboardLike = { writeText: (data: string) => Promise<void> };
let clipboardSpy: ClipboardSpy | null = null;
const writeTextSpy = vi.fn<(data: string) => Promise<void>>(async () => undefined);

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    email: 'tech@example.com',
    role: 'TECHNICIAN',
    active: true,
    ...overrides,
  };
}

function makeConfig(overrides: Partial<IngestionConfiguration> = {}): IngestionConfiguration {
  return {
    id: 7,
    name: 'PagerDuty',
    urlToken: 'tok_abc123',
    webhookUrl: '/api/v1/ingest/tok_abc123',
    defaultSeverity: 'HIGH',
    defaultAssigneeUserId: null,
    defaultTags: { service: 'api', env: 'prod' },
    active: true,
    createdAt: '2026-01-02T03:04:05.000Z',
    ...overrides,
  };
}

function makeCreatedConfig(
  overrides: Partial<IngestionConfigurationWithSecret> = {},
): IngestionConfigurationWithSecret {
  return {
    ...makeConfig({ id: 9, name: 'New config' }),
    hmacSecret: 'super-secret-token-xyz',
    ...overrides,
  };
}

function installClipboardSpy() {
  const clipboard = navigator.clipboard as ClipboardLike | undefined;
  if (clipboard?.writeText) {
    const target = clipboard as ClipboardLike;
    clipboardSpy = vi.spyOn(target, 'writeText').mockImplementation(writeTextSpy);
  }
}

function renderPage(currentUser: User | null = makeUser()) {
  server.use(
    http.get('/api/v1/me', () =>
      currentUser ? HttpResponse.json(currentUser) : new HttpResponse(null, { status: 401 })),
  );

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });

  const router = createMemoryRouter(
    [
      {
        path: '/ingestion-configurations',
        element: (
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <Outlet />
            </AuthProvider>
          </QueryClientProvider>
        ),
        children: [{ index: true, element: <IngestionConfigurationsPage /> }],
      },
    ],
    { initialEntries: ['/ingestion-configurations'] },
  );

  const user = userEvent.setup();
  installClipboardSpy();
  const result = render(<RouterProvider router={router} />);
  return { ...result, user, queryClient };
}

describe('IngestionConfigurationsPage', () => {
  beforeEach(() => {
    writeTextSpy.mockReset();
    writeTextSpy.mockImplementation(async () => undefined);
  });

  afterEach(() => {
    clipboardSpy?.mockRestore();
    clipboardSpy = null;
  });

  it('renders nothing when the current user is a CUSTOMER (defense-in-depth)', async () => {
    server.use(
      http.get('/api/v1/ingestion-configurations', () =>
        HttpResponse.json([makeConfig()])),
    );

    const { container } = renderPage(makeUser({ role: 'CUSTOMER' as Role }));

    await screen.findByRole('heading', { name: 'Ingestion configurations' }).catch(() => null);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole('heading', { name: 'Ingestion configurations' }))
      .not.toBeInTheDocument();
    expect(screen.queryByLabelText('Name')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create configuration' }))
      .not.toBeInTheDocument();
  });

  it('renders the create form and the existing configurations for an admin', async () => {
    server.use(
      http.get('/api/v1/ingestion-configurations', () =>
        HttpResponse.json([
          makeConfig({ id: 1, name: 'PagerDuty' }),
          makeConfig({ id: 2, name: 'Sentry', active: false }),
        ])),
    );

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Ingestion configurations' }))
      .toBeInTheDocument();
    expect(await screen.findByRole('row', { name: /PagerDuty/ })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Sentry/ })).toBeInTheDocument();

    expect(screen.getByLabelText('Name')).toBeInTheDocument();
    expect(screen.getByLabelText('Default severity')).toBeInTheDocument();
    expect(screen.getByLabelText('Default assignee user id')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add tag' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create configuration' })).toBeInTheDocument();
  });

  it('shows the webhook URL and a copy button on each row', async () => {
    server.use(
      http.get('/api/v1/ingestion-configurations', () =>
        HttpResponse.json([
          makeConfig({ id: 1, name: 'PagerDuty', webhookUrl: '/api/v1/ingest/tok_abc123' }),
        ])),
    );

    renderPage();

    const row = await screen.findByRole('row', { name: /PagerDuty/ });
    expect(within(row).getByText('/api/v1/ingest/tok_abc123')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Copy webhook URL' })).toBeInTheDocument();
  });

  it('copies the webhook URL to the clipboard when the row Copy button is clicked', async () => {
    server.use(
      http.get('/api/v1/ingestion-configurations', () =>
        HttpResponse.json([
          makeConfig({ id: 1, name: 'PagerDuty', webhookUrl: '/api/v1/ingest/tok_abc123' }),
        ])),
    );

    const { user } = renderPage();

    const row = await screen.findByRole('row', { name: /PagerDuty/ });
    await user.click(within(row).getByRole('button', { name: 'Copy webhook URL' }));

    expect(writeTextSpy).toHaveBeenCalledWith('/api/v1/ingest/tok_abc123');
  });

  it('creates a configuration and reveals the HMAC secret exactly once', async () => {
    let createBody: unknown = null;
    let listCalls = 0;
    let configs: IngestionConfiguration[] = [];
    server.use(
      http.get('/api/v1/ingestion-configurations', () => {
        listCalls += 1;
        return HttpResponse.json(configs);
      }),
      http.post('/api/v1/ingestion-configurations', async ({ request }) => {
        createBody = await request.json();
        const created = makeCreatedConfig({ id: 9, name: 'My new source' });
        configs = [created];
        return HttpResponse.json(created, { status: 201 });
      }),
    );

    const { user } = renderPage();

    await screen.findByRole('heading', { name: 'Ingestion configurations' });
    await user.type(screen.getByLabelText('Name'), 'My new source');
    await user.selectOptions(screen.getByLabelText('Default severity'), 'HIGH');

    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    await user.type(screen.getByLabelText('Tag key 1'), 'service');
    await user.type(screen.getByLabelText('Tag value 1'), 'api');

    await user.click(screen.getByRole('button', { name: 'Create configuration' }));

    const revealPanel = await screen.findByText('Save these credentials now');
    const revealSection = revealPanel.closest('section')!;
    expect(within(revealSection).getByText('super-secret-token-xyz')).toBeInTheDocument();
    expect(within(revealSection).getByText('/api/v1/ingest/tok_abc123')).toBeInTheDocument();

    expect(createBody).toEqual({
      name: 'My new source',
      defaultSeverity: 'HIGH',
      defaultTags: { service: 'api' },
    });

    await vi.waitFor(() => expect(listCalls).toBeGreaterThanOrEqual(2));

    await user.click(within(revealSection).getByRole('button', { name: 'I have saved it, dismiss' }));

    expect(screen.queryByText('Save these credentials now')).not.toBeInTheDocument();
    expect(screen.queryByText('super-secret-token-xyz')).not.toBeInTheDocument();

    expect(await screen.findByRole('row', { name: /My new source/ })).toBeInTheDocument();

    const copySecret = screen.queryByRole('button', { name: 'Copy HMAC secret' });
    expect(copySecret).not.toBeInTheDocument();
  });

  it('copies the HMAC secret to the clipboard from the reveal panel', async () => {
    server.use(
      http.get('/api/v1/ingestion-configurations', () => HttpResponse.json([])),
      http.post('/api/v1/ingestion-configurations', () =>
        HttpResponse.json(makeCreatedConfig({ id: 9, name: 'New' }), { status: 201 })),
    );

    const { user } = renderPage();

    await screen.findByRole('heading', { name: 'Ingestion configurations' });
    await user.type(screen.getByLabelText('Name'), 'New');
    await user.click(screen.getByRole('button', { name: 'Create configuration' }));

    const panel = await screen.findByText('Save these credentials now');
    const panelContainer = panel.closest('section') ?? document.body;
    await user.click(within(panelContainer as HTMLElement).getByRole('button', { name: 'Copy HMAC secret' }));

    expect(writeTextSpy).toHaveBeenCalledWith('super-secret-token-xyz');
  });

  it('opens the edit panel and PATCHes the configuration when fields are updated', async () => {
    let patchBody: unknown = null;
    server.use(
      http.get('/api/v1/ingestion-configurations', () =>
        HttpResponse.json([
          makeConfig({
            id: 1,
            name: 'PagerDuty',
            defaultSeverity: 'HIGH',
            defaultAssigneeUserId: null,
            defaultTags: { service: 'api', env: 'prod' },
            active: true,
          }),
        ])),
      http.patch('/api/v1/ingestion-configurations/1', async ({ request }) => {
        patchBody = await request.json();
        return HttpResponse.json(makeConfig({ id: 1, name: 'PagerDuty v2' }));
      }),
    );

    const { user } = renderPage();

    const row = await screen.findByRole('row', { name: /PagerDuty/ });
    await user.click(within(row).getByRole('button', { name: 'Edit' }));

    const editPanel = await screen.findByRole('heading', { name: 'Edit PagerDuty' });
    const editSection = editPanel.closest('section')!;
    const nameInput = within(editSection).getByLabelText('Name');
    await user.clear(nameInput);
    await user.type(nameInput, 'PagerDuty v2');

    await user.click(within(editSection).getByRole('button', { name: 'Save changes' }));

    await vi.waitFor(() => expect(patchBody).not.toBeNull());
    expect(patchBody).toEqual({
      name: 'PagerDuty v2',
      defaultSeverity: 'HIGH',
      defaultAssigneeUserId: null,
      defaultTags: { service: 'api', env: 'prod' },
      active: true,
    });
    expect(screen.queryByRole('heading', { name: 'Edit PagerDuty' })).not.toBeInTheDocument();
  });

  it('rotates the secret via the explicit Rotate secret action and reveals it once', async () => {
    server.use(
      http.get('/api/v1/ingestion-configurations', () =>
        HttpResponse.json([makeConfig({ id: 1, name: 'PagerDuty' })])),
      http.patch('/api/v1/ingestion-configurations/1', () =>
        HttpResponse.json({
          ...makeConfig({ id: 1, name: 'PagerDuty' }),
          hmacSecret: 'freshly-rotated-secret',
        })),
    );

    const { user } = renderPage();

    const row = await screen.findByRole('row', { name: /PagerDuty/ });
    await user.click(within(row).getByRole('button', { name: 'Edit' }));

    await user.click(await screen.findByRole('button', { name: 'Rotate secret' }));

    expect(await screen.findByText('Save these credentials now')).toBeInTheDocument();
    expect(screen.getByText('freshly-rotated-secret')).toBeInTheDocument();
  });

  it('shows a server error when creating a configuration fails', async () => {
    server.use(
      http.get('/api/v1/ingestion-configurations', () => HttpResponse.json([])),
      http.post('/api/v1/ingestion-configurations', () =>
        new HttpResponse('name must not be blank', { status: 400 })),
    );

    const { user } = renderPage();

    await user.click(await screen.findByRole('button', { name: 'Create configuration' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('name must not be blank');
  });
});
