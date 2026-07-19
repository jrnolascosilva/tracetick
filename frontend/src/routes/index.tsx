import {
  Navigate,
  createBrowserRouter,
  type RouteObject,
} from 'react-router-dom';

import { AppShell } from '@/components/AppShell';
import { RequireAuth } from '@/components/RequireAuth';
import { HomePage } from '@/pages/HomePage';
import { IngestionConfigurationsPage } from '@/pages/IngestionConfigurationsPage';
import { LoginPage } from '@/pages/LoginPage';
import { NewTicketPage } from '@/pages/NewTicketPage';
import { PasswordResetPage } from '@/pages/PasswordResetPage';
import { TicketDetailPage } from '@/pages/TicketDetailPage';
import { TicketListPage } from '@/pages/TicketListPage';
import { UserAdminPage } from '@/pages/UserAdminPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/password-reset', element: <PasswordResetPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AppShell />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 'tickets', element: <TicketListPage /> },
          { path: 'tickets/new', element: <NewTicketPage /> },
          { path: 'tickets/:id', element: <TicketDetailPage /> },
          { path: 'ingestion-configurations', element: <IngestionConfigurationsPage /> },
          {
            element: <RequireAuth adminOnly />,
            children: [{ path: 'admin/users', element: <UserAdminPage /> }],
          },
          { path: '*', element: <Navigate to="/" replace /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);