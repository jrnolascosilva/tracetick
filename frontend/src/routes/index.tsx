import { Navigate, createBrowserRouter } from 'react-router-dom';

import { AppShell } from '@/components/AppShell';
import { HomePage } from '@/pages/HomePage';
import { IngestionConfigurationsPage } from '@/pages/IngestionConfigurationsPage';
import { LoginPage } from '@/pages/LoginPage';
import { NewTicketPage } from '@/pages/NewTicketPage';
import { TicketDetailPage } from '@/pages/TicketDetailPage';
import { TicketListPage } from '@/pages/TicketListPage';
import { UserAdminPage } from '@/pages/UserAdminPage';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'tickets', element: <TicketListPage /> },
      { path: 'tickets/new', element: <NewTicketPage /> },
      { path: 'tickets/:id', element: <TicketDetailPage /> },
      { path: 'ingestion-configurations', element: <IngestionConfigurationsPage /> },
      { path: 'admin/users', element: <UserAdminPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);