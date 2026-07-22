import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, apiClient } from '@/lib/apiClient';
import type {
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  PasswordResetResponse,
  TicketEvent,
} from '@/lib/types';
import { server } from '@/test/server';

describe('apiClient password reset', () => {
  const defaultPath = '/test-start';

  const assignSpy = vi.fn();

  beforeEach(() => {
    window.history.replaceState({}, '', defaultPath);
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        ...window.location,
        assign: assignSpy,
      },
    });
    assignSpy.mockClear();
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: window.location,
    });
    vi.restoreAllMocks();
  });

  it('requests a token with the email wire shape', async () => {
    let observed: unknown = null;

    server.use(
      http.post('/api/v1/auth/password-reset', async ({ request }) => {
        observed = await request.json();
        return HttpResponse.json<PasswordResetResponse>({ token: 'returned-token' });
      }),
    );

    const body: PasswordResetRequest = { email: 'user@example.com' };
    const response = await apiClient.requestPasswordReset(body);

    expect(response).toEqual({ token: 'returned-token' });
    expect(observed).toEqual({ email: 'user@example.com' });
  });

  it('confirms with new_password and accepts 204', async () => {
    let observed: unknown = null;

    server.use(
      http.post('/api/v1/auth/password-reset/confirm', async ({ request }) => {
        observed = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const body: PasswordResetConfirmRequest = { token: 'reset-token', new_password: 'new-password' };
    await expect(apiClient.confirmPasswordReset(body)).resolves.toBeUndefined();
    expect(observed).toEqual({ token: 'reset-token', new_password: 'new-password' });
  });

  it('throws ApiError when a reset endpoint returns 401', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset', () => new HttpResponse(null, { status: 401 })),
    );

    await expect(apiClient.requestPasswordReset({ email: 'user@example.com' })).rejects.toBeInstanceOf(
      ApiError,
    );
    expect(assignSpy).not.toHaveBeenCalled();
    expect(window.location.pathname).toBe(defaultPath);
  });
});

describe('apiClient tickets', () => {
  it('hits /tickets with no query string when params are empty', async () => {
    let observedUrl: string | null = null;
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        observedUrl = request.url;
        return HttpResponse.json({ items: [], page: 0, size: 50, total: 0 });
      }),
    );

    const result = await apiClient.listTickets();
    expect(observedUrl).not.toBeNull();
    const url = new URL(observedUrl!);
    expect(url.pathname).toBe('/api/v1/tickets');
    expect(url.search).toBe('');
    expect(result).toEqual({ items: [], page: 0, size: 50, total: 0 });
  });

  it('encodes filter params in the query string', async () => {
    let observedUrl: string | null = null;
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        observedUrl = request.url;
        return HttpResponse.json({ items: [], page: 0, size: 50, total: 0 });
      }),
    );

    await apiClient.listTickets({
      state: 'OPEN',
      severity: 'CRITICAL',
      assignee: 7,
      tag: 'service:api',
      search: 'latency',
      page: 2,
      size: 25,
    });

    expect(observedUrl).not.toBeNull();
    const url = new URL(observedUrl!);
    expect(url.searchParams.get('state')).toBe('OPEN');
    expect(url.searchParams.get('severity')).toBe('CRITICAL');
    expect(url.searchParams.get('assignee')).toBe('7');
    expect(url.searchParams.get('tag')).toBe('service:api');
    expect(url.searchParams.get('search')).toBe('latency');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('size')).toBe('25');
  });

  it('omits blank filter params from the query string', async () => {
    let observedUrl: string | null = null;
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        observedUrl = request.url;
        return HttpResponse.json({ items: [], page: 0, size: 50, total: 0 });
      }),
    );

    await apiClient.listTickets({ state: 'OPEN', search: '   ', tag: '' });
    const url = new URL(observedUrl!);
    expect(url.searchParams.get('state')).toBe('OPEN');
    expect(url.searchParams.has('search')).toBe(false);
    expect(url.searchParams.has('tag')).toBe(false);
  });

  it('forwards the sort param as a single query entry', async () => {
    let observedUrl: string | null = null;
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        observedUrl = request.url;
        return HttpResponse.json({ items: [], page: 0, size: 50, total: 0 });
      }),
    );

    await apiClient.listTickets({ sort: 'severity,desc' });
    const url = new URL(observedUrl!);
    expect(url.searchParams.get('sort')).toBe('severity,desc');
  });

  it('omits a blank sort param from the query string', async () => {
    let observedUrl: string | null = null;
    server.use(
      http.get('/api/v1/tickets', ({ request }) => {
        observedUrl = request.url;
        return HttpResponse.json({ items: [], page: 0, size: 50, total: 0 });
      }),
    );

    await apiClient.listTickets({ sort: '   ' });
    const url = new URL(observedUrl!);
    expect(url.searchParams.has('sort')).toBe(false);
  });

  it('fetches a single ticket by id', async () => {
    server.use(
      http.get('/api/v1/tickets/42', () =>
        HttpResponse.json({
          ticket: { id: 42, title: 'Latency spike' },
          events: [{ id: 1, type: 'COMMENT' }],
          watcherIds: [],
        }),
      ),
    );

    const detail = await apiClient.getTicket(42);
    expect(detail.ticket.id).toBe(42);
    expect(detail.events).toHaveLength(1);
  });

  it('creates a ticket with the create wire shape and returns the new ticket', async () => {
    let observed: { url: string; body: unknown } | null = null;
    server.use(
      http.post('/api/v1/tickets', async ({ request }) => {
        observed = { url: request.url, body: await request.json() };
        return HttpResponse.json(
          {
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
            tags: [{ key: 'service', value: 'api' }],
          },
          { status: 201 },
        );
      }),
    );

    const created = await apiClient.createTicket({
      title: 'API is down',
      description: 'All endpoints returning 500.',
      severity: 'HIGH',
      tags: [{ key: 'service', value: 'api' }],
    });

    expect(created.id).toBe(42);
    expect(created.title).toBe('API is down');
    expect(observed).not.toBeNull();
    expect(observed!.url).toContain('/api/v1/tickets');
    expect(observed!.body).toEqual({
      title: 'API is down',
      description: 'All endpoints returning 500.',
      severity: 'HIGH',
      tags: [{ key: 'service', value: 'api' }],
    });
  });

  it('throws ApiError when create-ticket returns a validation error', async () => {
    server.use(
      http.post('/api/v1/tickets', () =>
        new HttpResponse('title must not be blank', { status: 400 }),
      ),
    );

    await expect(
      apiClient.createTicket({
        title: '',
        description: 'desc',
        severity: 'MEDIUM',
        tags: [],
      }),
    ).rejects.toBeInstanceOf(ApiError);
  });

  it('still surfaces a server-side rejection when an invalid tag key reaches POST /tickets', async () => {
    server.use(
      http.post('/api/v1/tickets', () =>
        new HttpResponse('Invalid tag key: must match [a-z0-9_-]{1,32}', {
          status: 400,
        }),
      ),
    );

    await expect(
      apiClient.createTicket({
        title: 'API is down',
        description: 'All endpoints returning 500.',
        severity: 'MEDIUM',
        tags: [{ key: 'BAD KEY', value: 'api' }],
      }),
    ).rejects.toMatchObject({
      status: 400,
      message: expect.stringContaining('Invalid tag key'),
    });
  });

  it('posts a comment with the wire shape and returns the appended event', async () => {
    let observed: { url: string; body: unknown } | null = null;
    const createdEvent: TicketEvent = {
      id: 99,
      type: 'COMMENT',
      actorUserId: 7,
      payload: { body: 'Looking into it now.' },
      createdAt: '2026-01-02T04:00:00.000Z',
    };

    server.use(
      http.post('/api/v1/tickets/42/comments', async ({ request }) => {
        observed = { url: request.url, body: await request.json() };
        return HttpResponse.json(createdEvent, { status: 201 });
      }),
    );

    const event = await apiClient.addComment(42, 'Looking into it now.');

    expect(event).toEqual(createdEvent);
    expect(observed).not.toBeNull();
    expect(observed!.url).toContain('/api/v1/tickets/42/comments');
    expect(observed!.body).toEqual({ body: 'Looking into it now.' });
  });

  it('surfaces a 404 from the comments endpoint as an ApiError', async () => {
    server.use(
      http.post('/api/v1/tickets/42/comments', () =>
        new HttpResponse(null, { status: 404 }),
      ),
    );

    await expect(apiClient.addComment(42, 'hello')).rejects.toBeInstanceOf(ApiError);
  });
});
