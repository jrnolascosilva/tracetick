import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from '@/lib/auth';

interface RequireAuthProps {
  adminOnly?: boolean;
}

export function RequireAuth({ adminOnly = false }: RequireAuthProps) {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'pending') {
    return <p>Loading…</p>;
  }

  if (status === 'anonymous' || !user) {
    const redirectTo = encodeURIComponent(location.pathname);
    return <Navigate to={`/login?next=${redirectTo}`} replace />;
  }

  if (adminOnly && user.role !== 'TECHNICIAN') {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
