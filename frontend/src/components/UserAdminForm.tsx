import { useState, type FormEvent } from 'react';

import type { Role } from '@/lib/types';

interface UserAdminFormProps {
  busy: boolean;
  error: string | null;
  onSubmit: (email: string, password: string, role: Role) => Promise<void>;
}

export function UserAdminForm({ busy, error, onSubmit }: UserAdminFormProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('CUSTOMER');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit(email, password, role);
    setEmail('');
    setPassword('');
    setRole('CUSTOMER');
  }

  return (
    <form className="user-admin-form" onSubmit={handleSubmit}>
      <label>
        Email
        <input
          type="email"
          name="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
      </label>
      <label>
        Password
        <input
          type="password"
          name="password"
          required
          minLength={8}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
      </label>
      <label>
        Role
        <select value={role} onChange={(event) => setRole(event.target.value as Role)}>
          <option value="CUSTOMER">CUSTOMER</option>
          <option value="TECHNICIAN">TECHNICIAN</option>
        </select>
      </label>
      {error && <p className="user-admin-error" role="alert">{error}</p>}
      <button type="submit" disabled={busy}>
        {busy ? 'Inviting…' : 'Invite'}
      </button>
    </form>
  );
}
