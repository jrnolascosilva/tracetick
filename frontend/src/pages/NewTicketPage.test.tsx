import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import {
  Outlet,
  createMemoryRouter,
  RouterProvider,
} from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/lib/auth';
import { NewTicketPage } from '@/pages/NewTicketPage';
import type { Ticket, User } from '@/lib/types';
import { server } from '@/test/server';

const navigateSpy = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => navigateSpy,
  };
});

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    email: 'alex@example.com',
    role: 'CUSTOMER',
    active: true,
    ...overrides,
  };
}

function renderNewTicket() {
  server.use(
    http.get('/api/v1/me', () => HttpResponse.json(makeUser())),
  );

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });

  const router = createMemoryRouter(
    [
      {
        path: '/tickets',
        element: (
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <Outlet />
            </AuthProvider>
          </QueryClientProvider>
        ),
        children: [{ path: 'new', element: <NewTicketPage /> }],
      },
    ],
    { initialEntries: ['/tickets/new'] },
  );

  const user = userEvent.setup();
  const result = render(<RouterProvider router={router} />);
  return { ...result, user };
}

function makeCreatedTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 42,
    origin: 'HUMAN',
    reporterUserId: 1,
    assigneeUserId: null,
    title: 'API is down',
    description: 'All endpoints returning 500.',
    severity: 'HIGH',
    state: 'OPEN',
    refireCount: 0,
    createdAt: '2026-01-02T03:04:05.000Z',
    updatedAt: '2026-01-02T03:04:05.000Z',
    resolvedAt: null,
    closedAt: null,
    tags: [],
    ...overrides,
  };
}

describe('NewTicketPage', () => {
  beforeEach(() => {
    navigateSpy.mockReset();
  });

  afterEach(() => {
    navigateSpy.mockReset();
  });

  it('renders the create-ticket form with required fields and default severity MEDIUM', async () => {
    renderNewTicket();

    expect(await screen.findByRole('heading', { name: 'New ticket' })).toBeInTheDocument();
    expect(screen.getByLabelText('Title')).toBeInTheDocument();
    expect(screen.getByLabelText('Description')).toBeInTheDocument();
    expect(screen.getByLabelText('Severity')).toHaveValue('MEDIUM');
    expect(screen.getByRole('button', { name: 'Create ticket' })).toBeInTheDocument();
  });

  it('does not render any tag rows until the user clicks Add tag', async () => {
    renderNewTicket();

    await screen.findByRole('heading', { name: 'New ticket' });
    expect(screen.queryByLabelText('Tag key 1')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add tag' })).toBeInTheDocument();
  });

  it('submits the form, calls POST /api/v1/tickets, and navigates to the new ticket', async () => {
    let observed: { url: string; body: unknown } | null = null;
    server.use(
      http.post('/api/v1/tickets', async ({ request }) => {
        observed = { url: request.url, body: await request.json() };
        return HttpResponse.json(
          makeCreatedTicket({ id: 42, title: 'API is down', severity: 'HIGH' }),
          { status: 201 },
        );
      }),
    );

    const { user } = renderNewTicket();

    await user.type(screen.getByLabelText('Title'), 'API is down');
    await user.type(screen.getByLabelText('Description'), 'All endpoints returning 500.');
    await user.selectOptions(screen.getByLabelText('Severity'), 'HIGH');
    await user.click(screen.getByRole('button', { name: 'Create ticket' }));

    await vi.waitFor(() => expect(navigateSpy).toHaveBeenCalledWith('/tickets/42'));

    expect(observed).not.toBeNull();
    expect(observed!.url).toContain('/api/v1/tickets');
    expect(observed!.body).toEqual({
      title: 'API is down',
      description: 'All endpoints returning 500.',
      severity: 'HIGH',
      tags: [],
    });
  });

  it('sends severity MEDIUM in the wire body when the user leaves the default', async () => {
    let observed: { body: unknown } | null = null;
    server.use(
      http.post('/api/v1/tickets', async ({ request }) => {
        observed = { body: await request.json() };
        return HttpResponse.json(makeCreatedTicket({ id: 43 }), { status: 201 });
      }),
    );

    const { user } = renderNewTicket();

    await user.type(screen.getByLabelText('Title'), 'Disk almost full');
    await user.type(screen.getByLabelText('Description'), '90% used');
    await user.click(screen.getByRole('button', { name: 'Create ticket' }));

    await vi.waitFor(() => expect(navigateSpy).toHaveBeenCalledWith('/tickets/43'));

    expect(observed).not.toBeNull();
    expect(observed!.body).toEqual({
      title: 'Disk almost full',
      description: '90% used',
      severity: 'MEDIUM',
      tags: [],
    });
  });

  it('surfaces the server error message and leaves the form interactive', async () => {
    server.use(
      http.post('/api/v1/tickets', () =>
        new HttpResponse('title must not be blank', { status: 400 }),
      ),
    );

    const { user } = renderNewTicket();

    await user.type(screen.getByLabelText('Title'), 'API is down');
    await user.type(screen.getByLabelText('Description'), 'All endpoints returning 500.');
    await user.click(screen.getByRole('button', { name: 'Create ticket' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('title must not be blank');
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Title')).toHaveValue('API is down');
    expect(screen.getByLabelText('Description')).toHaveValue('All endpoints returning 500.');
    expect(screen.getByRole('button', { name: 'Create ticket' })).toBeEnabled();
  });

  it('appends a new tag row when Add tag is clicked and removes the row when its button is clicked', async () => {
    const { user } = renderNewTicket();

    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    expect(screen.getByLabelText('Tag key 1')).toBeInTheDocument();
    expect(screen.getByLabelText('Tag value 1')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    expect(screen.getByLabelText('Tag key 2')).toBeInTheDocument();
    expect(screen.getByLabelText('Tag value 2')).toBeInTheDocument();

    const removeButtons = screen.getAllByRole('button', { name: 'Remove tag' });
    await user.click(removeButtons[1]);
    expect(screen.queryByLabelText('Tag key 2')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Tag key 1')).toBeInTheDocument();
  });

  it('submits only fully-filled, valid tag drafts in the wire body', async () => {
    let observed: { body: unknown } | null = null;
    server.use(
      http.post('/api/v1/tickets', async ({ request }) => {
        observed = { body: await request.json() };
        return HttpResponse.json(makeCreatedTicket({ id: 44 }), { status: 201 });
      }),
    );

    const { user } = renderNewTicket();

    await user.type(screen.getByLabelText('Title'), 'Latency spike');
    await user.type(screen.getByLabelText('Description'), 'p99 above SLO');
    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    await user.type(screen.getByLabelText('Tag key 1'), 'service');
    await user.type(screen.getByLabelText('Tag value 1'), 'api');
    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    await user.type(screen.getByLabelText('Tag key 2'), 'env');
    await user.type(screen.getByLabelText('Tag value 2'), 'prod');
    await user.click(screen.getByRole('button', { name: 'Create ticket' }));

    await vi.waitFor(() => expect(navigateSpy).toHaveBeenCalledWith('/tickets/44'));

    expect(observed).not.toBeNull();
    expect(observed!.body).toEqual({
      title: 'Latency spike',
      description: 'p99 above SLO',
      severity: 'MEDIUM',
      tags: [
        { key: 'service', value: 'api' },
        { key: 'env', value: 'prod' },
      ],
    });
  });

  it('blocks submit and shows a per-row error when a tag key does not match the pattern', async () => {
    let postCount = 0;
    server.use(
      http.post('/api/v1/tickets', () => {
        postCount += 1;
        return HttpResponse.json(makeCreatedTicket({ id: 45 }), { status: 201 });
      }),
    );

    const { user } = renderNewTicket();

    await user.type(screen.getByLabelText('Title'), 'Latency spike');
    await user.type(screen.getByLabelText('Description'), 'p99 above SLO');
    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    await user.type(screen.getByLabelText('Tag key 1'), 'BAD KEY');
    await user.type(screen.getByLabelText('Tag value 1'), 'api');

    const submitButton = screen.getByRole('button', { name: 'Create ticket' });
    expect(submitButton).toBeDisabled();
    expect(await screen.findByText(/tag key must match/i)).toBeInTheDocument();

    await user.click(submitButton);

    expect(postCount).toBe(0);
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('blocks submit and shows a per-row error when a tag value is set but the key is empty', async () => {
    let postCount = 0;
    server.use(
      http.post('/api/v1/tickets', () => {
        postCount += 1;
        return HttpResponse.json(makeCreatedTicket({ id: 46 }), { status: 201 });
      }),
    );

    const { user } = renderNewTicket();

    await user.type(screen.getByLabelText('Title'), 'Latency spike');
    await user.type(screen.getByLabelText('Description'), 'p99 above SLO');
    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    await user.type(screen.getByLabelText('Tag value 1'), 'api');

    const submitButton = screen.getByRole('button', { name: 'Create ticket' });
    expect(submitButton).toBeDisabled();
    expect(await screen.findByText(/tag key is required/i)).toBeInTheDocument();

    await user.click(submitButton);

    expect(postCount).toBe(0);
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('clears a per-row tag-key error once the user fixes the key', async () => {
    const { user } = renderNewTicket();

    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    await user.type(screen.getByLabelText('Tag key 1'), 'BAD KEY');
    expect(await screen.findByText(/tag key must match/i)).toBeInTheDocument();

    const keyInput = screen.getByLabelText('Tag key 1');
    await user.clear(keyInput);
    await user.type(keyInput, 'service');

    expect(screen.queryByText(/tag key must match/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create ticket' })).toBeEnabled();
  });
});