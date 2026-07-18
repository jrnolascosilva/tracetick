import { Link, Outlet } from 'react-router-dom';

import { useAuth } from '@/lib/auth';

export function AppShell() {
  const auth = useAuth();

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>TraceTick</h1>
        <nav>
          <Link to="/tickets">Tickets</Link>
          <Link to="/ingestion-configurations">Ingestion</Link>
          {auth.user?.role === 'TECHNICIAN' && <Link to="/admin/users">Users</Link>}
        </nav>
        <div className="app-header-user">
          {auth.user && (
            <>
              <span>{auth.user.email}</span>
              <button type="button" onClick={() => void auth.logout()}>Sign out</button>
            </>
          )}
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
