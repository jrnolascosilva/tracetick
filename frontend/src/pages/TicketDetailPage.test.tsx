import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import {
  Outlet,
  RouterProvider,
  createMemoryRouter,
} from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthProvider } from '@/lib/auth';
import { TicketDetailPage } from '@/pages/TicketDetailPage';
import type { Ticket, TicketEvent, User, Role } from '@/lib/types';
import { server } from '@/test/server';

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    email: 'alex@example.com',
    role: 'TECHNICIAN',
    active: true,
    ...overrides,
  };
}

function makeTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 7,
    origin: 'HUMAN',
    reporterUserId: 10,
    assigneeUserId: 99,
    title: 'Database is on fire',
    description: 'Connections are timing out across all services.',
    severity: 'CRITICAL',
    state: 'IN_PROGRESS',
    refireCount: 0,
    createdAt: '2026-01-02T03:04:05.000Z',
    updatedAt: '2026-01-02T05:30:00.000Z',
    resolvedAt: null,
    closedAt: null,
    tags: [{ key: 'service', value: 'db' }, { key: 'env', value: 'prod' }],
    ...overrides,
  };
}

function renderDetail(initialEntry: string, currentUser: User | null = makeUser()) {
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
        path: '/tickets',
        element: (
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <Outlet />
            </AuthProvider>
          </QueryClientProvider>
        ),
        children: [{ path: ':id', element: <TicketDetailPage /> }],
      },
      {
        path: '/tickets',
        element: <div>Tickets list page</div>,
      },
    ],
    { initialEntries: [initialEntry] },
  );

  return render(<RouterProvider router={router} />);
}

describe('TicketDetailPage', () => {
  it('renders the ticket fields', async () => {
    server.use(
      http.get('/api/v1/tickets/7', () =>
        HttpResponse.json({
          ticket: makeTicket(),
          events: [],
          watcherIds: [],
        })),
    );

    renderDetail('/tickets/7');

    expect(await screen.findByRole('heading', { name: 'Database is on fire' })).toBeInTheDocument();
    expect(screen.getByText('Connections are timing out across all services.')).toBeInTheDocument();
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
    expect(screen.getByText('service:db')).toBeInTheDocument();
    expect(screen.getByText('env:prod')).toBeInTheDocument();
  });

  it('renders events in chronological order with type-specific content', async () => {
    const ticket = makeTicket();
    const events: TicketEvent[] = [
      {
        id: 2, type: 'STATE_CHANGE',
        actorUserId: 1,
        payload: { from: 'OPEN', to: 'IN_PROGRESS' },
        createdAt: '2026-01-02T03:05:00.000Z',
      },
      {
        id: 3, type: 'ASSIGNMENT_CHANGE',
        actorUserId: 1,
        payload: { from_user_id: null, to_user_id: 99 },
        createdAt: '2026-01-02T03:06:00.000Z',
      },
      {
        id: 4, type: 'SEVERITY_CHANGE',
        actorUserId: 1,
        payload: { from: 'HIGH', to: 'CRITICAL' },
        createdAt: '2026-01-02T03:07:00.000Z',
      },
      {
        id: 5, type: 'TAG_CHANGE',
        actorUserId: 1,
        payload: { key: 'service', value: 'db', action: 'added' },
        createdAt: '2026-01-02T03:08:00.000Z',
      },
      {
        id: 6, type: 'WATCHER_CHANGE',
        actorUserId: 1,
        payload: { user_id: 5, action: 'added' },
        createdAt: '2026-01-02T03:09:00.000Z',
      },
      {
        id: 1, type: 'COMMENT',
        actorUserId: 10,
        payload: { body: 'Connections are still timing out.' },
        createdAt: '2026-01-02T03:10:00.000Z',
      },
      {
        id: 7, type: 'REFIRE',
        actorUserId: null,
        payload: {},
        createdAt: '2026-01-02T04:00:00.000Z',
      },
    ];

    server.use(
      http.get('/api/v1/tickets/7', () =>
        HttpResponse.json({
          ticket,
          events,
          watcherIds: [5],
        })),
    );

    renderDetail('/tickets/7');

    expect(await screen.findByRole('heading', { name: 'Database is on fire' })).toBeInTheDocument();

    const timeline = screen.getByRole('list');
    const items = within(timeline).getAllByRole('listitem');
    expect(items).toHaveLength(events.length);

    expect(within(items[0]).getByText('State change')).toBeInTheDocument();
    expect(within(items[0]).getByText('OPEN → IN_PROGRESS')).toBeInTheDocument();

    expect(within(items[1]).getByText('Assignee changed')).toBeInTheDocument();
    expect(within(items[1]).getByText('unassigned → #99')).toBeInTheDocument();

    expect(within(items[2]).getByText('Severity changed')).toBeInTheDocument();
    expect(within(items[2]).getByText('HIGH → CRITICAL')).toBeInTheDocument();

    expect(within(items[3]).getByText('Tag changed')).toBeInTheDocument();
    expect(within(items[3]).getByText(/added/)).toBeInTheDocument();
    expect(within(items[3]).getByText('service:db')).toBeInTheDocument();

    expect(within(items[4]).getByText('Watcher changed')).toBeInTheDocument();
    expect(within(items[4]).getByText('added user #5')).toBeInTheDocument();

    expect(within(items[5]).getByText('Comment')).toBeInTheDocument();
    expect(within(items[5]).getByText('Connections are still timing out.')).toBeInTheDocument();

    expect(within(items[6]).getByText('Refire')).toBeInTheDocument();
  });

  it('shows watcher ids in the sidebar', async () => {
    server.use(
      http.get('/api/v1/tickets/7', () =>
        HttpResponse.json({
          ticket: makeTicket(),
          events: [],
          watcherIds: [4, 8],
        })),
    );

    renderDetail('/tickets/7');

    await screen.findByRole('heading', { name: 'Database is on fire' });
    const watcherRow = screen.getByText('Watchers').parentElement;
    expect(watcherRow).toHaveTextContent('4, 8');
  });

  it('shows an empty-state message when there are no events', async () => {
    server.use(
      http.get('/api/v1/tickets/7', () =>
        HttpResponse.json({
          ticket: makeTicket(),
          events: [],
          watcherIds: [],
        })),
    );

    renderDetail('/tickets/7');

    expect(await screen.findByText('No activity yet.')).toBeInTheDocument();
  });

  it('shows a friendly error when the ticket is not found', async () => {
    server.use(
      http.get('/api/v1/tickets/404', () => new HttpResponse(null, { status: 404 })),
    );

    renderDetail('/tickets/404');

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Ticket not found, or you do not have access to it.');
    expect(screen.getByRole('link', { name: 'Back to tickets' })).toHaveAttribute('href', '/tickets');
  });

  it('treats 404 as not-visible when a CUSTOMER user tries to view another user\'s ticket', async () => {
    server.use(
      http.get('/api/v1/me', () => HttpResponse.json(makeUser({ role: 'CUSTOMER' as Role }))),
      http.get('/api/v1/tickets/42', () => new HttpResponse(null, { status: 404 })),
    );

    renderDetail('/tickets/42');

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Ticket not found, or you do not have access to it.');
  });

  it('shows an error message when the API call fails', async () => {
    server.use(
      http.get('/api/v1/tickets/7', () => new HttpResponse('boom', { status: 500 })),
    );

    renderDetail('/tickets/7');

    expect(await screen.findByRole('alert')).toHaveTextContent('boom');
  });

  it('renders attachment events', async () => {
    server.use(
      http.get('/api/v1/tickets/7', () =>
        HttpResponse.json({
          ticket: makeTicket(),
          events: [
            {
              id: 1, type: 'ATTACHMENT_ADDED',
              actorUserId: 1,
              payload: { name: 'logs.txt' },
              createdAt: '2026-01-02T03:00:00.000Z',
            },
          ],
          watcherIds: [],
        })),
    );

    renderDetail('/tickets/7');

    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(1);
    expect(within(items[0]).getByText('Attachment added')).toBeInTheDocument();
    expect(within(items[0]).getByText('logs.txt')).toBeInTheDocument();
  });
});