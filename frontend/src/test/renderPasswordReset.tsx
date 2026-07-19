import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';

import { routes } from '@/routes';

export function renderPasswordReset(initialEntry = '/password-reset') {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });
  const user = userEvent.setup();
  const result = render(<RouterProvider router={router} />);
  return { ...result, router, user };
}