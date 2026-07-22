import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, apiClient } from '@/lib/apiClient';
import type { PasswordResetConfirmRequest, PasswordResetRequest, PasswordResetResponse } from '@/lib/types';
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
});
