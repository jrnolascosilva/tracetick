import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthProvider } from '@/lib/auth';
import { routes } from '@/routes';
import { server } from '@/test/server';

function renderLogin(initialEntry: string) {
  server.use(
    http.get('/api/v1/me', () => new HttpResponse(null, { status: 401 })),
  );
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });

  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  );
}

describe('LoginPage', () => {
  it('links to password reset', async () => {
    renderLogin('/login');

    expect(await screen.findByRole('link', { name: 'Forgot password?' }))
      .toHaveAttribute('href', '/password-reset');
  });

  it('preserves next on the password reset link', async () => {
    renderLogin('/login?next=%2Ftickets');

    expect(await screen.findByRole('link', { name: 'Forgot password?' }))
      .toHaveAttribute('href', '/password-reset?next=%2Ftickets');
  });
});
