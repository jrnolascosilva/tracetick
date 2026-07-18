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
