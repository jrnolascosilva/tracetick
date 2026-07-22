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
import { TicketListPage } from '@/pages/TicketListPage';
import type { Role, Ticket, User } from '@/lib/types';
import { server } from '@/test/server';

const searchParamsState = {
  current: new URLSearchParams(),
};

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  const React = await import('react');
  return {
    ...actual,
    useSearchParams: () => {
      const [, force] = React.useReducer((x) => x + 1, 0);
      const ref = React.useRef<URLSearchParams>(searchParamsState.current);
      if (ref.current !== searchParamsState.current) {
        ref.current = searchParamsState.current;
      }
      const update = React.useCallback(
        (next: URLSearchParams | ((prev: URLSearchParams) => URLSearchParams)) => {
          const value = typeof next === 'function' ? next(searchParamsState.current) : next;
          searchParamsState.current = new URLSearchParams(value);
          force();
        },
        [],
      );
      return [searchParamsState.current, update] as ReturnType<typeof actual.useSearchParams>;
    },
  };
});

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
    id: 1,
    origin: 'HUMAN',
    reporterUserId: 10,
    assigneeUserId: null,
    title: 'Latency spike',
    description: 'p99 above SLO',
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

function setSearchParamsFromEntry(entry: string) {
  const queryIndex = entry.indexOf('?');
  if (queryIndex === -1) {
    searchParamsState.current = new URLSearchParams();
    return;
  }
  searchParamsState.current = new URLSearchParams(entry.slice(queryIndex + 1));
}

function renderList(initialEntry: string, currentUser: User | null = makeUser()) {
  server.use(
    http.get('/api/v1/me', () =>
      currentUser ? HttpResponse.json(currentUser) : new HttpResponse(null, { status: 401 })),
  );

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });

  setSearchParamsFromEntry(initialEntry);

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
        children: [{ index: true, element: <TicketListPage /> }],
      },
      {
        path: '/tickets/:id',
        element: <div>Ticket detail page</div>,
      },
    ],
    { initialEntries: [initialEntry] },
  );

  const user = userEvent.setup();
  const result = render(<RouterProvider router={router} />);
  return { ...result, router, user, queryClient };
}

describe('TicketListPage', () => {
  beforeEach(() => {
    searchParamsState.current = new URLSearchParams();
  });

  afterEach(() => {
    searchParamsState.current = new URLSearchParams();
  });

  it('renders tickets returned by the API', async () => {
    server.use(
      http.get('/api/v1/tickets', () =>
        HttpResponse.json({
          items: [
            makeTicket({ id: 1, title: 'Latency spike', severity: 'HIGH' }),
            makeTicket({ id: 2, title: 'Disk full', severity: 'CRITICAL' }),
          ],
          page: 0,
          size: 50,
          total: 2,
        })),
    );

    renderList('/tickets');

    expect(await screen.findByRole('link', { name: 'Latency spike' }))
      .toHaveAttribute('href', '/tickets/1');
    expect(screen.getByRole('link', { name: 'Disk full' })).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 1 (2 tickets)')).toBeInTheDocument();
  });

  it('sends filter params to the API', async () => {
    const calls: string[] = [];
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        calls.push(request.url);
        return HttpResponse.json({ items: [], page: 0, size: 50, total: 0 });
      }),
    );

    renderList('/tickets?state=OPEN&severity=CRITICAL&tag=service:api');

    await screen.findByText('No tickets match your filters.');

    expect(calls).toHaveLength(1);
    const url = new URL(calls[0]);
    expect(url.searchParams.get('state')).toBe('OPEN');
    expect(url.searchParams.get('severity')).toBe('CRITICAL');
    expect(url.searchParams.get('tag')).toBe('service:api');
  });

  it('writes sort=severity,desc to the URL and forwards it to the API when Severity is selected', async () => {
    const calls: URL[] = [];
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        calls.push(new URL(request.url));
        return HttpResponse.json({
          items: [
            makeTicket({ id: 2, title: 'Critical thing', severity: 'CRITICAL' }),
            makeTicket({ id: 3, title: 'High thing', severity: 'HIGH' }),
            makeTicket({ id: 1, title: 'Low thing', severity: 'LOW' }),
          ],
          page: 0,
          size: 50,
          total: 3,
        });
      }),
    );

    const { user } = renderList('/tickets');

    await screen.findByRole('link', { name: 'Critical thing' });
    await user.click(screen.getByRole('radio', { name: 'Severity' }));

    expect(searchParamsState.current.get('sort')).toBe('severity,desc');
    expect(calls.at(-1)?.searchParams.get('sort')).toBe('severity,desc');

    const rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('Critical thing')).toBeInTheDocument();
    expect(within(rows[1]).getByText('High thing')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Low thing')).toBeInTheDocument();
  });

  it('writes sort=state,desc to the URL and forwards it to the API when State is selected', async () => {
    const calls: URL[] = [];
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        calls.push(new URL(request.url));
        return HttpResponse.json({
          items: [
            makeTicket({ id: 2, title: 'Open', state: 'OPEN' }),
            makeTicket({ id: 1, title: 'Resolved', state: 'RESOLVED' }),
            makeTicket({ id: 3, title: 'Closed', state: 'CLOSED' }),
          ],
          page: 0,
          size: 50,
          total: 3,
        });
      }),
    );

    const { user } = renderList('/tickets');

    await screen.findByRole('link', { name: 'Open' });
    await user.click(screen.getByRole('radio', { name: 'State' }));

    expect(searchParamsState.current.get('sort')).toBe('state,desc');
    expect(calls.at(-1)?.searchParams.get('sort')).toBe('state,desc');
  });

  it('pre-selects the sort radio and forwards the sort param when the URL already carries it', async () => {
    const calls: URL[] = [];
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        calls.push(new URL(request.url));
        return HttpResponse.json({
          items: [makeTicket({ id: 1, title: 'Only ticket' })],
          page: 0,
          size: 50,
          total: 1,
        });
      }),
    );

    renderList('/tickets?sort=createdAt,asc');

    expect(await screen.findByRole('link', { name: 'Only ticket' })).toBeInTheDocument();
    expect(calls[0].searchParams.get('sort')).toBe('createdAt,asc');
  });

  it('paginates to the next page when Next is clicked', async () => {
    const calls: URL[] = [];
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        calls.push(new URL(request.url));
        const page = Number.parseInt(new URL(request.url).searchParams.get('page') ?? '0', 10);
        return HttpResponse.json({
          items: page === 0
            ? [makeTicket({ id: 1, title: 'Page 1 ticket' })]
            : [makeTicket({ id: 2, title: 'Page 2 ticket' })],
          page,
          size: 50,
          total: 100,
        });
      }),
    );

    const { user } = renderList('/tickets');

    expect(await screen.findByRole('link', { name: 'Page 1 ticket' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByRole('link', { name: 'Page 2 ticket' })).toBeInTheDocument();
    expect(calls.at(-1)?.searchParams.get('page')).toBe('1');
  });

  it('submits free-text search and updates the URL', async () => {
    const calls: URL[] = [];
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        calls.push(new URL(request.url));
        const search = new URL(request.url).searchParams.get('search');
        return HttpResponse.json({
          items: search
            ? [makeTicket({ id: 1, title: 'Search hit' })]
            : [makeTicket({ id: 1, title: 'Unfiltered ticket' })],
          page: 0,
          size: 50,
          total: 1,
        });
      }),
    );

    const { user } = renderList('/tickets');

    await screen.findByPlaceholderText('Title or description');
    await user.type(screen.getByPlaceholderText('Title or description'), 'latency');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByRole('link', { name: 'Search hit' })).toBeInTheDocument();
    expect(searchParamsState.current.get('search')).toBe('latency');
  });

  it('shows an error message when the API fails', async () => {
    server.use(
      http.get('/api/v1/tickets', () => new HttpResponse('boom', { status: 500 })),
    );

    renderList('/tickets');

    expect(await screen.findByRole('alert')).toHaveTextContent('boom');
  });

  it('respects server-side role scoping (CUSTOMER sees only their own tickets)', async () => {
    server.use(
      http.get('/api/v1/me', () => HttpResponse.json(makeUser({ role: 'CUSTOMER' as Role }))),
      http.get('/api/v1/tickets', () =>
        HttpResponse.json({
          items: [makeTicket({ id: 1, title: 'Only visible ticket' })],
          page: 0,
          size: 50,
          total: 1,
        })),
    );

    renderList('/tickets');

    expect(await screen.findByRole('link', { name: 'Only visible ticket' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Hidden ticket' })).not.toBeInTheDocument();
  });

  it('renders the empty-state copy when the API returns zero items', async () => {
    server.use(
      http.get('/api/v1/tickets', () =>
        HttpResponse.json({ items: [], page: 0, size: 50, total: 0 })),
    );

    renderList('/tickets');

    expect(await screen.findByText('No tickets match your filters.')).toBeInTheDocument();
  });
});