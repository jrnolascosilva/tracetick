import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderPasswordReset } from '@/test/renderPasswordReset';
import { server } from '@/test/server';

describe('PasswordResetPage', () => {
  it('requests a token and prefills confirmation', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset', () =>
        HttpResponse.json({ token: 'returned-token' })),
    );
    const { user } = renderPasswordReset();

    await user.type(screen.getByLabelText('Email'), 'alex@example.com');
    await user.click(screen.getByRole('button', { name: 'Continue' }));

    expect(await screen.findByRole('heading', { name: 'Choose a new password' }))
      .toBeInTheDocument();
    expect(screen.getByLabelText('Reset token')).toHaveValue('returned-token');
  });

  it('opens confirmation from a token query parameter', () => {
    renderPasswordReset('/password-reset?token=from-email');

    expect(screen.getByRole('heading', { name: 'Choose a new password' }))
      .toBeInTheDocument();
    expect(screen.getByLabelText('Reset token')).toHaveValue('from-email');
  });

  it('does not submit mismatched passwords', async () => {
    let confirmationRequests = 0;
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', () => {
        confirmationRequests += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderPasswordReset('/password-reset?token=token');

    await user.type(screen.getByLabelText('New password'), 'new-password');
    await user.type(screen.getByLabelText('Confirm new password'), 'different-password');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.');
    expect(confirmationRequests).toBe(0);
  });

  it('confirms and preserves next on the sign-in action', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', () =>
        new HttpResponse(null, { status: 204 })),
    );
    const { user } = renderPasswordReset(
      '/password-reset?token=token&next=%2Ftickets',
    );

    await user.type(screen.getByLabelText('New password'), 'new-password');
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByRole('heading', { name: 'Password updated' }))
      .toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to sign in' }))
      .toHaveAttribute('href', '/login?next=%2Ftickets');
  });

  it('shows an expired-token error without leaving the public route', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', () =>
        new HttpResponse(null, { status: 410 })),
    );
    const { router, user } = renderPasswordReset('/password-reset?token=expired');

    await user.type(screen.getByLabelText('New password'), 'new-password');
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('This reset token has expired.');
    expect(router.state.location.pathname).toBe('/password-reset');
  });
});