import { Outlet } from 'react-router-dom';

export function AppShell() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>TraceTick</h1>
        <nav>
          <a href="/tickets">Tickets</a>
          <a href="/ingestion-configurations">Ingestion</a>
          <a href="/admin/users">Users</a>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}